package com.dreamindream.app.ui.dream

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.dreamindream.app.AdManager
import com.dreamindream.app.R
import com.dreamindream.app.SubscriptionManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.play.core.review.ReviewManagerFactory // ✨ 리뷰 매니저 import
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Math
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// --- Theme Colors ---
private val DarkBg = Color(0xFF090C14)
private val CardBg = Color(0xFF161A25)
private val MetallicGold = Color(0xFFD4AF37)
private val GoldGradientStart = Color(0xFFD4AF37)
private val GoldGradientEnd = Color(0xFFF9A825)
private val TextMain = Color(0xFFECEFF1)
private val TextSub = Color(0xFF90A4AE)
private val GlassBg = Color(0x1AFFFFFF)
private val GlassBorder = Color(0x33FFFFFF)
private val Pretendard = FontFamily.Default

// --- ✨ Section Colors (Mystical Palette) ---
private val ColorMessage = Color(0xFF90CAF9) // ☁️
private val ColorSymbol = Color(0xFFF48FB1)  // 🧠
private val ColorPremonition = Color(0xFFFFCC80) // 📌
private val ColorTips = Color(0xFFA5D6A7)    // 💡
private val ColorAction = Color(0xFFCE93D8)  // ⚡

@Composable
fun DreamScreen(
    modifier: Modifier = Modifier,
    viewModel: DreamViewModel = viewModel(),
    onRequestSubscription: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val isSubscribed by SubscriptionManager.isSubscribed.collectAsState(initial = false)
    val keyboardController = LocalSoftwareKeyboardController.current

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startListening(speechRecognizer, context, viewModel)
        } else {
            Toast.makeText(context, context.getString(R.string.error_permissions), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onErrorConsumed()
        }
    }

    LaunchedEffect(uiState.showAdPrompt, isSubscribed) {
        if (uiState.showAdPrompt && isSubscribed) {
            viewModel.onRewardedAdEarned()
            viewModel.onAdPromptDismiss()
        }
    }

    // ✨ [인앱 리뷰 팝업 트리거 로직]
    LaunchedEffect(uiState.showReviewRequest) {
        if (uiState.showReviewRequest && activity != null) {
            try {
                val manager = ReviewManagerFactory.create(context)
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // 리뷰 정보를 성공적으로 가져오면 팝업 실행
                        val reviewInfo = task.result
                        val flow = manager.launchReviewFlow(activity, reviewInfo)
                        flow.addOnCompleteListener {
                            // 리뷰 작성 완료 혹은 취소 시 뷰모델에 알림 (상태 초기화)
                            viewModel.onReviewRequested()
                        }
                    } else {
                        // 실패해도 상태는 초기화해야 다음 로직이 안 꼬임
                        viewModel.onReviewRequested()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.onReviewRequested()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) { }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                keyboardController?.hide()
            }
    ) {
        Image(
            painter = painterResource(id = R.drawable.main_ground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.4f),
            contentScale = ContentScale.Crop
        )

        NightSkyEffect()

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (!isSubscribed) {
                    Box(Modifier.fillMaxWidth().background(DarkBg)) {
                        BannerAd(adUnitResId = R.string.ad_unit_dream_banner)
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = when {
                        uiState.isLoading -> DreamScreenState.Loading
                        uiState.resultText.isNotBlank() -> DreamScreenState.Result
                        else -> DreamScreenState.Input
                    },
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 0.95f))
                            .togetherWith(fadeOut(animationSpec = tween(400)))
                    },
                    label = "DreamTransition",
                    modifier = Modifier.fillMaxSize()
                ) { state ->
                    when (state) {
                        DreamScreenState.Input -> InputView(
                            uiState = uiState,
                            onValueChange = viewModel::onInputChanged,
                            onInterpret = viewModel::onInterpretClicked,
                            onRefresh = viewModel::onRefreshClicked,
                            onMicClick = {
                                if (speechRecognizer == null) {
                                    Toast.makeText(context, context.getString(R.string.error_speech_not_supported), Toast.LENGTH_LONG).show()
                                    return@InputView
                                }
                                if (uiState.isListening) {
                                    try { speechRecognizer.stopListening() } catch(e: Exception) {}
                                    viewModel.onListeningChanged(false)
                                } else {
                                    val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                        startListening(speechRecognizer, context, viewModel)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        )
                        DreamScreenState.Loading -> LoadingView()
                        DreamScreenState.Result -> ResultView(
                            resultText = uiState.resultText,
                            onReset = { viewModel.onResultClosed() },
                            onShare = viewModel::onShareClicked
                        )
                    }
                }
            }
        }

        if (uiState.showShareDialog) {
            ShareBottomSheet(
                dreamInput = uiState.inputText,
                resultText = uiState.resultText,
                onDismiss = viewModel::onShareDismiss
            )
        }

        if (uiState.showAdPrompt && !isSubscribed) {
            AdPromptBottomSheet(
                onWatchAd = {
                    if (activity != null) {
                        AdManager.showRewarded(activity, { viewModel.onRewardedAdEarned() }, {}, {})
                    }
                },
                onRequestSubscription = onRequestSubscription,
                onDismiss = viewModel::onAdPromptDismiss
            )
        }

        if (uiState.showLimitDialog) {
            LimitAlertDialog(onDismiss = viewModel::onLimitDialogDismiss)
        }
    }
}

