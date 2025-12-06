package com.dreamindream.app.ui.calendar

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.recyclerview.widget.RecyclerView
import com.dreamindream.app.AdPageScaffold
import com.dreamindream.app.R
import com.dreamindream.app.SubscriptionManager
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// --- Theme Colors & Fonts ---
private val DarkBg = Color(0xFF0F111A)
private val ColorGold = Color(0xFFFFD54F)
private val ColorTextMain = Color(0xFFEEEEEE)
private val ColorTextSub = Color(0xFF90949F)
private val ColorAccentPurple = Color(0xFF7B61FF)
// 기존 색상들 아래에 추가
private val ColorChampagne = Color(0xFFE6D6BC) // 은은하고 고급스러운 샴페인 골드
private val ColorSelectedBg = Color(0xFFE0E0E0) // 캘린더 선택 원 (흰색 계열로 변경하여 깔끔하게)
// Fonts
private val FontPretendardBold = FontFamily(Font(R.font.pretendard_bold, FontWeight.Bold))
private val FontPretendardMed = FontFamily(Font(R.font.pretendard_medium, FontWeight.Medium))

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSubscribed by SubscriptionManager.isSubscribed.collectAsState(initial = false)

    val calendarViewRef = remember { mutableStateOf<CalendarView?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedDream by remember { mutableStateOf<InlineDream?>(null) }
    var dreamToShare by remember { mutableStateOf<InlineDream?>(null) }

    LaunchedEffect(uiState.calendarRefreshToken) {
        calendarViewRef.value?.notifyCalendarChanged()
    }

    AdPageScaffold(
        adUnitRes = if (isSubscribed) null else R.string.ad_unit_calendar_banner
    ) { pad ->

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBg)
        ) {
            // [배경] DreamScreen과 동일
            Image(
                painter = painterResource(id = R.drawable.main_ground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.6f),
                contentScale = ContentScale.Crop
            )

            // [효과] 별 + 은하수
            NightSkyEffect()

            // 그라데이션 오버레이 (가독성 확보)
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(DarkBg.copy(0.3f), DarkBg.copy(0.8f))
                )
            ))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // [헤더] 요청: HomeTitle 느낌 + Pretendard Bold
                CalendarHeaderSimple(
                    title = uiState.monthTitle,
                    onTitleClick = { showMonthPicker = true }
                )

                Spacer(Modifier.height(12.dp))

                // [달력 본체]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E212B).copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, Color.White.copy(0.05f))
                ) {

                    // AndroidView Calendar
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        factory = { ctx ->
                            CalendarView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                overScrollMode = View.OVER_SCROLL_NEVER
                                orientation = RecyclerView.HORIZONTAL
                                scrollPaged = true
                                background = null

                                try {
                                    dayViewResource = R.layout.example_day_view
                                    monthHeaderResource = R.layout.example_month_header
                                } catch (_: Throwable) {}

                                setup(viewModel.calendarStartMonth(), viewModel.calendarEndMonth(), daysOfWeek(viewModel.firstDayOfWeek()).first())
                                scrollToMonth(YearMonth.now())

                                monthScrollListener = { month -> viewModel.onMonthScroll(month.yearMonth) }

                                dayBinder = object : MonthDayBinder<DayViewContainer> {
                                    override fun create(view: View) = DayViewContainer(view)
                                    override fun bind(container: DayViewContainer, day: CalendarDay) {
                                        bindDayViewCompact(container, day, viewModel, this@apply)
                                    }
                                }
                                monthHeaderBinder = object : MonthHeaderFooterBinder<MonthHeaderViewContainer> {
                                    override fun create(view: View) = MonthHeaderViewContainer(view)
                                    override fun bind(container: MonthHeaderViewContainer, month: CalendarMonth) {}
                                }
                                calendarViewRef.value = this
                            }
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))


                val selectedDate = uiState.selectedDate
                if (selectedDate != null) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = ColorTextSub,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))


                        Text(
                            text = selectedDate.toString(),
                            fontSize = 15.sp, // 20sp -> 15sp로 축소
                            fontFamily = FontPretendardMed, // Bold -> Medium으로 무게 줄임
                            color = ColorTextMain.copy(alpha = 0.9f), // 노란색 제거 -> 흰색 계열
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (uiState.isDreamListEmpty) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF1E212B).copy(0.3f), RoundedCornerShape(24.dp))
                            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = ColorTextSub.copy(0.3f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(id = R.string.dream_list_empty),
                                color = ColorTextSub.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                fontFamily = FontPretendardMed
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(uiState.dreamsForSelectedDay) { item ->
                            DreamDiaryCard(item = item, onOpen = { selectedDream = item })
                        }
                    }
                }
            }

            // --- Dialogs ---
            selectedDream?.let { dream ->
                DreamResultDialog(
                    dream = dream,
                    onDismiss = { selectedDream = null },
                    onShareClick = {
                        dreamToShare = dream
                        selectedDream = null
                    }
                )
            }

            dreamToShare?.let { dream ->
                ShareBottomSheet(
                    dreamInput = dream.originalDream,
                    resultText = dream.result,
                    onDismiss = { dreamToShare = null }
                )
            }

            if (showMonthPicker) {
                MonthYearPickerDialogV2(
                    initialMonth = YearMonth.now(),
                    onDismiss = { showMonthPicker = false },
                    onMonthSelected = { yearMonth ->
                        calendarViewRef.value?.let { cal ->
                            cal.smoothScrollToMonth(yearMonth)
                            viewModel.onMonthScroll(yearMonth)
                        }
                    }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// UI Components
// -----------------------------------------------------------------------------
@Composable
fun CalendarHeaderSimple(title: String, onTitleClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTitleClick
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // [수정] 쨍한 노랑(ColorGold) -> 은은한 샴페인(ColorChampagne)
        Text(
            text = title,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 28.sp,
                fontFamily = FontPretendardBold,
                color = ColorChampagne,
                shadow = Shadow(
                    color = ColorChampagne.copy(alpha = 0.3f), // 그림자도 은은하게
                    offset = Offset(0f, 4f),
                    blurRadius = 16f
                )
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.cal_tap_to_change),
                fontSize = 12.sp,
                color = ColorTextSub.copy(alpha = 0.6f), // 더 차분하게 투명도 조절
                fontFamily = FontPretendardMed
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = ColorTextSub.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun DreamDiaryCard(item: InlineDream, onOpen: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
        .clickable { onOpen() }) {

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 12.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(ColorGold, CircleShape)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(50.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(ColorGold, Color.Transparent)
                        )
                    )
            )
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E212B).copy(alpha = 0.8f)),
            border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color(0xFF2C3040), Color.Transparent)))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.dream_record_prefix)}${item.index + 1}",
                        fontSize = 10.sp,
                        color = ColorAccentPurple,
                        fontFamily = FontPretendardBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.preview,
                        fontSize = 13.sp,
                        color = ColorTextMain,
                        fontFamily = FontPretendardMed,
                        maxLines = 1,
                        lineHeight = 18.sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = ColorTextSub,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Legacy View Binding Logic (Compact Version)
