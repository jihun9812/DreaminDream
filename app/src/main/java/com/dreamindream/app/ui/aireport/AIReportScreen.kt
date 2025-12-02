package com.dreamindream.app.ui.aireport

import android.os.Build
import android.text.Layout
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dreamindream.app.R
import com.dreamindream.app.chart.renderPercentBars
import com.dreamindream.app.chart.richEmotionColor
import com.dreamindream.app.chart.richThemeColor
import com.dreamindream.app.chart.setupBarChart
import com.dreamindream.app.chart.useRoundedBars
import com.dreamindream.app.AdPageScaffold   // 네가 만든 AdkitCompose 패키지 이름에 맞게 수정
import com.dreamindream.app.ads.openGate        // 예시: 실제 시그니처에 맞게 수정
import com.dreamindream.app.AdManager
import com.github.mikephil.charting.charts.BarChart
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

@Composable
fun AIReportRoute(
    weekKeyArg: String?,
    // 네비게이션 콜백들
    onEmptyCta: () -> Unit,
    onOpenDreamWrite: () -> Unit = onEmptyCta,
    viewModel: AIReportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(weekKeyArg) {
        viewModel.onStart(weekKeyArg)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // 스낵바 처리
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.onSnackbarShown()
        }
    }


    AdPageScaffold(
        adUnitRes = R.string.ad_unit_ai_banner
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AIReportScreen(
                state = uiState,
                modifier = Modifier.fillMaxSize(),
                onClickHistory = { viewModel.onHistoryClicked() },
                onClickChartInfo = { viewModel.onChartInfoClicked() },
                onClickPro = {
                    val shouldOpenGate = viewModel.onProButtonClicked()
                    if (shouldOpenGate) {
                        AdManager.openGate {
                            viewModel.onProGateUnlocked()
                        }
                    }
                }
            )

            // Snackbar
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            )
        }
    }

    // 빈 상태 다이얼로그 (예전 WeeklyHistoryDialogFragment 대체)
        if (uiState.showEmptyDialog) {
            WeeklyHistoryEmptyDialog(
                onDismiss = { viewModel.onEmptyDialogDismissed() },
                onCta = {
                    viewModel.onEmptyDialogCta()
                    onOpenDreamWrite()
                }
            )
        }

        // 히스토리 바텀시트 (예전 WeeklyHistoryBottomSheet 대체)
        if (uiState.showHistorySheet) {
            WeeklyHistorySheet(
                weeks = uiState.historyWeeks,
                currentWeekKey = uiState.targetWeekKey,
                totalLabel = uiState.historyTotalWeeksLabel,
                onDismiss = { viewModel.onHistorySheetDismiss() },
                onPick = { picked ->
                    viewModel.onHistoryWeekPicked(picked)
                }
            )
        }

        if (uiState.showChartInfoDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onChartInfoDialogDismiss() },
                confirmButton = {
                    TextButton(onClick = { viewModel.onChartInfoDialogDismiss() }) {
                        Text(stringResource(id = R.string.ok))
                    }
                },
                title = { Text(stringResource(id = R.string.chart_info_title)) },
                text = { Text(uiState.chartInfoMessage) }
            )
        }
    }
}

