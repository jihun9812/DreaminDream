package com.example.dreamindream.chart

import android.graphics.*
import android.view.View
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.ViewPortHandler
import kotlin.math.roundToInt

/** 퍼센트 라벨(소수 1자리) */
class PercentFormatter : ValueFormatter() {
    override fun getBarLabel(e: BarEntry?): String {
        val v = e?.y ?: 0f
        return "${String.format("%.1f", v)}%"
    }
}

/** 공통 스타일: 고대비, 미니멀 그리드, 즉시 렌더 */
fun setupBarChart(chart: BarChart) = chart.apply {
    description.isEnabled = false
    setDrawGridBackground(false)
    setScaleEnabled(false)
    setPinchZoom(false)
    setExtraOffsets(8f, 8f, 8f, 16f)
    setDrawValueAboveBar(true)
    setDrawBarShadow(false)
    setNoDataText("데이터가 없습니다")
    setNoDataTextColor(Color.parseColor("#9AA3AB"))
    animateY(0)

    axisLeft.apply {
        axisMinimum = 0f
        axisMaximum = 100f
        granularity = 20f
        gridColor = Color.parseColor("#2F3A4A")
        textColor = Color.parseColor("#E8EDF3")
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setDrawAxisLine(false)
    }
    axisRight.isEnabled = false

    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        granularity = 1f
        setDrawGridLines(false)
        textColor = Color.parseColor("#E8EDF3")
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        yOffset = 8f
        setDrawAxisLine(false)
        labelRotationAngle = 0f
    }

    legend.apply {
        isEnabled = false
        verticalAlignment = Legend.LegendVerticalAlignment.TOP
        textColor = Color.WHITE
    }
}

/* ─────────────────────────────────────────────────────────────
 * 팔레트: 감정/테마 라벨별 ‘고정 색’ + 라벨 미지정 시 순환 팔레트
 * ──────────────────────────────────────────────────────────── */
fun richEmotionColor(label: String): Int = when (label) {
    "긍정" -> 0xFF4CAF50.toInt()
    "기쁨" -> 0xFF66BB6A.toInt()
    "행복" -> 0xFF81C784.toInt()
    "감사" -> 0xFF26A69A.toInt()
    "희망" -> 0xFF29B6F6.toInt()
    "설렘" -> 0xFF42A5F5.toInt()
    "사랑" -> 0xFFAB47BC.toInt()
    "만족" -> 0xFF8BC34A.toInt()
    "즐거움" -> 0xFF00BCD4.toInt()
    "뿌듯" -> 0xFF7E57C2.toInt()
    "성취감" -> 0xFF26C6DA.toInt()
    "안도" -> 0xFF26A69A.toInt()
    "자신감" -> 0xFF5C6BC0.toInt()

    "평온" -> 0xFF80CBC4.toInt()
    "안정" -> 0xFF90CAF9.toInt()
    "차분" -> 0xFF4DD0E1.toInt()
    "편안" -> 0xFF81D4FA.toInt()
    "중립" -> 0xFFB0BEC5.toInt()
    "몰입" -> 0xFF64B5F6.toInt()

    "활력" -> 0xFFFFC107.toInt()

    "혼란" -> 0xFF9575CD.toInt()
    "불안" -> 0xFFFF7043.toInt()
    "우울" -> 0xFFE53935.toInt()
    "피로" -> 0xFF8D6E63.toInt()
    "우울/피로" -> 0xFFD81B60.toInt()
    "좌절" -> 0xFFEF5350.toInt()
    "분노" -> 0xFFEF6C00.toInt()
    "슬픔" -> 0xFFEC407A.toInt()
    "외로움" -> 0xFF5C6BC0.toInt()
    else -> paletteFallback(label)
}

