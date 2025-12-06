package com.dreamindream.app.ui.aireport

import android.widget.TextView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.dreamindream.app.R
import com.dreamindream.app.chart.*
import com.github.mikephil.charting.charts.BarChart
import java.text.SimpleDateFormat
import java.util.Locale

// --- Design Tokens (공유해서 사용) ---
val FontBold = FontFamily(Font(R.font.pretendard_bold, FontWeight.Bold))
val FontMedium = FontFamily(Font(R.font.pretendard_medium, FontWeight.Medium))
val ColorBgDark = Color(0xFF0F111A)
val ColorCardSurface = Color(0xFF1E212B)
val ColorGold = Color(0xFFFFD54F)
val ColorTextMain = Color(0xFFEEEEEE)
val ColorTextSub = Color(0xFF9EA3B0)
val ColorDeepPurple = Color(0xFF5E35B1)
val ColorAccentBlue = Color(0xFF2979FF)
val ColorGrowthGreen = Color(0xFF15E881)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryTimelineSheet(weeks: List<String>, onWeekSelected: (String) -> Unit) {
    val othersLabel = stringResource(R.string.ai_report_history_group_others)

    val groupedHistory = remember(weeks, othersLabel) {
        weeks.groupBy { key ->
            try {
                val parts = key.split("-W")
                val year = parts[0].toInt()
                val week = parts[1].toInt()
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.YEAR, year)
                cal.set(java.util.Calendar.WEEK_OF_YEAR, week)
                val fmt = SimpleDateFormat("MMMM yyyy", Locale.US)
                fmt.format(cal.time)
            } catch (e: Exception) {
                othersLabel
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxHeight(0.85f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color.White.copy(0.2f), RoundedCornerShape(2.dp))
            )
        }
        Text(
            text = stringResource(R.string.ai_report_history_title),
            fontSize = 22.sp,
            fontFamily = FontBold,
            color = ColorTextMain
        )
        Spacer(Modifier.height(24.dp))

        if (weeks.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_report_history_empty),
                color = ColorTextSub
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                groupedHistory.forEach { (month, keys) ->
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ColorBgDark)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                month.uppercase(),
                                color = ColorGold,
                                fontSize = 12.sp,
                                fontFamily = FontBold
                            )
                        }
                    }
                    items(keys) { wk ->
                        Card(
                            onClick = { onWeekSelected(wk) },
                            colors = CardDefaults.cardColors(
                                containerColor = ColorCardSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(ColorAccentBlue, CircleShape)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = stringResource(
                                        R.string.ai_report_history_week_label,
                                        wk.substringAfter("-W")
                                    ),
                                    color = ColorTextMain,
                                    fontFamily = FontMedium
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = ColorTextSub
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    Card(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
        border = if (highlight) BorderStroke(1.dp, ColorGold.copy(0.5f)) else null
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label.uppercase(),
                color = ColorTextSub,
                fontSize = 10.sp,
                fontFamily = FontBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                color = if (highlight) ColorGold else ColorTextMain,
                fontSize = 18.sp,
                fontFamily = FontBold
            )
        }
    }
}

@Composable
fun ChartContainer(
    title: String,
    labels: List<String>,
    values: List<Float>,
    isEmo: Boolean,
    onInfo: (() -> Unit)?
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = ColorTextMain,
                fontFamily = FontBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(6.dp))
            if (onInfo != null) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(
                        R.string.ai_report_chart_info_button_content_description
                    ),
                    tint = ColorTextSub,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onInfo() }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        AndroidView(
            factory = { ctx ->
                BarChart(ctx).apply {
                    setupBarChart(this)
                    useRoundedBars(this, 12f)
                }
            },
            update = { chart ->
                if (labels.isNotEmpty()) {
                    renderPercentBars(
                        chart,
                        labels,
                        values,
                        if (isEmo) ::richEmotionColor else ::richThemeColor
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
    }
}

@Composable
fun BasicAnalysisView(htmlContent: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCardSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.ai_report_analysis_summary_title),
                color = ColorGold,
                fontFamily = FontBold
            )
            Spacer(Modifier.height(12.dp))
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        setTextColor(0xFFEEEEEE.toInt())
                        textSize = 15f
                        setLineSpacing(0f, 1.4f)
                    }
                },
                update = {
                    it.text = HtmlCompat.fromHtml(
                        htmlContent,
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                }
            )
        }
    }
}

@Composable
fun CreativeLoadingView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBgDark.copy(alpha = 0.95f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ColorGold)
            Spacer(Modifier.height(24.dp))
            Text(message, color = ColorTextMain, fontFamily = FontMedium)
        }
    }
}

@Composable
fun ProGradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val brush = Brush.linearGradient(listOf(Color(0xFFFEDCA6), Color(0xFF8BAAFF)))
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(12.dp, spotColor = ColorGold)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = ColorBgDark, fontFamily = FontBold, fontSize = 16.sp)
        }
    }
}