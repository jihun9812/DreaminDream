package com.example.dreamindream

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdView


class AIReportFragment : Fragment() {

    private lateinit var keywordsText: TextView
    private lateinit var aiComment: TextView
    private lateinit var emptyIconLayout: View
    private lateinit var barChart: BarChart

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_ai_report, container, false)

        // 광고 초기화 및 로드
        MobileAds.initialize(requireContext())
        view.findViewById<AdView>(R.id.adView_ai).loadAd(AdRequest.Builder().build())


        keywordsText = view.findViewById(R.id.text_keywords)
        aiComment = view.findViewById(R.id.text_ai_comment)
        emptyIconLayout = view.findViewById(R.id.empty_icon_layout)
        barChart = view.findViewById(R.id.emotion_bar_chart)
        val btnBack = view.findViewById<MaterialButton>(R.id.btn_back_home)

        val feeling = arguments?.getString("feeling")
        val keywords = arguments?.getStringArrayList("keywords")
        val analysis = arguments?.getString("analysis")

        if (!feeling.isNullOrBlank() && !keywords.isNullOrEmpty() && !analysis.isNullOrBlank()) {
            updateUI(feeling, keywords, analysis)
        } else {
            loadIfEnoughDreams()
        }

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }

    private fun loadIfEnoughDreams() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return showEmpty()

        val sdf = SimpleDateFormat("yyyy-'W'ww", Locale.KOREA)
        val weekKey = sdf.format(Date())

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .collection("dreams").document(weekKey)
            .collection("entries")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.size() >= 2) {
                    loadFromFirestore(uid)
                } else {
                    showEmpty()
                }
            }
            .addOnFailureListener {
                showEmpty()
            }
    }

    private fun loadFromFirestore(uid: String) {
        FirestoreManager.loadWeeklyReport(uid) { feeling, keywords, analysis ->
            if (!feeling.isNullOrBlank() && !keywords.isNullOrEmpty() && !analysis.isNullOrBlank()) {
                updateUI(feeling, keywords, analysis)
            } else {
                showEmpty()
            }
        }
    }

    private fun updateUI(feeling: String, keywords: List<String>, analysis: String) {
        emptyIconLayout.visibility = View.GONE
        keywordsText.visibility = View.VISIBLE
        aiComment.visibility = View.VISIBLE
        barChart.visibility = View.VISIBLE

        keywordsText.text = "감정: $feeling\n키워드: ${keywords.joinToString(", ")}"
        aiComment.text = "AI 분석 : $analysis"
        setupEmotionBarChart(barChart, feeling)
    }

    private fun showEmpty() {
        emptyIconLayout.visibility = View.VISIBLE
        keywordsText.visibility = View.GONE
        aiComment.visibility = View.GONE
        barChart.visibility = View.GONE
    }

    private fun setupEmotionBarChart(chart: BarChart, feelingText: String) {
        val label = feelingText.replace("↑", "").replace("↓", "").trim()
        val score = when {
            feelingText.contains("↑") -> 8f
            feelingText.contains("↓") -> 2f
            else -> 5f
        }

        val entry = BarEntry(0f, score)
        val dataSet = BarDataSet(listOf(entry), "감정 강도")
        dataSet.color = when (label) {
            "불안", "슬픔", "공포", "우울", "외로움", "지침", "분노" -> 0xFFE57373.toInt()
            "행복", "희망", "설렘", "긍정", "기쁨", "평온", "사랑", "감사" -> 0xFF81C784.toInt()
            else -> 0xFF64B5F6.toInt()
        }
        dataSet.valueTextSize = 13f
        dataSet.valueTextColor = 0xFF444444.toInt()

        chart.data = BarData(dataSet)
        chart.setScaleEnabled(false)
        chart.setTouchEnabled(false)
        chart.setDrawGridBackground(false)
        chart.setDrawBarShadow(false)
        chart.setFitBars(true)

        chart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 10f
            granularity = 1f
            setDrawGridLines(false)
            textColor = 0xFF888888.toInt()
            textSize = 12f
        }
        chart.axisRight.isEnabled = false

        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(listOf(label))
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(false)
            granularity = 1f
            textColor = 0xFF666666.toInt()
            textSize = 13f
        }

        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.animateY(900)
        chart.invalidate()
    }
}
