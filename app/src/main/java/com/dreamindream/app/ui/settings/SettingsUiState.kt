package com.dreamindream.app.ui.settings

data class SettingsUiState(
    // 화면 모드
    val isEditMode: Boolean = false,

    // 프로필
    val nickname: String = "",
    val birthIso: String = "",
    val gender: String = "",
    val mbti: String = "",
    val birthTimeCode: String = "none",
    val birthTimeLabel: String = "",

    // 계산/요약
    val age: Int = -1,
    val chineseZodiacIcon: String = "🧿",
    val chineseZodiacText: String = "",
    val westernZodiacText: String = "",

    // 앱 통계
    val gptUsedToday: Int = 0,
    val dreamTotalLocal: Int = 0,

    // 계정 링크 & 상태
    val accountStatusLabel: String = "",
    val canDeleteAccount: Boolean = false,
    val googleButtonLabel: String = "",
    val googleButtonEnabled: Boolean = true,
    val linkInProgress: Boolean = false,

    // 저장/로딩
    val saving: Boolean = false,

    // 메시지
    val toast: String? = null
)
