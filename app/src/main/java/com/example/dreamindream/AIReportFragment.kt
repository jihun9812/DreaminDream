package com.example.dreamindream

import android.graphics.*
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import java.util.*

class AIReportFragment : Fragment() {

    private lateinit var keywordsText: TextView
    private lateinit var aiComment: TextView
    private lateinit var emptyIconLayout: View
    private lateinit var reportCard: View
    private lateinit var barChart: BarChart
    private lateinit var adView: AdView

    // KPI
    private lateinit var kpiPositive: TextView
    private lateinit var kpiNeutral: TextView
    private lateinit var kpiNegative: TextView

    private lateinit var pastReportGroup: View
    private lateinit var pastFeeling: TextView
    private lateinit var pastKeywords: TextView
    private lateinit var pastAnalysis: TextView
    private lateinit var deletePastBtn: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_ai_report, container, false)

        MobileAds.initialize(requireContext())
        adView = view.findViewById(R.id.adView_ai)
        adView.loadAd(AdRequest.Builder().build())

        keywordsText     = view.findViewById(R.id.text_keywords)
        aiComment        = view.findViewById(R.id.text_ai_comment)
        emptyIconLayout  = view.findViewById(R.id.empty_icon_layout)
        reportCard       = view.findViewById(R.id.report_card)
        barChart         = view.findViewById(R.id.emotion_bar_chart)

        kpiPositive = view.findViewById(R.id.kpi_positive)
        kpiNeutral  = view.findViewById(R.id.kpi_neutral)
        kpiNegative = view.findViewById(R.id.kpi_negative)

        pastReportGroup = view.findViewById(R.id.past_report_group)
        pastFeeling     = view.findViewById(R.id.past_feeling)
        pastKeywords    = view.findViewById(R.id.past_keywords)
        pastAnalysis    = view.findViewById(R.id.past_analysis)
        deletePastBtn   = view.findViewById(R.id.btn_delete_past)

        val isSample = arguments?.getBoolean("is_sample", false) == true
        val feeling  = arguments?.getString("feeling")
        val keywords = arguments?.getStringArrayList("keywords")
        val analysis = arguments?.getString("analysis")

        if (!feeling.isNullOrBlank() && !keywords.isNullOrEmpty() && !analysis.isNullOrBlank()) {
            updateUI(feeling + if (isSample) " (샘플)" else "", keywords, analysis)
        } else {
            Toast.makeText(requireContext(), "데이터가 없습니다. 이번 주 꿈을 2개 이상 기록해보세요.", Toast.LENGTH_SHORT).show()
            showEmpty()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.slide_in_left, R.anim.slide_out_right,
                            R.anim.slide_in_right, R.anim.slide_out_left
                        )
                        .replace(R.id.fragment_container, HomeFragment())
                        .disallowAddToBackStack()
                        .commit()
                }
            })

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirestoreManager.loadWeeklyReport(uid, getPreviousWeekKey()) { f, k, a ->
            if (!f.isNullOrBlank() && !k.isNullOrEmpty() && !a.isNullOrBlank()) {
                pastReportGroup.visibility = View.VISIBLE
                pastFeeling.text  = f
                pastKeywords.text = k.joinToString(", ")
                pastAnalysis.text = a
                deletePastBtn.visibility = View.VISIBLE
                deletePastBtn.setOnClickListener {
                    FirestoreManager.deleteWeeklyReport(uid, getPreviousWeekKey()) {
                        Toast.makeText(requireContext(), "지난 주 리포트가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        pastReportGroup.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun updateUI(feeling: String, keywords: List<String>, analysis: String) {
        showReport(true)

        keywordsText.text = "감정: $feeling   |   키워드: ${keywords.joinToString(" · ")}"
        aiComment.text = Html.fromHtml(
            "<b>AI 분석</b> — <font color='#092133'>$analysis</font>",
            Html.FROM_HTML_MODE_LEGACY
        )

        val (pos, neu, neg) = toEmotionDistribution(feeling)
        kpiPositive.text = "${(pos * 100).toInt()}%"
        kpiNeutral.text  = "${(neu * 100).toInt()}%"
        kpiNegative.text = "${(neg * 100).toInt()}%"

        setupEnterpriseBarChart(barChart, feeling)
    }


    private fun showEmpty() = showReport(false)

    /** 데이터 유/무에 따른 뷰 토글 */
    private fun showReport(hasData: Boolean) {
        emptyIconLayout.isVisible = !hasData
        reportCard.isVisible      = hasData
        keywordsText.isVisible    = hasData
        aiComment.isVisible       = hasData
        barChart.isVisible        = hasData
    }

    private fun setupEnterpriseBarChart(chart: BarChart, feelingText: String) {
        val (pos, neu, neg) = toEmotionDistribution(feelingText)
        val labels  = listOf("긍정", "중립", "부정")
        val entries = listOf(
            BarEntry(0f, pos * 100f),
            BarEntry(1f, neu * 100f),
            BarEntry(2f, neg * 100f),
        )

        val dataSet = BarDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#4CAF50"),
                Color.parseColor("#B0BEC5"),
                Color.parseColor("#EF5350")
            )
            setDrawValues(true)
            valueTextSize  = 11f
            valueTextColor = Color.parseColor("#2A3A45")
            highLightAlpha = 0
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry?): String {
                    val v = (barEntry?.y ?: 0f)
                    return if (v < 1f) "" else "${v.toInt()}%"
                }
            }
        }

        chart.apply {
            description.isEnabled = false
            legend.isEnabled      = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawGridBackground(false)
            setViewPortOffsets(80f, 36f, 53f, 52f)
            extraTopOffset    = 10f
            extraBottomOffset = 12f
        }

        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            setDrawAxisLine(false)
            setDrawGridLines(true)
            enableGridDashedLine(6f, 6f, 0f)
            gridColor = Color.parseColor("#14000000")
            textColor = Color.parseColor("#5A6B78")
            textSize  = 12f
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
            gridColor = Color.parseColor("#11000000")
            textColor = Color.parseColor("#6F7F8A")
            textSize  = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}%"
            }
        }
        chart.axisRight.isEnabled = false

        chart.data = BarData(dataSet).apply { barWidth = 0.42f }

        val radius = Utils.convertDpToPixel(10f)
        chart.renderer = RoundedBarChartRenderer(chart, chart.animator, chart.viewPortHandler, radius)

        chart.animateY(900, Easing.EaseOutCubic)
        chart.invalidate()
    }

    private class RoundedBarChartRenderer(
        chart: BarChart,
        animator: ChartAnimator,
        viewPortHandler: ViewPortHandler,
        private val radius: Float
    ) : BarChartRenderer(chart, animator, viewPortHandler) {

        private val roundedRect = RectF()
        private val clipPath = Path()
        private val radii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)

        override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
            val trans = mChart.getTransformer(dataSet.axisDependency)
            val phaseX = mAnimator.phaseX
            val phaseY = mAnimator.phaseY

            if (mBarBuffers.size < index + 1) return
            val buffer = mBarBuffers[index]
            buffer.setPhases(phaseX, phaseY)
            buffer.setDataSet(index)
            buffer.setInverted(mChart.isInverted(dataSet.axisDependency))
            buffer.setBarWidth(mChart.barData.barWidth)
            buffer.feed(dataSet)

            trans.pointValuesToPixel(buffer.buffer)

            val isSingleColor = dataSet.colors.size == 1
            val paint = mRenderPaint
            paint.style = Paint.Style.FILL

            var j = 0
            while (j < buffer.size()) {
                if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])) {
                    j += 4; continue
                }
                if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j])) break

                roundedRect.set(buffer.buffer[j], buffer.buffer[j + 1], buffer.buffer[j + 2], buffer.buffer[j + 3])

                clipPath.reset()
                clipPath.addRoundRect(roundedRect, radii, Path.Direction.CW)
                c.save()
                c.clipPath(clipPath)

                val baseColor = if (isSingleColor) dataSet.color else dataSet.getColor(j / 4)
                val shader = LinearGradient(
                    0f, roundedRect.top, 0f, roundedRect.bottom,
                    lighten(baseColor, 1.05f), darken(baseColor, 0.80f),
                    Shader.TileMode.CLAMP
                )
                paint.shader = shader
                c.drawRect(roundedRect, paint)
                c.restore()
                paint.shader = null
                j += 4
            }
        }

        private fun darken(color: Int, factor: Float): Int {
            val a = Color.alpha(color)
            val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
            val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
            val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
            return Color.argb(a, r, g, b)
        }
        private fun lighten(color: Int, factor: Float): Int {
            val a = Color.alpha(color)
            val r = (Color.red(color) * factor).toInt().coerceAtMost(255)
            val g = (Color.green(color) * factor).toInt().coerceAtMost(255)
            val b = (Color.blue(color) * factor).toInt().coerceAtMost(255)
            return Color.argb(a, r, g, b)
        }
    }

    private fun toEmotionDistribution(feeling: String): Triple<Float, Float, Float> {
        val posList = setOf("행복","희망","설렘","긍정","기쁨","평온","사랑","감사","안정")
        val negList = setOf("불안","슬픔","공포","우울","외로움","지침","분노","짜증","피로")
        val hasUp = feeling.contains("↑")
        val hasDown = feeling.contains("↓")
        val clean = feeling.replace("↑","").replace("↓","").replace("(샘플)","").trim()
        return when {
            hasUp || clean in posList -> Triple(0.70f, 0.20f, 0.10f)
            hasDown || clean in negList -> Triple(0.10f, 0.20f, 0.70f)
            else -> Triple(0.25f, 0.50f, 0.25f)
        }
    }

    private fun getPreviousWeekKey(): String {
        val c = Calendar.getInstance()
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        c.add(Calendar.DATE, -7)
        val year = c.get(Calendar.YEAR)
        val week = c.get(Calendar.WEEK_OF_YEAR)
        return "%04d-W%02d".format(year, week)
    }
}
