package com.dreamindream.app.ui.aireport

import com.dreamindream.app.FirestoreManager

data class AIReportUiState(
    val isLoading: Boolean = true,
    val loadingMessage: String = "",

    val showReportCard: Boolean = false,
    val showEmptyState: Boolean = false,

    // Header Info
    val weekLabel: String = "",

    // Basic Data
    val keywordsLine: String = "",
    val analysisHtml: String = "",
    val analysisJson: String = "",

    // ★ [추가됨] 이미지 URL을 담을 변수
    val analysisImageUrl: String = "",

    // Charts
    val emotionLabels: List<String> = emptyList(),
    val emotionDist: List<Float> = emptyList(),
    val themeLabels: List<String> = emptyList(),
    val themeDist: List<Float> = emptyList(),

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

    // 사용자 이름
    val userName: String = "",

    // Navigation trigger
    val navigateToSubscription: Boolean = false,

    // Navigation/Dialogs
    val targetWeekKey: String = FirestoreManager.thisWeekKey(),
    val showEmptyDialog: Boolean = false,
    val showHistorySheet: Boolean = false,
    val historyWeeks: List<String> = emptyList(),
    val showChartInfoDialog: Boolean = false,
    val chartInfoMessage: String = "",
    val snackbarMessage: String? = null
)