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
import com.dreamindream.app.WeeklyReportData
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
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    val uid = user?.uid
    val isAnonymous = user?.isAnonymous == true

    // 키워드 저장을 위한 SharedPreferences (키 이름 동일 유지)
    val prefs = remember(uid) {
        context.getSharedPreferences("dream_history_${uid.orEmpty()}", Context.MODE_PRIVATE)
    }

    var dailyMessage by remember { mutableStateOf(context.getString(R.string.ai_msg_loading)) }
    var aiReportTitle by remember { mutableStateOf(context.getString(R.string.ai_report_summary)) }
    var aiReportSummary by remember { mutableStateOf(context.getString(R.string.preparing_analysis)) }
    var isReportEnabled by remember { mutableStateOf(false) }
    var cardVisible by remember { mutableStateOf(false) }

    // 1. 데일리 메시지 로드
    LaunchedEffect(Unit) {
        // DailyMessageManager가 이제 언어별로 분리된 키를 사용하므로,
        // 여기서는 기존과 동일하게 호출해도 내부적으로 언어에 맞는 메시지를 가져옵니다.
        DailyMessageManager.getMessage(context) { msg ->
            val safe = msg.trim().takeIf { it.isNotEmpty() && !it.contains("불러올 수 없어요") }
                ?: getDailyFallbacks(context).random()
            dailyMessage = safe
        }
        kotlinx.coroutines.delay(100)
        cardVisible = true
    }

    // 2. AI 리포트 요약 정보 로드
    LaunchedEffect(uid, isAnonymous) {
        if (uid == null || isAnonymous) {
            aiReportTitle = context.getString(R.string.ai_report_summary)
            aiReportSummary = context.getString(R.string.ai_report_need_login)
            isReportEnabled = true
            return@LaunchedEffect
        }

        // 캐시된 데이터 먼저 확인
        val cachedFeeling = prefs.getString("home_feeling", null)
        val cachedKeywordsStr = prefs.getString("home_keywords", null)
        val cachedKeywords = cachedKeywordsStr?.split("|")?.filter { it.isNotBlank() }

        aiReportTitle = context.getString(R.string.ai_report_summary)

        // 캐시 데이터가 있으면 먼저 보여주기
        if (!cachedFeeling.isNullOrBlank() || !cachedKeywords.isNullOrEmpty()) {
            aiReportSummary = buildWeeklySummaryLine(context, cachedFeeling, cachedKeywords)
        } else {
            aiReportSummary = context.getString(R.string.preparing_analysis)
        }

        // Firestore에서 최신 데이터 확인
        FirestoreManager.countDreamEntriesForWeek(uid, WeekUtils.weekKey()) { count ->
            if (count < 2) {
                // 꿈 데이터가 부족할 때
                aiReportTitle = context.getString(R.string.ai_report_summary)
                aiReportSummary = context.getString(R.string.ai_report_guide)
                isReportEnabled = true
            } else {
                // 꿈 데이터 충분 -> 리포트 로드
                FirestoreManager.loadWeeklyReportFull(
                    context, uid, WeekUtils.weekKey()
                ) { data: WeeklyReportData ->

                    aiReportTitle = context.getString(R.string.ai_report_summary)
                    // [핵심] 키워드 리스트 전체를 넘기면 buildWeeklySummaryLine 내부에서 첫 번째만 사용함
                    aiReportSummary = buildWeeklySummaryLine(context, data.feeling, data.keywords)
                    isReportEnabled = true

                    // 로컬에 최신 상태 저장
                    prefs.edit().apply {
                        putString("home_feeling", data.feeling)
                        // 리스트를 파이프로 연결해 저장
                        putString("home_keywords", data.keywords.joinToString("|"))
                        apply()
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(R.drawable.main_ground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            // 설정 버튼
            SettingsButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 22.dp)
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GradientTitle(
                    text = stringResource(R.string.home_title),
                    modifier = Modifier.padding(bottom = 24.dp).offset(y = (-12).dp)
                )

                // AI 리포트 카드 (애니메이션 적용)
                AnimatedVisibility(
                    visible = cardVisible,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(450)) + fadeIn(tween(450))
                ) {
                    AIReportCard(
                        title = aiReportTitle,
                        summary = aiReportSummary,
                        isEnabled = !isAnonymous && isReportEnabled,
                        onReportClick = {
                            if (user == null || user.isAnonymous) onNavigateToSettings()
                            else onNavigateToAIReport(WeekUtils.weekKey())
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                    )
                }

                DailyMessageBubble(
                    message = dailyMessage,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp).offset(y = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeButton(stringResource(R.string.btn_dream), R.drawable.ic_dream, onNavigateToDream, Modifier.weight(0.8f))
                    HomeButton(stringResource(R.string.btn_calendar), R.drawable.ic_calendar_moon, onNavigateToCalendar, Modifier.weight(0.8f))
                    HomeButton(stringResource(R.string.btn_fortune), R.drawable.ic_fortune, onNavigateToFortune, Modifier.weight(0.8f))
                }
            }

            CommunityButton(
                onClick = onNavigateToCommunity,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            )
        }
    }
}

// ----- 보조 컴포넌트들 -----

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
private fun AIReportCard(title: String, summary: String, isEnabled: Boolean, onReportClick: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(0.96f) }
    var alpha by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(initialValue = 0f, targetValue = 1f, animationSpec = tween(160)) { value, _ ->
            scale = 0.96f + (0.04f * value)
            alpha = value
        }
    }
    Card(
        modifier = modifier.scale(scale).graphicsLayer { this.alpha = alpha },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x14D7D7DB)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painter = painterResource(R.drawable.ic_ai_report), contentDescription = stringResource(R.string.ai_report), modifier = Modifier.size(18.dp).padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily(Font(R.font.pretendard_medium)), color = Color(0xFFE4E2E2)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 요약 텍스트 (감정 + 키워드)
                Text(text = summary, style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily(Font(R.font.pretendard_medium)), color = Color(0xFFC6D4DF)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            GradientButton(text = stringResource(R.string.ai_report_btn), onClick = onReportClick, enabled = isEnabled, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun DailyMessageBubble(message: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Image(painter = painterResource(R.drawable.home_message), contentDescription = stringResource(R.string.ai_avatar), modifier = Modifier.size(22.dp).padding(end = 8.dp))
        Text(text = message, style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily(Font(R.font.pretendard_medium)), color = Color.White, lineHeight = 15.sp), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HomeButton(text: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp).scale(scale).pointerInput(Unit) { detectTapGestures(onPress = { scale = 0.95f; tryAwaitRelease(); scale = 1f }) },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color(0xFFC6D4DF)),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFC6D4DF))
        Spacer(Modifier.width(7.dp))
        Text(text = text, style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily(Font(R.font.pretendard_medium))))
    }
}

