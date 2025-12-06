package com.dreamindream.app.ui.fortune

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.dreamindream.app.R
import com.dreamindream.app.SubscriptionManager
import kotlin.math.cos
import kotlin.math.sin

// ★ New Premium Colors (더 세련된 조합)
private val DarkBg = Color(0xFF090C14)       // 거의 블랙에 가까운 네이비
private val CardBg = Color(0xFF131722)       // 아주 어두운 차콜 네이비
private val ChampagneGold = Color(0xFFF7E7CE) // 은은한 샴페인 골드
private val MetallicGold = Color(0xFFD4AF37)  // 진한 금색
private val MysticPurple = Color(0xFF9F7AEA)  // 신비로운 보라색
private val TextMain = Color(0xFFECEFF1)
private val TextSub = Color(0xFF90A4AE)
private val GlassBorder = Color(0x1AFFFFFF)

@Composable
fun FortuneScreen(
    viewModel: FortuneViewModel = viewModel(),
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSubscribed by SubscriptionManager.isSubscribed.collectAsState()
    val context = LocalContext.current

    // 구독 화면 이동 이벤트 처리
    LaunchedEffect(uiState.navigateToSubscription) {
        if (uiState.navigateToSubscription) {
            viewModel.onSubscriptionNavHandled()
            onNavigateToSubscription()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        // 배경: 은은한 오로라 효과 (이미지 or 그라데이션)
        Image(
            painter = painterResource(R.drawable.main_ground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.3f), // 투명도 낮춰서 고급스럽게
            contentScale = ContentScale.Crop
        )

        if (uiState.isLoading) {
            LoadingView(stringResource(R.string.loading_analyzing))
        } else if (uiState.showStartButton) {
            StartView(uiState.userName) { viewModel.onStartClick(context) }
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSub)
            }
        } else if (uiState.showFortuneCard) {
            FortuneResultView(
                uiState = uiState,
                isSubscribed = isSubscribed,
                onChecklistToggle = viewModel::onChecklistToggle,
                onSectionClick = viewModel::onSectionClick,
                onRadarClick = viewModel::onRadarClick,
                onDeepClick = { viewModel.onDeepAnalysisClick(context) },
                onSubscribeClick = onNavigateToSubscription // ★ 잠금 해제 누르면 구독창으로
            )
        }

        // --- Dialogs ---
        if (uiState.showRadarDetail) {
            RadarDetailDialog(uiState.sections, viewModel::closeRadarDialog)
        }

        if (uiState.showDeepDialog && uiState.deepResult != null) {
            DeepAnalysisDialog(result = uiState.deepResult!!, onDismiss = viewModel::closeDeepDialog)
        } else if (uiState.isDeepLoading) {
            Dialog(onDismissRequest = {}) {
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(CardBg).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MetallicGold)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.loading_analyzing), color = TextMain)
                    }
                }
            }
        }

        uiState.sectionDialog?.let { section ->
            SectionDetailDialog(section = section, onDismiss = viewModel::closeSectionDialog)
        }

        if (uiState.showProfileDialog) {
            FortuneAlertDialog(
                title = stringResource(R.string.fortune_profile_title),
                text = stringResource(R.string.fortune_profile_desc),
                confirmText = stringResource(R.string.fortune_profile_go),
                onConfirm = { viewModel.onProfileDialogDismiss(); onNavigateToSettings() },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { viewModel.onProfileDialogDismiss() }
            )
        }
    }
}

@Composable
fun StartView(userName: String, onStart: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (userName.isNotBlank()) stringResource(R.string.fortune_intro_user, userName)
                else stringResource(R.string.fortune_intro_title),
                color = ChampagneGold, // 고급스러운 색
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )
            Spacer(Modifier.height(48.dp))

            // 그라데이션 버튼
            Box(
                modifier = Modifier
                    .width(220.dp).height(56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(MetallicGold, Color(0xFFF59E0B))))
                    .clickable { onStart() },
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.fortune_start_btn), color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun LoadingView(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LottieLoader(resId = R.raw.generator)
            Spacer(Modifier.height(16.dp))
            Text(message, color = TextSub)
        }
    }
}