private fun startListening(speechRecognizer: SpeechRecognizer?, context: Context, viewModel: DreamViewModel) {
    if (speechRecognizer == null) return
    val currentLocale = Locale.getDefault()
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale.toString())
        putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.dream_input_empty))
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            viewModel.onListeningChanged(true)
            Toast.makeText(context, context.getString(R.string.toast_speak_language, currentLocale.displayLanguage), Toast.LENGTH_LONG).show()
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { viewModel.onListeningChanged(false) }
        override fun onError(error: Int) {
            viewModel.onListeningChanged(false)
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.error_no_match)
                SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.error_network)
                else -> context.getString(R.string.error_generic, error)
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                val currentText = viewModel.uiState.value.inputText
                val newText = if (currentText.isEmpty()) spokenText else "$currentText $spokenText"
                viewModel.onInputChanged(newText)
            }
            viewModel.onListeningChanged(false)
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    try { speechRecognizer.startListening(intent) } catch (e: Exception) {
        viewModel.onListeningChanged(false)
    }
}

private enum class DreamScreenState { Input, Loading, Result }

@Composable
private fun InputView(
    uiState: DreamUiState,
    onValueChange: (String) -> Unit,
    onInterpret: () -> Unit,
    onRefresh: () -> Unit,
    onMicClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text(text = stringResource(R.string.dream_interpreter), color = MetallicGold, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.dream_input_label), color = TextSub, fontSize = 14.sp, fontFamily = Pretendard)
        Spacer(Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(GlassBg)
                .border(1.dp, Brush.linearGradient(listOf(MetallicGold.copy(0.3f), Color.Transparent)), RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨ ", fontSize = 12.sp)
                Text(text = stringResource(R.string.dream_today_left, uiState.remainingCount), color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(GlassBg)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            BasicTextField(
                value = uiState.inputText,
                onValueChange = { if (it.length <= 500) onValueChange(it) },
                textStyle = TextStyle(color = TextMain, fontSize = 16.sp, lineHeight = 26.sp, fontFamily = Pretendard),
                cursorBrush = SolidColor(MetallicGold),
                modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
                decorationBox = { innerTextField ->
                    if (uiState.inputText.isEmpty()) {
                        Text(text = stringResource(R.string.hint_input), color = TextSub.copy(alpha = 0.5f), fontSize = 13.sp, lineHeight = 26.sp)
                    }
                    innerTextField()
                }
            )
            Text(text = "${uiState.inputText.length} / 500", color = TextSub.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(GoldGradientStart, GoldGradientEnd)))
                    .clickable(onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = DarkBg, modifier = Modifier.size(24.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(GoldGradientStart, GoldGradientEnd)))
                    .clickable(enabled = !uiState.isLoading && uiState.remainingCount > 0, onClick = onInterpret),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = DarkBg)
                    Spacer(Modifier.width(10.dp))
                    Text(text = stringResource(R.string.button_interpret), color = DarkBg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (uiState.isListening) Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFFF1744))) else Brush.horizontalGradient(listOf(GoldGradientStart, GoldGradientEnd)))
                    .clickable(onClick = onMicClick),
                contentAlignment = Alignment.Center
            ) {
                val icon = if (uiState.isListening) Icons.Default.Mic else Icons.Default.MicNone
                Icon(imageVector = icon, contentDescription = "Mic", tint = if (uiState.isListening) Color.White else DarkBg, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 30.dp).alpha(0.5f)) {
            Spacer(Modifier.height(4.dp))
            Text(text = stringResource(R.string.dream_mirror), color = TextSub, fontSize = 10.sp, fontFamily = Pretendard)
        }
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AndroidView(
                modifier = Modifier.size(200.dp),
                factory = { ctx ->
                    LottieAnimationView(ctx).apply {
                        setAnimation(R.raw.just_flow_teal)
                        repeatCount = LottieDrawable.INFINITE
                        playAnimation()
                    }
                }
            )
            Spacer(Modifier.height(20.dp))
            Text(text = stringResource(R.string.dream_interpreting), color = MetallicGold, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alpha(0.9f))
        }
    }
}

