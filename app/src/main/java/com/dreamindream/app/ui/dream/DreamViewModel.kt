package com.dreamindream.app.ui.dream

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import com.dreamindream.app.BuildConfig
import com.dreamindream.app.R
import com.dreamindream.app.ReportWarmup
import com.dreamindream.app.FirestoreManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

class DreamViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val apiKey = BuildConfig.OPENAI_API_KEY

    // 쿼터
    private val freeLimit = 1
    private val adLimit = 2
    private val prefKeyDate = "dream_last_date"
    private val prefKeyCount = "dream_count"
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // 사용자별 prefs
    private val userId: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val prefs: SharedPreferences by lazy {
        ctx.getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)
    }

    private val _ui = MutableStateFlow(DreamUiState(remaining = remaining()))
    val ui: StateFlow<DreamUiState> = _ui

    // HTTP
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(65, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    private var ongoingCall: Call? = null
    private var pendingPrompt: String? = null

    // ====== 입력/상태 ======
    fun onInputChange(text: String) { _ui.value = _ui.value.copy(input = text) }

    fun onClickInterpret() {
        val input = _ui.value.input.trim()
        if (!validate(input)) return

        val used = getTodayCount()
        when {
            used < freeLimit -> {
                // ✅ 이름있는 인자로 고정 → 람다 버전 확정
                startInterpret(prompt = input, onCountInc = { increaseTodayCount(used) })
            }
            used < freeLimit + adLimit -> {
                pendingPrompt = input
                _ui.value = _ui.value.copy(showAdPrompt = true)
            }
            else -> _ui.value = _ui.value.copy(showLimitDialog = true)
        }
    }

    fun onAdPromptDismiss() { _ui.value = _ui.value.copy(showAdPrompt = false) }

    fun onAdRewardEarned() {
        val latest = pendingPrompt?.trim().orEmpty()
        if (validate(latest)) {
            // ✅ 이름있는 인자로 고정
            startInterpret(prompt = latest, onCountInc = { increaseTodayCount(getTodayCount()) })
        }
        _ui.value = _ui.value.copy(showAdPrompt = false)
    }

    fun onLimitDialogDismiss() { _ui.value = _ui.value.copy(showLimitDialog = false) }

    // ====== 유효성 ======
    private fun validate(input: String): Boolean {
        val bannedStarters = listOf(
            "안녕","gpt","hello","how are you","what is","tell me","chatgpt","who are you","날씨","시간",
            "씨발","개새끼","병신","니애미","좆밥","씨발롬","애미","창녀"
        )
        val isMath = Regex("^\\s*\\d+\\s*[-+*/]\\s*\\d+\\s*$").containsMatchIn(input)
        val smallTalk = bannedStarters.any { input.lowercase(Locale.ROOT).startsWith(it) }
        return when {
            input.isBlank() -> { toast(ctx.getString(R.string.dream_input_empty)); false }
            input.length < 10 || isMath || smallTalk -> {
                toast(ctx.getString(R.string.dream_input_not_meaningful)); false
            }
            else -> true
        }
    }

    // ====== OpenAI 호출 ======
    /** ✅ 이 시그니처 하나만 남겨두세요. (다른 startInterpret 오버로드/옛 버전 삭제) */
    private fun startInterpret(
        prompt: String,
        onCountInc: () -> Unit,
        attempt: Int = 1
    ) {
        if (_ui.value.isLoading) return
        setLoading(true)

        val content = ctx.getString(
            R.string.dream_prompt_template,
            prompt,
            ctx.getString(R.string.dream_section_message),
            ctx.getString(R.string.dream_section_symbols),
            ctx.getString(R.string.dream_section_premonition),
            ctx.getString(R.string.dream_section_tips_today),
            ctx.getString(R.string.dream_section_actions_three)
        )

        val body = JSONObject().apply {
            put("model", "gpt-4.1-mini")
            put("temperature", 0.85)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            put("max_tokens", 1200)
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        fun finishWith(text: String) {
            _ui.value = _ui.value.copy(
                isLoading = false,
                resultRaw = text,
                resultStyled = styleResult(text)
            )
        }
        fun retryOrFail(code: String, e: Throwable? = null) {
            val transient = (e is java.net.SocketTimeoutException) || code == "408" || code == "429" || code.startsWith("5")
            if (attempt == 1 && transient) startInterpret(prompt, onCountInc, attempt = 2)
            else finishWith(ctx.getString(R.string.dream_network_error))
        }

        ongoingCall = http.newCall(req).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled()) return
                    retryOrFail("fail", e)
                }
                override fun onResponse(call: Call, response: Response) {
                    if (call.isCanceled()) { response.close(); return }
                    response.use { resp ->
                        if (!resp.isSuccessful) { retryOrFail(resp.code.toString(), null); return }
                        val raw = resp.body?.string().orEmpty()
                        val parsed = runCatching {
                            JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content").trim()
                        }.getOrNull()
                        if (parsed.isNullOrBlank()) { retryOrFail("parse", null); return }
                        onCountInc()
                        saveDream(prompt, parsed)
                        finishWith(parsed)
                    }
                }
            })
        }
    }

    private fun setLoading(show: Boolean) { _ui.value = _ui.value.copy(isLoading = show) }

    // ====== 결과 스타일 ======
    private fun styleResult(raw: String): AnnotatedString {
        val cleaned = raw.ifBlank { ctx.getString(R.string.dream_result_empty) }
            .replace(Regex("(?m)^\\s*#{1,4}\\s*"), "")
            .replace("**", "")
            .replace(Regex("`{1,3}"), "")
            .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
            .trimEnd()

        data class Sec(val regex: Regex, val head: Color, val body: Color)
        val secs = listOf(
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(ctx.getString(R.string.dream_section_message))})\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color(0xFF9BE7FF), Color(0xFFE6F7FF)),
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(ctx.getString(R.string.dream_section_symbols))}|핵심\s*포인트)\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color(0xFFFFB3C1), Color(0xFFFFE6EC)),
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(ctx.getString(R.string.dream_section_premonition))})\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color(0xFFFFD166), Color(0xFFFFF1CC)),
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(ctx.getString(R.string.dream_section_tips_today))})\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color(0xFFFFE082), Color(0xFFFFF4D6)),
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(ctx.getString(R.string.dream_section_actions_three))})\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color(0xFFA5D6A7), Color(0xFFE9F8ED))
        )
        fun matchHeader(line: String): Sec? = secs.firstOrNull { it.regex.matches(line.trim()) }

        val lines = cleaned.split('\n')
        val txt = StringBuilder(cleaned.length + 64)
        var metFirst = false
        lines.forEach { line ->
            val sec = matchHeader(line)
            if (sec != null && metFirst) txt.append('\n')
            txt.append(line.trimEnd()).append('\n')
            if (sec != null) metFirst = true
        }
        val finalText = txt.toString().trimEnd()

        return buildAnnotatedString {
            append(finalText)
            data class Hit(val start: Int, val end: Int, val sec: Sec)
            val hits = mutableListOf<Hit>()
            var idx = 0
            finalText.split('\n').forEach { line ->
                val st = idx
                val en = st + line.length
                matchHeader(line)?.let { sec -> hits += Hit(st, en, sec) }
                idx = en + 1
            }
            hits.forEach { h ->
                addStyle(SpanStyle(color = h.sec.head, fontWeight = FontWeight.Bold), h.start, h.end)
            }
            for (i in hits.indices) {
                val bodyStart = hits[i].end + 1
                val bodyEnd   = if (i + 1 < hits.size) hits[i + 1].start - 1 else finalText.length
                if (bodyStart < bodyEnd) addStyle(SpanStyle(color = hits[i].sec.body), bodyStart, bodyEnd)
            }
        }
    }

    // ====== 카운터/저장 ======
    private fun todayKey(): String = dateFmt.format(Date())
    private fun getTodayCount(): Int {
        val today = todayKey()
        val savedDate = prefs.getString(prefKeyDate, "")
        val count = prefs.getInt(prefKeyCount, 0)
        return if (savedDate == today) count else 0
    }
    private fun increaseTodayCount(current: Int) {
        prefs.edit().apply {
            putString(prefKeyDate, todayKey())
            putInt(prefKeyCount, (current + 1).coerceAtMost(freeLimit + adLimit))
        }.apply()
        _ui.value = _ui.value.copy(remaining = remaining())
    }
    private fun remaining(): Int = (freeLimit + adLimit - getTodayCount()).coerceAtLeast(0)

    private fun saveDream(dream: String, result: String) {
        val dayKey = todayKey()
        val prev = prefs.getString(dayKey, "[]") ?: "[]"
        val arr = try { JSONArray(prev) } catch (_: Exception) { JSONArray() }
        if (arr.length() >= 10) { try { arr.remove(0) } catch (_: Exception) {} }
        arr.put(JSONObject().put("dream", dream).put("result", result))
        prefs.edit().putString(dayKey, arr.toString()).apply()

        if (userId.isNotBlank()) {
            runCatching { FirestoreManager.saveDream(userId, dream, result) }.onFailure { }
            runCatching { ReportWarmup.warmUpThisWeek(ctx, userId) }.onFailure { }
        }
    }

    private fun toast(msg: String) { _ui.value = _ui.value.copy(toast = msg) }
}