fun richThemeColor(label: String): Int = when (label) {
    "관계" -> 0xFF4FC3F7.toInt()
    "성취" -> 0xFFA5D6A7.toInt()
    "변화" -> 0xFFFFD54F.toInt()
    "불안요인" -> 0xFFF06292.toInt()
    "성장" -> 0xFF81C784.toInt()
    "자아" -> 0xFFBA68C8.toInt()
    "재정" -> 0xFFFFA726.toInt()
    "건강" -> 0xFF26A69A.toInt()
    "학업" -> 0xFF64B5F6.toInt()
    "일/커리어" -> 0xFF90CAF9.toInt()
    "가족" -> 0xFFFF8A65.toInt()
    "모험" -> 0xFF26C6DA.toInt()
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

private fun darker(color: Int, f: Float = 0.82f): Int {
    val a = color ushr 24
    val r = ((color shr 16) and 0xFF)
    val g = ((color shr 8) and 0xFF)
    val b = (color and 0xFF)
    return (a shl 24) or
            ((r * f).toInt() shl 16) or
            ((g * f).toInt() shl 8) or
            ((b * f).toInt())
}

private fun makeSet(values: List<Float>, labels: List<String>, colorFor: (String) -> Int): BarDataSet {
    val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v.coerceIn(0f, 100f)) }
    return BarDataSet(entries, "").apply {
        setDrawValues(true)
        valueFormatter = PercentFormatter()
        valueTextColor = Color.parseColor("#F5F9FF")
        valueTextSize = when {
            labels.size >= 9 -> 8.5f
            labels.size >= 7 -> 9.5f
            else -> 11f
        }
        valueTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        colors = labels.map { colorFor(it) }
        highLightAlpha = 28
        barBorderWidth = 0f
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

    // 값 색 대비를 위해 라벨색=어둡게
    set.colors = labels.map { colorFor(it) }
    set.valueTextColor = Color.parseColor("#F4F7FB")

    chart.data = BarData(set).apply { barWidth = 0.56f }

    // 접근성: 최고치 안내
    val topIdx = values.indexOf(values.maxOrNull() ?: 0f).coerceAtLeast(0)
    chart.contentDescription =
        "최고치: ${labels.getOrNull(topIdx) ?: ""} ${(values.getOrNull(topIdx) ?: 0f).roundToInt()}%"

    chart.invalidate()
}

/** 둥근 모서리 렌더러 적용 (기업풍) */
fun useRoundedBars(chart: BarChart, radiusDp: Float = 12f) {
    val rpx = radiusDp * chart.resources.displayMetrics.density
    chart.renderer = RoundedBarChartRenderer(chart, chart.animator, chart.viewPortHandler, rpx)
    chart.invalidate()
}

private fun short(s: String): String = if (s.length <= 6) s else s.take(6) + "…"

/* ─────────────────────────────────────────────────────────────
 * 커스텀 렌더러: 둥근 모서리
 * ──────────────────────────────────────────────────────────── */
private class RoundedBarChartRenderer(
    chart: BarChart,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
    private val radiusPx: Float
) : BarChartRenderer(chart, animator, viewPortHandler) {

    private val barRect = RectF()

    override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
        val trans = mChart.getTransformer(dataSet.axisDependency)

        mBarBorderPaint.color = dataSet.barBorderColor
        mBarBorderPaint.strokeWidth = dataSet.barBorderWidth
        val drawBorder = dataSet.barBorderWidth > 0f

        val phaseX = mAnimator.phaseX
        val phaseY = mAnimator.phaseY

        mBarBuffers[index].apply {
            setPhases(phaseX, phaseY)
            setDataSet(index)
            setInverted(mChart.isInverted(dataSet.axisDependency))
            setBarWidth(mChart.barData.barWidth)
            feed(dataSet)
        }

        trans.pointValuesToPixel(mBarBuffers[index].buffer)
        val buffer = mBarBuffers[index].buffer

        for (j in buffer.indices step 4) {
            val left = buffer[j]
            val top = buffer[j + 1]
            val right = buffer[j + 2]
            val bottom = buffer[j + 3]

            barRect.set(left, top, right, bottom)
            mRenderPaint.color = dataSet.getColor(j / 4)

            // 바 그림자(아주 연하게)
            val shadowPaint = Paint(mRenderPaint).apply {
                color = darker(mRenderPaint.color, 0.75f)
                alpha = 40
            }
            val shadowRect = RectF(barRect).apply { offset(0f, 2f) }
            c.drawRoundRect(shadowRect, radiusPx, radiusPx, shadowPaint)

            // 본체
            c.drawRoundRect(barRect, radiusPx, radiusPx, mRenderPaint)

            if (drawBorder) c.drawRoundRect(barRect, radiusPx, radiusPx, mBarBorderPaint)
        }
    }
}