@Composable
private fun ResultView(resultText: String, onReset: () -> Unit, onShare: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // ✨ 이모지 기반 파싱 로직 사용
    val sections = remember(resultText) { parseToDreamSections(resultText) }

    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = screenHeight * 0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(CardBg)
                .border(width = 1.5.dp, brush = Brush.verticalGradient(colors = listOf(MetallicGold.copy(alpha = 0.6f), Color.Transparent)), shape = RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.explain_dream), color = MetallicGold, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSub) }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onReset)
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMain, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorder)

                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                        sections.forEachIndexed { index, section ->
                            AnimatedDreamSection(section = section, delayMillis = index * 400)
                            Spacer(Modifier.height(24.dp))
                        }
                        // Fallback: 파싱 결과가 없으면 원본 표시
                        if (sections.isEmpty() && resultText.isNotBlank()) {
                            Text(text = resultText, color = TextMain, fontSize = 15.sp, lineHeight = 24.sp)
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(20.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(colors = listOf(CardBg, Color.Transparent))))
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(colors = listOf(Color.Transparent, CardBg))))
                }
            }
        }
    }
}

// 📌 데이터 구조
data class DreamSection(val title: String, val content: String, val headerColor: Color)

// 📌 핵심: 이모지 기반 파싱 로직 (다국어 지원)
fun parseToDreamSections(raw: String): List<DreamSection> {
    val sections = mutableListOf<DreamSection>()
    val lines = raw.lines()

    var currentTitle = ""
    var currentContent = StringBuilder()
    var currentColor = MetallicGold

    // 이모지 정의 (strings.xml에 정의된 아이콘들)
    val iconMessage = "☁️"
    val iconSymbol = "🧠"
    val iconPremonition = "📌"
    val iconTip = "💡"
    val iconAction = "⚡"

    fun saveSection() {
        if (currentTitle.isNotBlank()) {
            sections.add(DreamSection(currentTitle, currentContent.toString().trim(), currentColor))
        }
        currentContent.clear()
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        // ❌ 구분선 무시
        if (trimmed.startsWith("---") || trimmed.contains("---")) continue

        // 헤더 감지 (이모지로 시작하는지 확인)
        val isHeader = trimmed.startsWith(iconMessage) ||
                trimmed.startsWith(iconSymbol) ||
                trimmed.startsWith(iconPremonition) ||
                trimmed.startsWith(iconTip) ||
                trimmed.startsWith(iconAction)

        if (isHeader) {
            saveSection() // 이전 섹션 저장
            currentTitle = trimmed
            // 이모지에 따른 색상 매핑
            currentColor = when {
                trimmed.startsWith(iconMessage) -> ColorMessage
                trimmed.startsWith(iconSymbol) -> ColorSymbol
                trimmed.startsWith(iconPremonition) -> ColorPremonition
                trimmed.startsWith(iconTip) -> ColorTips
                trimmed.startsWith(iconAction) -> ColorAction
                else -> MetallicGold
            }
        } else {
            // 내용 추가
            currentContent.append(trimmed).append("\n")
        }
    }
    saveSection() // 마지막 섹션 저장

    return sections
}

