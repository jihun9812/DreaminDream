package com.dreamindream.app.ui.fortune

import android.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.LottieAnimationView
import com.dreamindream.app.AdPageScaffold
import com.dreamindream.app.AdManager
import com.dreamindream.app.R
import androidx.compose.ui.viewinterop.AndroidView
import com.dreamindream.app.ui.fortune.FortuneScreenContent

@Composable
fun FortuneScreen(
    viewModel: FortuneViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    // 광고 초기화 (배너 + 보상형) :contentReference[oaicite:4]{index=4}
    LaunchedEffect(Unit) {
        AdManager.initialize(context)
        AdManager.loadRewarded(context)
    }

    // 스낵바 처리
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.onSnackbarShown()
        }
    }

    AdPageScaffold(
        adUnitRes = R.string.ad_unit_fortune_banner,
        topBar = null
    ) { innerPadding ->
        FortuneScreenContent(
            state = uiState,
            paddingValues = innerPadding,
            onStartClick = { viewModel.onStartButtonClick(context) },
            onChecklistToggle = { id, checked -> viewModel.onChecklistToggle(id, checked) },
            onSectionClick = { key -> viewModel.onSectionClicked(key) },
            onDeepClick = { viewModel.onDeepButtonClick(context) },
            snackbarHostState = snackbarHostState
        )

        if (uiState.showProfileDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onProfileDialogDismiss() },
                confirmButton = {
                    TextButton(onClick = { viewModel.onProfileDialogDismiss() }) {
                        Text(text = stringResource(id = R.string.dialog_ok))
                    }
                },
                title = {
                    Text(text = stringResource(id = R.string.dialog_profile_title))
                },
                text = {
                    Text(text = stringResource(id = R.string.dialog_profile_message))
                }
            )
        }

        uiState.sectionDialog?.let { dialogState ->
            SectionDetailDialog(
                data = dialogState,
                onDismiss = { viewModel.onSectionDialogDismiss() }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun BoxScope.FortuneScreenContent(
    state: FortuneUiState,
    paddingValues: PaddingValues,
    onStartClick: () -> Unit,
    onChecklistToggle: (Int, Boolean) -> Unit,
    onSectionClick: (String) -> Unit,
    onDeepClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val bgPainter = painterResource(id = R.drawable.main_ground)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Image(
            painter = bgPainter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            alpha = 1f
        )

        // 가운데 물음표 카드
        AnimatedVisibility(
            visible = state.showStartButton,
            modifier = Modifier.align(Alignment.Center)
        ) {
            FortuneStartButton(
                enabled = state.startButtonEnabled,
                breathing = state.startButtonBreathing,
                onClick = onStartClick
            )
        }

        // 결과 카드
        AnimatedVisibility(
            visible = state.showFortuneCard,
            modifier = Modifier
                .align(Alignment.TopCenter)
        ) {
            FortuneResultCard(
                state = state,
                onChecklistToggle = onChecklistToggle,
                onSectionClick = onSectionClick,
                onDeepClick = onDeepClick,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
            )
        }

        // 로딩 Lottie
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(ComposeColor.Black.copy(alpha = 0.30f)),
                contentAlignment = Alignment.Center
            ) {
                LottieLoading()
            }
        }

        // 오늘 이미 봤다는 안내 메시지 (카드/버튼 둘 다 없을 때)
        if (!state.showStartButton && !state.showFortuneCard && state.infoMessage != null) {
            Text(
                text = state.infoMessage,
                modifier = Modifier.align(Alignment.Center),
                color = ComposeColor(0xFFCFD8DC),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ------------------------------------------------------
//  Start Button (물음표 사각 버튼)
// ------------------------------------------------------
@Composable
private fun FortuneStartButton(
    enabled: Boolean,
    breathing: Boolean,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "breath")
    val scale by if (breathing && enabled) {
        infinite.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1.0f) }
    }

    val borderGradient = Brush.verticalGradient(
        colors = listOf(
            ComposeColor(0x66FFFFFF),
            ComposeColor(0x19FFFFFF)
        )
    )
    val innerGradient = Brush.verticalGradient(
        colors = listOf(
            ComposeColor(0xFF1D2233),
            ComposeColor(0xFF050713)
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(28.dp))
                .background(borderGradient)
                .padding(1.5.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(innerGradient)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                color = ComposeColor.White,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(id = R.string.btn_fortune_show),
            color = ComposeColor(0xFFE3F2FD),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            )
        )

        Text(
            text = stringResource(id = R.string.btn_fortune_show),
            color = ComposeColor(0xFFB0BEC5),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ------------------------------------------------------
//  결과 카드
// ------------------------------------------------------
@Composable
private fun FortuneResultCard(
    state: FortuneUiState,
    onChecklistToggle: (Int, Boolean) -> Unit,
    onSectionClick: (String) -> Unit,
    onDeepClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = Brush.verticalGradient(
        colors = listOf(
            ComposeColor(0xCC111827),
            ComposeColor(0xE6000513)
        )
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = ComposeColor.Transparent
        ),
        border = CardDefaults.outlinedCardBorder(
            borderColor = ComposeColor(0x66101010)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(glass)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val scroll = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
            ) {
                // 키워드 칩
                KeywordRow(state.keywords)

                Spacer(modifier = Modifier.height(8.dp))
                DividerLine()

                // 행운 지표
                LuckyRow(
                    colorHex = state.luckyColorHex,
                    number = state.luckyNumber,
                    time = state.luckyTime
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 감정 밸런스
                EmotionBalance(
                    pos = state.emoPositive,
                    neu = state.emoNeutral,
                    neg = state.emoNegative
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 체크리스트
                ChecklistSection(
                    items = state.checklist,
                    onToggle = onChecklistToggle
                )

                DividerLine()

                // 섹션 카드들
                state.sections.forEach { section ->
                    FortuneSectionCard(
                        section = section,
                        onClick = { if (!section.isLotto) onSectionClick(section.key) }
                    )
                }

                DividerLine()

                // 심화 분석 버튼
                DeepButton(
                    enabled = state.deepButtonEnabled,
                    label = if (state.deepButtonLabel.isNotBlank())
                        state.deepButtonLabel
                    else
                        stringResource(id = R.string.btn_deep_analysis),
                    onClick = onDeepClick
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun KeywordRow(keywords: List<String>) {
    if (keywords.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keywords.take(4).forEach { word ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                ComposeColor(0x332196F3),
                                ComposeColor(0x332EE7D9)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = word,
                    color = ComposeColor(0xFFE3F2FD),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun DividerLine() {
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .alpha(0.9f)
            .background(ComposeColor(0xFFFFD86B))
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun LuckyRow(
    colorHex: String,
    number: Int?,
    time: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 색
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            val dotColor = runCatching { ComposeColor(Color.parseColor(colorHex)) }
                .getOrElse { ComposeColor(0xFFFFD54F) }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.fortune_lucky_color),
                color = ComposeColor(0xFFFFD54F),
                style = MaterialTheme.typography.labelSmall
            )
        }

        // 숫자
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = number?.toString() ?: "-",
                color = ComposeColor.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(id = R.string.fortune_lucky_number),
                color = ComposeColor(0xFFFFD54F),
                style = MaterialTheme.typography.labelSmall
            )
        }

        // 시간
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (time.isNotBlank()) time else stringResource(id = R.string.placeholder_dash),
                color = ComposeColor.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(id = R.string.fortune_good_time),
                color = ComposeColor(0xFFFFD54F),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun EmotionBalance(
    pos: Int,
    neu: Int,
    neg: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(id = R.string.chart_emotion_balance),
            color = ComposeColor(0xFFC6D4DF),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        EmotionBarRow(
            label = stringResource(id = R.string.label_positive),
            value = pos,
            tint = ComposeColor(0xFF17D499)
        )
        Spacer(modifier = Modifier.height(4.dp))
        EmotionBarRow(
            label = stringResource(id = R.string.label_neutral),
            value = neu,
            tint = ComposeColor(0xFFFFC75F)
        )
        Spacer(modifier = Modifier.height(4.dp))
        EmotionBarRow(
            label = stringResource(id = R.string.label_negative),
            value = neg,
            tint = ComposeColor(0xFFFF6B7B)
        )
    }
}

@Composable
private fun EmotionBarRow(
    label: String,
    value: Int,
    tint: ComposeColor
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format("%d%%", value.coerceIn(0, 100)),
            modifier = Modifier.width(42.dp),
            color = tint.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall
        )
        LinearProgressIndicator(
            progress = (value.coerceIn(0, 100) / 100f),
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            trackColor = ComposeColor(0x222B3B4D),
            color = tint
        )
    }
}

@Composable
private fun ChecklistSection(
    items: List<ChecklistItemUi>,
    onToggle: (Int, Boolean) -> Unit
) {
    if (items.isEmpty()) return

    Text(
        text = stringResource(id = R.string.fortune_today_check),
        color = ComposeColor(0xFFB3FFFFFF),
        style = MaterialTheme.typography.labelMedium
    )
    Spacer(modifier = Modifier.height(4.dp))

    items.forEach { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable {
                    onToggle(item.id, !item.checked)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Checkbox(
                checked = item.checked,
                onCheckedChange = { checked -> onToggle(item.id, checked) }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "• ${item.text}",
                color = ComposeColor(0xFFF0F4F8),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun FortuneSectionCard(
    section: FortuneSectionUi,
    onClick: () -> Unit
) {
    val glassBg = painterResource(id = R.drawable.bg_glass_fortune_section)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = !section.isLotto, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = ComposeColor.Transparent
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    ComposeColor.Transparent
                )
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ComposeColor(0x33222B3D),
                                ComposeColor(0x55000421)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = section.title,
                            modifier = Modifier.weight(1f),
                            color = ComposeColor.White,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!section.isLotto && section.score != null) {
                            val c = ComposeColor(section.colorInt)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(c)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        id = R.string.score_points,
                                        section.score
                                    ),
                                    color = ComposeColor(0xFF0C1830),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    if (!section.isLotto && section.score != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            progress = section.score.coerceIn(0, 100) / 100f,
                            color = ComposeColor(section.colorInt),
                            trackColor = ComposeColor(0x1FFFFFFF)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = section.body,
                        color = ComposeColor(0xFFEAF2FA),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DeepButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            ComposeColor(0xFFFEDCA6),
            ComposeColor(0xFF8BAAFF)
        )
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        contentPadding = PaddingValues(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = ComposeColor.Black,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun LottieLoading() {
    AndroidView(
        factory = { ctx ->
            LottieAnimationView(ctx).apply {
                setAnimation(R.raw.generator)
                repeatCount = LottieAnimationView.INFINITE
                playAnimation()
            }
        },
        modifier = Modifier.size(120.dp),
        update = { view ->
            if (!view.isAnimating) view.playAnimation()
        }
    )
}

// ------------------------------------------------------
//  섹션 상세 다이얼로그 (Compose 버전)
// ------------------------------------------------------
@Composable
private fun SectionDetailDialog(
    data: SectionDialogUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.title,
                    modifier = Modifier.weight(1f),
                    color = ComposeColor(0xFFFABD3E),
                    style = MaterialTheme.typography.titleMedium
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(ComposeColor(data.colorInt))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.score_points,
                            data.score
                        ),
                        color = ComposeColor.Black,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        text = {
            Text(
                text = data.body,
                color = ComposeColor(0xFFEAF2FA),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    )
}
