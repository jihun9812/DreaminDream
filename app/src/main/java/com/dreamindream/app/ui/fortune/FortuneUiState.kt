package com.dreamindream.app.ui.fortune

data class ChecklistItemUi(
    val id: Int,
    val text: String,
    val checked: Boolean
)

data class FortuneSectionUi(
    val key: String,
    val titleResId: Int, // 리소스 ID로 관리하여 다국어 지원
    val score: Int?,
    val colorInt: Int,
    val body: String,
    val isLotto: Boolean = false
)

data class DeepFortuneResult(
    val flowCurve: List<Int> = listOf(50, 50, 50, 50, 50, 50, 50),
    val timeLabels: List<String> = listOf("6AM", "9AM", "12PM", "3PM", "6PM", "9PM", "12AM"),
    val highlights: List<String> = emptyList(),
    val riskAndOpportunity: String = "",
    val solution: String = "",
    val tomorrowPreview: String = ""
)

data class FortuneUiState(
    val isLoading: Boolean = false,
    val isDeepLoading: Boolean = false,

    val showStartButton: Boolean = true,
    val showFortuneCard: Boolean = false,

    val userName: String = "",

    val radarChartData: Map<String, Int> = emptyMap(),
    val oneLineSummary: String = "",

    val keywords: List<String> = emptyList(),
    val luckyColorHex: String = "#FFD54F",
    val luckyNumber: Int? = null,
    val luckyTime: String = "",
    val luckyDirection: String = "",

    val checklist: List<ChecklistItemUi> = emptyList(),
    val sections: List<FortuneSectionUi> = emptyList(),

    // 레이더 차트 클릭 시 상세 다이얼로그 상태
    val showRadarDetail: Boolean = false,

    val deepButtonEnabled: Boolean = false,
    val showDeepDialog: Boolean = false,
    val deepResult: DeepFortuneResult? = null,

    val snackbarMessage: String? = null,
    val showProfileDialog: Boolean = false,
    val sectionDialog: FortuneSectionUi? = null,

    val navigateToSubscription: Boolean = false
)