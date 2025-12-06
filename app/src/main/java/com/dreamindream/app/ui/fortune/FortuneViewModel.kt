package com.dreamindream.app.ui.fortune

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.FortuneApi
import com.dreamindream.app.FortuneStorage
import com.dreamindream.app.R
import com.dreamindream.app.SubscriptionManager
import com.dreamindream.app.lottery.generateLotteryNumbers
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import kotlin.random.Random

class FortuneViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    private val storage = FortuneStorage(ctx)
    private val api = FortuneApi(ctx, storage)
    private val internalPrefs = storage.prefs

    // [핵심] API 중복 호출 방지를 위한 플래그
    @Volatile private var isDeepFetchingInProgress = false

    private fun getUserPrefs() = ctx.getSharedPreferences(
        "dream_profile_" + (FirebaseAuth.getInstance().currentUser?.uid ?:
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)),
        Context.MODE_PRIVATE
    )

    private val _uiState = MutableStateFlow(FortuneUiState(isInitializing = true))
    val uiState: StateFlow<FortuneUiState> = _uiState.asStateFlow()
    private var lastPayload: JSONObject? = null

    init {
        storage.syncProfileFromFirestore {
            refreshConfig()
            checkInitialState()
        }
    }

    fun refreshConfig() {
        val prefs = getUserPrefs()
        val countryName = prefs.getString("country_name", "") ?: ""
        val countryFlag = prefs.getString("country_flag", "") ?: ""
        val countryCode = prefs.getString("country_code", "") ?: ""

        val displayFlag = if (countryFlag.isBlank()) "" else countryFlag
        val displayName = if (countryName.isBlank()) "" else countryName

        _uiState.update { it.copy(userFlag = displayFlag, userCountryName = displayName) }

        if (lastPayload != null && countryCode.isNotBlank()) {
            val userInfo = storage.loadUserInfoStrict()
            val seed = storage.seedForToday(userInfo)
            updateLotteryInfo(countryCode, seed)
        }
    }

    private fun checkInitialState() {
        val cached = storage.getCachedTodayPayload()
        if (cached != null) {
            lastPayload = cached
            parseAndBindBasic(cached)
            checkDeepState()
            _uiState.update { it.copy(showStartButton = false, showFortuneCard = true, isInitializing = false) }
        } else {
            _uiState.update { it.copy(showStartButton = true, showFortuneCard = false, isInitializing = false) }
        }
    }

    private fun checkDeepState() {
        val todayKey = storage.todayPersonaKey()
        val deepCached = storage.getCachedDeep(todayKey)
        if (deepCached != null) {
            parseAndBindDeep(deepCached)
        } else {
            // [안전장치] 앱 시작 시, 이미 기본 운세가 있고 구독자라면 조용히 미리 로딩 시도
            if (SubscriptionManager.isSubscribedNow() && lastPayload != null) {
                prefetchDeepAnalysis(isBackground = true)
            }
        }
    }

    fun onStartClick(context: Context) {
        if (!storage.isProfileComplete()) {
            _uiState.update { it.copy(showProfileDialog = true) }
            return
        }

        _uiState.update { it.copy(isLoading = true, showStartButton = false) }

        val userInfo = storage.loadUserInfoStrict()
        val seed = storage.seedForToday(userInfo)

        api.fetchDaily(userInfo, seed,
            onSuccess = { json ->
                lastPayload = json
                storage.cacheTodayPayload(json)
                storage.markSeenToday()
                FirestoreManager.saveDailyFortune(
                    FirebaseAuth.getInstance().currentUser?.uid ?: "guest",
                    storage.todayKey(),
                    json
                )
                parseAndBindBasic(json)

                // [안전장치] 기본 운세 로딩 성공 후, 구독자라면 즉시 백그라운드 프리페칭 시작
                if (SubscriptionManager.isSubscribedNow()) {
                    prefetchDeepAnalysis(isBackground = true)
                }

                _uiState.update { it.copy(isLoading = false, showFortuneCard = true, showStartButton = false) }
            },
            onError = { msg ->
                _uiState.update { it.copy(isLoading = false, showStartButton = true, snackbarMessage = msg) }
            }
        )
    }

    // [New] 안전한 프리페칭 함수 (백그라운드 여부에 따라 에러 처리 분기)
    private fun prefetchDeepAnalysis(isBackground: Boolean) {
        // 1. 이미 데이터가 있거나, 현재 네트워크 요청 중이라면 절대 중복 실행하지 않음
        if (_uiState.value.deepResult != null || isDeepFetchingInProgress) return

        val userInfo = storage.loadUserInfoStrict()
        val seed = storage.seedForToday(userInfo)
        val payload = lastPayload ?: return

        isDeepFetchingInProgress = true

        // 사용자가 직접 누른 경우에만 로딩 상태 표시
        if (!isBackground) {
            _uiState.update { it.copy(isDeepLoading = true) }
        }

        api.fetchDeep(userInfo, payload, seed) { deepJson ->
            isDeepFetchingInProgress = false // [중요] 락 해제

            if (deepJson != null) {
                // 성공 로직
                storage.cacheDeep(storage.todayPersonaKey(), deepJson)
                FirestoreManager.saveDeepFortune(
                    FirebaseAuth.getInstance().currentUser?.uid ?: "guest",
                    storage.todayKey(),
                    deepJson
                )
                parseAndBindDeep(deepJson)

                // 로딩창 끄고 다이얼로그 띄우기 (데이터가 있으면 UI에서 자동 감지됨)
                _uiState.update { it.copy(isDeepLoading = false, showDeepDialog = true) }
            } else {
                // 실패 로직
                if (isBackground) {
                    // 백그라운드면 사용자에게 알리지 않고 조용히 종료 (다음에 버튼 누르면 재시도됨)
                    _uiState.update { it.copy(isDeepLoading = false) }
                } else {
                    // 사용자가 기다리고 있었다면 에러 메시지 표시
                    _uiState.update { it.copy(isDeepLoading = false, snackbarMessage = ctx.getString(R.string.parse_error)) }
                }
            }
        }
    }

    // [UI Action] 사용자가 심화 분석 버튼 클릭 시
    fun onDeepAnalysisClick(context: Context) {
        // 1. 이미 데이터가 있으면(프리페칭 성공) 바로 보여줌
        if (_uiState.value.deepResult != null) {
            _uiState.update { it.copy(showDeepDialog = true) }
            return
        }

        // 2. 구독 확인
        if (!SubscriptionManager.isSubscribedNow()) {
            _uiState.update { it.copy(navigateToSubscription = true) }
            return
        }

        // 3. 현재 백그라운드에서 가져오는 중이라면?
        if (isDeepFetchingInProgress) {
            // 로딩창만 띄워주고 기다리게 함 (콜백이 오면 자동으로 다이얼로그 뜸)
            _uiState.update { it.copy(isDeepLoading = true) }
            return
        }

        // 4. 아무것도 안 하고 있었다면 지금 요청 시작 (Foreground 모드)
        prefetchDeepAnalysis(isBackground = false)
    }

    private fun parseAndBindBasic(json: JSONObject) {
        try {
            val radar = json.optJSONObject("radar")
            val radarMap = mapOf(
                "Love" to (radar?.optInt("love") ?: 50),
                "Money" to (radar?.optInt("money") ?: 50),
                "Work" to (radar?.optInt("work") ?: 50),
                "Health" to (radar?.optInt("health") ?: 50),
                "Social" to (radar?.optInt("social") ?: 50)
            )

            val basicInfo = json.optJSONObject("basic_info")
            val basicFortune = BasicFortuneInfo(
                overall = sanitizeText(basicInfo?.optString("overall") ?: ""),
                moneyText = sanitizeText(basicInfo?.optString("money_text") ?: ""),
                loveText = sanitizeText(basicInfo?.optString("love_text") ?: ""),
                healthText = sanitizeText(basicInfo?.optString("health_text") ?: ""),
                actionTip = sanitizeText(basicInfo?.optString("action_tip") ?: "")
            )

            val rawMissions = json.optJSONArray("missions")
            val todayKey = storage.todayPersonaKey()
            val checklist = if (rawMissions != null) {
                (0 until rawMissions.length()).map { index ->
                    val text = sanitizeText(rawMissions.getString(index))
                    val isChecked = internalPrefs.getBoolean("fortune_check_${todayKey}_$index", false)
                    ChecklistItemUi(index, text, isChecked)
                }
            } else emptyList()

            val userPrefs = getUserPrefs()
            val countryCode = userPrefs.getString("country_code", "KR") ?: "KR"
            val userInfo = storage.loadUserInfoStrict()
            val seed = storage.seedForToday(userInfo)
            updateLotteryInfo(countryCode, seed)

            _uiState.update {
                it.copy(
                    radarChartData = radarMap,
                    basicFortune = basicFortune,
                    checklist = checklist,
                    deepButtonEnabled = true
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(isLoading = false, snackbarMessage = ctx.getString(R.string.parse_error)) }
        }
    }

    private fun updateLotteryInfo(countryCode: String, seed: Int) {
        val lotteryData = generateLotteryNumbers(countryCode, seed)
        _uiState.update {
            if (lotteryData != null) {
                it.copy(lottoName = lotteryData.first, lottoNumbers = lotteryData.second, luckyNumber = null)
            } else {
                val rand = Random(seed)
                it.copy(lottoName = null, lottoNumbers = null, luckyNumber = rand.nextInt(1, 100))
            }
        }
    }

    private fun parseAndBindDeep(json: JSONObject) {
        val flowArr = json.optJSONArray("flow_curve")
        val flowList = if (flowArr != null && flowArr.length() >= 7) {
            (0 until 7).map { flowArr.getInt(it) }
        } else listOf(50, 55, 60, 58, 65, 70, 60)

        val summaryObj = json.optJSONObject("summary_data")
        val luckySummary = LuckySummary(
            color = sanitizeText(summaryObj?.optString("lucky_color") ?: "Gold"),
            item = sanitizeText(summaryObj?.optString("lucky_item") ?: "Watch"),
            time = sanitizeText(summaryObj?.optString("lucky_time") ?: "12 PM"),
            direction = sanitizeText(summaryObj?.optString("lucky_direction") ?: "East")
        )

        val prosConsObj = json.optJSONObject("pros_cons")
        val prosCons = ProsCons(
            positive = sanitizeText(prosConsObj?.optString("positive") ?: ""),
            negative = sanitizeText(prosConsObj?.optString("negative") ?: "")
        )

        val result = DeepFortuneResult(
            flowCurve = flowList,
            timeLabels = listOf("6AM", "9AM", "12PM", "3PM", "6PM", "9PM", "11PM"),
            todayKeyword = sanitizeText(summaryObj?.optString("keywords") ?: ""),
            luckySummary = luckySummary,
            prosCons = prosCons,
            overallVerdict = formatParagraphs(json.optString("overall_verdict")),
            moneyAnalysis = formatParagraphs(json.optString("money_analysis")),
            loveAnalysis = formatParagraphs(json.optString("love_analysis")),
            healthAnalysis = formatParagraphs(json.optString("health_analysis")),
            careerAnalysis = formatParagraphs(json.optString("career_analysis")),
            riskWarning = formatParagraphs(json.optString("risk_warning")),
            emotionalAnalysis = formatParagraphs(json.optString("emotional_analysis")),
            actionGuide = formatParagraphs(json.optString("action_guide"))
        )
        // [수정] showDeepDialog 플래그는 버튼 클릭 로직에서 제어하므로 여기선 데이터만 채워넣음
        _uiState.update { it.copy(deepResult = result) }
    }

    private fun sanitizeText(input: String): String = input.replace(";", ".").replace("- ", "").trim()
    private fun formatParagraphs(input: String): String = input.replace("\\n", "\n").trim()

    fun closeDeepDialog() { _uiState.update { it.copy(showDeepDialog = false) } }

    fun onChecklistToggle(id: Int, checked: Boolean) {
        val todayKey = storage.todayPersonaKey()
        internalPrefs.edit().putBoolean("fortune_check_${todayKey}_$id", checked).apply()
        _uiState.update { s -> s.copy(checklist = s.checklist.map { if (it.id == id) it.copy(checked = checked) else it }) }
    }

    fun onSubscriptionNavHandled() { _uiState.update { it.copy(navigateToSubscription = false) } }
    fun onProfileDialogDismiss() { _uiState.update { it.copy(showProfileDialog = false) } }
}