package com.dreamindream.app.ui.fortune

import androidx.compose.runtime.Immutable

@Immutable
data class ChecklistItemUi(
    val id: Int,
    val text: String,
    val checked: Boolean
)

@Immutable
data class BasicFortuneInfo(
    val overall: String = "",
    val moneyText: String = "",
    val loveText: String = "",
    val healthText: String = "",
    val actionTip: String = ""
)

// 행운 요약 (Deep Analysis 내)
@Immutable
data class LuckySummary(
    val color: String = "",
    val item: String = "",
    val time: String = "",
    val direction: String = ""
)

// 긍정/부정 포인트
@Immutable
data class ProsCons(
    val positive: String = "",
    val negative: String = ""
)

@Immutable
data class DeepFortuneResult(
    val flowCurve: List<Int> = listOf(50, 50, 50, 50, 50, 50, 50),
    // [수정] 6시부터 11시까지 현실적인 시간대 라벨
    val timeLabels: List<String> = listOf("6AM", "9AM", "12PM", "3PM", "6PM", "9PM", "11PM"),

    // 요약 정보
    val todayKeyword: String = "",
    val luckySummary: LuckySummary = LuckySummary(),
    val prosCons: ProsCons = ProsCons(),
    val overallVerdict: String = "", // 종합 코멘트

    // 상세 분석 (텍스트)
    val moneyAnalysis: String = "",
    val loveAnalysis: String = "",
    val healthAnalysis: String = "",
    val careerAnalysis: String = "",
    val luckyFactors: String = "", // 기존 텍스트 유지 (필요시)
    val riskWarning: String = "",
    val emotionalAnalysis: String = "", // Mind 탭
    val actionGuide: String = ""
)

@Immutable
data class FortuneUiState(
    // [핵심 수정] 초기화 상태 플래그 (true로 시작하여 데이터 확인 전까지 화면 렌더링 방지)
    val isInitializing: Boolean = true,

    val isLoading: Boolean = false,
    val isDeepLoading: Boolean = false,

    val showStartButton: Boolean = true,
    val showFortuneCard: Boolean = false,

    val userName: String = "",
    val userFlag: String = "",
    val userCountryName: String = "",

    val radarChartData: Map<String, Int> = emptyMap(),
    val basicFortune: BasicFortuneInfo = BasicFortuneInfo(),

    val lottoName: String? = null,
    val lottoNumbers: String? = null,
    val luckyNumber: Int? = null,

    val checklist: List<ChecklistItemUi> = emptyList(),

    val showDeepDialog: Boolean = false,
    val deepResult: DeepFortuneResult? = null,
    val deepButtonEnabled: Boolean = false,

    val snackbarMessage: String? = null,
    val showProfileDialog: Boolean = false,

    val navigateToSubscription: Boolean = false
) {
    // 화면 전환 애니메이션을 위한 상태 키
    val screenStep: Int
        get() = when {
            isLoading -> 0
            showStartButton -> 1
            showFortuneCard -> 2
            else -> 1
        }
}