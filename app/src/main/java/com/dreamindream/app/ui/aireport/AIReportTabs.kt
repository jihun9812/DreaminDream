package com.dreamindream.app.ui.aireport

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dreamindream.app.R
import com.dreamindream.app.chart.*
import com.github.mikephil.charting.charts.BarChart
import org.json.JSONObject
import org.json.JSONArray

// --- Design Tokens ---
private val Gold = Color(0xFFFFD54F)
private val TextMain = Color(0xFFEEEEEE)
private val TextSub = Color(0xFF9EA3B0)
private val CardBg = Color(0xFF1E212B)

@Composable
fun DeepAnalysisTabs(state: AIReportUiState) {
    val jsonString = state.analysisJson
    val data = remember(jsonString) {
        try { JSONObject(jsonString) } catch (e: Exception) { null }
    } ?: return

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // 탭 제목 다국어 처리
    val tabs = listOf(
        stringResource(R.string.ai_report_tab_insight),
        stringResource(R.string.ai_report_tab_symbols),
        stringResource(R.string.ai_report_tab_prediction),
        stringResource(R.string.ai_report_tab_action),
        stringResource(R.string.ai_report_tab_theme),
        stringResource(R.string.ai_report_tab_growth)
    )

    val icons = listOf(
        Icons.Default.Psychology,
        Icons.Default.AutoAwesome,
        Icons.Default.Visibility,
        Icons.Default.Lightbulb,
        Icons.Default.BarChart,
        Icons.AutoMirrored.Filled.TrendingUp
    )

    val defaultTitle = stringResource(R.string.ai_report_default_title)

    Column {
        // --- Title & Summary ---
        Text(
            text = data.optString("title").ifBlank { defaultTitle },
            color = TextMain, fontSize = 22.sp, fontFamily = FontBold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center
        )
        Text(
            text = data.optString("summary"),
            color = TextSub, fontSize = 14.sp, fontFamily = FontMedium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        // --- Tabs ---
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent, contentColor = Gold, edgePadding = 0.dp,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = Gold)
                }
            },
            divider = { HorizontalDivider(color = Color.White.copy(0.1f)) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontFamily = FontBold, fontSize = 13.sp) },
                    icon = { Icon(icons[index], contentDescription = null, modifier = Modifier.size(20.dp)) },
                    unselectedContentColor = TextSub
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // --- Content Area ---
        AnimatedContent(
            targetState = selectedTabIndex,
            label = "TabContent",
            transitionSpec = { fadeIn(tween(300)) + slideInVertically { 20 } togetherWith fadeOut(tween(200)) }
        ) { targetIndex ->
            when (targetIndex) {
                0 -> TabContentInsight(data)
                1 -> TabContentSymbols(state.symbolDetails, data.optJSONArray("core_themes"))
                2 -> TabContentPredictions(state.futurePredictions)
                3 -> TabContentAction(data)
                4 -> TabContentThemeChart(state)
                5 -> TabContentGrowth(data, state.dreamScore)
            }
        }
    }
}

// ------------------------------------------------------------------------
// 1. INSIGHT TAB
// ------------------------------------------------------------------------
@Composable
fun TabContentInsight(data: JSONObject) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A2F1F), Color(0xFF1B1B1B))))
                .padding(20.dp)
        ) {
            Column {
                Icon(Icons.Default.FormatQuote, contentDescription = null, tint = Color.White.copy(0.3f))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = data.optString("subconscious_message"),
                    color = Color.White, fontSize = 16.sp, fontFamily = FontMedium,
                    fontStyle = FontStyle.Italic, lineHeight = 24.sp
                )
            }
        }

        val paragraphs = remember(data) {
            val rawObj = data.opt("deep_analysis")
            val rawTextList = mutableListOf<String>()

            if (rawObj is JSONArray) {
                for (i in 0 until rawObj.length()) rawTextList.add(rawObj.getString(i))
            } else {
                var str = data.optString("deep_analysis", "").trim()
                try {
                    val jsonArr = JSONArray(str)
                    for (i in 0 until jsonArr.length()) rawTextList.add(jsonArr.getString(i))
                } catch (e: Exception) {
                    if (str.startsWith("[") && str.endsWith("]")) {
                        str = str.substring(1, str.length - 1)
                    }
                    val parts = str.split("\",\"").map {
                        it.replace("\"", "").replace("\\n", "\n")
                    }
                    rawTextList.addAll(parts)
                }
            }
            rawTextList.filter { it.isNotBlank() }
        }

        paragraphs.forEach { para ->
            Text(text = para, color = TextMain, fontSize = 15.sp, lineHeight = 26.sp, fontFamily = FontMedium)
            HorizontalDivider(color = Color.White.copy(0.05f))
        }
    }
}

