package com.dreamindream.app.ui.fortune

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.FortuneApi
import com.dreamindream.app.FortuneStorage
import com.dreamindream.app.R
import com.dreamindream.app.SubscriptionManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

class FortuneViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    private val storage = FortuneStorage(ctx)
    private val api = FortuneApi(ctx, storage)
    private val prefs = storage.prefs

    private val _uiState = MutableStateFlow(FortuneUiState())
    val uiState: StateFlow<FortuneUiState> = _uiState.asStateFlow()

    private var lastPayload: JSONObject? = null

    init {
        storage.syncProfileFromFirestore {
            fetchUserName()
            checkInitialState()
        }
    }

    private fun fetchUserName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirestoreManager.getUserProfile(uid) { data ->
                val name = data?.get("name")?.toString() ?: data?.get("nickname")?.toString() ?: ""
                _uiState.update { it.copy(userName = name) }
            }
        }
    }

    private fun checkInitialState() {
        val cached = storage.getCachedTodayPayload()
        if (cached != null) {
            lastPayload = cached
            parseAndBindBasic(cached)
            checkDeepState()
        } else {
            _uiState.update { it.copy(showStartButton = true, showFortuneCard = false) }
        }
    }

    private fun checkDeepState() {
        val todayKey = storage.todayPersonaKey()
        val deepCached = storage.getCachedDeep(todayKey)

        if (deepCached != null) {
            parseAndBindDeep(deepCached)
        } else {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirestoreManager.getDeepFortune(uid, storage.todayKey()) { json ->
                if (json != null) {
                    storage.cacheDeep(todayKey, json)
                    parseAndBindDeep(json)
                }
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

                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirestoreManager.saveDailyFortune(uid, storage.todayKey(), json)
                }
                parseAndBindBasic(json)
            },
            onError = { msg ->
                _uiState.update { state ->
                    state.copy(isLoading = false, showStartButton = true, snackbarMessage = msg)
                }
            }
        )
    }

    fun onDeepAnalysisClick(context: Context) {
        if (_uiState.value.deepResult != null) {
            _uiState.update { it.copy(showDeepDialog = true) }
            return
        }

        val payload = lastPayload ?: return

        if (!SubscriptionManager.isSubscribedNow()) {
            _uiState.update { it.copy(navigateToSubscription = true) }
            return
        }

        if (_uiState.value.isDeepLoading) return

        _uiState.update { it.copy(isDeepLoading = true) }

        val userInfo = storage.loadUserInfoStrict()
        val seed = storage.seedForToday(userInfo)

        api.fetchDeep(userInfo, payload, seed) { deepJson ->
            if (deepJson != null) {
                val todayKey = storage.todayPersonaKey()
                storage.cacheDeep(todayKey, deepJson)

                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirestoreManager.saveDeepFortune(uid, storage.todayKey(), deepJson)
                }

                parseAndBindDeep(deepJson)
                _uiState.update { it.copy(isDeepLoading = false, showDeepDialog = true) }
            } else {
                _uiState.update {
                    it.copy(isDeepLoading = false, snackbarMessage = ctx.getString(R.string.parse_error))
                }
            }
        }
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

            val summary = json.optString("summary", "")
            val kws = json.optJSONArray("keywords")
            val keywords = (0 until (kws?.length() ?: 0)).map { kws!!.getString(it) }

            val lucky = json.optJSONObject("lucky")
            val color = lucky?.optString("colorHex") ?: "#FFD54F"
            val number = lucky?.optInt("number")
            val time = lucky?.optString("time") ?: "-"
            val direction = lucky?.optString("direction") ?: "-"

            val rawChecklist = json.optJSONArray("checklist")
            val todayKey = storage.todayPersonaKey()
            val checklist = if (rawChecklist != null) {
                (0 until rawChecklist.length()).mapIndexed { index, _ ->
                    val text = rawChecklist.getString(index)
                    val isChecked = prefs.getBoolean("fortune_check_${todayKey}_$index", false)
                    ChecklistItemUi(index, text, isChecked)
                }
            } else emptyList()

            // 로또 번호 파싱 (배열 -> 문자열)
            val lottoArr = json.optJSONArray("lottoNumbers")
            val lottoText = if (lottoArr != null && lottoArr.length() > 0) {
                (0 until lottoArr.length()).map { lottoArr.getInt(it) }.joinToString(", ")
            } else {
                "번호 생성 중..."
            }

            val sectionsJson = json.optJSONObject("sections")
            val sectionsList = mutableListOf<FortuneSectionUi>()

            val sectionMap = listOf(
                Triple("overall", R.string.fortune_overall, 80),
                Triple("love", R.string.fortune_love, radarMap["Love"] ?: 0),
                Triple("money", R.string.fortune_money, radarMap["Money"] ?: 0),
                Triple("work", R.string.fortune_work, radarMap["Work"] ?: 0),
                Triple("health", R.string.fortune_health, radarMap["Health"] ?: 0),
                Triple("social", R.string.fortune_social, radarMap["Social"] ?: 0),
                Triple("lotto", R.string.fortune_lotto, 0)
            )

            sectionMap.forEach { (key, titleRes, defaultScore) ->
                val s = sectionsJson?.optJSONObject(key)
                if (s != null || key == "lotto") {
                    val isLotto = key == "lotto"
                    val score = if (isLotto) null else s?.optInt("score") ?: defaultScore

                    // ★ 수정됨: 로또면 위에서 만든 lottoText 사용, 아니면 섹션 텍스트 사용
                    val body = if (isLotto) lottoText else s?.optString("text") ?: ""

                    sectionsList.add(FortuneSectionUi(
                        key = key,
                        titleResId = titleRes,
                        score = score,
                        colorInt = if (isLotto) 0 else api.scoreColor(score ?: 0),
                        body = body,
                        isLotto = isLotto
                    ))
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    showFortuneCard = true,
                    showStartButton = false,
                    radarChartData = radarMap,
                    oneLineSummary = summary,
                    keywords = keywords,
                    luckyColorHex = color,
                    luckyNumber = number,
                    luckyTime = time,
                    luckyDirection = direction,
                    checklist = checklist,
                    sections = sectionsList,
                    deepButtonEnabled = true
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(isLoading = false, snackbarMessage = ctx.getString(R.string.parse_error)) }
        }
    }

    private fun parseAndBindDeep(json: JSONObject) {
        try {
            val flowArr = json.optJSONArray("flow_curve")
            val flowList = if (flowArr != null && flowArr.length() >= 7) {
                (0 until 7).map { flowArr.getInt(it) }
            } else listOf(50, 50, 50, 50, 50, 50, 50)

            val highArr = json.optJSONArray("highlights")
            val highlights = (0 until (highArr?.length() ?: 0)).map { highArr!!.getString(it) }

            val result = DeepFortuneResult(
                flowCurve = flowList,
                highlights = highlights,
                riskAndOpportunity = json.optString("risk_opp"),
                solution = json.optString("solution"),
                tomorrowPreview = json.optString("tomorrow_preview")
            )

            _uiState.update {
                it.copy(isDeepLoading = false, deepResult = result)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(isDeepLoading = false, snackbarMessage = ctx.getString(R.string.parse_error)) }
        }
    }

    fun onRadarClick() {
        _uiState.update { it.copy(showRadarDetail = true) }
    }

    fun closeRadarDialog() {
        _uiState.update { it.copy(showRadarDetail = false) }
    }

    fun closeDeepDialog() {
        _uiState.update { it.copy(showDeepDialog = false) }
    }

    fun onSectionClick(key: String) {
        val section = _uiState.value.sections.find { it.key == key }
        _uiState.update { it.copy(sectionDialog = section) }
    }

    fun closeSectionDialog() {
        _uiState.update { it.copy(sectionDialog = null) }
    }

    fun onChecklistToggle(id: Int, checked: Boolean) {
        val todayKey = storage.todayPersonaKey()
        prefs.edit().putBoolean("fortune_check_${todayKey}_$id", checked).apply()

        _uiState.update { state ->
            val newChecklist = state.checklist.map { item ->
                if (item.id == id) item.copy(checked = checked) else item
            }
            state.copy(checklist = newChecklist)
        }
    }

    fun onSubscriptionNavHandled() { _uiState.update { it.copy(navigateToSubscription = false) } }
    fun onProfileDialogDismiss() { _uiState.update { it.copy(showProfileDialog = false) } }
    fun onSnackbarShown() { _uiState.update { it.copy(snackbarMessage = null) } }
}