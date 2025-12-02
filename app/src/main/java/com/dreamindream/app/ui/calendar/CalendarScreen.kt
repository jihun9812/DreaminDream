package com.dreamindream.app.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dreamindream.app.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ✅ 공통 배너 스캐폴드
import com.dreamindream.app.AdPageScaffold
// ✅ DreamEntry 모델
import com.dreamindream.app.DreamEntry

@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val locale: Locale = ctx.resources.configuration.locales[0]
    val isArabic = remember { locale.language == "ar" }

    val monthTitle = remember(ui.month, locale) {
        DateTimeFormatter.ofPattern(ctx.getString(R.string.fmt_month_year), locale).format(ui.month)
    }

    AdPageScaffold(adUnitRes = R.string.ad_unit_calendar_banner) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad) // ⬅⬅ 하단 배너 높이 자동 반영
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF17212B)))
                )
                .systemBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        )
        {
            // 월 이동 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { vm.prevMonth() },
                    modifier = Modifier
                        .size(36.dp)
                        .let { if (isArabic) it.then(Modifier.scale(scaleX = -1f, scaleY = 1f)) else it }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.prev_month),
                        tint = Color(0xFFD6E5F6)
                    )
                }
                Text(
                    text = monthTitle,
                    color = Color(0xFFD6E5F6),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
                IconButton(
                    onClick = { vm.nextMonth() },
                    modifier = Modifier
                        .size(36.dp)
                        .let { if (isArabic) it.then(Modifier.scale(scaleX = -1f, scaleY = 1f)) else it }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = stringResource(R.string.next_month),
                        tint = Color(0xFFD6E5F6)
                    )
                }
            }

            // 공휴일 라벨(옵션)
            AnimatedVisibility(visible = !ui.holidayLabel.isNullOrBlank()) {
                Text(
                    text = ui.holidayLabel ?: "",
                    color = Color(0xFFEB5D56),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // 달력 카드
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    // 요일 헤더
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            R.string.weekday_sun to Color(0xFFFF6B6B),
                            R.string.weekday_mon to Color(0xFFC6D4DF),
                            R.string.weekday_tue to Color(0xFFC6D4DF),
                            R.string.weekday_wed to Color(0xFFC6D4DF),
                            R.string.weekday_thu to Color(0xFFC6D4DF),
                            R.string.weekday_fri to Color(0xFFC6D4DF),
                            R.string.weekday_sat to Color(0xFF6FA8FF)
                        ).forEach { (id, c) ->
                            Text(
                                text = stringResource(id),
                                color = c,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(40.dp)
                            )
                        }
                    }

                    // 날짜 그리드(7x6)
                    val days = remember(ui.month) { buildMonthGrid(ui.month) }
                    LazyVerticalGrid(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        columns = GridCells.Fixed(7),
                        userScrollEnabled = false
                    ) {
                        items(days) { cell ->
                            DayCell(
                                day = cell,
                                selected = ui.selected,
                                counts = ui.counts,
                                onClick = { d -> vm.select(d) }
                            )
                        }
                    }
                }
            }

            // 리스트 타이틀
            val dowName = remember(ui.selected, locale) {
                ui.selected.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
            }
            Text(
                text = stringResource(R.string.dream_list_title, ui.selected.toString(), dowName),
                color = Color(0xFFFBDF86),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // 인라인 리스트
            if (ui.dreams.isEmpty()) {
                Text(
                    text = stringResource(R.string.dream_list_empty),
                    color = Color(0xFFE4EFF6),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ui.dreams.forEachIndexed { index, entry: DreamEntry ->
                        DreamInlineRow(
                            entry = entry,
                            onOpen = { e -> vm.openResult(e) },
                            onDelete = { vm.askDelete(index) }
                        )
                        if (index != ui.dreams.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // 결과 보기 다이얼로그
    if (ui.showResult != null) {
        AlertDialog(
            onDismissRequest = { vm.closeResult() },
            confirmButton = {
                TextButton(onClick = { vm.closeResult() }) { Text(stringResource(R.string.ok)) }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(ui.showResult!!, color = Color.White)
                }
            },
            containerColor = Color(0xE5030523),
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(22.dp)
        )
    }

    // 삭제 확인
    if (ui.askDeleteAt != null) {
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            confirmButton = {
                TextButton(onClick = { vm.confirmDelete() }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDelete() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            title = { Text(stringResource(R.string.cal_delete_title)) },
            text  = { Text(stringResource(R.string.cal_delete_message)) }
        )
    }
}

private fun buildMonthGrid(month: YearMonth): List<LocalDate> {
    val firstOfMonth = month.atDay(1)
    val firstDayOfWeek = DayOfWeek.SUNDAY
    val shift = (firstOfMonth.dayOfWeek.value % 7) - (firstDayOfWeek.value % 7)
    val leading = if (shift >= 0) shift else shift + 7
    val total = 42 // 6행 × 7열
    val start = firstOfMonth.minusDays(leading.toLong())
    return (0 until total).map { start.plusDays(it.toLong()) }
}

@Composable
private fun DayCell(
    day: LocalDate,
    selected: LocalDate,
    counts: Map<LocalDate, Int>,
    onClick: (LocalDate) -> Unit
) {
    val inMonth = day.month == selected.month
    val isToday = day == LocalDate.now()
    val isSelected = day == selected
    val textColor = when {
        isSelected -> Color.White
        !inMonth   -> Color(0xFFA0A0A0)
        day.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFFF6B6B)
        day.dayOfWeek == DayOfWeek.SATURDAY -> Color(0xFF6FA8FF)
        else -> Color(0xFFE8F1F8)
    }
    val badge = counts[day] ?: 0
    val dot = when {
        badge >= 5 -> 8.dp
        badge >= 3 -> 7.dp
        badge >= 1 -> 5.dp
        else -> 0.dp
    }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> Color(0x3327C2D0)
                    isToday -> Color(0x2217C2D0)
                    else -> Color.Transparent
                }
            )
            .clickable { onClick(day) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (dot > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(dot)
                    .clip(CircleShape)
                    .background(Color(0xFF37C2D0))
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, Color(0xFF27C2D0), RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun DreamInlineRow(
    entry: DreamEntry,
    onOpen: (DreamEntry) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x22FFFFFF))
            .fillMaxWidth()
            .clickable { onOpen(entry) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.dream,
            color = Color(0xDDEBF2FA),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.common_delete),
                tint = Color(0xFFFBA29D)
            )
        }
    }
}
