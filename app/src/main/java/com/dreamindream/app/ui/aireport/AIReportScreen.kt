package com.dreamindream.app.ui.aireport

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dreamindream.app.AdPageScaffold
import com.dreamindream.app.R
import com.dreamindream.app.WeekEntry
import com.dreamindream.app.SubscriptionManager

// --- Reuse Fortune Colors locally for UI consistency ---
private val PremiumGold = Color(0xFFD4AF37)
private val TextPrimary = Color(0xFFEEEEEE)

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
    val isSubscribed by SubscriptionManager.isSubscribed.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(weekKeyArg) { viewModel.onStart(weekKeyArg) }
    LaunchedEffect(uiState.navigateToSubscription) {
        if (uiState.navigateToSubscription) {
            viewModel.onSubscriptionNavigationHandled()
            onNavigateToSubscription()
        }
    }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarShown()
        }
    }

    if (uiState.showChartInfoDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onChartInfoDialogDismiss,
            title = { Text(stringResource(R.string.ai_report_chart_info_title), color = ColorTextMain, fontFamily = FontBold) },
            text = { Text(uiState.chartInfoMessage, color = ColorTextSub, fontFamily = FontMedium) },
            confirmButton = {
                TextButton(onClick = viewModel::onChartInfoDialogDismiss) {
                    Text(stringResource(R.string.ai_report_chart_info_ok), color = ColorGold)
                }
            },
            containerColor = ColorCardSurface
        )
    }

    // ★ Dream Selection Dialog (3개 이상일 때)
    if (uiState.showDreamSelectionDialog) {
        DreamSelectionDialog(
            dreams = uiState.availableDreamsForSelection,
            onDismiss = viewModel::onDreamSelectionDialogDismiss,
            onConfirm = viewModel::onDreamsSelectedForAnalysis
        )
    }

    if (uiState.showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onHistorySheetDismiss,
            containerColor = ColorBgDark,
            scrimColor = Color.Black.copy(0.6f)
        ) {
            HistoryTimelineSheet(
                weeks = uiState.historyWeeks,
                onWeekSelected = viewModel::onHistoryWeekPicked
            )
        }
    }

    AdPageScaffold(adUnitRes = R.string.ad_unit_ai_banner) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(ColorBgDark, Color(0xFF000000))))
                .padding(innerPadding)
        ) {
            Image(
                painter = painterResource(R.drawable.main_ground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.2f),
                contentScale = ContentScale.Crop
            )

            AIReportScreen(
                state = uiState,
                isSubscribed = isSubscribed,
                onClickHistory = viewModel::onHistoryClicked,
                onClickChartInfo = viewModel::onChartInfoClicked,
                onClickPro = { if (viewModel.onProButtonClicked()) viewModel.onProGateUnlocked() }
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (uiState.isLoading || uiState.isProSpinnerVisible) {
                CreativeLoadingView(message = uiState.loadingMessage)
            }
        }
    }
}

@Composable
fun AIReportScreen(
    state: AIReportUiState,
    isSubscribed: Boolean,
    onClickHistory: () -> Unit,
    onClickChartInfo: () -> Unit,
    onClickPro: () -> Unit
) {
    if (state.showReportCard) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // --- Header ---
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_chart),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = state.weekLabel,
                        color = ColorTextMain,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontBold
                    )
                    Text(
                        text = stringResource(R.string.ai_report_weekly_insight_subtitle),
                        color = ColorGold,
                        fontSize = 12.sp,
                        fontFamily = FontMedium
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClickHistory) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = stringResource(R.string.ai_report_history_content_description),
                        tint = ColorTextSub
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Stats Dashboard ---
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = stringResource(R.string.ai_report_stat_core_emotion),
                    value = state.dominantEmotion,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.ai_report_stat_total_dreams),
                    value = "${state.thisWeekDreamCount}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.ai_report_stat_score),
                    value = state.dreamGrade,
                    modifier = Modifier.weight(1f),
                    highlight = true
                )
            }

            // --- Emotion Chart ---
            Spacer(Modifier.height(30.dp))
            ChartContainer(
                title = stringResource(R.string.chart_emotion_title),
                labels = state.emotionLabels,
                values = state.emotionDist,
                isEmo = true,
                onInfo = onClickChartInfo
            )

            // --- Content Area ---
            Spacer(Modifier.height(40.dp))
            Divider(color = Color.White.copy(0.1f))
            Spacer(Modifier.height(24.dp))

            Crossfade(targetState = state.isProCompleted, label = "AnalysisContent") { isPro ->
                if (isPro) {
                    DeepAnalysisTabs(state)
                } else {
                    // ★ Storytelling for Basic
                    BasicAnalysisView(state.analysisHtml)
                }
            }

            Spacer(Modifier.height(30.dp))

            // ★ Replaced Old Button with Fortune-Style Entry Card
            if (!state.isProCompleted) {
                DeepAnalysisEntry(isSubscribed = isSubscribed, onClick = onClickPro)
            }

            Spacer(Modifier.height(50.dp))
        }
    } else if (state.showEmptyState && !state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.NightsStay,
                    contentDescription = null,
                    tint = ColorTextSub,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.ai_report_empty),
                    color = ColorTextSub,
                    fontFamily = FontMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// =========================================================
