package com.dreamindream.app.ui.calendar

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale

// --- Theme Colors ---
private val DarkBg = Color(0xFF090C14)
private val MetallicGold = Color(0xFFD4AF37)
private val GlassBg = Color(0x1AFFFFFF)
private val TextMain = Color(0xFFECEFF1)
private val TextSub = Color(0xFF90A4AE)

private val ColorMessage = Color(0xFF90CAF9)
private val ColorSymbol = Color(0xFFF48FB1)
private val ColorPremonition = Color(0xFFFFCC80)
private val ColorTips = Color(0xFFA5D6A7)
private val ColorAction = Color(0xFFCE93D8)

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
                .paint(
                    painterResource(id = R.drawable.main_ground),
                    contentScale = ContentScale.FillBounds,
                    alpha = 0.4f
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)
            ) {
                // 상단 월 이동 헤더
                MonthSelector(
                    title = uiState.monthTitle,
                    calendarViewRef = calendarViewRef,
                    onPrev = { cal ->
                        cal.findFirstVisibleMonth()?.yearMonth?.minusMonths(1)?.let {
                            cal.smoothScrollToMonth(it); viewModel.onMonthScroll(it)
                        }
                    },
                    onNext = { cal ->
                        cal.findFirstVisibleMonth()?.yearMonth?.plusMonths(1)?.let {
                            cal.smoothScrollToMonth(it); viewModel.onMonthScroll(it)
                        }
                    },
                    onTitleClick = { showMonthPicker = true },
                    onTodayClick = { cal ->
                        val today = LocalDate.now()
                        val month = YearMonth.from(today)
                        cal.smoothScrollToMonth(month)
                        viewModel.onMonthScroll(month)
                        val old = viewModel.onDateClicked(today)
                        cal.notifyDateChanged(today)
                        if (old != today) cal.notifyDateChanged(old)
                    }
                )

                Spacer(Modifier.height(16.dp))


                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161A25).copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        factory = { ctx ->
                            CalendarView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                overScrollMode = View.OVER_SCROLL_NEVER
                                orientation = RecyclerView.HORIZONTAL
                                scrollPaged = true
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
                                        bindDayView(container, day, viewModel, this@apply)
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

                Spacer(Modifier.height(20.dp))

                Text(
                    text = uiState.dreamListTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MetallicGold, fontSize = 16.sp)
                )

                Spacer(Modifier.height(12.dp))

                if (uiState.isDreamListEmpty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(id = R.string.dream_list_empty), color = TextSub.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(uiState.dreamsForSelectedDay) { item ->
                            DreamInlineCard(item = item, onOpen = { selectedDream = item })
                        }
                    }
                }
            }

            // 꿈 결과 상세 다이얼로그
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

            // 공유 바텀 시트 (dreamToShare가 있을 때만 표시)
            dreamToShare?.let { dream ->
                ShareBottomSheet(
                    dreamInput = dream.originalDream,
                    resultText = dream.result,
                    onDismiss = { dreamToShare = null }
                )
            }

            // 월/년 선택 피커
            if (showMonthPicker) {
                MonthYearPickerDialog(
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
            modifier = Modifier.fillMaxSize().padding(vertical = 40.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161A25).copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Brush.verticalGradient(listOf(MetallicGold.copy(0.5f), Color.Transparent)))
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Dream Interpretation", color = MetallicGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row {
                            IconButton(onClick = onShareClick) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSub)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMain)
                            }
                        }
                    }
                    HorizontalDivider(color = GlassBg)
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
                    ) {
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
        title.contains("Message", true) || title.contains("메시지") -> ColorMessage to ColorMessage
        title.contains("Symbol", true) || title.contains("상징") -> ColorSymbol to ColorSymbol
        title.contains("Premonition", true) || title.contains("예지") -> ColorPremonition to ColorPremonition
        title.contains("Tip", true) || title.contains("Advice", true) || title.contains("팁") -> ColorTips to ColorTips
        title.contains("Action", true) || title.contains("행동") -> ColorAction to ColorAction
        else -> MetallicGold to MetallicGold
    }

    //  텍스트 정리 로직 적용 (불필요한 기호 제거 및 마크다운 처리)
    val cleanedBody = cleanTextContent(body)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .background(Color(0xFF0F111A), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = headerColor, fontSize = 15.sp)
            )
            Spacer(Modifier.height(8.dp))

            // 파싱된 텍스트 표시
            Text(
                text = parseMarkdownToAnnotatedString(cleanedBody),
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE0E0E0), lineHeight = 22.sp)
            )
        }
    }
}

// ---------------------------------------------------------------------
// Share Logic (DreamScreen과 동일)
// ---------------------------------------------------------------------

