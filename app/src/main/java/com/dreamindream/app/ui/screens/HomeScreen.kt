package com.dreamindream.app.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.dreamindream.app.R
import com.dreamindream.app.DailyMessageManager
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.WeekUtils
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview

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

    // uid & prefs (원본과 동일 키)
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val prefs = remember(uid) {
        context.getSharedPreferences("dream_history_${uid.orEmpty()}", Context.MODE_PRIVATE)
    }

    // ---- State (1:1)
    var dailyMessage by remember { mutableStateOf(context.getString(R.string.ai_msg_loading)) }
    var aiReportTitle by remember { mutableStateOf(context.getString(R.string.ai_report_summary)) }
    var aiReportSummary by remember { mutableStateOf(context.getString(R.string.preparing_analysis)) }
    var isReportEnabled by remember { mutableStateOf(false) }
    var cardVisible by remember { mutableStateOf(false) }

    // ---- Daily message
    LaunchedEffect(Unit) {
        DailyMessageManager.getMessage(context) { msg ->
            val safe = msg?.trim()?.takeIf { it.isNotEmpty() && !it.contains("불러올 수 없어요") }
                ?: getDailyFallbacks(context).random()
            dailyMessage = safe
        }
        kotlinx.coroutines.delay(100)
        cardVisible = true
    }

    // ---- Weekly
    LaunchedEffect(uid) {
        val cachedFeeling = prefs.getString("home_feeling", null)
        val cachedKeywords = prefs.getString("home_keywords", null)
            ?.split("|")?.filter { it.isNotBlank() }

        aiReportTitle = context.getString(R.string.ai_report_summary)
        aiReportSummary = if (!cachedFeeling.isNullOrBlank() || !cachedKeywords.isNullOrEmpty()) {
            buildWeeklySummaryLine(context, cachedFeeling, cachedKeywords)
        } else {
            context.getString(R.string.preparing_analysis)
        }

        uid?.let { currentUid ->
            val weekKey = WeekUtils.weekKey()
            FirestoreManager.countDreamEntriesForWeek(currentUid, weekKey) { count ->
                if (count < 2) {
                    aiReportTitle = context.getString(R.string.ai_report_summary)
                    aiReportSummary = context.getString(R.string.ai_report_guide)
                    isReportEnabled = true
                } else {
                    FirestoreManager.loadWeeklyReportFull(
                        context, currentUid, weekKey
                    ) { feeling, keywords, _, _, _, _, _, _, _, _, _, _ ->
                        aiReportTitle = context.getString(R.string.ai_report_summary)
                        aiReportSummary = buildWeeklySummaryLine(context, feeling, keywords.take(1))
                        isReportEnabled = true
                        // 캐시
                        prefs.edit().apply {
                            putString("home_feeling", feeling)
                            putString("home_keywords", keywords.take(1).joinToString("|"))
                            apply()
                        }
                    }
                }
            }
        } ?: run { isReportEnabled = true }
    }

    // ---- UI
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // 1) 전체 배경 이미지
        Image(
            painter = painterResource(R.drawable.main_ground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 실제 콘텐츠 레이어
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // 설정 아이콘(스케일 터치)
            SettingsButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 22.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 2) 타이틀 – 살짝 위로 올림
                GradientTitle(
                    text = stringResource(R.string.home_title),
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .offset(y = (-12).dp) // 위로 12dp
                )

                // AI Report 카드
                AnimatedVisibility(
                    visible = cardVisible,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(450)
                    ) + fadeIn(tween(450))
                ) {
                    AIReportCard(
                        title = aiReportTitle,
                        summary = aiReportSummary,
                        isEnabled = isReportEnabled,
                        onReportClick = {
                            uid?.let { onNavigateToAIReport(WeekUtils.weekKey()) }
                                ?: Unit
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                    )
                }

                // Daily message
                DailyMessageBubble(
                    message = dailyMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )

                // 하단 3버튼 (꿈/캘린더/운세) – 살짝 아래로
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .offset(y = 12.dp),      // 아래로 12dp
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeButton(
                        text = stringResource(R.string.btn_dream),
                        iconRes = R.drawable.ic_dream,
                        onClick = onNavigateToDream,
                        modifier = Modifier.weight(0.8f)
                    )
                    HomeButton(
                        text = stringResource(R.string.btn_calendar),
                        iconRes = R.drawable.ic_calendar_moon,
                        onClick = onNavigateToCalendar,
                        modifier = Modifier.weight(0.8f)
                    )
                    HomeButton(
                        text = stringResource(R.string.btn_fortune),
                        iconRes = R.drawable.ic_fortune,
                        onClick = onNavigateToFortune,
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }

            // 커뮤니티 버튼
            CommunityButton(
                onClick = onNavigateToCommunity,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

// ----- 아래 보조 컴포넌트들은 원본 스타일을 Compose로 1:1 반영 -----

@Composable
private fun GradientTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 34.sp,
            fontFamily = FontFamily(Font(R.font.jalnan)),
            brush = Brush.linearGradient(listOf(Color(0xFFF9B84A), Color(0xFF7B61FF))),
            shadow = Shadow(color = Color(0xFFFDCA60), offset = Offset(2f, 4f), blurRadius = 2f),
            textAlign = TextAlign.Center
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun AIReportCard(
    title: String,
    summary: String,
    isEnabled: Boolean,
    onReportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(0.96f) }
    var alpha by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(160)
        ) { value, _ ->
            scale = 0.96f + (0.04f * value)
            alpha = value
        }
    }

    Card(
        modifier = modifier
            .scale(scale)
            .graphicsLayer { this.alpha = alpha },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x14D7D7DB)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_ai_report),
                contentDescription = stringResource(R.string.ai_report),
                modifier = Modifier.size(18.dp).padding(end = 8.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily(Font(R.font.pretendard_medium)),
                        color = Color(0xFFE4E2E2)
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontFamily = FontFamily(Font(R.font.pretendard_medium)),
                        color = Color(0xFFC6D4DF)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            GradientButton(
                text = stringResource(R.string.ai_report_btn),
                onClick = onReportClick,
                enabled = isEnabled,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun DailyMessageBubble(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.home_message),
            contentDescription = stringResource(R.string.ai_avatar),
            modifier = Modifier.size(22.dp).padding(end = 8.dp)
        )
        Text(
            text = message,
            style = TextStyle(
                fontSize = 12.sp,
                fontFamily = FontFamily(Font(R.font.pretendard_medium)),
                color = Color.White,
                lineHeight = 15.sp
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeButton(text: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(40.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scale = 0.95f
                    tryAwaitRelease()
                    scale = 1f
                })
            },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x33FFFFFF),
            contentColor = Color(0xFFC6D4DF)
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color(0xFFC6D4DF)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily(Font(R.font.pretendard_medium)))
        )
    }
}