@Composable
fun FortuneResultView(
    uiState: FortuneUiState,
    isSubscribed: Boolean,
    onChecklistToggle: (Int, Boolean) -> Unit,
    onSectionClick: (String) -> Unit,
    onRadarClick: () -> Unit,
    onDeepClick: () -> Unit,
    onSubscribeClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        // One Line Summary (카드 형태로 변경)
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .border(1.dp, MetallicGold.copy(0.3f), RoundedCornerShape(16.dp))
                .background(Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                // 📌 1. TODAY’S FORTUNE 텍스트 크기 수정 (12.sp -> 11.sp)
                Text(
                    text = stringResource(R.string.fortune_today_title),
                    color = MetallicGold,
                    fontSize = 11.sp, // Reduced by 1sp as requested
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(uiState.oneLineSummary, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp)
            }
        }

        Spacer(Modifier.height(30.dp))

        // Radar Chart
        Box(
            Modifier.size(320.dp).clickable { onRadarClick() },
            contentAlignment = Alignment.Center
        ) {
            RadarChart(uiState.radarChartData)
        }

        Spacer(Modifier.height(20.dp))

        // Lucky Items
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            LuckyItemView(stringResource(R.string.lucky_color), uiState.luckyColorHex, isColor = true)
            LuckyItemView(stringResource(R.string.lucky_number), "${uiState.luckyNumber ?: "-"}")
            LuckyItemView(stringResource(R.string.lucky_time), uiState.luckyTime)
            LuckyItemView(stringResource(R.string.lucky_dir), uiState.luckyDirection)
        }

        Spacer(Modifier.height(30.dp))

        // Checklist
        GlassContainer(stringResource(R.string.fortune_checklist_title)) {
            uiState.checklist.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChecklistToggle(item.id, !item.checked) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (item.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        null, tint = if (item.checked) MetallicGold else TextSub
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(item.text, color = if (item.checked) TextSub else TextMain, fontSize = 14.sp,
                        textDecoration = if (item.checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                }
                if (item != uiState.checklist.last()) HorizontalDivider(color = GlassBorder)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Sections
        Column(Modifier.padding(horizontal = 16.dp)) {
            uiState.sections.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { sec ->
                        Box(Modifier.weight(1f)) {
                            SectionCardView(sec) { onSectionClick(sec.key) }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(40.dp))

        // 📌 3. “운세 심화 분석” 버튼 디자인 개선 (Creative Redesign)
        DeepAnalysisButton(isSubscribed, onDeepClick, onSubscribeClick)
    }
}

// --- Visual Components ---

@Composable
fun RadarChart(data: Map<String, Int>) {
    val context = LocalContext.current
    val labels = data.keys.toList()
    val values = data.values.toList()
    val labelColor = TextSub.toArgb()

    Canvas(modifier = Modifier.size(280.dp)) {
        val center = center
        val radius = size.minDimension / 2 - 35.dp.toPx()
        val angleStep = (2 * Math.PI / 5).toFloat()
        val startAngle = -Math.PI.toFloat() / 2

        // Draw Web
        val webPath = Path()
        for (i in 0 until 5) {
            val angle = i * angleStep + startAngle
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)
            if (i == 0) webPath.moveTo(x, y) else webPath.lineTo(x, y)
            drawLine(TextSub.copy(alpha = 0.2f), center, Offset(x, y), strokeWidth = 1.dp.toPx())
        }
        webPath.close()
        drawPath(webPath, style = Stroke(width = 1.dp.toPx()), color = TextSub.copy(alpha = 0.2f))

        // Draw Data
        val dataPath = Path()
        values.forEachIndexed { i, v ->
            val normalized = (v / 100f).coerceIn(0.1f, 1f)
            val angle = i * angleStep + startAngle
            val x = center.x + (radius * normalized) * cos(angle)
            val y = center.y + (radius * normalized) * sin(angle)
            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            drawCircle(MetallicGold, radius = 3.dp.toPx(), center = Offset(x, y))
        }
        dataPath.close()
        // 내부 채우기 (그라데이션 느낌)
        drawPath(dataPath, color = MetallicGold.copy(alpha = 0.15f))
        // 외곽선
        drawPath(dataPath, style = Stroke(width = 2.dp.toPx()), color = MetallicGold)

        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = labelColor
                textSize = 11.dp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

            labels.forEachIndexed { i, labelKey ->
                val angle = i * angleStep + startAngle
                val labelRadius = radius + 22.dp.toPx()
                val x = center.x + labelRadius * cos(angle)
                val y = center.y + labelRadius * sin(angle) + 5.dp.toPx()

                val resName = "fortune_${labelKey.lowercase()}"
                val resId = context.resources.getIdentifier(resName, "string", context.packageName)
                val text = if (resId != 0) context.getString(resId) else labelKey

                canvas.nativeCanvas.drawText(text, x, y, paint)
            }
        }
    }
}

@Composable
fun DeepAnalysisButton(isSubscribed: Boolean, onClick: () -> Unit, onSubscribe: () -> Unit) {
    // 📌 3. 디자인 개선: 반짝이는 효과와 입체적인 카드 UI 적용
    val infiniteTransition = rememberInfiniteTransition(label = "deep_anim")

    // 테두리나 배경이 은은하게 빛나는 애니메이션
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 그라데이션 색상 정의
    val bgGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2E1065), // Deep Purple
            Color(0xFF4C1D95), // Lighter Purple
            Color(0xFF1E1B4B)  // Dark Blue
        ),
        start = Offset(0f, 0f),
        end = Offset(1000f, 1000f)
    )

    val borderBrush = Brush.sweepGradient(
        listOf(
            MysticPurple.copy(alpha = 0.3f),
            MetallicGold.copy(alpha = pulseAlpha), // 애니메이션 적용
            MysticPurple.copy(alpha = 0.3f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // 그림자 효과로 떠있는 느낌
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if(isSubscribed) MysticPurple else Color.Black
            )
            .clip(RoundedCornerShape(24.dp))
            .background(bgGradient)
            .border(2.dp, borderBrush, RoundedCornerShape(24.dp))
            .clickable {
                if (isSubscribed) onClick() else onSubscribe()
            }
    ) {
        // 배경에 미세한 패턴 추가 (선택사항)
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.1f)) {
            drawCircle(Color.White, radius = size.width * 0.2f, center = Offset(size.width, 0f))
            drawCircle(Color.White, radius = size.width * 0.1f, center = Offset(0f, size.height))
        }

        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 왼쪽: 아이콘 영역
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if(isSubscribed) Icons.Default.AutoAwesome else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if(isSubscribed) ChampagneGold else TextSub,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 가운데: 텍스트 영역
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.fortune_deep_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(if(isSubscribed) R.string.fortune_deep_desc else R.string.fortune_deep_lock_desc),
                    color = TextSub,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 오른쪽: 화살표 또는 CTA
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if(isSubscribed) MetallicGold else TextSub.copy(alpha = 0.5f)
            )
        }

        // 하단에 "잠금 해제" 뱃지 (미구독 시)
        if (!isSubscribed) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        color = MetallicGold,
                        shape = RoundedCornerShape(topStart = 16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.fortune_deep_unlock),
                    color = DarkBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun LuckyItemView(title: String, value: String, isColor: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(CardBg)
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isColor) {
                val c = try { Color(android.graphics.Color.parseColor(value)) } catch(e:Exception){ Color.Gray }
                Box(Modifier.size(28.dp).clip(CircleShape).background(c))
            } else {
                Text(value, color = MetallicGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextSub, fontSize = 11.sp)
    }
}

