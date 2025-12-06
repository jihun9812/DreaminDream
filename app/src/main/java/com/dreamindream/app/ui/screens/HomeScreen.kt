package com.dreamindream.app.ui.screens


import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dreamindream.app.*
import com.dreamindream.app.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.lang.Math
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private val FontJalnan = FontFamily(Font(R.font.jalnan))
private val FontPretendardBold = FontFamily(Font(R.font.pretendard_bold, FontWeight.Bold))
private val FontPretendardMed = FontFamily(Font(R.font.pretendard_medium, FontWeight.Medium))
private val ColorGold = Color(0xFFFFD54F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDream: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToFortune: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAIReport: (String) -> Unit,
    onNavigateToCommunity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    val uid = user?.uid

    val activity = context as? Activity
    var backPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime <= 2000) {
            // 2초 안에 다시 눌렀으면 앱 종료
            activity?.finish()
        } else {
            // 첫 번째 눌렀을 때
            backPressedTime = currentTime
            Toast.makeText(
                context,
                context.getString(R.string.back_press_exit),
                Toast.LENGTH_SHORT
            ).show()
        }
    }



    // UI States
    var dailyMessage by remember { mutableStateOf(context.getString(R.string.ai_msg_loading)) }
    var aiReportTitle by remember { mutableStateOf(context.getString(R.string.ai_report_summary)) }
    var aiReportSummary by remember { mutableStateOf(context.getString(R.string.preparing_analysis)) }
    var isReportEnabled by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }

    // 1. Load Daily Message (Locale aware)
    LaunchedEffect(Unit) {
        DailyMessageManager.getMessage(context) { msg ->
            val safe = msg.trim().takeIf { it.isNotEmpty() } ?: context.getString(R.string.daily_fallback_1)
            dailyMessage = safe
        }
        kotlinx.coroutines.delay(200)
        showContent = true
    }

    // 2. Sync AI Report Data
    LaunchedEffect(uid) {
        if (uid == null) {
            aiReportSummary = context.getString(R.string.ai_report_need_login)
            isReportEnabled = true
            return@LaunchedEffect
        }

        FirestoreManager.countDreamEntriesForWeek(uid, WeekUtils.weekKey()) { count ->
            if (count < 2) {
                aiReportSummary = context.getString(R.string.ai_report_guide)
                isReportEnabled = true
            } else {
                FirestoreManager.loadWeeklyReportFull(context, uid, WeekUtils.weekKey()) { data ->
                    val finalKeywords = if (data.tier == "pro" && data.analysisJson.isNotBlank()) {
                        val json = try { JSONObject(data.analysisJson) } catch (e: Exception) { null }
                        val arr = json?.optJSONArray("core_themes")
                        if (arr != null && arr.length() > 0) {
                            (0 until arr.length()).take(2).map { arr.getString(it).replace("#", "").trim() }
                        } else data.keywords.take(2)
                    } else {
                        data.keywords.take(2)
                    }

                    aiReportSummary = buildSummary(context, data.feeling, finalKeywords)
                    isReportEnabled = true
                }
            }
        }
    }

    // ✨ DreamScreen과 동일한 밤하늘 배경 적용
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090C14)) // DarkBg와 동일
    ) {
        // DreamScreen과 동일한 배경 이미지 + 알파값
        Image(
            painter = painterResource(R.drawable.main_ground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.4f), // DreamScreen과 동일
            contentScale = ContentScale.Crop
        )

        // 🌌 은하수 + 반짝이는 별 + 유성효과 (이제 구현체가 아래에 포함되어 작동합니다)
        NightSkyEffect()

        // Main Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            SettingsButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 20.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Title
                Text(
                    text = stringResource(R.string.home_title),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 36.sp,
                        fontFamily = FontJalnan,
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFFF9B84A),
                                Color(0xFF7B61FF)
                            )
                        ),
                        shadow = Shadow(
                            color = Color(0xFFFDCA60),
                            offset = Offset(0f, 4f),
                            blurRadius = 8f
                        ),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(bottom = 30.dp)
                )

                AnimatedVisibility(
                    visible = showContent,
                    enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        // AI Report Widget
                        HomeReportCard(
                            title = aiReportTitle,
                            summary = aiReportSummary,
                            isEnabled = isReportEnabled,
                            onClick = { onNavigateToAIReport(WeekUtils.weekKey()) }
                        )

                        // Daily Message Bubble
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF1E212B).copy(alpha = 0.8f))
                                .border(
                                    1.dp,
                                    Color.White.copy(0.1f),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Row {
                                Image(
                                    painter = painterResource(R.drawable.home_message),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = dailyMessage,
                                    color = Color(0xFFEEEEEE),
                                    fontSize = 13.sp,
                                    fontFamily = FontPretendardMed,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Navigation Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NavButton(
                                text = stringResource(R.string.btn_dream),
                                iconRes = R.drawable.ic_dream,
                                onClick = onNavigateToDream,
                                modifier = Modifier.weight(1f)
                            )
                            NavButton(
                                text = stringResource(R.string.btn_calendar),
                                iconRes = R.drawable.ic_calendar_moon,
                                onClick = onNavigateToCalendar,
                                modifier = Modifier.weight(1f)
                            )
                            NavButton(
                                text = stringResource(R.string.btn_fortune),
                                iconRes = R.drawable.ic_fortune,
                                onClick = onNavigateToFortune,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            CommunityButton(
                onClick = onNavigateToCommunity,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp)
            )
        }
    }
}

