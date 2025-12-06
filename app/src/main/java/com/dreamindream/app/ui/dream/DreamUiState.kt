package com.dreamindream.app.ui.dream

data class DreamUiState(
    val inputText: String = "",
    val resultText: String = "",
    val isLoading: Boolean = false,

    // 음성 인식 상태
    val isListening: Boolean = false,

    // 통계 관련
    val todayUsedCount: Int = 0,   // 오늘 사용 횟수
    val remainingCount: Int = 3,   // freeLimit + adLimit (1 + 2)

    // 다이얼로그 및 광고 상태
    val showLimitDialog: Boolean = false,
    val showAdPrompt: Boolean = false,
    val isSecondAd: Boolean = false, // true면 "두 번째 광고" -> 구독 유도 강화

    // 공유 관련
    val showShareDialog: Boolean = false,

    val showSubscriptionUpsell: Boolean = false,

    val errorMessage: String? = null
)