package com.example.dreamindream

import android.graphics.*
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.Utils
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.util.*

class AIReportFragment : Fragment() {

    // palette
    private val COLOR_TEXT_PRIMARY = Color.parseColor("#F3F8FC")
    private val COLOR_TEXT_DIMMED  = Color.parseColor("#B6C7D3")
    private val NEON_POSITIVE      = Color.parseColor("#00F5A0")
    private val NEON_NEUTRAL       = Color.parseColor("#9CB6C7")
    private val NEON_NEGATIVE      = Color.parseColor("#FF5A7D")
    private val GRID_LIGHT         = Color.parseColor("#22FFFFFF")
    private val GRID_LIGHTER       = Color.parseColor("#14FFFFFF")
    private val VALUE_TEXT         = Color.parseColor("#EFF6FB")

    private lateinit var keywordsText: TextView
    private lateinit var aiComment: TextView
    private lateinit var analysisScore: TextView
    private lateinit var emptyIconLayout: View
    private lateinit var reportCard: View
    private lateinit var barChart: BarChart
    private lateinit var adView: AdView
    private lateinit var weekLabel: TextView

    private lateinit var kpiPositive: TextView
    private lateinit var kpiNeutral: TextView
    private lateinit var kpiNegative: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_ai_report, container, false)

        MobileAds.initialize(requireContext())
        adView = v.findViewById(R.id.adView_ai)
        adView.loadAd(AdRequest.Builder().build())

        keywordsText     = v.findViewById(R.id.text_keywords)
        aiComment        = v.findViewById(R.id.text_ai_comment)
        analysisScore    = v.findViewById(R.id.analysis_score)
        emptyIconLayout  = v.findViewById(R.id.empty_icon_layout)
        reportCard       = v.findViewById(R.id.report_card)
        barChart         = v.findViewById(R.id.emotion_bar_chart)
        weekLabel        = v.findViewById(R.id.week_label)
        kpiPositive      = v.findViewById(R.id.kpi_positive)
        kpiNeutral       = v.findViewById(R.id.kpi_neutral)
        kpiNegative      = v.findViewById(R.id.kpi_negative)

        weekLabel.text = getCurrentWeekLabel()

        // ✅ 기본은 '빈 상태'로 시작 (깜빡임 방지)
        showReport(false)

        // 인자 수신
        val feeling  = arguments?.getString("feeling")
        val keywords = arguments?.getStringArrayList("keywords")
        val analysis = arguments?.getString("analysis")
        val scoreArg = arguments?.getInt("score", -1) ?: -1
        val isSample = arguments?.getBoolean("is_sample", false) == true

        if (!feeling.isNullOrBlank() && !keywords.isNullOrEmpty() && !analysis.isNullOrBlank()) {
            bindUI(
                feeling + if (isSample) " (샘플)" else "",
                keywords,
                analysis,
                if (scoreArg >= 0) scoreArg else estimateScore(feeling)
            )
        } else {
            // 데이터 없음 → 빈 상태 유지
            // 필요시 토스트만
            // Toast.makeText(requireContext(), "이번 주 꿈을 2개 이상 기록해보세요.", Toast.LENGTH_SHORT).show()
        }

        return v
    }

    private fun bindUI(feeling: String, keywords: List<String>, analysis: String, score: Int) {
        showReport(true)

        // KPI %
        val (pos, neu, neg) = toEmotionDistribution(feeling)
        kpiPositive.text = "${(pos * 100).toInt()}%"
        kpiNeutral.text  = "${(neu * 100).toInt()}%"
        kpiNegative.text = "${(neg * 100).toInt()}%"

        // 키워드/감정
        keywordsText.setTextColor(COLOR_TEXT_DIMMED)
        keywordsText.text = "감정: $feeling   |   키워드: ${keywords.joinToString(" · ")}"

        // 분석 섹션
        analysisScore.text = "${score}점"
        aiComment.setTextColor(COLOR_TEXT_PRIMARY)
        aiComment.text = Html.fromHtml(analysis, Html.FROM_HTML_MODE_LEGACY)

        // 차트 (라벨/100% 안 잘리게)
        setupBarChart(barChart, pos * 100f, neu * 100f, neg * 100f)

        // ✅ 데이터 있을 때만 카드 등장 애니메이션
        reportCard.alpha = 0f
        reportCard.translationY = 20f
        reportCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun showReport(hasData: Boolean) {
        emptyIconLayout.isVisible = !hasData
        reportCard.isVisible      = hasData
    }

    /** 감정 텍스트로 대략 분포 추정 (퍼센트 API 미제공 시 보완) */
    private fun toEmotionDistribution(feeling: String): Triple<Float, Float, Float> {
        val posList = setOf("행복","희망","설렘","긍정","기쁨","평온","사랑","감사","안정","자신감")
        val negList = setOf("불안","슬픔","공포","우울","외로움","지침","분노","짜증","피로")
        val hasUp = feeling.contains("↑")
        val hasDown = feeling.contains("↓")
        val clean = feeling.replace("↑", "").replace("↓", "").replace("(샘플)", "").trim()
        return when {
            hasUp || clean in posList -> Triple(0.70f, 0.20f, 0.10f)
            hasDown || clean in negList -> Triple(0.10f, 0.20f, 0.70f)
            else -> Triple(0.25f, 0.50f, 0.25f)
        }
    }

    /** 점수 추정 (score 인자 없을 때만 사용) */
    private fun estimateScore(feeling: String): Int {
        val (p, n, ne) = toEmotionDistribution(feeling)
        return (p * 100 * 0.9 + ne * 100 * 0.6 + (1 - n) * 100 * 0.2).toInt().coerceIn(0, 100)
    }

    /** 차트: 바폭 축소 + 오프셋 확대 (라벨/0~100% 안 잘림) */
    private fun setupBarChart(chart: BarChart, pos: Float, neu: Float, neg: Float) {
        val labels = listOf("긍정", "중립", "부정")
        val set = BarDataSet(
            listOf(BarEntry(0f, pos), BarEntry(1f, neu), BarEntry(2f, neg)), ""
        ).apply {
            colors = listOf(NEON_POSITIVE, NEON_NEUTRAL, NEON_NEGATIVE)
            valueTextSize = 12f
            valueTextColor = VALUE_TEXT
            highLightAlpha = 0
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(e: BarEntry?): String {
                    val v = e?.y ?: 0f; return if (v < 1f) "" else "${v.toInt()}%"
                }
            }
        }

        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawGridBackground(false)
            setViewPortOffsets(64f, 48f, 44f, 64f)
            setExtraOffsets(0f, 6f, 0f, 0f)
            setFitBars(true)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            setDrawAxisLine(false)
            setDrawGridLines(true)
            enableGridDashedLine(6f, 6f, 0f)
            gridColor = GRID_LIGHT
            textColor = COLOR_TEXT_DIMMED
            textSize = 12f
            granularity = 1f
            labelCount = labels.size
            yOffset = 6f
        }

        chart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            granularity = 25f
            setDrawAxisLine(false)
            setDrawGridLines(true)
            gridColor = GRID_LIGHTER
            textColor = COLOR_TEXT_DIMMED
            textSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}%"
            }
        }
        chart.axisRight.isEnabled = false

        chart.data = BarData(set).apply { barWidth = 0.32f }
        chart.renderer = RoundedBarChartRenderer(chart, Utils.convertDpToPixel(14f))
        chart.animateY(900, Easing.EaseOutCubic)
        chart.invalidate()
    }

    // 라운드 + 네온 글로우 렌더러
    private class RoundedBarChartRenderer(
        private val chart: BarChart,
        private val radius: Float
    ) : BarChartRenderer(chart, chart.animator, chart.viewPortHandler) {

        private val r = RectF()
        private val clip = Path()
        private val radii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
            alpha = 42
        }

        override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
            val trans = chart.getTransformer(dataSet.axisDependency)
            val buffer = mBarBuffers[index].apply {
                setPhases(mAnimator.phaseX, mAnimator.phaseY)
                setDataSet(index)
                setInverted(chart.isInverted(dataSet.axisDependency))
                setBarWidth(chart.barData.barWidth)
                feed(dataSet)
            }
            trans.pointValuesToPixel(buffer.buffer)

            val single = dataSet.colors.size == 1
            val p = mRenderPaint.apply { style = Paint.Style.FILL }

            var j = 0
            while (j < buffer.size()) {
                if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])) { j += 4; continue }
                if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j])) break

                r.set(buffer.buffer[j], buffer.buffer[j + 1], buffer.buffer[j + 2], buffer.buffer[j + 3])
                clip.reset(); clip.addRoundRect(r, radii, Path.Direction.CW)
                c.save(); c.clipPath(clip)

                val base = if (single) dataSet.color else dataSet.getColor(j / 4)
                val shader = LinearGradient(0f, r.top, 0f, r.bottom,
                    lighten(base, 1.12f), darken(base, 0.66f), Shader.TileMode.CLAMP)
                p.shader = shader

                c.drawRect(r.left, r.top - 8f, r.right, r.bottom, glow)
                c.drawRect(r, p)
                c.restore()
                p.shader = null
                j += 4
            }
        }

        private fun darken(color: Int, factor: Float) =
            Color.argb(Color.alpha(color),
                (Color.red(color) * factor).toInt().coerceIn(0,255),
                (Color.green(color) * factor).toInt().coerceIn(0,255),
                (Color.blue(color) * factor).toInt().coerceIn(0,255))

        private fun lighten(color: Int, factor: Float) =
            Color.argb(Color.alpha(color),
                (Color.red(color) * factor).toInt().coerceAtMost(255),
                (Color.green(color) * factor).toInt().coerceAtMost(255),
                (Color.blue(color) * factor).toInt().coerceAtMost(255))
    }

    private fun getCurrentWeekLabel(): String {
        val c = Calendar.getInstance()
        c.firstDayOfWeek = Calendar.MONDAY
        return "WEEK ${c.get(Calendar.WEEK_OF_YEAR)} · ${c.get(Calendar.YEAR)}"
    }
}