@Composable
fun SectionCardView(section: FortuneSectionUi, onClick: () -> Unit) {
    Box(
        Modifier.height(100.dp).clip(RoundedCornerShape(16.dp))
            .background(CardBg).clickable(onClick = onClick).padding(16.dp)
    ) {
        Column {
            Text(stringResource(section.titleResId), color = MetallicGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(section.body, color = TextSub, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3)
        }
        if (section.score != null) {
            Text(
                "${section.score}",
                color = Color(section.colorInt), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
fun GlassContainer(title: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg).border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MetallicGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun DeepAnalysisDialog(result: DeepFortuneResult, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkBg)
                .border(1.dp, MysticPurple.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fortune_deep_title), color = MysticPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Close, null, tint = TextSub, modifier = Modifier.clickable { onDismiss() })
                }
                Spacer(Modifier.height(24.dp))

                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    TimeFlowGraphEnhanced(result.flowCurve, result.timeLabels)
                }
                Spacer(Modifier.height(24.dp))

                DeepSection(Icons.Default.Star, "Key Highlights", result.highlights)
                DeepSectionText(Icons.Default.Balance, "Risk & Opportunity", result.riskAndOpportunity)
                DeepSectionText(Icons.Default.Lightbulb, "Actionable Tip", result.solution)
            }
        }
    }
}