fun cleanTextContent(text: String): String {
    return text.lines().joinToString("\n") { line ->
        line.replace(Regex("^(\\s*[-•\\d.]+)\\s*[:]\\s*"), "$1 ")
    }
}

@Composable
fun AnimatedDreamSection(section: DreamSection, delayMillis: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(800, easing = LinearOutSlowInEasing), label = "alpha")
    val offsetY by animateFloatAsState(targetValue = if (visible) 0f else 40f, animationSpec = tween(800, easing = LinearOutSlowInEasing), label = "offset")

    Column(modifier = Modifier.fillMaxWidth().alpha(alpha).graphicsLayer { translationY = offsetY }) {
        Text(text = section.title, style = TextStyle(color = section.headerColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard))
        Spacer(Modifier.height(8.dp))
        Text(text = cleanTextContent(section.content), style = TextStyle(color = TextMain, fontSize = 15.sp, lineHeight = 24.sp, fontFamily = Pretendard))
    }
}

// --- ETC Components (Ads, Dialogs, Shares) ---

@Composable
private fun BannerAd(adUnitResId: Int) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = context.getString(adUnitResId)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdPromptBottomSheet(onWatchAd: () -> Unit, onRequestSubscription: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1E212B), windowInsets = WindowInsets.navigationBars) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(text = stringResource(R.string.ad_prompt_title), color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onWatchAd, colors = ButtonDefaults.buttonColors(containerColor = MetallicGold), modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.ad_prompt_watch_ad), color = DarkBg, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRequestSubscription, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, TextSub)) {
                Text(text = stringResource(R.string.sub_buy_cta), color = TextMain)
            }
        }
    }
}

