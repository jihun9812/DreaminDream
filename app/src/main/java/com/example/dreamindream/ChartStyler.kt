// app/src/main/java/com/example/dreamindream/chart/ChartStyler.kt
package com.example.dreamindream.chart

import android.graphics.Color
import android.graphics.Typeface
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.math.roundToInt

/** 퍼센트 라벨(소수 1) */
class PercentFormatter : ValueFormatter() {
    override fun getBarLabel(e: BarEntry?): String {
        val v = e?.y ?: 0f
        return "${String.format("%.1f", v)}%"
    }
}

/** 공통 스타일: 고대비, 격자 최소화, 즉시 렌더 */
fun setupBarChart(chart: BarChart) = chart.apply {
    description.isEnabled = false
    setDrawGridBackground(false)
    setScaleEnabled(false)
    setPinchZoom(false)
    setExtraOffsets(6f, 6f, 6f, 12f)

    axisLeft.apply {
        axisMinimum = 0f
        axisMaximum = 100f
        granularity = 20f
        gridColor = Color.parseColor("#3A3A4A")
        textColor = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    axisRight.isEnabled = false

    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        granularity = 1f
        setDrawGridLines(false)
        textColor = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        yOffset = 6f
    }

    legend.apply {
        isEnabled = false
        verticalAlignment = Legend.LegendVerticalAlignment.TOP
        textColor = Color.WHITE
    }

    setNoDataText("데이터가 없습니다")
    setNoDataTextColor(Color.LTGRAY)

    animateY(0) // 딜레이 제거
}

/* ─────────────────────────────────────────────────────────────
 * 팔레트: 감정/테마 라벨별 ‘고정 색’ + 라벨 미지정 시 순환 팔레트
 * 어두운 배경에서 대비 잘 나오는 톤으로 구성
 * ──────────────────────────────────────────────────────────── */

/** 감정 라벨별 풍부한 팔레트 */
fun richEmotionColor(label: String): Int = when (label) {
    // 긍정 계열
    "긍정" -> 0xFF4CAF50.toInt()   // Green 500
    "기쁨" -> 0xFF66BB6A.toInt()
    "행복" -> 0xFF81C784.toInt()
    "감사" -> 0xFF26A69A.toInt()   // Teal 400
    "희망" -> 0xFF29B6F6.toInt()   // Light Blue 400
    "설렘" -> 0xFF42A5F5.toInt()   // Blue 400
    "사랑" -> 0xFFAB47BC.toInt()   // Purple 400
    "만족" -> 0xFF8BC34A.toInt()
    "즐거움" -> 0xFF00BCD4.toInt()
    "뿌듯" -> 0xFF7E57C2.toInt()
    "성취감" -> 0xFF26C6DA.toInt()
    "안도" -> 0xFF26A69A.toInt()
    "자신감" -> 0xFF5C6BC0.toInt()

    // 평온/중립/몰입
    "평온" -> 0xFF80CBC4.toInt()
    "안정" -> 0xFF90CAF9.toInt()
    "차분" -> 0xFF4DD0E1.toInt()
    "편안" -> 0xFF81D4FA.toInt()
    "중립" -> 0xFFB0BEC5.toInt()
    "몰입" -> 0xFF64B5F6.toInt()

    // 활력/에너지
    "활력" -> 0xFFFFC107.toInt()
    "설득" -> 0xFFFFB300.toInt()

    // 부정(스펙트럼 세분화)
    "혼란" -> 0xFF9575CD.toInt()   // Deep Purple 300
    "불안" -> 0xFFFF7043.toInt()   // Deep Orange 400
    "우울" -> 0xFFE53935.toInt()   // Red 600
    "피로" -> 0xFF8D6E63.toInt()   // Brown 400
    "우울/피로" -> 0xFFD81B60.toInt() // Pink 600 (복합)
    "좌절" -> 0xFFEF5350.toInt()
    "분노" -> 0xFFEF6C00.toInt()
    "슬픔" -> 0xFFEC407A.toInt()
    "외로움" -> 0xFF5C6BC0.toInt()

    else -> paletteFallback(label)
}


fun richThemeColor(label: String): Int = when (label) {
    "관계"     -> 0xFF4FC3F7.toInt()
    "성취"     -> 0xFFA5D6A7.toInt()
    "변화"     -> 0xFFFFD54F.toInt()
    "불안요인" -> 0xFFF06292.toInt()

    // 추가 테마(혹시 쓰면 고정)
    "성장"     -> 0xFF81C784.toInt()
    "자아"     -> 0xFFBA68C8.toInt()
    "재정"     -> 0xFFFFA726.toInt()
    "건강"     -> 0xFF26A69A.toInt()
    "학업"     -> 0xFF64B5F6.toInt()
    "일/커리어"-> 0xFF90CAF9.toInt()
    "가족"     -> 0xFFFF8A65.toInt()
    "모험"     -> 0xFF26C6DA.toInt()

    else -> paletteFallback(label)
}


private fun paletteFallback(key: String): Int {
    val palette = intArrayOf(
        0xFF7C9AFF.toInt(), 0xFF6EE7F2.toInt(), 0xFFFFB74D.toInt(), 0xFF4DD0E1.toInt(),
        0xFFAB47BC.toInt(), 0xFF81C784.toInt(), 0xFFE57373.toInt(), 0xFFFF8A65.toInt(),
        0xFF26C6DA.toInt(), 0xFF5C6BC0.toInt(), 0xFFAED581.toInt(), 0xFFFFD54F.toInt()
    )
    val idx = (key.hashCode() and 0x7fffffff) % palette.size
    return palette[idx]
}

private fun makeSet(values: List<Float>, labels: List<String>, colorFor: (String) -> Int): BarDataSet {
    val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v.coerceIn(0f, 100f)) }
    return BarDataSet(entries, "").apply {
        setDrawValues(true)
        valueFormatter = PercentFormatter()
        valueTextColor = Color.WHITE
        valueTextSize = when {
            labels.size >= 9 -> 8.5f
            labels.size >= 7 -> 9.5f
            else -> 11f
        }
        valueTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        colors = labels.map { colorFor(it) }
        setDrawIcons(false)
        highLightAlpha = 30
    }
}

/** 퍼센트 막대 렌더 (단일 세트) */
fun renderPercentBars(
    chart: BarChart,
    labels: List<String>,
    values: List<Float>,
    colorFor: (String) -> Int
) {
    chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels.map { short(it) })
    val set = makeSet(values, labels, colorFor)
    chart.data = BarData(set).apply { barWidth = 0.56f }

    // 접근성: 최고치 설명
    val topIdx = values.indexOf(values.maxOrNull() ?: 0f).coerceAtLeast(0)
    chart.contentDescription =
        "최고치: ${labels.getOrNull(topIdx) ?: ""} ${(values.getOrNull(topIdx) ?: 0f).roundToInt()}%"

    chart.invalidate()
}

private fun short(s: String): String = if (s.length <= 6) s else s.take(6) + "…"