// -----------------------------------------------------------------------------

// [수정] 정원형 배경 (타원 방지)
private fun getCircleDrawable(isStroke: Boolean = false, color: Int): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        if (isStroke) {
            setStroke(3, color)
            setColor(android.graphics.Color.TRANSPARENT)
        } else {
            setColor(color)
        }
    }
}
private fun bindDayViewCompact(container: DayViewContainer, day: CalendarDay, viewModel: CalendarViewModel, calendarView: CalendarView) {
    val context = calendarView.context

    // [레이아웃] 달력 셀 높이 설정 (납작하게 34dp)
    val cellHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 34f, context.resources.displayMetrics).toInt()
    container.view.layoutParams.height = cellHeight

    // [레이아웃] 선택 원형 배경을 위한 정사각형 크기 고정 및 정렬
    val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30f, context.resources.displayMetrics).toInt()
    val params = container.textView.layoutParams as ViewGroup.MarginLayoutParams
    params.width = size
    params.height = size
    container.textView.layoutParams = params

    // [중요] 텍스트 수직/수평 정렬 완벽하게 맞추기
    container.textView.gravity = Gravity.CENTER
    container.textView.includeFontPadding = false // 폰트 자체의 여백 제거 (수직 정렬 핵심)

    container.textView.text = day.date.dayOfMonth.toString()
    container.textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    container.textView.textSize = 13f

    val isSelected = viewModel.uiState.value.selectedDate == day.date
    val isToday = day.date == LocalDate.now()
    val count = viewModel.getDreamCount(day.date)

    // --- 색상 정의 (세련된 톤으로 변경) ---
    val colTextNormal = android.graphics.Color.parseColor("#EEEEEE") // 기본 흰색
    val colTextDim = android.graphics.Color.parseColor("#505050")    // 비활성 회색
    val colSunday = android.graphics.Color.parseColor("#FF6B6B")     // 일요일 빨강

    // [수정] 쨍한 노란색 대신 '샴페인 골드' & '화이트' 사용
    val colChampagne = android.graphics.Color.parseColor("#E6D6BC")  // 샴페인 골드 (오늘 날짜)
    val colSelectedBg = android.graphics.Color.parseColor("#E0E0E0") // 선택 배경 (흰색 계열)
    val colTextSelected = android.graphics.Color.BLACK               // 선택 글씨 (검정)

    // 꿈 표시 점 (보라색 포인트 유지)
    container.dreamIndicator.apply {
        visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#9575CD")) // Soft Purple
            }
        }
    }

    container.view.setOnClickListener { view ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val old = viewModel.onDateClicked(day.date)
        calendarView.notifyDateChanged(day.date)
        if (old != day.date) calendarView.notifyDateChanged(old)
    }

    if (day.position == DayPosition.MonthDate) {
        when {
            isSelected -> {
                // [선택됨] 흰색 배경에 검은 글씨 (깔끔함 강조)
                container.textView.background = getCircleDrawable(isStroke = false, color = colSelectedBg)
                container.textView.setTextColor(colTextSelected)
                container.textView.alpha = 1f
            }
            isToday -> {
                // [오늘] 샴페인 골드 테두리만 은은하게
                container.textView.background = getCircleDrawable(isStroke = true, color = colChampagne)
                container.textView.setTextColor(colChampagne)
            }
            else -> {
                container.textView.background = null
                if (day.date.dayOfWeek == DayOfWeek.SUNDAY) {
                    container.textView.setTextColor(colSunday)
                } else {
                    container.textView.setTextColor(colTextNormal)
                }
            }
        }
        container.view.alpha = 1f
    } else {
        // 이전/다음 달 날짜 흐리게 처리
        container.textView.background = null
        container.textView.setTextColor(colTextDim)
        container.view.alpha = 0.3f
    }
}