@Composable
fun TimeFlowGraphEnhanced(points: List<Int>, labels: List<String>) {
    Canvas(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 20.dp)) {
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1)
        val path = Path()

        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = h - (p / 100f * h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(MetallicGold, 4.dp.toPx(), Offset(x, y))
        }

        drawPath(path, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round), color = MetallicGold)
        drawLine(TextSub.copy(0.2f), Offset(0f, h/2), Offset(w, h/2))

        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = TextSub.toArgb()
                textSize = 10.dp.toPx()
                textAlign = Paint.Align.CENTER
            }
            labels.forEachIndexed { i, label ->
                val x = i * stepX
                canvas.nativeCanvas.drawText(label, x, h + 15.dp.toPx(), paint)
            }
        }
    }
}

@Composable
fun DeepSection(icon: ImageVector, title: String, items: List<String>) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MysticPurple, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(title, color = MysticPurple, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(16.dp)) {
            Column { items.forEach { Text("• $it", color = TextMain, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp)) } }
        }
    }
}

@Composable
fun DeepSectionText(icon: ImageVector, title: String, content: String) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MysticPurple, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(title, color = MysticPurple, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(16.dp)) {
            Text(content, color = TextMain.copy(0.9f), fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun RadarDetailDialog(sections: List<FortuneSectionUi>, onDismiss: () -> Unit) {
    LuxuryDialog(onDismiss) {
        Column {
            Text(stringResource(R.string.fortune_overall), color = MetallicGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            sections.filter { !it.isLotto }.forEach { sec ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(sec.titleResId), color = TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                    Column(Modifier.weight(1f)) {
                        LinearProgressIndicator(
                            progress = { (sec.score ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(sec.colorInt),
                            trackColor = Color.DarkGray
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(sec.body, color = TextSub, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${sec.score}", color = Color(sec.colorInt), fontWeight = FontWeight.Bold)
                }
                if (sec != sections.last()) HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GlassBorder)) {
                Text(stringResource(R.string.ok), color = TextMain)
            }
        }
    }
}

@Composable
fun SectionDetailDialog(section: FortuneSectionUi, onDismiss: () -> Unit) {
    LuxuryDialog(onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(section.titleResId), color = MetallicGold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            if (section.isLotto) Text(section.body, color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            else Text(section.body, color = TextMain, fontSize = 16.sp, lineHeight = 26.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GlassBorder)) { Text(stringResource(R.string.ok), color = TextMain) }
        }
    }
}

@Composable
fun LuxuryDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DarkBg).border(1.dp, MetallicGold.copy(0.5f), RoundedCornerShape(24.dp))) {
            Column(Modifier.padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Icon(Icons.Default.Close, null, tint = TextSub, modifier = Modifier.clickable { onDismiss() }) }
                content()
            }
        }
    }
}

@Composable
fun FortuneAlertDialog(title: String, text: String, confirmText: String, onConfirm: () -> Unit, dismissText: String?, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, color = TextMain, fontWeight = FontWeight.Bold) }, text = { Text(text, color = TextSub) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = MetallicGold) } },
        dismissButton = if (dismissText != null) { { TextButton(onClick = onDismiss) { Text(dismissText, color = TextSub) } } } else null,
        containerColor = DarkBg, shape = RoundedCornerShape(16.dp))
}

@Composable
fun LottieLoader(resId: Int) {
    AndroidView(modifier = Modifier.size(150.dp), factory = { ctx -> LottieAnimationView(ctx).apply { setAnimation(resId); repeatCount = LottieDrawable.INFINITE; playAnimation() } })
}