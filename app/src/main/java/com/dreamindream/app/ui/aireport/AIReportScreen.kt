package com.dreamindream.app.ui.aireport

import android.annotation.SuppressLint
import android.widget.TextView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dreamindream.app.AdPageScaffold
import com.dreamindream.app.R
import com.dreamindream.app.chart.renderPercentBars
import com.dreamindream.app.chart.richEmotionColor
import com.dreamindream.app.chart.richThemeColor
import com.dreamindream.app.chart.setupBarChart
import com.dreamindream.app.chart.useRoundedBars
import com.github.mikephil.charting.charts.BarChart

// --- Fonts ---
private val PretendardBold = FontFamily(Font(R.font.pretendard_bold, FontWeight.Bold))
private val PretendardMedium = FontFamily(Font(R.font.pretendard_medium, FontWeight.Medium))

// --- Colors ---
private val DeepNavy = Color(0xFF121626)
private val TextWhite = Color(0xFFEEEEEE)
private val TextGray = Color(0xFFB0BEC5)
private val AccentGold = Color(0xFFFFD54F)
private val CardBg = Color(0x1AFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIReportRoute(
    weekKeyArg: String?,
    onEmptyCta: () -> Unit,
    onOpenDreamWrite: () -> Unit = onEmptyCta,
    onNavigateToSubscription: () -> Unit = {},
    viewModel: AIReportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(weekKeyArg) { viewModel.onStart(weekKeyArg) }

    LaunchedEffect(uiState.navigateToSubscription) {
        if (uiState.navigateToSubscription) {
            viewModel.onSubscriptionNavigationHandled()
            onNavigateToSubscription()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarShown()
        }
    }

    // History BottomSheet
    if (uiState.showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onHistorySheetDismiss() },
            containerColor = DeepNavy,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(id = R.string.ai_report_history),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = PretendardBold,
                    color = TextWhite,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                // History List Implementation...
            }
        }
    }


     AdPageScaffold(adUnitRes = R.string.ad_unit_ai_banner) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy)
                .padding(innerPadding)

        ) {
            // Background Art
            Image(
                painter = painterResource(R.drawable.main_ground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.4f),
                contentScale = ContentScale.Crop
            )

            AIReportScreen(
                state = uiState,
                onClickHistory = viewModel::onHistoryClicked,
                onClickChartInfo = viewModel::onChartInfoClicked,
                onClickPro = { if (viewModel.onProButtonClicked()) viewModel.onProGateUnlocked() }
            )

            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

            // Creative Loading Overlay
            if (uiState.isLoading || uiState.isProSpinnerVisible) {
                CreativeLoadingView(message = uiState.loadingMessage)
            }
        }
    }
}