// -----------------------------------------------------------------------------
// Pickers & Dialogs
// -----------------------------------------------------------------------------

@Composable
private fun MonthYearPickerDialogV2(initialMonth: YearMonth, onDismiss: () -> Unit, onMonthSelected: (YearMonth) -> Unit) {
    val years = remember { (YearMonth.now().year - 3..YearMonth.now().year + 3).toList() }
    var selectedYear by remember { mutableStateOf(initialMonth.year) }
    var selectedMonth by remember { mutableStateOf(initialMonth.monthValue) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E212B)),
            border = BorderStroke(1.dp, ColorGold.copy(0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.cal_select_date_title), style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontFamily = FontPretendardBold, color = ColorTextMain))
                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.height(180.dp).fillMaxWidth()) {
                    // Year List
                    LazyColumn(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        items(years) { year ->
                            Text(
                                text = "$year",
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .clickable { selectedYear = year }
                                    .background(if(selectedYear == year) ColorGold.copy(0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = if(selectedYear == year) ColorGold else ColorTextSub,
                                fontWeight = if(selectedYear == year) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(0.1f)))
                    // Month List
                    LazyColumn(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        items((1..12).toList()) { month ->
                            Text(
                                text = "$month${stringResource(R.string.date_month_unit)}",
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .clickable { selectedMonth = month }
                                    .background(if(selectedMonth == month) ColorGold.copy(0.2f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = if(selectedMonth == month) ColorGold else ColorTextSub,
                                fontWeight = if(selectedMonth == month) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onMonthSelected(YearMonth.of(selectedYear, selectedMonth)); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorGold),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(stringResource(R.string.common_confirm), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Dream Result Dialog
// -----------------------------------------------------------------------------

@Composable
private fun DreamResultDialog(
    dream: InlineDream,
    onDismiss: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    val sections = remember(dream.result) { splitDreamResult(context, dream.result) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(vertical = 40.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161A25).copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, Brush.verticalGradient(listOf(ColorGold.copy(0.5f), Color.Transparent)))
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.dream_interpretation_title), color = ColorGold, fontFamily = FontPretendardMed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Row {
                            IconButton(onClick = onShareClick) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = ColorTextSub)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = ColorTextMain)
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(0.1f))

                    // Content
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)
                    ) {
                        // Original Dream
                        Text("꿈 내용", color = ColorTextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(dream.originalDream, color = Color.White.copy(0.8f), fontSize = 14.sp, lineHeight = 22.sp)

                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = Color.White.copy(0.05f))
                        Spacer(Modifier.height(24.dp))

                        sections.forEach { sec ->
                            ResultSectionCard(sec.title, sec.body)
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultSectionCard(title: String, body: String) {
    val (headerColor, borderColor) = when {
        title.contains("Message", true) || title.contains("메시지") -> Color(0xFF90CAF9) to Color(0xFF90CAF9)
        title.contains("Symbol", true) || title.contains("상징") -> Color(0xFFF48FB1) to Color(0xFFF48FB1)
        else -> ColorGold to ColorGold
    }
    val cleanedBody = cleanTextContent(body)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F111A).copy(0.5f), RoundedCornerShape(16.dp))
            .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = headerColor, fontSize = 15.sp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = parseMarkdownToAnnotatedString(cleanedBody),
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE0E0E0), lineHeight = 22.sp)
        )
    }
}

// ---------------------------------------------------------------------
// Share Logic
// ---------------------------------------------------------------------

enum class ShareTarget(@StringRes val labelRes: Int, val iconRes: Int, val packageName: String?) {
    Save(R.string.share_target_save, 0, null),
    Instagram(R.string.share_target_instagram, R.drawable.instagram, "com.instagram.android"),
    Facebook(R.string.share_target_facebook, R.drawable.facebook, "com.facebook.katana"),
    KakaoTalk(R.string.share_target_kakao, R.drawable.kakaotalk, "com.kakao.talk"),
    WhatsApp(R.string.share_target_whatsapp, R.drawable.whatsapp, "com.whatsapp")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBottomSheet(dreamInput: String, resultText: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E212B),
        windowInsets = WindowInsets.navigationBars
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 50.dp)) {
            Text(stringResource(R.string.share_sheet_title), color = ColorTextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(30.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ShareTarget.values().forEach { target ->
                    ShareIconItem(
                        target = target,
                        enabled = !isGenerating,
                        onClick = {
                            isGenerating = true
                            coroutineScope.launch {
                                shareDreamSpecific(context, target, dreamInput, resultText)
                                isGenerating = false
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ShareIconItem(target: ShareTarget, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // [수정] 아이콘 배경 제거 (원형 Box 배경 Transparent 처리 및 아이콘 직접 클릭)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable(enabled = enabled, onClick = onClick), // 배경색 없음
            contentAlignment = Alignment.Center
        ) {
            if (target == ShareTarget.Save) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "Save", tint = ColorTextMain, modifier = Modifier.size(28.dp))
            } else {
                Image(painter = painterResource(id = target.iconRes), contentDescription = stringResource(target.labelRes), modifier = Modifier.size(32.dp), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(target.labelRes), color = ColorTextSub, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------
// Logic Implementation (Canvas Drawing & Text Parsing)
// ---------------------------------------------------------------------

private fun shareDreamSpecific(context: Context, target: ShareTarget, dream: String, result: String) {
    try {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0xFF050505.toInt(), 0xFF151A25.toInt()),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw Stars
        val starPaint = Paint().apply { color = 0xFFFFF176.toInt(); alpha = 180 }
        for (i in 0..50) {
            val x = (Math.random() * width).toFloat(); val y = (Math.random() * height).toFloat(); val r = (Math.random() * 3 + 1).toFloat()
            starPaint.alpha = (Math.random() * 150 + 50).toInt()
            canvas.drawCircle(x, y, r, starPaint)
        }

        val padding = 100f
        val textWidth = width - (padding * 2)
        val headerHeight = 250f
        val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val serifNormal = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val sansSerifLight = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        val headerPaint = TextPaint().apply { color = 0xFFB0BEC5.toInt(); textSize = 34f; typeface = sansSerifLight; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())

        val appNameTitle = context.getString(R.string.share_canvas_app_title)
        canvas.drawText("$appNameTitle · $dateStr", width / 2f, 130f, headerPaint)

        val quotePaint = TextPaint().apply { color = 0xFFD4AF37.toInt(); textSize = 100f; typeface = serifBold; isAntiAlias = true }
        val dreamBodyPaint = TextPaint().apply { color = 0xFFECEFF1.toInt(); textSize = 46f; typeface = serifNormal; isAntiAlias = true }

        val dreamSnippet = if (dream.length > 80) dream.take(80) + "..." else dream
        val dreamLayout = StaticLayout.Builder.obtain(dreamSnippet, 0, dreamSnippet.length, dreamBodyPaint, textWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(20f, 1.2f).setMaxLines(3).build()

        var currentY = headerHeight
        canvas.drawText("“", padding - 20, currentY + 60f, quotePaint)
        canvas.save(); canvas.translate(padding + 40, currentY + 80f); dreamLayout.draw(canvas); canvas.restore()
        currentY += dreamLayout.height + 100f
        canvas.drawText("”", width - padding - 40, currentY - 40f, quotePaint)

        val linePaint = Paint().apply { color = 0x4DFFFFFF.toInt(); strokeWidth = 2f }
        canvas.drawLine(width/2f - 100f, currentY + 40f, width/2f + 100f, currentY + 40f, linePaint)
        currentY += 120f

        val resultTitlePaint = TextPaint().apply { color = 0xFF90CAF9.toInt(); textSize = 42f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC); isAntiAlias = true; textAlign = Paint.Align.CENTER }

        val interpretationTitle = context.getString(R.string.share_canvas_interpretation_header)
        canvas.drawText(interpretationTitle, width / 2f, currentY, resultTitlePaint)
        currentY += 80f

        val resultBodyPaint = TextPaint().apply { color = 0xFFCFD8DC.toInt(); textSize = 38f; typeface = serifNormal; isAntiAlias = true }

        val cleanResult = cleanTextContent(result).replace(Regex("[#*]"), "").trim()

        val availableHeight = height - currentY - 150f
        val resultLayout = StaticLayout.Builder.obtain(cleanResult, 0, cleanResult.length, resultBodyPaint, textWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(16f, 1.1f)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).setMaxLines((availableHeight / 45f).toInt()).build()

        canvas.save(); canvas.translate(padding, currentY); resultLayout.draw(canvas); canvas.restore()

        val fileName = context.getString(
            R.string.dream_image_file_prefix,
            System.currentTimeMillis()
        )

        val description = context.getString(R.string.dream_image_desc)

        val path = MediaStore.Images.Media.insertImage(
            context.contentResolver,
            bitmap,
            fileName,
            description
        )

        if (path != null) {
            val uri = Uri.parse(path)

            if (target == ShareTarget.Save) {
                Toast.makeText(
                    context,
                    context.getString(R.string.image_saved_msg),
                    Toast.LENGTH_SHORT
                ).show()

            } else if (target == ShareTarget.Instagram) {
                try {
                    val storiesIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                        setDataAndType(uri, "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.instagram.android")
                    }
                    context.startActivity(storiesIntent)
                } catch (_: Exception) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            context.getString(R.string.share_insta_chooser_title)
                        )
                    )
                }
            } else {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        target.packageName?.let { setPackage(it) }
                    }
                    context.startActivity(shareIntent)
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.app_not_found_msg, context.getString(target.labelRes)),
                        Toast.LENGTH_SHORT
                    ).show()

                    val generalIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            generalIntent,
                            context.getString(R.string.share_chooser_title)
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        val errorMsg = context.getString(R.string.share_error_msg, e.message)
        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
    }
}

// Helpers
fun cleanTextContent(text: String): String = text.lines().joinToString("\n") { it.replace(Regex("^(\\s*[-•\\d.]+)\\s*[:]\\s*"), "$1 ") }
fun parseMarkdownToAnnotatedString(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split(Regex("(\\*\\*.*?\\*\\*)"))
    parts.forEach { part ->
        if (part.startsWith("**") && part.endsWith("**")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(part.removePrefix("**").removeSuffix("**")) }
        } else { append(part) }
    }
}
private data class ResultSection(val title: String, val body: String)
private fun splitDreamResult(context: Context, raw: String): List<ResultSection> {
    val text = raw.trim()
    if (text.isBlank()) return emptyList()
    val titles = listOf("Message", "Symbol", "Premonition", "Tip", "Action", "메시지", "상징", "예지", "팁", "행동")
    val lines = text.lines()
    val result = mutableListOf<ResultSection>()
    var currentTitle: String? = null
    val currentBody = StringBuilder()
    fun push() {
        val t = currentTitle ?: return
        val b = currentBody.toString().trim()
        if (b.isNotEmpty()) result += ResultSection(t, b)
        currentBody.clear()
    }
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val headerMatch = titles.firstOrNull { trimmed.contains(it, ignoreCase = true) }
        if (headerMatch != null) { push(); currentTitle = trimmed } else { currentBody.appendLine(trimmed) }
    }
    push()
    if (result.isEmpty()) result += ResultSection(context.getString(R.string.dream_analysis_default_title), text)
    return result
}

