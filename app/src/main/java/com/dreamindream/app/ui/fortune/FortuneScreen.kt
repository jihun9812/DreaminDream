package com.dreamindream.app.ui.fortune

import android.app.Activity
import android.graphics.Paint
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.*
import com.dreamindream.app.R
import com.dreamindream.app.SubscriptionManager
import kotlin.math.cos
import kotlin.math.sin

private val MediumDark = Color(0xFF1A1A1A)
private val PremiumGold = Color(0xFFD4AF37)
private val GlassSurface = Color(0xFF2D2D2D)
private val TextPrimary = Color(0xFFEEEEEE)
private val TextSecondary = Color(0xFFAAAAAA)
private val PositiveGreen = Color(0xFF4CAF50)
private val NegativeRed = Color(0xFFE57373)

@Composable
fun FortuneScreen(
    viewModel: FortuneViewModel = viewModel(),
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSubscribed by SubscriptionManager.isSubscribed.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // [New] 화면 꺼짐 방지 로직 (로딩 중일 때만 활성화)
    val activity = context as? Activity
    DisposableEffect(uiState.isLoading || uiState.isDeepLoading) {
        if (uiState.isLoading || uiState.isDeepLoading) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshConfig()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.navigateToSubscription) {
        if (uiState.navigateToSubscription) {
            viewModel.onSubscriptionNavHandled()
            onNavigateToSubscription()
        }
    }

    // 에러 메시지(스낵바) 표시
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            // Toast 대신 스낵바로 변경하거나 간단히 Toast로 띄움
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (uiState.isInitializing) {
        Box(Modifier.fillMaxSize().background(MediumDark))
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(MediumDark)) {
        Image(
            painter = painterResource(R.drawable.main_ground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.2f),
            contentScale = ContentScale.Crop
        )

        AnimatedContent(
            targetState = uiState.screenStep,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
            label = "ScreenTransition"
        ) { step ->
            when (step) {
                0 -> PremiumLoadingScreen()
                1 -> PremiumStartScreen(
                    userName = uiState.userName,
                    userFlag = uiState.userFlag,
                    onStart = { viewModel.onStartClick(context) }
                )
                2 -> PremiumDashboard(
                    uiState = uiState,
                    isSubscribed = isSubscribed,
                    viewModel = viewModel,
                    context = context
                )
            }
        }

        // [Deep Analysis 결과 다이얼로그]
        if (uiState.showDeepDialog && uiState.deepResult != null) {
            DeepAnalysisDialog(
                result = uiState.deepResult!!,
                onDismiss = viewModel::closeDeepDialog
            )
        }
        // [로딩 다이얼로그] - 버튼 클릭 후 대기 중일 때만 표시
        else if (uiState.isDeepLoading) {
            Dialog(onDismissRequest = {}) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF222222)).padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PremiumGold)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.loading_text), color = PremiumGold)
                        Spacer(Modifier.height(8.dp))
                        // [New] 로딩이 길어질 때 사용자 안심 멘트
                        Text("I know it is boring...", color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }

        if (uiState.showProfileDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onProfileDialogDismiss() },
                containerColor = Color(0xFF222222),
                title = { Text(stringResource(R.string.profile_req_title), color = PremiumGold) },
                text = { Text(stringResource(R.string.profile_req_msg), color = TextPrimary) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.onProfileDialogDismiss(); onNavigateToSettings() },
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGold)
                    ) { Text(stringResource(R.string.btn_go_settings), color = Color.Black) }
                }
            )
        }
    }
}

// ... (PremiumStartScreen, PremiumLoadingScreen 등 나머지 컴포넌트는 기존 코드와 동일) ...
@Composable
fun PremiumStartScreen(userName: String, userFlag: String, onStart: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (userFlag.isNotBlank()) {
                    Text(text = userFlag, fontSize = 80.sp)
                } else {
                    Text(text = "🌐", fontSize = 80.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                if (userName.isBlank()) stringResource(R.string.destiny_awaits) else stringResource(R.string.hello_user, userName),
                color = PremiumGold, fontSize = 24.sp, fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(60.dp))

            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFFD4AF37), Color(0xFFF9E076), Color(0xFFD4AF37))
                        )
                    )
                    .clickable(onClick = onStart),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.btn_open_fortune),
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun PremiumLoadingScreen() {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.generator))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Box(Modifier.fillMaxSize().background(MediumDark), contentAlignment = Alignment.Center) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(200.dp)
        )
    }
}

