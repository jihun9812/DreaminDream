package com.dreamindream.app.ui.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsUiState(
    // 화면 상태
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val saving: Boolean = false,
    val toastMessage: String? = null,

    // 프로필 데이터
    val nickname: String = "",
    val birthIso: String = "",     // YYYY-MM-DD
    val gender: String = "",
    val mbti: String = "",
    val birthTimeCode: String = "none", // none, 23_01, 01_03 ...
    val birthTimeLabel: String = "",

    // ★ 국가 설정 (New)
    val countryCode: String = "",
    val countryName: String = "",
    val countryFlag: String = "",

    // ★ 프로필 잠금 (New) - 이름, 생년월일, 국가는 1회 설정 후 변경 불가
    val isProfileLocked: Boolean = false,

    // 계산된 정보
    val age: Int = -1,
    val zodiacSign: String = "",    // 서양 별자리
    val zodiacAnimal: String = "",  // 십이지신

    // 통계 데이터
    val gptUsedToday: Int = 0,
    val dreamTotalCount: Int = 0,

    // 계정 연결 상태
    val email: String = "",
    val accountProviderLabel: String = "",
    val isGuest: Boolean = true,
    val googleButtonLabel: String = "",
    val googleButtonEnabled: Boolean = true,
    val linkInProgress: Boolean = false,

    // 프리미엄 상태
    val isPremium: Boolean = false
)