@Composable
private fun LimitAlertDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Limit Reached", color = TextMain) },
        text = { Text("You have reached the daily limit for dream interpretations.", color = TextSub) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = MetallicGold) } },
        containerColor = Color(0xFF1E212B)
    )
}

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
        windowInsets = WindowInsets.navigationBars,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSub.copy(alpha = 0.4f)) }
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 50.dp)) {
            Text("Share Your Dream", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(30.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ShareTarget.values().forEach { target ->
                    ShareIconItem(target = target, enabled = !isGenerating, onClick = {
                        isGenerating = true
                        coroutineScope.launch {
                            shareDreamSpecific(context, target, dreamInput, resultText)
                            isGenerating = false
                            onDismiss()
                        }
                    })
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
        val width = 1080; val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            shader = android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), intArrayOf(0xFF050505.toInt(), 0xFF151A25.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val starPaint = Paint().apply { color = 0xFFFFF176.toInt(); alpha = 180 }
        for (i in 0..50) {
            val x = (Math.random() * width).toFloat(); val y = (Math.random() * height).toFloat(); val r = (Math.random() * 3 + 1).toFloat()
            starPaint.alpha = (Math.random() * 150 + 50).toInt()
            canvas.drawCircle(x, y, r, starPaint)
        }

        val headerPaint = TextPaint().apply { color = 0xFFB0BEC5.toInt(); textSize = 34f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        canvas.drawText("Dream In Dream · $dateStr", width / 2f, 130f, headerPaint)

        val quotePaint = TextPaint().apply { color = 0xFFD4AF37.toInt(); textSize = 100f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); isAntiAlias = true }
        val dreamBodyPaint = TextPaint().apply { color = 0xFFECEFF1.toInt(); textSize = 46f; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL); isAntiAlias = true }

        val padding = 100f
        val textWidth = width - (padding * 2)
        val dreamSnippet = if (dream.length > 80) dream.take(80) + "..." else dream
        val dreamLayout = StaticLayout.Builder.obtain(dreamSnippet, 0, dreamSnippet.length, dreamBodyPaint, textWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(20f, 1.2f).setMaxLines(3).build()

        var currentY = 250f
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

        val resultBodyPaint = TextPaint().apply { color = 0xFFCFD8DC.toInt(); textSize = 38f; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL); isAntiAlias = true }
        val cleanResult = cleanTextContent(result).replace(Regex("[#*]"), "").trim()
        val availableHeight = height - currentY - 150f
        val resultLayout = StaticLayout.Builder.obtain(cleanResult, 0, cleanResult.length, resultBodyPaint, textWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(16f, 1.1f).setEllipsize(android.text.TextUtils.TruncateAt.END).setMaxLines((availableHeight / 45f).toInt()).build()

        canvas.save(); canvas.translate(padding, currentY); resultLayout.draw(canvas); canvas.restore()

        val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "DreamInDream_${System.currentTimeMillis()}", "Dream Interpretation Result")
        val uri = Uri.parse(path ?: return)

        if (target == ShareTarget.Instagram) {
            try {
                val storiesIntent = Intent("com.instagram.share.ADD_TO_STORY").apply { setDataAndType(uri, "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setPackage("com.instagram.android") }
                context.startActivity(storiesIntent)
            } catch (e: Exception) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "image/*"; putExtra(Intent.EXTRA_STREAM, uri) }
                context.startActivity(Intent.createChooser(shareIntent, "Share Dream"))
            }
        } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "image/*"; putExtra(Intent.EXTRA_STREAM, uri); target.packageName?.let { setPackage(it) } }
            context.startActivity(Intent.createChooser(shareIntent, "Share Dream"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Effect Components
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
    val stars = remember { List(20) { StarData(Math.random().toFloat(), Math.random().toFloat(), (Math.random() * 0.4 + 0.1).toFloat(), Math.random().toFloat() * 2000f, (Math.random() * 0.06 + 0.02).toFloat()) } }
    val infiniteTransition = rememberInfiniteTransition(label = "stars_time")
    val time by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1000f, animationSpec = infiniteRepeatable(tween(120000, easing = LinearEasing), RepeatMode.Restart), label = "time")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width; val height = size.height
        stars.forEach { star ->
            val rawSin = sin((time * star.speed + star.offset).toDouble()).toFloat()
            val alpha = (((rawSin + 1) / 2).pow(30) * 0.7f).coerceIn(0f, 1f)
            if (alpha > 0.01f) drawCircle(color = Color.White.copy(alpha = alpha), radius = star.size * density.density, center = Offset(star.x * width, star.y * height))
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
            delay(Random.nextLong(3000, 5000))
            startX = Random.nextFloat() * 0.5f + 0.4f; startY = Random.nextFloat() * 0.3f; scale = Random.nextFloat() * 0.5f + 0.5f
            progress.snapTo(0f); progress.animateTo(1f, tween(800, easing = LinearEasing))
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (progress.value > 0f && progress.value < 1f) {
            val width = size.width; val height = size.height
            val moveDistance = width * 0.4f
            val currentX = (startX * width) - (moveDistance * progress.value); val currentY = (startY * height) + (moveDistance * progress.value)
            val tailLength = 100f * scale
            val alpha = if (progress.value < 0.1f) progress.value * 10f else if (progress.value > 0.8f) (1f - progress.value) * 5f else 1f
            drawLine(brush = Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = alpha)), start = Offset(currentX + tailLength * 0.7f, currentY - tailLength * 0.7f), end = Offset(currentX, currentY)), start = Offset(currentX + tailLength * 0.7f, currentY - tailLength * 0.7f), end = Offset(currentX, currentY), strokeWidth = 2f * scale, cap = StrokeCap.Round)
            drawCircle(color = Color.White.copy(alpha = alpha), radius = 1.5f * scale, center = Offset(currentX, currentY))
        }
    }
}
private data class StarData(val x: Float, val y: Float, val size: Float, val offset: Float, val speed: Float)