@Composable
fun AIReportScreen(
    state: AIReportUiState,
    onClickHistory: () -> Unit,
    onClickChartInfo: () -> Unit,
    onClickPro: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.showReportCard) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp) // 여백 조정
            ) {
                // --- Header ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text(
                        state.weekLabel,
                        color = TextWhite,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = PretendardBold,
                        modifier = Modifier.weight(1f)
                    )

                    // History Button
                    IconButton(onClick = onClickHistory) {
                        Icon(Icons.Default.History, contentDescription = "History", tint = TextGray)
                    }
                }

                Spacer(Modifier.height(20.dp))
                StatsRow(state)
                Spacer(Modifier.height(24.dp))

                // --- Keywords ---
                if (state.keywordsLine.isNotBlank()) {
                    Text("# Keywords", color = AccentGold, fontFamily = PretendardBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(state.keywordsLine, color = TextWhite, fontFamily = PretendardMedium, fontSize = 18.sp, lineHeight = 26.sp)
                    Spacer(Modifier.height(24.dp))
                }

                // --- Charts ---
                ChartSection(stringResource(R.string.chart_emotion_title), state.emotionLabels, state.emotionDist, true, onClickChartInfo)
                Spacer(Modifier.height(24.dp))
                ChartSection(stringResource(R.string.chart_theme_title), state.themeLabels, state.themeDist, false, null)
                Spacer(Modifier.height(30.dp))

                // --- Analysis Content (Switch between Basic & Pro) ---
                Crossfade(targetState = state.isProCompleted, label = "AnalysisSwitch") { isPro ->
                    if (isPro) {
                        // ★ Deep Analysis View 연결
                        DeepAnalysisResultView(jsonString = state.analysisJson)
                    } else {
                        // Basic Text View
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(CardBg)
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                                .padding(20.dp)
                        ) {
                            Column {
                                Text(stringResource(R.string.ai_report_deep_title), color = AccentGold, fontFamily = PretendardBold, fontSize = 16.sp)
                                Spacer(Modifier.height(12.dp))
                                HtmlRichText(state.analysisHtml)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // --- Pro Unlock Button ---
                if (!state.isProCompleted) {
                    ProGradientButton(
                        text = stringResource(R.string.ai_report_pro_cta),
                        enabled = state.proButtonEnabled,
                        alpha = state.proButtonAlpha,
                        onClick = onClickPro
                    )
                }

                // --- Dream Count Footer (4개 분석됨 표시) ---
                Spacer(Modifier.height(30.dp))
                Text(
                    text = stringResource(
                        R.string.this_week_dream_count,
                        state.thisWeekDreamCount
                    ),
                    color = TextGray.copy(alpha = 0.6f),
                    fontFamily = PretendardMedium,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(50.dp))
            }

        } else if (state.showEmptyState && !state.isLoading) {
            // Empty State
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_no_data), // 아이콘 필요
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).alpha(0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.ai_report_empty),
                    color = TextGray,
                    fontFamily = PretendardMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// --- Creative Loading View ---
@Composable
fun CreativeLoadingView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy.copy(alpha = 0.9f)) // 배경을 살짝 어둡게 깔아서 몰입감 증대
            .clickable(enabled = false) {}, // 터치 차단
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 커스텀 로더 (또는 Lottie 애니메이션 권장)
            CircularProgressIndicator(
                color = AccentGold,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(24.dp))

            // 감성적인 로딩 멘트
            Text(
                text = message,
                color = TextWhite,
                fontFamily = PretendardBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

// --- Stats Row ---
@Composable
fun StatsRow(state: AIReportUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatChip(stringResource(R.string.ai_report_stat_emotion), state.dominantEmotion, Modifier.weight(1f))
        StatChip(stringResource(R.string.ai_report_stat_count), "${state.thisWeekDreamCount}", Modifier.weight(1f))
        StatChip(stringResource(R.string.ai_report_stat_score), "${state.dreamGrade}", Modifier.weight(1f))
    }
}

@Composable
fun StatChip(title: String, value: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontFamily = PretendardMedium, fontSize = 11.sp, color = TextGray)
            Spacer(Modifier.height(4.dp))
            Text(value, fontFamily = PretendardBold, fontSize = 18.sp, color = TextWhite)
        }
    }
}

@Composable
fun ChartSection(title: String, labels: List<String>, values: List<Float>, isEmo: Boolean, onInfo: (() -> Unit)?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextWhite, fontFamily = PretendardBold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (onInfo != null) {
                Icon(
                    imageVector = Icons.Default.Info, // 아이콘 변경
                    contentDescription = "Info",
                    tint = TextGray,
                    modifier = Modifier.size(18.dp).clickable { onInfo() }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Chart logic remains...
        AndroidView(
            factory = { ctx -> BarChart(ctx).apply { setupBarChart(this); useRoundedBars(this, 12f) } },
            update = { chart ->
                if (labels.isNotEmpty()) {
                    renderPercentBars(chart, labels, values, if(isEmo) ::richEmotionColor else ::richThemeColor)
                }
            },
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
    }
}

// Rich Text, Pro Button 등 나머지 컴포넌트는 기존과 동일하지만 폰트 적용
@SuppressLint("SetTextI18n")
@Composable
fun HtmlRichText(html: String) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextColor(0xFFEEEEEE.toInt())
                textSize = 15f
                setLineSpacing(0f, 1.5f)
                // typeface 설정 가능 (Pretendard 폰트 파일을 asset에서 로드해야 함)
            }
        },
        update = { it.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY) }
    )
}
@Composable
fun ProGradientButton(
    text: String,
    enabled: Boolean,
    alpha: Float,
    onClick: () -> Unit
) {
    // 고급스러운 골드-블루 그라데이션
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFEDCA6), Color(0xFF8BAAFF))
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .height(54.dp) // 터치하기 좋게 높이 약간 증가
            .fillMaxWidth()
            .alpha(alpha)
            .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = AccentGold, spotColor = AccentGold) // 살짝 빛나는 효과 추가
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 그라데이션 배경
            Box(modifier = Modifier.matchParentSize().background(gradient))

            // 텍스트 (Pretendard Bold 적용)
            Text(
                text = text,
                color = Color(0xFF121626), // DeepNavy (가독성)
                fontFamily = PretendardBold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}