// --- Components ---

@Composable
fun HomeReportCard(title: String, summary: String, isEnabled: Boolean, onClick: () -> Unit) {
    // summary: "감정 • 키워드1, 키워드2" 형태
    val (emotionPart, keywordPart) = remember(summary) {
        val parts = summary.split("•")
        if (parts.size >= 2) {
            parts[0].trim() to parts[1].trim()
        } else {
            summary.trim() to ""
        }
    }

    val borderBrush = Brush.linearGradient(
        listOf(Color(0xFF7B61FF).copy(alpha = 0.4f), ColorGold.copy(alpha = 0.4f))
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp) // 🔼 살짝 더 키워서 2줄 구조 여유 확보
            .clickable(enabled = isEnabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E212B).copy(alpha = 0.95f)
        ),
        border = BorderStroke(
            1.dp,
            if (isEnabled) borderBrush else SolidColor(Color.White.copy(0.1f))
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 아이콘 박스
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2C3040), Color(0xFF3F455A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ai_report),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // 상단 작은 타이틀
                Text(
                    text = title,
                    color = Color(0xFF90949F),
                    fontSize = 10.sp,
                    fontFamily = FontPretendardMed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                // 메인 감정 한 줄
                Text(
                    text = emotionPart,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontPretendardBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 🔽 키워드는 그 아래 줄에 따로
                if (keywordPart.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = keywordPart, // "자아 발견, ..." 이런 형태
                        color = ColorGold,
                        fontSize = 13.sp,
                        fontFamily = FontPretendardMed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isEnabled) {
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = null,
                    tint = ColorGold.copy(alpha = 0.8f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun NavButton(text: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)

    val borderBrush = Brush.linearGradient(
        listOf(Color(0xFF7B61FF).copy(alpha = 0.4f), ColorGold.copy(alpha = 0.4f))
    )
    val bgBrush = Brush.verticalGradient(
        listOf(Color(0xFF2C3040).copy(0.7f), Color(0xFF1E212B).copy(0.9f))
    )

    Column(
        modifier = modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bgBrush)
                .border(1.dp, borderBrush, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color(0xFFE0E0E0),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            color = Color(0xFFB0B4C0),
            fontSize = 12.sp,
            fontFamily = FontPretendardMed
        )
    }
}

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_setting),
            contentDescription = null,
            tint = Color.White.copy(0.8f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun CommunityButton(onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1E212B),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.1f)),
        modifier = modifier.height(48.dp)
    ) {
        Text("Community", fontSize = 13.sp, fontFamily = FontPretendardMed)
    }
}

private fun buildSummary(context: Context, emotion: String?, keywords: List<String>): String {
    val emo = emotion?.takeIf { it.isNotBlank() } ?: "..."
    val kwStr = if (keywords.isNotEmpty()) keywords.joinToString(", ") else "..."
    return "$emo • $kwStr"
}


// ------------------------------------------------------------------------
// 🌌 Night Sky Effect Implementation
// (Moved from DreamScreen.kt to ensure availability in HomeScreen)
// ------------------------------------------------------------------------

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
                    color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
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
                    colors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = alpha)),
                    start = Offset(tailX, tailY),
                    end = Offset(headX, headY)
                ),
                start = Offset(tailX, tailY),
                end = Offset(headX, headY),
                strokeWidth = 2f * scale,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = 1.5f * scale,
                center = Offset(headX, headY)
            )
        }
    }
}

private data class StarData(
    val x: Float, val y: Float, val size: Float, val offset: Float, val speed: Float
)