class DayViewContainer(view: View) : ViewContainer(view) {
    val textView: TextView = view.findViewById(R.id.calendarDayText)
    val dreamIndicator: View = view.findViewById(R.id.dreamIndicator)
}
class MonthHeaderViewContainer(view: View) : ViewContainer(view)

// -----------------------------------------------------------------------------
// [추가된 효과] NightSkyEffect (DreamScreen과 동일한 별/은하수 효과)
// -----------------------------------------------------------------------------

@Composable
fun NightSkyEffect() {
    Box(Modifier.fillMaxSize()) {
        TwinklingStars()
        ShootingStar()
    }
}

@Composable
fun TwinklingStars() {
    val density = LocalDensity.current
    val stars = remember {
        List(20) {
            StarData(
                x = Math.random().toFloat(),
                y = Math.random().toFloat(),
                size = (Math.random() * 0.4 + 0.1).toFloat(),
                offset = Math.random().toFloat() * 2000f,
                speed = (Math.random() * 0.06 + 0.02).toFloat()
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "stars_time")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(120000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        stars.forEach { star ->
            val rawSin = sin((time * star.speed + star.offset).toDouble()).toFloat()
            val alphaBase = ((rawSin + 1) / 2).pow(30)
            val alpha = alphaBase * 0.7f

            if (alpha > 0.01f) {
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = star.size * density.density,
                    center = Offset(star.x * width, star.y * height)
                )
            }
        }
    }
}

@Composable
fun ShootingStar() {
    val progress = remember { Animatable(0f) }
    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        while(true) {
            val waitTime = Random.nextLong(3000, 5000)
            delay(waitTime)
            startX = Random.nextFloat() * 0.5f + 0.4f
            startY = Random.nextFloat() * 0.3f
            scale = Random.nextFloat() * 0.5f + 0.5f
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = LinearEasing)
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (progress.value > 0f && progress.value < 1f) {
            val width = size.width
            val height = size.height
            val moveDistance = width * 0.4f
            val currentX = (startX * width) - (moveDistance * progress.value)
            val currentY = (startY * height) + (moveDistance * progress.value)
            val tailLength = 100f * scale
            val headX = currentX
            val headY = currentY
            val tailX = currentX + (tailLength * 0.7f)
            val tailY = currentY - (tailLength * 0.7f)
            val alpha = if (progress.value < 0.1f) progress.value * 10f else if (progress.value > 0.8f) (1f - progress.value) * 5f else 1f

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0f),
                        androidx.compose.ui.graphics.Color.White.copy(alpha = alpha)
                    ),
                    start = Offset(tailX, tailY),
                    end = Offset(headX, headY)
                ),
                start = Offset(tailX, tailY),
                end = Offset(headX, headY),
                strokeWidth = 2f * scale,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha),
                radius = 1.5f * scale,
                center = Offset(headX, headY)
            )
        }
    }
}

data class StarData(
    val x: Float, val y: Float, val size: Float, val offset: Float, val speed: Float
)