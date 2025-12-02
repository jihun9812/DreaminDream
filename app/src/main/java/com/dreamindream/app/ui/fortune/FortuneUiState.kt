package com.dreamindream.app.ui.fortune

data class ChecklistItemUi(
    val id: Int,
    val text: String,
    val checked: Boolean
)

data class FortuneSectionUi(
    val key: String,
    val title: String,
    val score: Int?,      // lotto는 null
    val colorInt: Int,    // score 색 (lotto는 0)
    val body: String,
    val isLotto: Boolean
)

data class SectionDialogUiState(
    val title: String,
    val score: Int,
    val colorInt: Int,
    val body: String
)

data class FortuneUiState(
    // 로딩 / 레이아웃
    val isLoading: Boolean = false,
    val showStartButton: Boolean = true,
    val startButtonEnabled: Boolean = true,
    val startButtonBreathing: Boolean = true,
    val showFortuneCard: Boolean = false,

    // 키워드 & 행운 지표
    val keywords: List<String> = emptyList(),
    val luckyColorHex: String = "#FFD54F",
    val luckyNumber: Int? = null,
    val luckyTime: String = "",

    // 감정 밸런스
    val emoPositive: Int = 60,
    val emoNeutral: Int = 25,
    val emoNegative: Int = 15,

    // 오늘 체크리스트
    val checklist: List<ChecklistItemUi> = emptyList(),

    // 섹션 카드들
    val sections: List<FortuneSectionUi> = emptyList(),

    // 기타 상태
    val hasDailyPayload: Boolean = false,
    val deepButtonEnabled: Boolean = false,
    val deepButtonLabel: String = "",
    val infoMessage: String? = null,         // "오늘 운세는 이미 확인했어요" 같은 안내

    // UI 이벤트
    val snackbarMessage: String? = null,
    val showProfileDialog: Boolean = false,
    val sectionDialog: SectionDialogUiState? = null
)