@Composable
private fun GradientButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brush = Brush.linearGradient(listOf(Color(0xFFFEDCA6), Color(0xFF8BAAFF))))
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                android.util.Log.e("🔴 HOME", "AI Report Button Clicked!")
                onClick()
            }
            .scale(scale)  // scale을 clickable 뒤로 이동!
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                fontFamily = FontFamily(Font(R.font.pretendard_medium)),
                color = Color(0xFF17212B)
            )
        )
    }
}
@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(33.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scale = 1.4f
                    tryAwaitRelease()
                    scale = 1f
                })
            }
    ) {
        Image(
            painter = painterResource(R.drawable.ic_setting),
            contentDescription = stringResource(R.string.settings),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun CommunityButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    Button(
        onClick = onClick,
        modifier = modifier
            .size(72.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scale = 0.95f
                    tryAwaitRelease()
                    scale = 1f
                })
            },
        shape = RoundedCornerShape(36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x33FFFFFF),
            contentColor = Color.White
        )
    ) {
        Text(
            text = "커뮤니티",
            style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily(Font(R.font.pretendard_medium)))
        )
    }
}

private fun buildWeeklySummaryLine(context: Context, topEmotion: String?, keywords: List<String>?): String {
    val emo = topEmotion?.trim().orEmpty()
    val kw = keywords?.asSequence()?.filter { it.isNotBlank() }?.map { it.trim() }?.distinct()?.take(1)?.joinToString(", ").orEmpty()
    val emoLabel = context.getString(R.string.label_emotion)
    val kwLabel  = context.getString(R.string.label_keywords)
    return when {
        emo.isNotEmpty() && kw.isNotEmpty() -> "$emoLabel: $emo • $kwLabel: $kw"
        emo.isNotEmpty() -> "$emoLabel: $emo"
        kw.isNotEmpty()  -> "$kwLabel: $kw"
        else -> context.getString(R.string.home_ai_sub)
    }
}

private fun getDailyFallbacks(context: Context): List<String> = listOf(
    context.getString(R.string.daily_fallback_1),
    context.getString(R.string.daily_fallback_2),
    context.getString(R.string.daily_fallback_3),
    context.getString(R.string.daily_fallback_4),
    context.getString(R.string.daily_fallback_5)
)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(
        onNavigateToDream = {},
        onNavigateToCalendar = {},
        onNavigateToFortune = {},
        onNavigateToSettings = {},
        onNavigateToAIReport = {},
        onNavigateToCommunity = {}
    )
}
