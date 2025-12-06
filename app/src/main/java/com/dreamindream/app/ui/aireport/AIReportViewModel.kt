package com.dreamindream.app.ui.aireport

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.R
import com.dreamindream.app.SubscriptionManager
import com.dreamindream.app.WeeklyReportData
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AIReportViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val res get() = app.resources
    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    private val _uiState = MutableStateFlow(AIReportUiState())
    val uiState: StateFlow<AIReportUiState> = _uiState.asStateFlow()

    private var targetWeekKey: String = FirestoreManager.thisWeekKey()
    private var analysisJob: Job? = null

    private val context = application.applicationContext
    private val loadingMessages =
        context.resources.getStringArray(R.array.loading_messages)

    fun onStart(weekKey: String?) {
        val target = weekKey ?: FirestoreManager.thisWeekKey()
        _uiState.update { it.copy(targetWeekKey = target) }
        setRandomLoadingMessage()
        reloadForWeekInternal(target, retryCount = 0)
    }

    private fun setRandomLoadingMessage() {
        _uiState.update { it.copy(loadingMessage = loadingMessages.random()) }
    }

    private fun reloadForWeekInternal(weekKey: String, retryCount: Int) {
        val userId = uid ?: return

        // 안전장치 1: 3회 이상 재시도 시 중단
        if (retryCount > 3) {
            _uiState.update {
                it.copy(isLoading = false, isProSpinnerVisible = false, snackbarMessage = "데이터 동기화 지연. 잠시 후 다시 시도해주세요.")
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, showEmptyState = false, showReportCard = false) }

        // 안전장치 2: 타임아웃
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            delay(30000)
            if (_uiState.value.isLoading || _uiState.value.isProSpinnerVisible) {
                _uiState.update {
                    it.copy(isLoading = false, isProSpinnerVisible = false, snackbarMessage = "분석 시간이 초과되었습니다.")
                }
            }
        }

        FirestoreManager.countDreamEntriesForWeek(userId, weekKey) { currentCount ->
            if (currentCount < 2) {
                analysisJob?.cancel()
                _uiState.update {
                    it.copy(showReportCard = false, showEmptyState = true, isLoading = false, weekLabel = formatWeekLabel(weekKey))
                }
                return@countDreamEntriesForWeek
            }

            FirestoreManager.loadWeeklyReportFull(app, userId, weekKey) { data ->
                val isSubscribed = SubscriptionManager.isSubscribedNow()

                // 데이터 누락 여부 확인
                val isDataMissing = if (data.tier == "pro") {
                    data.analysisJson.isBlank()
                } else {
                    data.analysis.isBlank()
                }

                // 조건: 데이터가 오래됨 OR 꿈 개수 변경됨 OR 데이터가 누락됨
                if (data.stale || data.sourceCount != currentCount || isDataMissing) {

                    setRandomLoadingMessage()

                    // ★ [수정됨] 람다 반환 타입 오류 해결
                    val onComplete = { success: Boolean ->
                        if (success) {
                            // 저장 후 읽기 지연(Latency) 방지 2초 대기
                            viewModelScope.launch {
                                delay(2000)
                                reloadForWeekInternal(weekKey, retryCount + 1)
                            }
                            Unit // ★ 중요: Job 객체 대신 Unit을 반환하여 타입 에러 해결
                        } else {
                            analysisJob?.cancel()
                            showErrorState()
                        }
                    }

                    if (isSubscribed || data.tier == "pro") {
                        FirestoreManager.generateProReport(userId, weekKey, app, onComplete)
                    } else {
                        FirestoreManager.aggregateDreamsForWeek(userId, weekKey, app, onComplete)
                    }
                    return@loadWeeklyReportFull
                }

                // 정상 로드
                analysisJob?.cancel()
                bindDataToUi(data, weekKey, currentCount)
            }
        }
    }

    private fun showErrorState() {
        _uiState.update { it.copy(isLoading = false, isProSpinnerVisible = false, snackbarMessage = res.getString(R.string.msg_analysis_failed)) }
    }

    private fun bindDataToUi(data: WeeklyReportData, weekKey: String, count: Int) {
        val currentEmotionLabels = runCatching { res.getStringArray(R.array.emotion_labels).toList() }.getOrElse { emptyList() }
        val currentThemeLabels = runCatching { res.getStringArray(R.array.theme_labels).toList() }.getOrElse { emptyList() }

        val finalEmotionLabels = if (currentEmotionLabels.size == data.emotionDist.size) currentEmotionLabels else data.emotionLabels
        val finalThemeLabels = if (currentThemeLabels.size == data.themeDist.size) currentThemeLabels else data.themeLabels

        val maxIdx = data.emotionDist.indices.maxByOrNull { data.emotionDist[it] } ?: 0
        val domEmo = finalEmotionLabels.getOrElse(maxIdx) { "-" }

        val kwString = data.keywords.take(3).joinToString(" ") { "#$it" }

        // Pro 여부 판단
        val isPro = data.tier == "pro" && data.analysisJson.isNotBlank()

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isProSpinnerVisible = false,
                showReportCard = true,
                showEmptyState = false,
                weekLabel = formatWeekLabel(weekKey),
                keywordsLine = kwString,
                analysisHtml = data.analysis,
                analysisJson = data.analysisJson,
                emotionLabels = finalEmotionLabels,
                emotionDist = data.emotionDist,
                themeLabels = finalThemeLabels,
                themeDist = data.themeDist,
                dominantEmotion = domEmo,
                thisWeekDreamCount = count,
                dreamScore = data.score,
                dreamGrade = data.grade,
                isProCompleted = isPro,
                proButtonEnabled = true

            )
        }
    }

    private fun formatWeekLabel(weekKey: String): String {
        return if (weekKey == FirestoreManager.thisWeekKey())
            res.getString(R.string.report_title_this_week)
        else weekKey
    }

    fun onHistoryClicked() {
        val userId = uid ?: return
        FirestoreManager.listWeeklyReportKeys(userId, 20) { keys ->
            if (keys.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = res.getString(R.string.msg_no_history)) }
            } else {
                _uiState.update { it.copy(showHistorySheet = true, historyWeeks = keys) }
            }
        }
    }

    fun onHistorySheetDismiss() { _uiState.update { it.copy(showHistorySheet = false) } }
    fun onHistoryWeekPicked(k: String) { onHistorySheetDismiss(); onStart(k) }

    fun onChartInfoClicked() {
        _uiState.update { it.copy(showChartInfoDialog = true, chartInfoMessage = res.getString(R.string.chart_info_msg)) }
    }
    fun onChartInfoDialogDismiss() { _uiState.update { it.copy(showChartInfoDialog = false) } }

    fun onSnackbarShown() { _uiState.update { it.copy(snackbarMessage = null) } }

    fun onProButtonClicked(): Boolean {
        if (!SubscriptionManager.isSubscribedNow()) {
            _uiState.update { it.copy(navigateToSubscription = true) }
            return false
        }
        return true
    }

    fun onSubscriptionNavigationHandled() { _uiState.update { it.copy(navigateToSubscription = false) } }

    fun onProGateUnlocked() {
        if (_uiState.value.isProSpinnerVisible) return

        _uiState.update { it.copy(isProSpinnerVisible = true) }
        setRandomLoadingMessage()

        val userId = uid ?: return

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            delay(30000)
            if (_uiState.value.isProSpinnerVisible) {
                _uiState.update {
                    it.copy(isProSpinnerVisible = false, snackbarMessage = "분석 시간이 초과되었습니다. 다시 시도해주세요.")
                }
            }
        }

        FirestoreManager.generateProReport(userId, targetWeekKey, app) { success ->
            if (success) {
                viewModelScope.launch {
                    delay(2000)
                    reloadForWeekInternal(targetWeekKey, 0)
                    _uiState.update { it.copy(snackbarMessage = res.getString(R.string.msg_pro_analysis_complete)) }
                }
            } else {
                analysisJob?.cancel()
                _uiState.update { it.copy(isProSpinnerVisible = false, snackbarMessage = res.getString(R.string.pro_analysis_error)) }
            }
        }
    }
}