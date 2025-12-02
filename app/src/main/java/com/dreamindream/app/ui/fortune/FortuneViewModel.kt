package com.dreamindream.app.ui.fortune

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dreamindream.app.AdManager
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.FortuneApi
import com.dreamindream.app.FortuneStorage
import com.dreamindream.app.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class FortuneViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    private val storage = FortuneStorage(ctx)
    private val prefs: SharedPreferences = storage.prefs

    // 텍스트/색 계산용(네트워크 X) FortuneApi – Application context로만 사용
    private val textApi = FortuneApi(ctx, storage)

    private val _uiState = MutableStateFlow(FortuneUiState())
    val uiState: StateFlow<FortuneUiState> = _uiState.asStateFlow()

    // 마지막 Daily JSON (심화 분석/다이얼로그용)
    private var lastPayload: JSONObject? = null

    // 섹션 상세 텍스트용 raw 데이터
    private val sectionRaw: MutableMap<String, SectionRaw> = mutableMapOf()

    private data class SectionRaw(
        val title: String,
        val score: Int,
        val text: String?,
        val advice: String?
    )

    init {
        // 프로필 동기화 후 초기 UI 결정
        storage.syncProfileFromFirestore {
            decideInitialUi()
        }
    }

    private fun setState(block: (FortuneUiState) -> FortuneUiState) {
        _uiState.update(block)
    }

    private fun decideInitialUi() {
        val cached = storage.getCachedTodayPayload()
        if (cached != null) {
            lastPayload = cached
            bindFromPayload(cached)
            return
        }

        if (storage.isFortuneSeenToday()) {
            // 오늘 이미 봤는데 캐시는 없는 경우 → 버튼 숨기고 안내만
            setState {
                it.copy(
                    showStartButton = false,
                    showFortuneCard = false,
                    isLoading = false,
                    deepButtonEnabled = false,
                    deepButtonLabel = ctx.getString(R.string.btn_deep_analysis)
                )
            }
        } else {
            // 첫 진입 – 물음표 버튼만 보여줌
            setState {
                it.copy(
                    showStartButton = true,
                    showFortuneCard = false,
                    isLoading = false,
                    startButtonEnabled = true,
                    startButtonBreathing = true,
                    deepButtonEnabled = false,
                    deepButtonLabel = ctx.getString(R.string.btn_deep_analysis)
                )
            }
        }
    }

    // -------------------------------
    //  시작 버튼 클릭
    // -------------------------------
    fun onStartButtonClick(activityContext: Context) {
        if (!storage.isProfileComplete()) {
            setState { it.copy(showProfileDialog = true) }
            return
        }

        if (storage.isFortuneSeenToday() && storage.getCachedTodayPayload() == null) {
            pushSnackbar(ctx.getString(R.string.toast_already_seen_today))
            setState {
                it.copy(
                    startButtonEnabled = false,
                    startButtonBreathing = false
                )
            }
            return
        }

        setState {
            it.copy(
                isLoading = true,
                showFortuneCard = true,
                showStartButton = false,
                deepButtonEnabled = false
            )
        }

        val u = storage.loadUserInfoStrict()
        val seed = storage.seedForToday(u)
        val api = FortuneApi(activityContext, storage)

        api.fetchDaily(
            u = u,
            seed = seed,
            onSuccess = { payload ->
                lastPayload = payload
                storage.cacheTodayPayload(payload)
                storage.markSeenToday()

                // Firestore에도 저장 (예전 Fragment와 동일)
                FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    FirestoreManager.saveDailyFortune(uid, storage.todayKey(), payload) {}
                }

                bindFromPayload(payload)
            },
            onError = { msg, seedPreset ->
                val (pos, neu, neg) = seedPreset
                setState {
                    it.copy(
                        isLoading = false,
                        showFortuneCard = true,
                        showStartButton = true,
                        startButtonEnabled = true,
                        startButtonBreathing = true,
                        emoPositive = pos,
                        emoNeutral = neu,
                        emoNegative = neg,
                        deepButtonEnabled = false
                    )
                }
                pushSnackbar(msg)
            }
        )
    }

    // -------------------------------
    //  Daily 결과 바인딩 → UiState
    // -------------------------------
    private fun bindFromPayload(obj: JSONObject) {
        val kwArr = obj.optJSONArray("keywords") ?: JSONArray()
        val keywords = (0 until kwArr.length())
            .mapNotNull { kwArr.optString(it)?.takeIf { s -> s.isNotBlank() } }

        val luckyObj = obj.optJSONObject("lucky") ?: JSONObject()
        val luckyColor = luckyObj.optString("colorHex", "#FFD54F")
        val luckyNumber = luckyObj.optInt("number", -1).takeIf { it > 0 }
        val luckyTime = luckyObj.optString("time").orEmpty()

        val emoObj = obj.optJSONObject("emotions") ?: JSONObject()
        val pos = emoObj.optInt("positive", 60)
        val neu = emoObj.optInt("neutral", 25)
        val neg = emoObj.optInt("negative", 15)

        // 체크리스트
        val rawChecklist = (0 until (obj.optJSONArray("checklist")?.length() ?: 0))
            .mapNotNull { idx -> obj.optJSONArray("checklist")?.optString(idx) }

        val cleanedChecklist = textApi.sanitizeChecklist(rawChecklist)
        val todayKey = storage.todayPersonaKey()
        val checklistItems = cleanedChecklist.mapIndexed { idx, label ->
            ChecklistItemUi(
                id = idx,
                text = label,
                checked = prefs.getBoolean("fortune_check_${todayKey}_$idx", false)
            )
        }

        // 섹션 카드들
        val sectionsJson = obj.optJSONObject("sections") ?: JSONObject()
        val lottoNums = obj.optJSONArray("lottoNumbers")
        sectionRaw.clear()
        val sections = mutableListOf<FortuneSectionUi>()

        fun addSection(key: String, titleRes: Int) {
            val title = ctx.getString(titleRes)
            val sObj = sectionsJson.optJSONObject(key) ?: JSONObject()
            val score = sObj.optInt("score", -1).coerceIn(40, 100)

            val isLotto = key == "lotto"
            val color = if (isLotto || score < 0) 0 else textApi.scoreColor(score)

            val body = if (isLotto) {
                if (lottoNums != null && lottoNums.length() == 6) {
                    val arr = (0 until 6).map { lottoNums.optInt(it) }.sorted()
                    ctx.getString(R.string.label_lotto_numbers, arr.joinToString(", "))
                } else {
                    ctx.getString(R.string.label_lotto_numbers_dash)
                }
            } else {
                val t = sObj.optString("text").ifBlank { sObj.optString("advice") }.trim()
                if (t.isNotBlank()) t else ctx.getString(R.string.section_body_fallback)
            }

            sections += FortuneSectionUi(
                key = key,
                title = title,
                score = if (isLotto) null else score,
                colorInt = color,
                body = body,
                isLotto = isLotto
            )

            if (!isLotto) {
                sectionRaw[key] = SectionRaw(
                    title = title,
                    score = score,
                    text = sObj.optString("text").takeIf { it.isNotBlank() },
                    advice = sObj.optString("advice").takeIf { it.isNotBlank() }
                )
            }
        }

        addSection("overall", R.string.section_overall)
        addSection("love", R.string.section_love)
        addSection("study", R.string.section_study)
        addSection("work", R.string.section_work)
        addSection("money", R.string.section_money)
        addSection("lotto", R.string.section_lotto)

        setState {
            it.copy(
                isLoading = false,
                showFortuneCard = true,
                showStartButton = false,
                startButtonEnabled = false,
                startButtonBreathing = false,
                keywords = keywords,
                luckyColorHex = luckyColor,
                luckyNumber = luckyNumber,
                luckyTime = luckyTime,
                emoPositive = pos,
                emoNeutral = neu,
                emoNegative = neg,
                checklist = checklistItems,
                sections = sections,
                hasDailyPayload = true,
                deepButtonEnabled = true,
                deepButtonLabel = ctx.getString(R.string.btn_deep_analysis),
                infoMessage = null
            )
        }
    }

    // -------------------------------
    //  체크리스트 토글
    // -------------------------------
    fun onChecklistToggle(id: Int, checked: Boolean) {
        val todayKey = storage.todayPersonaKey()
        prefs.edit().putBoolean("fortune_check_${todayKey}_$id", checked).apply()
        setState { state ->
            state.copy(
                checklist = state.checklist.map {
                    if (it.id == id) it.copy(checked = checked) else it
                }
            )
        }
    }

    // -------------------------------
    //  섹션 카드 클릭 → 상세 다이얼로그
    // -------------------------------
    fun onSectionClicked(sectionKey: String) {
        val raw = sectionRaw[sectionKey] ?: return
        val color = textApi.scoreColor(raw.score)
        val body = textApi.buildSectionDetails(
            title = raw.title,
            score = raw.score,
            text = raw.text,
            advice = raw.advice
        )
        setState {
            it.copy(
                sectionDialog = SectionDialogUiState(
                    title = raw.title,
                    score = raw.score,
                    colorInt = color,
                    body = body
                )
            )
        }
    }

    fun onSectionDialogDismiss() {
        setState { it.copy(sectionDialog = null) }
    }

    // -------------------------------
    //  심화 분석 버튼
    // -------------------------------
    fun onDeepButtonClick(activityContext: Context) {
        val daily = lastPayload
        if (daily == null) {
            pushSnackbar(ctx.getString(R.string.toast_run_fortune_first))
            return
        }

        val personaKey = storage.todayPersonaKey()
        val prefKey = "fortune_deep_unlocked_$personaKey"
        val alreadyUnlocked = prefs.getBoolean(prefKey, false) || AdManager.isPremium

        if (alreadyUnlocked) {
            openDeepNow(activityContext, daily)
        } else {
            // 광고 게이트 오픈 → 보상 후 심화 분석 실행
            AdManager.openGate {
                prefs.edit().putBoolean(prefKey, true).apply()
                openDeepNow(activityContext, daily)
            }
        }
    }

    private fun openDeepNow(activityContext: Context, daily: JSONObject) {
        val todayPersonaKey = storage.todayPersonaKey()
        val cached = storage.getCachedDeep(todayPersonaKey)
        val api = FortuneApi(activityContext, storage)

        if (cached != null) {
            api.showDeepDialog(activityContext, cached, daily)
            return
        }

        setState {
            it.copy(
                deepButtonEnabled = false,
                deepButtonLabel = ctx.getString(R.string.deep_generating_label)
            )
        }

        val u = storage.loadUserInfoStrict()
        val seed = storage.seedForToday(u)

        api.fetchDeep(u, daily, seed) { deep ->
            viewModelScope.launch {
                val obj = deep ?: JSONObject()
                storage.cacheDeep(todayPersonaKey, obj)
                setState {
                    it.copy(
                        deepButtonEnabled = true,
                        deepButtonLabel = ctx.getString(R.string.deep_button_label)
                    )
                }
                api.showDeepDialog(activityContext, obj, daily)
            }
        }
    }

    // -------------------------------
    //  프로필 다이얼로그 / 스낵바
    // -------------------------------
    fun onProfileDialogDismiss() {
        setState { it.copy(showProfileDialog = false) }
    }

    private fun pushSnackbar(msg: String) {
        setState { it.copy(snackbarMessage = msg) }
    }

    fun onSnackbarShown() {
        setState { it.copy(snackbarMessage = null) }
    }
}
