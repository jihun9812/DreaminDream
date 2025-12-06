package com.dreamindream.app.ui.dream

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dreamindream.app.BuildConfig
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.SubscriptionManager
import com.dreamindream.app.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class DreamViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val logTag = "DreamViewModel"
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }

    private val freeLimit = 1
    private val adLimit = 2
    private val totalLimit = freeLimit + adLimit

    private val prefKeyDate = "dream_last_date"
    private val prefKeyCount = "dream_count"
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // 욕설 및 비속어 필터링 키워드
    private val bannedKeywords = listOf(
        "씨발", "개새끼", "병신", "니애미", "좆밥", "씨발롬", "애미", "창녀", "지랄", "염병",
        "fuck", "bitch", "shit", "asshole"
    )

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private var ongoingCall: Call? = null

    private val _uiState = MutableStateFlow(DreamUiState())
    val uiState: StateFlow<DreamUiState> = _uiState

    private val prefs: SharedPreferences by lazy {
        val app = getApplication<Application>()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        app.getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)
    }

    private var pendingInputForAd: String? = null

    init {
        refreshUsageCounts()
    }

    override fun onCleared() {
        super.onCleared()
        ongoingCall?.cancel()
        ongoingCall = null
    }

    // --- Public Events ---

    fun onRefreshClicked() {
        _uiState.update { it.copy(inputText = "") }
        refreshUsageCounts()
    }

    fun onResultClosed() {
        // 닫을 때 입력값과 결과값 모두 초기화
        _uiState.update { it.copy(resultText = "", inputText = "") }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // 음성 인식 상태 변경
    fun onListeningChanged(isListening: Boolean) {
        _uiState.update { it.copy(isListening = isListening) }
    }

    fun onErrorConsumed() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onLimitDialogDismiss() {
        _uiState.update { it.copy(showLimitDialog = false) }
    }

    fun onAdPromptDismiss() {
        _uiState.update { it.copy(showAdPrompt = false, showSubscriptionUpsell = false) }
        pendingInputForAd = null
    }

    fun onShareDismiss() {
        _uiState.update { it.copy(showShareDialog = false) }
    }

    fun onShareClicked() {
        _uiState.update { it.copy(showShareDialog = true) }
    }

    fun onInterpretClicked() {
        val input = uiState.value.inputText.trim()

        // 1. 유효성 검사 (실패 시 카운트 차감 없이 종료)
        if (!validateInput(input)) return

        val used = getTodayCount()
        val subscribed = SubscriptionManager.isSubscribedNow()

        // 2. 횟수 제한 검사
        if (used >= totalLimit) {
            _uiState.update { it.copy(showLimitDialog = true) }
            return
        }

        // 3. 실행 (무료 범위 내 or 구독자)
        if (subscribed || used < freeLimit) {
            startInterpret(input)
        } else {
            // 광고 유도
            pendingInputForAd = input
            val isSecondAd = used >= freeLimit + 1
            _uiState.update {
                it.copy(
                    showAdPrompt = true,
                    isSecondAd = isSecondAd,
                    showSubscriptionUpsell = isSecondAd
                )
            }
        }
    }

    fun onRewardedAdEarned() {
        val latest = pendingInputForAd ?: uiState.value.inputText.trim()
        pendingInputForAd = null
        if (validateInput(latest)) {
            _uiState.update { it.copy(showAdPrompt = false, showSubscriptionUpsell = false) }
            startInterpret(latest)
        }
    }

    // --- Private Methods ---

    private fun refreshUsageCounts() {
        val used = getTodayCount()
        val remain = (totalLimit - used).coerceAtLeast(0)
        _uiState.update {
            it.copy(todayUsedCount = used, remainingCount = remain)
        }
    }

    private fun todayKey(): String = dateFmt.format(Date())

    private fun getTodayCount(): Int {
        val today = todayKey()
        val savedDate = prefs.getString(prefKeyDate, "")
        val count = prefs.getInt(prefKeyCount, 0)
        return if (savedDate == today) count else 0
    }

    private fun increaseTodayCount() {
        val current = getTodayCount()
        val next = (current + 1).coerceAtMost(totalLimit)
        prefs.edit {
            putString(prefKeyDate, todayKey())
            putInt(prefKeyCount, next)
        }
        refreshUsageCounts()
    }

    /**
     * 강화된 입력 검증 로직
     */
    private fun validateInput(input: String): Boolean {
        val app = getApplication<Application>()

        if (input.isBlank()) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.dream_input_empty)) }
            return false
        }

        // 길이 체크
        if (input.length < 10) {
            _uiState.update {
                it.copy(errorMessage = app.getString(R.string.dream_input_too_short))
            }
            return false
        }

        // 반복 문자 체크
        if (Regex("(.)\\1{4,}").containsMatchIn(input)) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.dream_input_not_meaningful)) }
            return false
        }

        // 자음/모음만 있는 경우 체크
        val hasHangulJamo = input.any { it.code in 0x3131..0x318E }
        val hasHangulSyllables = input.any { it.code in 0xAC00..0xD7A3 }
        if (hasHangulJamo && !hasHangulSyllables) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.dream_input_not_meaningful)) }
            return false
        }

        // 의미 없는 영어 나열 체크
        val words = input.split("\\s+".toRegex())
        val hasLongGibberish = words.any { word ->
            word.length > 15 && word.matches(Regex("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$"))
        }
        if (hasLongGibberish) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.dream_input_not_meaningful)) }
            return false
        }

        // 욕설 필터링
        val lower = input.lowercase(Locale.ROOT)
        if (bannedKeywords.any { lower.contains(it) }) {
            _uiState.update {
                it.copy(errorMessage = app.getString(R.string.dream_input_clean_language))
            }
            return false
        }

        return true
    }
    @SuppressLint("StringFormatMatches")
    private fun startInterpret(prompt: String) {
        val app = getApplication<Application>()
        _uiState.update { it.copy(isLoading = true, errorMessage = null, resultText = "") }

        val content = try {
            app.getString(
                R.string.dream_prompt_template,
                prompt,
                app.getString(R.string.dream_section_message),
                app.getString(R.string.dream_section_symbols),
                app.getString(R.string.dream_section_premonition),
                app.getString(R.string.dream_section_tips_today),
                app.getString(R.string.dream_section_actions_three)
            )
        } catch (e: Exception) {
            "Analyze this dream: $prompt"
        }

        val systemInstruction = """
             You are a mystical and professional dream interpreter. 
             Do NOT give generic wellness advice (no “drink water,” “rest,” “meditate,” “breathe deeply,” etc.).
             Every action must be a realistic, doable behavior that a person could actually perform today.
             Each action must come directly from specific symbols in the user’s dream. No symbol = no action.
             The actions should feel like a subtle, grounded, mystical quest, not a ritual or fantasy.
             Keep the mystical tone in the description, but keep the action itself practical and real-world.
             No exaggeration, no impossible tasks.
        """.trimIndent()

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemInstruction))
            .put(JSONObject().put("role", "user").put("content", content))

        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.6)
            put("messages", messages)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        ongoingCall = http.newCall(request).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled()) return
                    _uiState.update { it.copy(isLoading = false, errorMessage = app.getString(R.string.dream_network_error)) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${resp.code}") }
                            return
                        }
                        val raw = resp.body?.string().orEmpty()
                        val result = try {
                            JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                        } catch (e: Exception) {
                            null
                        }

                        if (result != null) {
                            viewModelScope.launch {
                                onResultArrived(prompt, result)
                            }
                        } else {
                            _uiState.update { it.copy(isLoading = false, errorMessage = "Parsing error") }
                        }
                    }
                }
            })
        }
    }

    private fun onResultArrived(prompt: String, result: String) {
        increaseTodayCount()
        _uiState.update { it.copy(isLoading = false, resultText = result) }
        saveDream(prompt, result)
    }

    private fun saveDream(dream: String, result: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirestoreManager.saveDream(uid, dream, result)
        }

        val dayKey = todayKey()
        val prev = prefs.getString(dayKey, "[]") ?: "[]"
        val arr = try { JSONArray(prev) } catch(e: Exception) { JSONArray() }
        arr.put(JSONObject().put("dream", dream).put("result", result))
        prefs.edit { putString(dayKey, arr.toString()) }
    }
}