@Composable
fun AIReportScreen(
    state: AIReportUiState,
    modifier: Modifier = Modifier,
    onClickHistory: () -> Unit,
    onClickChartInfo: () -> Unit,
    onClickPro: () -> Unit,
) {
    val bgPainter = painterResource(id = R.drawable.main_ground)

    Box(
        modifier = modifier
            .background(bgPainter)
            .padding(horizontal = dimensionResource(id = R.dimen.content_side_padding))
    ) {

        // 빈 상태 아이콘
        if (state.showEmptyState) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_no_data),
                    contentDescription = null,
                    modifier = Modifier
                        .size(dimensionResource(id = R.dimen.icon_btn))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.ai_report_empty),
                    color = Color(0xFFE1F0FA),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.showReportCard) {
            // 카드와 PRO 스피너를 함께 감싸서 Constraint 비슷하게
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .widthIn(max = dimensionResource(id = R.dimen.ai_card_max_width))
            ) {
                ReportCard(
                    state = state,
                    onClickHistory = onClickHistory,
                    onClickChartInfo = onClickChartInfo,
                    onClickPro = onClickPro,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.isProSpinnerVisible) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportCard(
    state: AIReportUiState,
    modifier: Modifier = Modifier,
    onClickHistory: () -> Unit,
    onClickChartInfo: () -> Unit,
    onClickPro: () -> Unit
) {
    val cardCorner = dimensionResource(id = R.dimen.ai_card_corner)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(cardCorner),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            color = Color(0xFF1D1D1D)
        ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    painter = painterResource(id = R.drawable.bg_panel_glass),
                    shape = RoundedCornerShape(cardCorner)
                )
                .padding(dimensionResource(id = R.dimen.card_pad))
        ) {
            Column(
                modifier = Modifier.fillMaxHeight()
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_chart),
                        contentDescription = null,
                        modifier = Modifier.size(dimensionResource(id = R.dimen.ai_icon))
                    )
                    Text(
                        text = state.weekLabel.ifBlank {
                            stringResource(id = R.string.ai_report_week_label_default)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFFE1F0FA),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    TextButton(
                        onClick = onClickHistory,
                        modifier = Modifier.height(29.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color(0x332A355C),
                            contentColor = Color(0xFFE1F0FA)
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.ai_report_history),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Divider
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.divider_margin_top)))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFFFD54F))
                )
                Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.divider_margin_bottom)))

                // 스크롤 영역
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    // KPI 3개 카드
                    KpiRow(state = state)

                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.section_margin_top)))

                    // 차트 타이틀 + info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.section_title_emotion_percent),
                            color = Color(0xFFE1F0FA),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onClickChartInfo) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chart),
                                contentDescription = null,
                                tint = Color(0x80FFFFFF)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    EmotionBarChart(
                        labels = state.emotionLabels,
                        values = state.emotionDist
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(id = R.string.section_title_theme_percent),
                        color = Color(0xFFE1F0FA),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ThemeBarChart(
                        labels = state.themeLabels,
                        values = state.themeDist
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(id = R.string.chart_caption),
                        color = Color(0xFFB0C4D8),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.section_margin_top)))

                    // 키워드
                    Text(
                        text = state.keywordsLine,
                        color = Color(0xFFE1F0FA),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.section_margin_top)))

                    // PRO 버튼 / 리프레시 힌트
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProGradientButton(
                            text = state.proButtonText.ifBlank {
                                stringResource(id = R.string.pro_cta)
                            },
                            enabled = state.proButtonEnabled,
                            alpha = state.proButtonAlpha,
                            onClick = onClickPro,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.label_refresh_hint),
                            color = Color(0x80E1F0FA),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.section_margin_top)))

                    // 분석 타이틀 + 내용
                    Text(
                        text = state.analysisTitle.ifBlank {
                            stringResource(id = R.string.ai_basic_title)
                        },
                        color = Color(0xFFE1F0FA),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    HtmlRichText(
                        html = state.analysisHtml,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun KpiRow(state: AIReportUiState) {
    val cardShape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Positive
        Card(
            modifier = Modifier
                .weight(1f)
                .height(dimensionResource(id = R.dimen.kpi_card_height)),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = CardDefaults.outlinedCardBorder().copy(
                color = Color(0x2EFFFFFF),
                width = 1.dp
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.kpi_positive),
                    color = Color(0xFF89EFCB),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = state.kpiPositiveText,
                    color = Color(0xFFE7FFF6),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.kpi_gap_h)))

        Card(
            modifier = Modifier
                .weight(1f)
                .height(dimensionResource(id = R.dimen.kpi_card_height)),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = CardDefaults.outlinedCardBorder().copy(
                color = Color(0x2EFFFFFF),
                width = 1.dp
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.kpi_neutral),
                    color = Color(0xFFBFCBD1),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = state.kpiNeutralText,
                    color = Color(0xFFF0F6FB),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.kpi_gap_h)))

        Card(
            modifier = Modifier
                .weight(1f)
                .height(dimensionResource(id = R.dimen.kpi_card_height)),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = CardDefaults.outlinedCardBorder().copy(
                color = Color(0x2EFFFFFF),
                width = 1.dp
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.kpi_negative),
                    color = Color(0xFFFB99AD),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = state.kpiNegativeText,
                    color = Color(0xFFFFE6EC),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun ProGradientButton(
    text: String,
    enabled: Boolean,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFEDCA6),
            Color(0xFF8BAAFF)
        )
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.Black,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.Black.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(gradient)
                .alpha(alpha)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/** 기존 TextView + HtmlCompat 스타일을 유지하기 위해 AndroidView 사용 */
