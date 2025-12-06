package com.dreamindream.app.ui.aireport

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.R
import com.dreamindream.app.SubscriptionManager
import com.dreamindream.app.WeekEntry
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
    private val loadingMessages = res.getStringArray(R.array.loading_messages)

    fun onStart(weekKey: String?) {
        val target = weekKey ?: FirestoreManager.thisWeekKey()
        targetWeekKey = target
        _uiState.update { it.copy(targetWeekKey = target) }
        setRandomLoadingMessage()
        reloadForWeekInternal(target, retryCount = 0)
    }

    private fun setRandomLoadingMessage() {
        if (loadingMessages.isNotEmpty()) {
            _uiState.update { it.copy(loadingMessage = loadingMessages.random()) }
        }
    }

    private fun reloadForWeekInternal(weekKey: String, retryCount: Int) {
        val userId = uid ?: return

        // 3회 이상 재시도 실패 시 종료
        if (retryCount > 3) {
            _uiState.update { it.copy(isLoading = false, isProSpinnerVisible = false, snackbarMessage = res.getString(R.string.msg_analysis_failed)) }
            return
        }

        // 초기 로딩 UI 설정 (최초 진입 시에만)
        if (retryCount == 0) {
            _uiState.update { it.copy(isLoading = true, showEmptyState = false, showReportCard = false) }
        }

        // 타임아웃 처리
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            delay(30000)
            if (_uiState.value.isLoading) _uiState.update { it.copy(isLoading = false, snackbarMessage = "Timeout") }
        }

        FirestoreManager.countDreamEntriesForWeek(userId, weekKey) { currentCount ->
            // 1. 꿈 개수 부족 (2개 미만) -> 빈 화면 처리
            if (currentCount < 2) {
                analysisJob?.cancel()
                _uiState.update {
                    it.copy(
                        showReportCard = false,
                        showEmptyState = true,
                        isLoading = false,
                        weekLabel = formatWeekLabel(weekKey),
                        thisWeekDreamCount = currentCount
                    )
                }
                return@countDreamEntriesForWeek
            }

            // 2. 리포트 데이터 로드
            FirestoreManager.loadWeeklyReportFull(app, userId, weekKey) { data ->
                val isDataMissing = if (data.tier == "pro") data.analysisJson.isBlank() else data.analysis.isBlank()

                // ★ [핵심 수정] 데이터가 낡았거나(stale) 없으면 -> 자동으로 '기본 분석' 실행
                if (data.stale || data.sourceCount != currentCount || isDataMissing) {
                    analysisJob?.cancel()

                    // (1) UI를 '로딩 중' 상태로 업데이트 (기존 데이터가 있다면 잠깐 보여줌)
                    _uiState.update {
                        it.copy(
                            showReportCard = true,
                            isLoading = true,
                            loadingMessage = loadingMessages.random(),
                            thisWeekDreamCount = currentCount,
                            weekLabel = formatWeekLabel(weekKey),
                            // 화면 깜빡임 방지를 위해 기존 데이터 유지
                            analysisHtml = data.analysis,
                            emotionDist = data.emotionDist,
                            themeDist = data.themeDist,
                            dreamScore = data.score,
                            dreamGrade = data.grade,
                            isProCompleted = false,
                            proButtonEnabled = true
                        )
                    }

                    // (2) FirestoreManager의 '기본 분석(Basic Analysis)' 자동 호출
                    viewModelScope.launch {
                        FirestoreManager.aggregateDreamsForWeek(userId, weekKey, app) { success ->
                            if (success) {
                                // 성공 시 재귀 호출로 데이터를 다시 불러와 화면에 표시
                                reloadForWeekInternal(weekKey, retryCount + 1)
                            } else {
                                // 실패 시 에러 메시지
                                _uiState.update {
                                    it.copy(isLoading = false, snackbarMessage = res.getString(R.string.msg_analysis_failed))
                                }
                            }
                        }
                    }
                    return@loadWeeklyReportFull
                }

                analysisJob?.cancel()
                bindDataToUi(data, weekKey, currentCount)
            }
        }
    }

    private fun bindDataToUi(data: WeeklyReportData, weekKey: String, count: Int) {
        val currentEmotionLabels = runCatching { res.getStringArray(R.array.emotion_labels).toList() }.getOrElse { emptyList() }
        val finalEmotionLabels = if (currentEmotionLabels.size == data.emotionDist.size) currentEmotionLabels else data.emotionLabels

        val maxIdx = data.emotionDist.indices.maxByOrNull { data.emotionDist[it] } ?: 0
        val domEmo = finalEmotionLabels.getOrElse(maxIdx) { "-" }
        val kwString = data.keywords.take(2).joinToString("  ") { "#$it" }
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
                themeLabels = data.themeLabels,
                themeDist = data.themeDist,
                dominantEmotion = domEmo,
                thisWeekDreamCount = count,
                dreamScore = data.score,
                dreamGrade = data.grade,
                isProCompleted = isPro,
                proButtonEnabled = true,
                symbolDetails = data.symbolDetails,
                themeAnalysis = data.themeAnalysis,
                futurePredictions = data.futurePredictions
            )
        }
    }

    private fun formatWeekLabel(weekKey: String): String {
        return if (weekKey == FirestoreManager.thisWeekKey()) res.getString(R.string.report_title_this_week) else weekKey
    }

    fun onHistoryClicked() {
        val userId = uid ?: return
        FirestoreManager.listWeeklyReportKeys(userId, 20) { keys ->
            if (keys.isEmpty()) _uiState.update { it.copy(snackbarMessage = res.getString(R.string.msg_no_history)) }
            else _uiState.update { it.copy(showHistorySheet = true, historyWeeks = keys) }
        }
    }

    fun onHistorySheetDismiss() { _uiState.update { it.copy(showHistorySheet = false) } }
    fun onHistoryWeekPicked(k: String) { onHistorySheetDismiss(); onStart(k) }

    fun onChartInfoClicked() {
        val msg = runCatching { res.getString(R.string.chart_info_msg) }.getOrDefault("Chart Data shows frequency of emotions.")
        _uiState.update { it.copy(showChartInfoDialog = true, chartInfoMessage = msg) }
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

    // ★ Entry Point for Pro Analysis
    fun onProGateUnlocked() {
        if (_uiState.value.isProSpinnerVisible) return
        val userId = uid ?: return

        // ★ 꿈이 3개 이상이면 무조건 다이얼로그 띄움 (자동 분석 방지)
        if (_uiState.value.thisWeekDreamCount >= 3) {
            _uiState.update { it.copy(isLoading = true) }
            FirestoreManager.fetchWeeklyDreams(userId, targetWeekKey) { dreams ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showDreamSelectionDialog = true,
                        availableDreamsForSelection = dreams
                    )
                }
            }
        } else {
            triggerProAnalysis(null)
        }
    }

    fun onDreamSelectionDialogDismiss() { _uiState.update { it.copy(showDreamSelectionDialog = false) } }

    fun onDreamsSelectedForAnalysis(selectedDreams: List<WeekEntry>) {
        onDreamSelectionDialogDismiss()
        triggerProAnalysis(selectedDreams)
    }

    private fun triggerProAnalysis(selectedDreams: List<WeekEntry>?) {
        _uiState.update { it.copy(isProSpinnerVisible = true) }
        setRandomLoadingMessage()
        val userId = uid ?: return

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            delay(40000)
            if (_uiState.value.isProSpinnerVisible) {
                _uiState.update { it.copy(isProSpinnerVisible = false, snackbarMessage = "Timeout") }
            }
        }

        FirestoreManager.generateProReport(userId, targetWeekKey, app, selectedDreams) { success ->
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