package com.dreamindream.app.ui.calendar

import java.time.LocalDate
import java.time.YearMonth
import com.dreamindream.app.DreamEntry   // ✅ 추가

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val counts: Map<LocalDate, Int> = emptyMap(), // 일자별 기록 수
    val dreams: List<DreamEntry> = emptyList(),   // 선택일 인라인 리스트
    val holidayLabel: String? = null,             // 필요 시 노출
    val showResult: String? = null,               // 결과 보기 다이얼로그 내용
    val askDeleteAt: Int? = null                  // 삭제 확인용 인덱스
)