// ★ Dream Selection Dialog (Human Selection Logic)
// =========================================================
@Composable
fun DreamSelectionDialog(
    dreams: List<WeekEntry>,
    onDismiss: () -> Unit,
    onConfirm: (List<WeekEntry>) -> Unit
) {
    // Select latest 4 by default or all if <4
    val initialSelection = remember {
        dreams.sortedByDescending { it.ts }.take(4).map { it.id }.toSet()
    }
    var selectedIds by remember { mutableStateOf(initialSelection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = {
            // ★ [수정] 하드코딩된 "Select Dreams..." 제거 -> stringResource 사용
            Text(
                text = stringResource(R.string.dream_select_title),
                color = PremiumGold,
                fontFamily = FontBold
            )
        },
        text = {
            Column {
                // ★ [수정] 하드코딩된 설명 제거
                Text(
                    text = stringResource(R.string.dream_select_desc),
                    color = TextPrimary.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    dreams.forEach { dream ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    val isSelected = selectedIds.contains(dream.id)
                                    if (isSelected) {
                                        selectedIds = selectedIds - dream.id
                                    } else {
                                        if (selectedIds.size < 4) {
                                            selectedIds = selectedIds + dream.id
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selectedIds.contains(dream.id)) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selectedIds.contains(dream.id)) PremiumGold else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = dream.dream.take(50) + if(dream.dream.length>50)"..." else "",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 2
                            )
                        }
                        Divider(color = Color.White.copy(0.05f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedObjects = dreams.filter { selectedIds.contains(it.id) }
                    onConfirm(selectedObjects)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                enabled = selectedIds.isNotEmpty()
            ) {
                // ★ [수정] "Analyze" 버튼 텍스트 리소스화
                Text(
                    text = stringResource(R.string.dream_select_confirm, selectedIds.size),
                    color = Color.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                // ★ [수정] "Cancel" 버튼 텍스트 리소스화
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = Color.Gray
                )
            }
        }
    )
}

// ... (DeepAnalysisEntry, SecretTextPattern 기존 코드 동일) ...
@Composable
fun DeepAnalysisEntry(isSubscribed: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "border")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "alpha"
    )

    val borderColor = if (isSubscribed) PremiumGold.copy(alpha = alpha) else Color.White.copy(alpha = 0.1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        if (isSubscribed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF2E004B), Color(0xFF190028))))
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0F)))

            Box(modifier = Modifier.fillMaxSize().alpha(0.08f).rotate(-15f)) {
                SecretTextPattern()
            }

            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha=0.9f)),
                        radius = 400f
                    )
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isSubscribed) {
                    Text(stringResource(R.string.deep_unlock_title), color = PremiumGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.deep_view_full), color = TextPrimary.copy(alpha=0.8f), fontSize = 13.sp)
                } else {
                    Text("Hidden Destiny Analysis", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.deep_premium_content), color = PremiumGold.copy(alpha=0.8f), fontSize = 12.sp)
                }
            }

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSubscribed) SolidColor(PremiumGold)
                        else Brush.linearGradient(listOf(Color(0xFFD4AF37), Color(0xFF8B7500)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSubscribed) Icons.Rounded.ArrowForward else Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SecretTextPattern() {
    Column(Modifier.fillMaxSize().wrapContentSize(unbounded = true)) {
        repeat(8) {
            Row {
                repeat(12) {
                    Text(
                        text = "PREMIUM FUTURE ",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}