// ------------------------------------------------------------------------
// 2. SYMBOLS TAB
// ------------------------------------------------------------------------
@Composable
fun TabContentSymbols(symbolDetails: List<Map<String, String>>, fallbackThemes: JSONArray?) {
    var selectedSymbol by remember { mutableStateOf<Map<String, String>?>(null) }

    if (selectedSymbol != null) {
        AlertDialog(
            onDismissRequest = { selectedSymbol = null },
            containerColor = CardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#", color = Gold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedSymbol!!["keyword"] ?: "", color = TextMain, fontFamily = FontBold)
                }
            },
            text = {
                Text(selectedSymbol!!["description"] ?: "", color = TextSub, fontSize = 15.sp, lineHeight = 24.sp)
            },
            confirmButton = {
                TextButton(onClick = { selectedSymbol = null }) {
                    Text(stringResource(R.string.common_close), color = Gold)
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.ai_report_symbols_description),
            color = TextSub, fontSize = 13.sp
        )

        if (symbolDetails.isNotEmpty()) {
            symbolDetails.forEach { item ->
                SymbolCard(keyword = item["keyword"] ?: "", onClick = { selectedSymbol = item })
            }
        } else if (fallbackThemes != null) {
            (0 until fallbackThemes.length()).map { fallbackThemes.getString(it).replace("#", "") }
                .forEach { keyword ->
                    SymbolCard(keyword = keyword, onClick = null)
                }
        }
    }
}

@Composable
fun SymbolCard(keyword: String, onClick: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(Gold.copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("#", color = Gold, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Text(text = keyword, color = TextMain, fontSize = 16.sp, fontFamily = FontBold)
            if (onClick != null) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Info, contentDescription = null, tint = TextSub.copy(0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ------------------------------------------------------------------------
// 3. PREDICTIONS TAB (다국어 지원)
// ------------------------------------------------------------------------
@Composable
fun TabContentPredictions(predictions: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0D47A1), Color(0xFF1976D2))))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.ai_report_prediction_title), color = Gold, fontSize = 13.sp, fontFamily = FontBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ai_report_prediction_subtitle),
                        color = Color.White.copy(0.9f),
                        fontSize = 14.sp,
                        fontFamily = FontMedium
                    )
                }
            }
        }

        if (predictions.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_report_prediction_empty),
                color = TextSub,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            )
        } else {
            predictions.forEachIndexed { index, prediction ->
                PredictionCard(index = index, content = prediction)
            }
        }
    }
}

@Composable
fun PredictionCard(index: Int, content: String) {
    // ★ [핵심] 인덱스에 따라 strings.xml의 제목을 매핑 (0:직업, 1:관계, 2:내면)
    val titleResId = when(index) {
        0 -> R.string.ai_report_pred_category_career   // 직업
        1 -> R.string.ai_report_pred_category_relation // 관계
        2 -> R.string.ai_report_pred_category_inner    // 내면
        3 -> R.string.ai_report_pred_category_love     // 사랑 & 연애 (New)
        4 -> R.string.ai_report_pred_category_wealth   // 재정 & 풍요 (New)
        else -> R.string.ai_report_pred_category_health // 건강 & 에너지 (New)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .background(Gold, CircleShape)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                // 앱 내 다국어 제목
                Text(stringResource(titleResId), color = TextSub, fontSize = 11.sp, fontFamily = FontBold)
                Spacer(Modifier.height(6.dp))
                // AI 번역 내용 (혹시 모를 라벨 제거)
                Text(
                    text = content.replace(Regex("^(Prediction \\d+:|Predictions:|Theme: )"), "").trim(),
                    color = TextMain,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    fontFamily = FontMedium
                )
            }
        }
    }
}

