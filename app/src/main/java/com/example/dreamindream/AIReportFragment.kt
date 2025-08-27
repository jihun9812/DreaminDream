// file: app/src/main/java/com/example/dreamindream/AIReportFragment.kt
package com.example.dreamindream

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class AIReportFragment : Fragment() {

    // 색상
    private val COLOR_TEXT_DIMMED  = Color.parseColor("#B6C7D3")
    private val GOLD               = Color.parseColor("#FDCA60")
    private val GRID_LIGHT         = Color.parseColor("#22FFFFFF")

    // 뷰
    private lateinit var emptyIconLayout: View
    private lateinit var reportCard: View
    private lateinit var adView: AdView
    private lateinit var weekLabel: TextView
    private lateinit var keywordsText: TextView
    private lateinit var aiComment: TextView
    private lateinit var analysisScore: TextView
    private lateinit var chartTitle: TextView
    private lateinit var chartInfoBtn: ImageButton
    private lateinit var chartCaption: TextView
    private lateinit var emotionChart: BarChart
    private lateinit var themeChart: BarChart
    private lateinit var kpiPositive: TextView
    private lateinit var kpiNeutral: TextView
    private lateinit var kpiNegative: TextView

    // 상태
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
    private lateinit var prevWeekKey: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_ai_report, container, false)

        // Ads
        MobileAds.initialize(requireContext())
        adView = v.findViewById(R.id.adView_ai)
        adView.loadAd(AdRequest.Builder().build())

        // bind
        emptyIconLayout = v.findViewById(R.id.empty_icon_layout)
        reportCard      = v.findViewById(R.id.report_card)
        weekLabel       = v.findViewById(R.id.week_label)
        keywordsText    = v.findViewById(R.id.text_keywords)
        aiComment       = v.findViewById(R.id.text_ai_comment)
        analysisScore   = v.findViewById(R.id.analysis_score)
        chartTitle      = v.findViewById(R.id.chart_title)
        chartInfoBtn    = v.findViewById(R.id.btn_chart_info)
        chartCaption    = v.findViewById(R.id.chart_caption)
        emotionChart    = v.findViewById(R.id.emotion_bar_chart)
        themeChart      = v.findViewById(R.id.theme_bar_chart)
        kpiPositive     = v.findViewById(R.id.kpi_positive)
        kpiNeutral      = v.findViewById(R.id.kpi_neutral)
        kpiNegative     = v.findViewById(R.id.kpi_negative)

        setPending()

        // ✅ 이번 주 기준 항상 "저번 주" 키로 표시
        val thisWeek = WeekUtils.weekKey()
        prevWeekKey = WeekUtils.previousWeekKey(thisWeek, 1)

        // 상단 라벨/캡션 세팅
        weekLabel.text = "저번 주 분석 리포트  ▼"
        chartTitle.text = "감정 밸런스 (세분화)"
        chartInfoBtn.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("감정/테마 분석")
                .setMessage("저번 주에 기록된 모든 꿈을 종합하여 감정 8축, 테마 4축으로 세분화해 표시합니다.")
                .setPositiveButton("확인", null).show()
        }
        // 캡션: 상대라벨 + 절대 주차
        val abs = WeekUtils.weekChipLabel(prevWeekKey)     // ex) WEEK 35 · 2025
        val rel = WeekUtils.relativeLabel(prevWeekKey, thisWeek) // "저번 주"
        chartCaption.text = "$rel · $abs"

        // 데이터 로드
        val userId = uid
        if (userId != null) {
            // 1) 먼저 저번 주 리포트 로드 시도
            FirestoreManager.loadWeeklyReport(userId, prevWeekKey) { feeling, keywords, analysis,
                                                                     emoLabels, emoDist, themeLabels, themeDist ->
                if (feeling.isBlank() || keywords.isEmpty() || analysis.isBlank()) {
                    // 2) 없으면 저번 주를 즉시 집계 → 성공 시 다시 로드
                    FirestoreManager.aggregateDreamsForWeek(userId, prevWeekKey) { ok ->
                        if (ok) {
                            FirestoreManager.loadWeeklyReport(userId, prevWeekKey) { f2, k2, a2, el2, ed2, tl2, td2 ->
                                if (f2.isNotBlank()) bindUI(prevWeekKey, f2, k2, a2, el2, ed2, tl2, td2)
                                else showReport(false)
                            }
                        } else {
                            showReport(false)
                        }
                    }
                } else {
                    bindUI(prevWeekKey, feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
                }
            }
        } else showReport(false)

        return v
    }

    private fun setPending() { emptyIconLayout.isVisible = false; reportCard.isVisible = false }
    private fun showReport(has: Boolean) { emptyIconLayout.isVisible = !has; reportCard.isVisible = has }

    private fun bindUI(
        weekKey: String,
        feeling: String,
        keywords: List<String>,
        analysis: String,
        emoLabels: List<String>,
        emoDist: List<Float>,
        themeLabels: List<String>,
        themeDist: List<Float>
    ) {
        showReport(true)

        // 헤더 설명
        weekLabel.text = "저번 주 분석 리포트  ▼"
        keywordsText.text = "감정: $feeling • 키워드: ${keywords.joinToString(", ")}"
        analysisScore.text = "-" // (원하면 종합 점수 계산해 넣을 수 있음)
        aiComment.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(analysis, Html.FROM_HTML_MODE_LEGACY)
        else Html.fromHtml(analysis)

        // KPI (긍/중/부) 계산: 감정 8축에서 그룹핑
        val (pos, neu, neg) = computeKpis(emoLabels, emoDist)
        kpiPositive.text = String.format("%.1f%%", pos)
        kpiNeutral.text  = String.format("%.1f%%", neu)
        kpiNegative.text = String.format("%.1f%%", neg)

        // 차트 렌더
        setupBarChart(emotionChart, emoDist, emoLabels)
        setupBarChart(themeChart, themeDist, themeLabels)

        // 캡션 업데이트 (안전)
        val rel = WeekUtils.relativeLabel(weekKey, WeekUtils.weekKey())
        val abs = WeekUtils.weekChipLabel(weekKey)
        chartCaption.text = "$rel · $abs"
    }

    private fun computeKpis(labels: List<String>, dist: List<Float>): Triple<Float, Float, Float> {
        // 긍정군: 긍정/평온/활력/몰입
        val pos = sumOf(labels, dist, listOf("긍정","평온","활력","몰입"))
        // 중립
        val neu = sumOf(labels, dist, listOf("중립"))
        // 부정군: 혼란/불안/우울/피로 (우울/피로 라벨은 변형 가능)
        val neg = sumOf(labels, dist, listOf("혼란","불안","우울/피로","우울","피로"))
        return Triple(pos, neu, neg)
    }

    private fun sumOf(labels: List<String>, dist: List<Float>, targets: List<String>): Float {
        var s = 0f
        targets.forEach { t ->
            val idx = labels.indexOf(t)
            if (idx in labels.indices && idx in dist.indices) s += dist[idx]
        }
        return s
    }

    private fun setupBarChart(chart: BarChart, values: List<Float>, labels: List<String>) {
        chart.clear()
        chart.setScaleEnabled(false)
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.axisLeft.apply {
            textColor = COLOR_TEXT_DIMMED
            axisMinimum = 0f; axisMaximum = 100f
            granularity = 5f
            setDrawGridLines(true); gridColor = GRID_LIGHT
        }
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            valueFormatter = IndexAxisValueFormatter(labels)
            textColor = COLOR_TEXT_DIMMED
            granularity = 1f
        }

        val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
        val set = BarDataSet(entries, "").apply {
            setDrawValues(true)
            valueTextSize = 11f
            valueTextColor = GOLD
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(e: BarEntry): String = String.format("%.1f%%", e.y)
            }
        }

        // 파스텔-네온 팔레트 반복
        val palette = listOf(
            Color.parseColor("#17D499"), Color.parseColor("#7CD1FF"),
            Color.parseColor("#96E6B3"), Color.parseColor("#BDA7FF"),
            Color.parseColor("#CFE0EA"), Color.parseColor("#FFC95C"),
            Color.parseColor("#FF8A80"), Color.parseColor("#E57373")
        )
        set.colors = List(values.size) { idx -> palette[idx % palette.size] }

        chart.data = BarData(set as IBarDataSet).apply { barWidth = 0.45f }
        chart.animateY(700, Easing.EaseInOutCubic)
        chart.invalidate()
    }
}
