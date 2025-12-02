package com.dreamindream.app.ui.dream

import androidx.compose.ui.text.AnnotatedString

data class DreamUiState(
    val input: String = "",
    val remaining: Int = 3,            // free 1 + ad 2
    val isLoading: Boolean = false,
    val resultRaw: String = "",        // 원문
    val resultStyled: AnnotatedString = AnnotatedString(""), // 표시용
    val showAdPrompt: Boolean = false, // 보상형 광고 바텀시트
    val showLimitDialog: Boolean = false,
    val toast: String? = null
)
