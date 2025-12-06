package com.dreamindream.app.ui.aireport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import com.dreamindream.app.R

// --- Luxury Theme Colors ---
private val CardBg = Color(0xFF1E212B)
private val TextMain = Color(0xFFEEEEEE)
private val TextSub = Color(0xFFB0BEC5)
private val AccentGold = Color(0xFFFFD54F)
private val DeepPurple = Color(0xFF7E57C2)
private val MidnightBlue = Color(0xFF1A237E)

@Composable
fun DeepAnalysisResultView(jsonString: String) { // imageUrl 인자 제거됨
    val data = remember(jsonString) { try { JSONObject(jsonString) } catch (e: Exception) { null } }
    var visibleStep by remember { mutableIntStateOf(0) }

    // 애니메이션 스텝 재조정 (이미지가 빠지면서 순서 당겨짐)
    LaunchedEffect(Unit) {
        delay(300); visibleStep = 1 // Title & Summary
        delay(500); visibleStep = 2 // Core Themes
        delay(500); visibleStep = 3 // Deep Analysis
        delay(600); visibleStep = 4 // Subconscious Message
        delay(600); visibleStep = 5 // Advice & Lucky Action
    }

    if (data == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            Text("데이터 로드 실패", color = TextSub)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Title & Summary (이제 가장 먼저 나옴)
        AnimatedSection(visible = visibleStep >= 1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoAwesome, null, tint = AccentGold, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = data.optString("title", "심층 꿈 분석 리포트"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMain,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = data.optString("summary"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // 2. Core Themes
        AnimatedSection(visible = visibleStep >= 2) {
            val themes = data.optJSONArray("core_themes")
            if (themes != null && themes.length() > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("CORE THEMES", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val count = minOf(themes.length(), 4)
                        for (i in 0 until count) {
                            LuxuryChip(text = themes.getString(i))
                            if (i < count - 1) Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
        }

        // 3. Deep Analysis (배열 텍스트 정제 로직 포함)
        AnimatedSection(visible = visibleStep >= 3) {
            val rawDeep = data.opt("deep_analysis")
            val cleanDeepContent = if (rawDeep is JSONArray) {
                // 배열이면 줄바꿈으로 합치기
                (0 until rawDeep.length()).joinToString("\n\n") { rawDeep.getString(it) }
            } else {
                // 문자열이면 혹시 모를 대괄호/따옴표 제거
                data.optString("deep_analysis", "")
                    .replace(Regex("^\\[\"|\"\\]$"), "") // 시작 [" 과 끝 "] 제거
                    .replace("\",\"", "\n\n")      // 중간 구분자 교체
                    .replace("\", \"", "\n\n")     // 공백 포함 구분자 교체
            }

            AnalysisCard(
                title = "심층 심리 분석",
                icon = Icons.Default.Psychology,
                highlightColor = DeepPurple,
                content = cleanDeepContent
            )
        }

        // 4. Subconscious Message
        AnimatedSection(visible = visibleStep >= 4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = DeepPurple)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(MidnightBlue, Color(0xFF311B92))))
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.FormatQuote, null, tint = Color.White.copy(alpha = 0.1f), modifier = Modifier.size(80.dp).align(Alignment.TopStart).offset((-10).dp, (-10).dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("잠재의식의 메시지", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "\"" + data.optString("subconscious_message") + "\"",
                        color = TextMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 5. Advice & Lucky Action
        AnimatedSection(visible = visibleStep >= 5) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                val adviceArray = data.optJSONArray("actionable_advice")
                AnalysisCard(
                    title = stringResource(R.string.realistic_advice_multi),
                    icon = Icons.Default.Lightbulb,
                    highlightColor = AccentGold,
                    content = "",
                    customContent = {
                        if (adviceArray != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                for (i in 0 until adviceArray.length()) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text("✦", color = AccentGold, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(adviceArray.getString(i), color = TextSub, fontSize = 15.sp, lineHeight = 22.sp)
                                    }
                                }
                            }
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = AccentGold)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, AccentGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .background(Color(0xFF252836))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(AccentGold.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Star, null, tint = AccentGold, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("행운을 부르는 행동", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(text = data.optString("lucky_action"), color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun AnimatedSection(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(600)) + slideInVertically(spring(stiffness = Spring.StiffnessLow)) { 50 }) { content() }
}

@Composable
fun LuxuryChip(text: String) {
    Surface(color = Color.Transparent, shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, TextSub.copy(alpha = 0.3f)), modifier = Modifier.height(32.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            Text(text = text.uppercase(), color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun AnalysisCard(
    title: String,
    icon: ImageVector,
    highlightColor: Color,
    content: String,
    customContent: (@Composable () -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = highlightColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    color = highlightColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(16.dp))

            if (content.isNotBlank()) {
                val paragraphs = content
                    .split("\n\n")
                    .map { it.trim().removePrefix("\"").removeSuffix("\"") }
                    .filter { it.isNotEmpty() }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            color = TextSub,
                            lineHeight = 24.sp,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }

            customContent?.invoke()
        }
    }
}