enum class ShareTarget(val label: String, val iconRes: Int, val packageName: String?) {
    Save("Save", 0, null),
    Instagram("Instagram", R.drawable.instagram, "com.instagram.android"),
    Facebook("Facebook", R.drawable.facebook, "com.facebook.katana"),
    KakaoTalk("KakaoTalk", R.drawable.kakaotalk, "com.kakao.talk"),
    WhatsApp("WhatsApp", R.drawable.whatsapp, "com.whatsapp")
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
            Text("Share Your Dream", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
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
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(GlassBg).clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (target == ShareTarget.Save) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "Save", tint = TextMain, modifier = Modifier.size(28.dp))
            } else {
                Image(painter = painterResource(id = target.iconRes), contentDescription = target.label, modifier = Modifier.size(32.dp), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(target.label, color = TextSub, fontSize = 12.sp)
    }
}

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
        canvas.drawText("Dream Interpreter · $dateStr", width / 2f, 130f, headerPaint)

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
        canvas.drawText("Interpretation", width / 2f, currentY, resultTitlePaint)
        currentY += 80f

        val resultBodyPaint = TextPaint().apply { color = 0xFFCFD8DC.toInt(); textSize = 38f; typeface = serifNormal; isAntiAlias = true }

        // 이미지 공유 시에도 텍스트 정제 적용
        val cleanResult = cleanTextContent(result).replace(Regex("[#*]"), "").trim()

        val availableHeight = height - currentY - 150f
        val resultLayout = StaticLayout.Builder.obtain(cleanResult, 0, cleanResult.length, resultBodyPaint, textWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(16f, 1.1f)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).setMaxLines((availableHeight / 45f).toInt()).build()

        canvas.save(); canvas.translate(padding, currentY); resultLayout.draw(canvas); canvas.restore()

        val fileName = context.getString(
            R.string.dream_image_prefix,
            System.currentTimeMillis()
        )

        val description = context.getString(R.string.dream_image_description)

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
                    context.getString(R.string.image_saved_gallery),
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
                            context.getString(R.string.share_instagram_title)
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
                        context.getString(R.string.app_not_found, target.label),
                        Toast.LENGTH_SHORT
                    ).show()

                    val generalIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                    }

                    context.startActivity(
                        Intent.createChooser(
                            generalIntent,
                            context.getString(R.string.share_dream_title)
                        )
                    )
                }
            }
        }

    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------------
// Utility Logic
// ---------------------------------------------------------------------

/** * ✨ 텍스트 정제 함수 (핵심)
 * 1. "1. :" 또는 "- :" 처럼 번호/불렛 뒤에 불필요한 콜론이 붙은 경우 제거
 * 2. "**" 마크다운이 어설프게 남은 경우 처리 등은 parseMarkdownToAnnotatedString에서 수행하지만,
 * 여기서는 문장 구조적인 오류를 먼저 잡습니다.
 */
fun cleanTextContent(text: String): String {
    return text.lines().joinToString("\n") { line ->
        // 정규식: (숫자+점 or - or •) 뒤에 공백, 그리고 콜론(:)이 오면 -> 콜론 제거
        // 예: "1. : 내용" -> "1. 내용", "- : 내용" -> "- 내용"
        line.replace(Regex("^(\\s*[-•\\d.]+)\\s*[:]\\s*"), "$1 ")
    }
}

/** **마크다운** -> Bold Span 변환 및 ** 제거 */
fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        // **텍스트** 패턴을 찾아서 Bold 처리하고 ** 기호는 제거
        val parts = text.split(Regex("(\\*\\*.*?\\*\\*)"))
        parts.forEach { part ->
            if (part.startsWith("**") && part.endsWith("**")) {
                val content = part.removePrefix("**").removeSuffix("**")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(content) }
            } else {
                append(part)
            }
        }
    }
}

private data class ResultSection(val title: String, val body: String)
private fun splitDreamResult(context: Context, raw: String): List<ResultSection> {
    val text = raw.trim()
    if (text.isBlank()) return emptyList()
    val titles = listOf(
        context.getString(R.string.dream_section_message),
        context.getString(R.string.dream_section_symbols),
        context.getString(R.string.dream_section_premonition),
        context.getString(R.string.dream_section_tips_today),
        context.getString(R.string.dream_section_actions_three)
    )
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
        val headerMatch = titles.firstOrNull { t -> trimmed.contains(t, ignoreCase = true) || (t.contains(" ") && trimmed.contains(t.split(" ").last(), ignoreCase = true)) }
        if (headerMatch != null) { push(); currentTitle = headerMatch } else { currentBody.appendLine(trimmed) }
    }
    push()
    if (result.isEmpty()) result += ResultSection(context.getString(R.string.dream_section_message), text)
    return result
}

// ---------------------------------------------------------------------
// Calendar Helper Classes
// ---------------------------------------------------------------------

class DayViewContainer(view: View) : ViewContainer(view) {
    val textView: TextView = view.findViewById(R.id.calendarDayText)
    val dreamIndicator: View = view.findViewById(R.id.dreamIndicator)
}
class MonthHeaderViewContainer(view: View) : ViewContainer(view)