@Composable
fun PremiumDashboard(
    uiState: FortuneUiState,
    isSubscribed: Boolean,
    viewModel: FortuneViewModel,
    context: android.content.Context
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        Text(stringResource(R.string.daily_oracle_title), color = PremiumGold.copy(alpha = 0.7f), fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        TicketView(uiState.userFlag, uiState.lottoName, uiState.lottoNumbers, uiState.luckyNumber)
        Spacer(Modifier.height(30.dp))

        Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            CosmicRadarChart(uiState.radarChartData)
        }
        Spacer(Modifier.height(30.dp))

        Column(Modifier.padding(horizontal = 20.dp)) {
            BasicInfoCard(stringResource(R.string.card_overview), uiState.basicFortune.overall, Icons.Rounded.AutoAwesome)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { BasicInfoCard(stringResource(R.string.card_wealth), uiState.basicFortune.moneyText, Icons.Rounded.AttachMoney, compact = true) }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) { BasicInfoCard(stringResource(R.string.card_love), uiState.basicFortune.loveText, Icons.Rounded.Favorite, compact = true) }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { BasicInfoCard(stringResource(R.string.card_health), uiState.basicFortune.healthText, Icons.Rounded.Spa, compact = true) }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) { BasicInfoCard(stringResource(R.string.card_action), uiState.basicFortune.actionTip, Icons.Rounded.DirectionsRun, compact = true) }
            }
        }

        Spacer(Modifier.height(30.dp))

        PremiumGlassBox(stringResource(R.string.missions_title)) {
            uiState.checklist.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onChecklistToggle(item.id, !item.checked) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (item.checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        null,
                        tint = if (item.checked) PremiumGold else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.text,
                        color = if (item.checked) TextSecondary else TextPrimary,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                if (idx < uiState.checklist.size - 1) Divider(color = Color.White.copy(alpha = 0.1f))
            }
        }

        Spacer(Modifier.height(40.dp))

        DeepAnalysisEntry(isSubscribed) { viewModel.onDeepAnalysisClick(context) }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun TicketView(flag: String, lottoName: String?, lottoNumbers: String?, luckyNum: Int?) {
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp).fillMaxWidth().height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF252525), Color(0xFF303030))))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxHeight().weight(0.3f).background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if(flag.isNotBlank()) flag else "🌐", fontSize = 32.sp)
                }
            }
            Canvas(Modifier.fillMaxHeight().width(1.dp)) {
                drawLine(Color.Gray.copy(alpha=0.3f), Offset(0f,0f), Offset(0f, size.height), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f,10f)))
            }
            Column(
                Modifier.fillMaxHeight().weight(0.7f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(lottoName?.uppercase() ?: stringResource(R.string.lotto_default_title), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (lottoNumbers != null) {
                    Text(lottoNumbers, color = PremiumGold, fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                } else {
                    Text("${luckyNum ?: 7}", color = PremiumGold, fontSize = 40.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun BasicInfoCard(title: String, content: String, icon: ImageVector, compact: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassSurface)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = PremiumGold, modifier = Modifier.size(if(compact) 18.dp else 24.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = PremiumGold, fontSize = if(compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(content, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

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
            .padding(horizontal = 24.dp)
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

@Composable
fun DeepAnalysisDialog(result: DeepFortuneResult, onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_verdict) to Icons.Rounded.Star,
        stringResource(R.string.tab_trend) to Icons.Rounded.ShowChart,
        stringResource(R.string.tab_money) to Icons.Rounded.AttachMoney,
        stringResource(R.string.tab_love) to Icons.Rounded.Favorite,
        stringResource(R.string.tab_health) to Icons.Rounded.Spa,
        stringResource(R.string.tab_career) to Icons.Rounded.Work,
        stringResource(R.string.tab_mind) to Icons.Rounded.Psychology,
        stringResource(R.string.tab_risk) to Icons.Rounded.Warning,
        stringResource(R.string.tab_action) to Icons.Rounded.DirectionsRun
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF181818))
                .border(1.dp, PremiumGold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.deep_dialog_title), color = PremiumGold, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = TextSecondary) }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = PremiumGold,
                    edgePadding = 0.dp,
                    indicator = { positions ->
                        if (selectedTab < positions.size) {
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(positions[selectedTab]),
                                color = PremiumGold
                            )
                        }
                    }
                ) {
                    tabs.forEachIndexed { index, pair ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(pair.first, fontSize = 11.sp, maxLines = 1) },
                            icon = { Icon(pair.second, null, modifier = Modifier.size(18.dp)) },
                            selectedContentColor = PremiumGold,
                            unselectedContentColor = TextSecondary
                        )
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.1f))

                Box(Modifier.weight(1f).padding(20.dp)) {
                    AnimatedContent(targetState = selectedTab, label = "TabContent") { tabIndex ->
                        val scrollState = rememberScrollState()
                        Column(Modifier.verticalScroll(scrollState)) {
                            when (tabIndex) {
                                0 -> VerdictTab(result)
                                1 -> TrendTab(result)
                                2 -> TextTab(stringResource(R.string.title_financial), result.moneyAnalysis)
                                3 -> TextTab(stringResource(R.string.title_relationship), result.loveAnalysis)
                                4 -> TextTab(stringResource(R.string.title_vitality), result.healthAnalysis)
                                5 -> TextTab(stringResource(R.string.title_career), result.careerAnalysis)
                                6 -> TextTab(stringResource(R.string.title_emotional), result.emotionalAnalysis)
                                7 -> TextTab(stringResource(R.string.title_risk), result.riskWarning, isWarning = true)
                                8 -> TextTab(stringResource(R.string.title_action), result.actionGuide)
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VerdictTab(result: DeepFortuneResult) {
    Column {
        Text(stringResource(R.string.keywords_title), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            result.todayKeyword.ifBlank { stringResource(R.string.keywords_placeholder) },
            color = PremiumGold, fontSize = 20.sp, fontWeight = FontWeight.Light
        )

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth()) {
            LuckyItemBox(stringResource(R.string.lucky_color), result.luckySummary.color, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            LuckyItemBox(stringResource(R.string.lucky_item), result.luckySummary.item, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            LuckyItemBox(stringResource(R.string.lucky_time), result.luckySummary.time, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            LuckyItemBox(stringResource(R.string.lucky_dir), result.luckySummary.direction, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ThumbUp, null, tint = PositiveGreen, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pros_opportunity), color = PositiveGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(result.prosCons.positive, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(start = 24.dp, top = 4.dp))

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ThumbDown, null, tint = NegativeRed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cons_caution), color = NegativeRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(result.prosCons.negative, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(start = 24.dp, top = 4.dp))

        Spacer(Modifier.height(24.dp))

        Text(stringResource(R.string.masters_verdict), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(result.overallVerdict, color = TextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
fun LuckyItemBox(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(GlassSurface, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(label, color = TextSecondary, fontSize = 9.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
fun TrendTab(result: DeepFortuneResult) {
    Column {
        Text(stringResource(R.string.trend_flow_title), color = TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(15.dp))
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            DestinyCurveGraph(result.flowCurve, result.timeLabels)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.trend_summary), color = PremiumGold, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(result.overallVerdict, color = TextPrimary, lineHeight = 22.sp, fontSize = 14.sp)
    }
}

@Composable
fun TextTab(title: String, content: String, isWarning: Boolean = false) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isWarning) Icon(Icons.Rounded.Warning, null, tint = NegativeRed, modifier = Modifier.size(20.dp))
            Text(
                title,
                color = if(isWarning) NegativeRed else PremiumGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if(isWarning) 8.dp else 0.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        val paragraphs = content.split("\n\n")
        paragraphs.forEach { para ->
            if (para.isNotBlank()) {
                Text(
                    para.trim(),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun DestinyCurveGraph(points: List<Int>, labels: List<String>) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height - 40f
        val step = w / (points.size - 1)

        drawLine(Color.Gray.copy(alpha=0.3f), Offset(0f, h/2), Offset(w, h/2))

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * step
            val y = h - (p / 100f * h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(PremiumGold.copy(alpha=0.3f), Color.Transparent), endY = h))
        drawPath(path, PremiumGold, style = Stroke(width = 3.dp.toPx()))

        drawIntoCanvas {
            val paint = Paint().apply { color = android.graphics.Color.GRAY; textSize = 30f; textAlign = Paint.Align.CENTER }
            labels.forEachIndexed { i, label ->
                it.nativeCanvas.drawText(label, i * step, size.height, paint)
            }
        }
    }
}

@Composable
fun CosmicRadarChart(data: Map<String, Int>) {
    val labels = data.keys.toList()
    val values = data.values.toList()
    Canvas(Modifier.fillMaxSize()) {
        val center = center
        val radius = size.minDimension / 2 * 0.7f
        val angleStep = (2 * Math.PI / labels.size).toFloat()

        for (i in 1..4) drawCircle(GlassSurface, radius = radius * i / 4, style = Stroke(1.dp.toPx()))

        val path = Path()
        values.forEachIndexed { i, v ->
            val angle = i * angleStep - Math.PI.toFloat()/2
            val r = radius * (v / 100f)
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, PremiumGold.copy(alpha = 0.4f))
        drawPath(path, PremiumGold, style = Stroke(2.dp.toPx()))

        drawIntoCanvas {
            val labelPaint = Paint().apply { color = android.graphics.Color.LTGRAY; textSize = 32f; textAlign = Paint.Align.CENTER }
            val scorePaint = Paint().apply { color = android.graphics.Color.parseColor("#D4AF37"); textSize = 30f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

            labels.forEachIndexed { i, label ->
                val angle = i * angleStep - Math.PI.toFloat()/2
                val dist = radius + 50
                val x = center.x + dist * cos(angle)
                val y = center.y + dist * sin(angle)

                it.nativeCanvas.drawText(label, x, y, labelPaint)
                it.nativeCanvas.drawText("${values[i]}", x, y + 40, scorePaint)
            }
        }
    }
}

@Composable
fun PremiumGlassBox(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .padding(16.dp)
        ) {
            Column { content() }
        }
    }
}