@Composable
private fun HtmlRichText(
    html: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            TextView(it).apply {
                includeFontPadding = false
                setLineSpacing(1f, 1.10f)
                if (Build.VERSION.SDK_INT >= 26) {
                    justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
                }
                setTextColor(0xFFE1F0FA.toInt())
            }
        },
        update = { tv ->
            val spanned = HtmlCompat.fromHtml(
                html,
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            tv.text = spanned
        },
        modifier = modifier
    )
}

@Composable
private fun EmotionBarChart(
    labels: List<String>,
    values: List<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(190.dp)
) {
    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply {
                setupBarChart(this)
                useRoundedBars(this, 12f)
            }
        },
        update = { chart ->
            if (labels.isNotEmpty() && values.isNotEmpty()) {
                renderPercentBars(
                    chart,
                    labels,
                    values,
                    ::richEmotionColor
                )
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ThemeBarChart(
    labels: List<String>,
    values: List<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(190.dp)
) {
    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply {
                setupBarChart(this)
                useRoundedBars(this, 12f)
            }
        },
        update = { chart ->
            if (labels.isNotEmpty() && values.isNotEmpty()) {
                renderPercentBars(
                    chart,
                    labels,
                    values,
                    ::richThemeColor
                )
            }
        },
        modifier = modifier
    )
}

/* ----- WeeklyHistory Empty Dialog (Compose 버전) ----- */

@Composable
private fun WeeklyHistoryEmptyDialog(
    onDismiss: () -> Unit,
    onCta: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.whd_hero_title))
        },
        text = {
            Text(text = stringResource(id = R.string.whd_hero_sub))
        },
        confirmButton = {
            TextButton(onClick = {
                onCta()
            }) {
                Text(text = stringResource(id = R.string.whd_cta_go_record))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

/* ----- WeeklyHistory BottomSheet (Compose 버전) ----- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklyHistorySheet(
    weeks: List<String>,
    currentWeekKey: String?,
    totalLabel: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.whs_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A93A6)
            )

            Spacer(modifier = Modifier.height(12.dp))

            weeks.forEach { weekKey ->
                val isCurrent = (weekKey == currentWeekKey)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .let {
                            if (isCurrent) it.background(
                                Color(0x4D352D49),
                                RoundedCornerShape(12.dp)
                            ) else it
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .clickable {
                            onPick(weekKey)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = weekKey,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE1F0FA)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isCurrent) {
                        Text(
                            text = stringResource(id = R.string.chip_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1F2234),
                            modifier = Modifier
                                .background(
                                    Color(0xFFE3F0FF),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------- Preview ----------

@Preview(showBackground = true)
@Composable
private fun AIReportScreenPreviewEmpty() {
    AIReportScreen(
        state = AIReportUiState(
            showReportCard = false,
            showEmptyState = true
        ),
        onClickHistory = {},
        onClickChartInfo = {},
        onClickPro = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun AIReportScreenPreviewReport() {
    AIReportScreen(
        state = AIReportUiState(
            showReportCard = true,
            showEmptyState = false,
            weekLabel = "이번 주 감정 리포트\n총 5개의 기록에서 분석",
            analysisTitle = "기본 AI 해석",
            keywordsLine = "기분: 잔잔함 · 키워드: 휴식",
            emotionLabels = listOf("긍정", "중립", "부정"),
            emotionDist = listOf(40f, 30f, 30f),
            themeLabels = listOf("관계", "일/성취", "변화"),
            themeDist = listOf(34f, 33f, 33f),
            kpiPositiveText = "60.0%",
            kpiNeutralText = "20.0%",
            kpiNegativeText = "20.0%",
            proButtonText = "PRO 심층 분석 받기",
            proButtonEnabled = true,
        ),
        onClickHistory = {},
        onClickChartInfo = {},
        onClickPro = {}
    )
}
