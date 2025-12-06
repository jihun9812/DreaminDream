package com.dreamindream.app.ui.aireport

import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.WeekEntry

data class AIReportUiState(
    // ... (기존 필드들 유지) ...
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val showReportCard: Boolean = false,
    val showEmptyState: Boolean = false,

    // Header Info
    val weekLabel: String = "",

    // Basic Data
    val keywordsLine: String = "",
    val analysisHtml: String = "",
    val analysisJson: String = "",
    val analysisImageUrl: String = "",

    // Charts
    val emotionLabels: List<String> = emptyList(),
    val emotionDist: List<Float> = emptyList(),
    val themeLabels: List<String> = emptyList(),
    val themeDist: List<Float> = emptyList(),

    // Premium Data
    val symbolDetails: List<Map<String, String>> = emptyList(),
    val themeAnalysis: Map<String, String> = emptyMap(),

    // ★ [추가됨] 미래 예측 데이터 (3개 문단)
    val futurePredictions: List<String> = emptyList(),

    // 상단 통계
    val dominantEmotion: String = "-",
    val thisWeekDreamCount: Int = 0,
    val dreamCountTrend: Int = 0,
    val dreamScore: Int = 0,
    val dreamGrade: String = "-",

    // PRO Logic
    val proButtonText: String = "",
    val proButtonEnabled: Boolean = false,
    val proButtonAlpha: Float = 1f,
    val isProSpinnerVisible: Boolean = false,
    val isProCompleted: Boolean = false,

    // Navigation & Etc
    val userName: String = "",
    val navigateToSubscription: Boolean = false,
    val targetWeekKey: String = FirestoreManager.thisWeekKey(),
    val showEmptyDialog: Boolean = false,
    val showHistorySheet: Boolean = false,
    val historyWeeks: List<String> = emptyList(),
    val showChartInfoDialog: Boolean = false,
    val chartInfoMessage: String = "",
    val snackbarMessage: String? = null,

    val showDreamSelectionDialog: Boolean = false,
    val availableDreamsForSelection: List<WeekEntry> = emptyList()
)