private fun bindDayView(container: DayViewContainer, day: CalendarDay, viewModel: CalendarViewModel, calendarView: CalendarView) {
    val context = calendarView.context
    val colSun = android.graphics.Color.parseColor("#FF6B6B")
    val colSat = android.graphics.Color.parseColor("#6FA8FF")
    val colText = android.graphics.Color.parseColor("#E8F1F8")
    val colDim = android.graphics.Color.parseColor("#505050")
    val colAccent = android.graphics.Color.parseColor("#D4AF37")

    container.view.scaleX = 1f; container.view.scaleY = 1f
    container.textView.text = day.date.dayOfMonth.toString()
    val isSelected = viewModel.uiState.value.selectedDate == day.date
    val isToday = day.date == LocalDate.now()
    val count = viewModel.getDreamCount(day.date)
    container.dreamIndicator.apply {
        visibility = if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) {
            val sizeDp = if (count >= 3) 6 else 4
            val px = (sizeDp * context.resources.displayMetrics.density).toInt()
            layoutParams = (layoutParams as ViewGroup.LayoutParams).apply { width = px; height = px }
            alpha = if (count >= 3) 1f else 0.7f
        }
    }
    container.view.setOnClickListener { view ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(50).start() }.start()
        val old = viewModel.onDateClicked(day.date)
        calendarView.notifyDateChanged(day.date)
        if (old != day.date) calendarView.notifyDateChanged(old)
    }
    when {
        isSelected -> { container.textView.setBackgroundResource(R.drawable.day_selected_background); container.textView.setTextColor(android.graphics.Color.BLACK) }
        day.position != DayPosition.MonthDate -> { container.textView.setBackgroundResource(android.R.color.transparent); container.textView.setTextColor(colDim) }
        isToday -> { container.textView.setBackgroundResource(R.drawable.day_today_background); container.textView.setTextColor(colAccent) }
        else -> {
            container.textView.setBackgroundResource(android.R.color.transparent)
            container.textView.setTextColor(when (day.date.dayOfWeek) { DayOfWeek.SUNDAY -> colSun; DayOfWeek.SATURDAY -> colSat; else -> colText })
        }
    }
}

@Composable
private fun MonthSelector(title: String, calendarViewRef: MutableState<CalendarView?>, onPrev: (CalendarView) -> Unit, onNext: (CalendarView) -> Unit, onTitleClick: () -> Unit, onTodayClick: (CalendarView) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { calendarViewRef.value?.let(onTodayClick) }) { Text("Today", style = MaterialTheme.typography.labelMedium, color = MetallicGold) }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { calendarViewRef.value?.let(onPrev) }) { Icon(painterResource(id = R.drawable.ic_chevron_left), null, tint = TextMain) }
            Crossfade(targetState = title, animationSpec = tween(200), label = "Title") { t -> Text(text = t, style = MaterialTheme.typography.titleMedium, color = TextMain, modifier = Modifier.padding(horizontal = 12.dp).clickable { onTitleClick() }) }
            IconButton(onClick = { calendarViewRef.value?.let(onNext) }) { Icon(painterResource(id = R.drawable.ic_chevron_right), null, tint = TextMain) }
        }
    }
}

@Composable
private fun MonthYearPickerDialog(initialMonth: YearMonth, onDismiss: () -> Unit, onMonthSelected: (YearMonth) -> Unit) {
    val years = remember { (YearMonth.now().year - 5..YearMonth.now().year + 5).toList() }
    var selectedYear by remember { mutableStateOf(initialMonth.year) }
    var selectedMonth by remember { mutableStateOf(initialMonth.monthValue) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF1E212B),
        title = { Text("Select Date", color = TextMain) },
        text = {
            Row(Modifier.fillMaxWidth().height(240.dp)) {
                LazyColumn(Modifier.weight(1f)) { items(years) { year -> Row(Modifier.fillMaxWidth().clickable { selectedYear = year }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = year == selectedYear, onClick = { selectedYear = year }, colors = RadioButtonDefaults.colors(selectedColor = MetallicGold)); Text("$year", color = TextMain) } } }
                LazyColumn(Modifier.weight(1f)) { items((1..12).toList()) { month -> Row(Modifier.fillMaxWidth().clickable { selectedMonth = month }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = month == selectedMonth, onClick = { selectedMonth = month }, colors = RadioButtonDefaults.colors(selectedColor = MetallicGold)); Text("$month", color = TextMain) } } }
            }
        },
        confirmButton = { TextButton(onClick = { onMonthSelected(YearMonth.of(selectedYear, selectedMonth)); onDismiss() }) { Text("Confirm", color = MetallicGold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSub) } }
    )
}

@Composable
private fun DreamInlineCard(item: InlineDream, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpen() },
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = GlassBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = item.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = TextMain, modifier = Modifier.weight(1f))
        }
    }
}