// ------------------------------------------------------------------------
// 4. ACTION TAB
// ------------------------------------------------------------------------
@Composable
fun TabContentAction(data: JSONObject) {
    val adviceArray = data.optJSONArray("actionable_advice")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
            border = BorderStroke(1.dp, Gold.copy(0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Gold)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.ai_report_action_lucky_label), color = Gold, fontSize = 11.sp, fontFamily = FontBold)
                    Spacer(Modifier.height(4.dp))
                    Text(text = data.optString("lucky_action"), color = TextMain, fontSize = 15.sp, fontFamily = FontMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.ai_report_action_practical_title), color = TextMain, fontFamily = FontBold, fontSize = 16.sp)
        if (adviceArray != null) {
            for (i in 0 until adviceArray.length()) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(24.dp).background(CardBg, CircleShape).border(1.dp, TextSub.copy(0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("${i + 1}", color = TextSub, fontSize = 12.sp) }
                    Spacer(Modifier.width(12.dp))
                    Text(text = adviceArray.getString(i), color = TextMain.copy(0.9f), fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// 5. THEME TAB (상세 설명 및 다국어 지원)
// ------------------------------------------------------------------------
@Composable
fun TabContentThemeChart(state: AIReportUiState) {
    // ★ [핵심] 영어 키값과 strings.xml 리소스 매핑
    val themeTitleMap = mapOf(
        "Relationships" to R.string.theme_label_relationships,
        "Achievement" to R.string.theme_label_achievement,
        "Change" to R.string.theme_label_change,
        "Anxiety" to R.string.theme_label_anxiety
    )
    // 고정 순서
    val orderedKeys = listOf("Relationships", "Achievement", "Change", "Anxiety")

    Column {
        Text(stringResource(R.string.ai_report_theme_chart_title), color = TextMain, fontFamily = FontBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ai_report_theme_chart_subtitle), color = TextSub, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))

        AndroidView(
            factory = { ctx -> BarChart(ctx).apply { setupBarChart(this); useRoundedBars(this, 12f) } },
            update = { chart -> if (state.themeLabels.isNotEmpty()) renderPercentBars(chart, state.themeLabels, state.themeDist, ::richThemeColor) },
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )

        if (state.themeAnalysis.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(0.1f))
            Spacer(Modifier.height(24.dp))

            orderedKeys.forEach { key ->
                // 데이터가 있는지 확인 (JSON에서 가져옴)
                val description = state.themeAnalysis[key] ?: return@forEach

                // 리소스 ID 찾기 (없으면 영어 키 그대로 사용)
                val resId = themeTitleMap[key]
                val displayTitle = if (resId != null) stringResource(resId) else key

                Row(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", color = Gold, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        // 앱 언어 제목
                        Text(displayTitle, color = TextMain, fontFamily = FontBold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        // AI 상세 설명
                        Text(description, color = TextSub, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// 6. GROWTH TAB
// ------------------------------------------------------------------------
@Composable
fun TabContentGrowth(data: JSONObject, score: Int) {
    val aiGrowthMsg = data.optString("growth_message")
    val highMsg = stringResource(R.string.ai_report_growth_msg_high)
    val midMsg = stringResource(R.string.ai_report_growth_msg_mid)
    val lowMsg = stringResource(R.string.ai_report_growth_msg_low)
    val ColorGrowthGreen = Color(0xFF15E881)

    val growthMessage = if (aiGrowthMsg.isNotBlank()) aiGrowthMsg else when {
        score >= 80 -> highMsg
        score >= 50 -> midMsg
        else -> lowMsg
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.ai_report_growth_current_energy_label),
                    color = ColorGrowthGreen, fontSize = 12.sp, fontFamily = FontBold, letterSpacing = 1.sp
                )
                Spacer(Modifier.height(16.dp))
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { 1f }, modifier = Modifier.size(120.dp), color = Color.White.copy(0.1f), strokeWidth = 8.dp)
                    CircularProgressIndicator(progress = { score / 100f }, modifier = Modifier.size(120.dp), color = ColorGrowthGreen, strokeWidth = 8.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$score", color = TextMain, fontSize = 32.sp, fontFamily = FontBold)
                        Text(text = stringResource(R.string.ai_report_growth_point_label), color = TextSub, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(text = stringResource(R.string.ai_report_growth_potential_title), color = TextMain, fontSize = 16.sp, fontFamily = FontBold)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().border(1.dp, ColorGrowthGreen.copy(0.3f), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)).background(ColorBgDark).padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = ColorGrowthGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.ai_report_growth_next_step_title), color = ColorGrowthGreen, fontFamily = FontBold)
                }
                Spacer(Modifier.height(12.dp))
                Text(text = growthMessage, color = TextMain, fontSize = 14.sp, lineHeight = 22.sp, fontFamily = FontMedium)
            }
        }
    }
}