@Composable
private fun GradientButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = if (enabled) listOf(Color(0xFFFEDCA6), Color(0xFF8BAAFF)) else listOf(Color(0x66FEDCA6), Color(0x668BAAFF))
    Box(
        modifier = modifier.height(32.dp).clip(RoundedCornerShape(14.dp)).background(brush = Brush.linearGradient(colors)).clickable(enabled = true) { onClick() }.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily(Font(R.font.pretendard_medium)), color = Color(0xFF17212B)))
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    IconButton(onClick = onClick, modifier = modifier.size(33.dp).scale(scale).pointerInput(Unit) { detectTapGestures(onPress = { scale = 1.4f; tryAwaitRelease(); scale = 1f }) }) {
        Image(painter = painterResource(R.drawable.ic_setting), contentDescription = stringResource(R.string.settings), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CommunityButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    Button(
        onClick = onClick, modifier = modifier.size(72.dp).scale(scale).pointerInput(Unit) { detectTapGestures(onPress = { scale = 0.95f; tryAwaitRelease(); scale = 1f }) },
        shape = RoundedCornerShape(36.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color.White)
    ) {
        Text(text = "커뮤니티", style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily(Font(R.font.pretendard_medium))))
    }
}

// [수정] 1개의 핵심 키워드만 선별하여 보여주는 로직
private fun buildWeeklySummaryLine(context: Context, topEmotion: String?, keywords: List<String>?): String {
    val emo = topEmotion?.trim().orEmpty()

    // 키워드 리스트 중 첫 번째(Index 0)만 가져옴. # 제거.
    val singleKeyword = keywords?.asSequence()
        ?.filter { it.isNotBlank() }
        ?.map { it.replace("#", "").trim() }
        ?.firstOrNull()
        .orEmpty()

    val emoLabel = context.getString(R.string.label_emotion)
    val kwLabel  = context.getString(R.string.label_keywords)

    // "감정: 긍정 • 키워드: 탈출" 형태로 포맷팅
    return when {
        emo.isNotEmpty() && singleKeyword.isNotEmpty() -> "$emoLabel: $emo • $kwLabel: $singleKeyword"
        emo.isNotEmpty() -> "$emoLabel: $emo"
        singleKeyword.isNotEmpty()  -> "$kwLabel: $singleKeyword"
        else -> context.getString(R.string.home_ai_sub)
    }
}

private fun getDailyFallbacks(context: Context): List<String> = listOf(
    context.getString(R.string.daily_fallback_1), context.getString(R.string.daily_fallback_2),
    context.getString(R.string.daily_fallback_3), context.getString(R.string.daily_fallback_4), context.getString(R.string.daily_fallback_5)
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen({}, {}, {}, {}, {}, {})
}