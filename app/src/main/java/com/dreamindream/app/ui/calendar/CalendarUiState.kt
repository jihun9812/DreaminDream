package com.dreamindream.app.ui.calendar

import java.time.LocalDate

data class InlineDream(
    val index: Int,
    val preview: String,
    val result: String,
    val originalDream: String // ✅ 공유 기능을 위해 추가됨
)

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val monthTitle: String = "",
    val dreamListTitle: String = "",
    val dreamsForSelectedDay: List<InlineDream> = emptyList(),
    val isDreamListEmpty: Boolean = true,

    // Firestore 동기화 후 캘린더 뷰 갱신용 토큰
    val calendarRefreshToken: Int = 0
)