package com.dreamindream.app.ui.settings

import androidx.compose.runtime.Immutable

/**
 * Settings 화면의 UI 상태를 정의합니다.
 * Immutable 어노테이션을 통해 Compose 리컴포지션 성능을 최적화합니다.
 */
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

    // 계산된 정보 (별자리, 띠, 나이)
    val age: Int = -1,
    val zodiacSign: String = "",    // 서양 별자리 (아이콘 + 이름)
    val zodiacAnimal: String = "",  // 십이지신 (아이콘 + 이름)




    // 통계 데이터
    val gptUsedToday: Int = 0,
    val dreamTotalCount: Int = 0,

    // 계정 연결 상태
    val email: String = "",
    val accountProviderLabel: String = "", // "Google", "Guest" 등
    val isGuest: Boolean = true,
    val googleButtonLabel: String = "",
    val googleButtonEnabled: Boolean = true,
    val linkInProgress: Boolean = false,
    //프리미엄감지 다이아몬드 뱃지
    val isPremium: Boolean = false
)