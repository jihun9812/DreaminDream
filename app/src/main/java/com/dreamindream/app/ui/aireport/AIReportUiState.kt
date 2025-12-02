package com.dreamindream.app.ui.aireport

import com.dreamindream.app.FirestoreManager

data class AIReportUiState(
    val isLoading: Boolean = false,

    // 카드/빈 상태 토글
    val showReportCard: Boolean = false,
    val showEmptyState: Boolean = true,

    // 헤더
    val weekLabel: String = "",
    val analysisTitle: String = "",

    // 키워드 라벨
    val keywordsLine: String = "",

    // AI 분석 (HTML 문자열, 스타일링은 Composable에서)
    val analysisHtml: String = "",

    // 감정 차트
    val emotionLabels: List<String> = emptyList(),
    val emotionDist: List<Float> = emptyList(),

    // 테마 차트
    val themeLabels: List<String> = emptyList(),
    val themeDist: List<Float> = emptyList(),

    // KPI 텍스트
    val kpiPositiveText: String = "-",
    val kpiNeutralText: String = "-",
    val kpiNegativeText: String = "-",

    // PRO 버튼 상태
    val proButtonText: String = "",
    val proButtonEnabled: Boolean = false,
    val proButtonAlpha: Float = 1f,

    // PRO 스피너
    val isProSpinnerVisible: Boolean = false,

    // PRO 진행상태
    val isProCompleted: Boolean = false,
    val isProRefreshNeeded: Boolean = false,

    // 주간/이력
    val targetWeekKey: String = FirestoreManager.thisWeekKey(),
    val autoSwitchedFromWeekKey: String? = null,
    val lastDreamCount: Int = 0,

    // 위클리 히스토리 UI
    val showEmptyDialog: Boolean = false,
    val showHistorySheet: Boolean = false,
    val historyWeeks: List<String> = emptyList(),
    val historyTotalWeeksLabel: String = "",

    // 차트 정보 다이얼로그
    val showChartInfoDialog: Boolean = false,
    val chartInfoMessage: String = "",

    // 스낵바 1회성 메시지
    val snackbarMessage: String? = null,
)
