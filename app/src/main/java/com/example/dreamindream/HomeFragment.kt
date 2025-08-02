package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }
    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid }

    private var lastFeeling: String? = null
    private var lastKeywords: List<String>? = null
    private var lastAnalysis: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val userId = uid ?: ""
        prefs = requireContext().getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)

        val aiMessage = view.findViewById<TextView>(R.id.ai_message)
        aiMessage.text = getString(R.string.ai_msg_loading)
        DailyMessageManager.getMessage(requireContext()) { msg ->
            activity?.runOnUiThread { aiMessage.text = msg }
        }

        val aiReportTitle = view.findViewById<TextView>(R.id.ai_report_title)
        val aiReportSummary = view.findViewById<TextView>(R.id.ai_report_summary)
        val btnAiReport = view.findViewById<MaterialButton>(R.id.btn_ai_report)

        val weekDreams = getThisWeekDreamList()

        uid?.let {
            FirestoreManager.loadWeeklyReport(it) { feeling, keywords, analysis ->
                if (feeling.isNotBlank() && keywords.isNotEmpty() && analysis.isNotBlank() && weekDreams.isNotEmpty()) {
                    aiReportTitle.text = getString(R.string.ai_report_summary)
                    aiReportSummary.text = "감정: $feeling\n키워드: ${keywords.joinToString(", ")}"
                    lastFeeling = feeling
                    lastKeywords = keywords
                    lastAnalysis = analysis
                    btnAiReport.isEnabled = true
                    btnAiReport.alpha = 1f
                } else {
                    analyzeWeeklyDreams(view, weekDreams, aiReportTitle, aiReportSummary, btnAiReport)
                }
            }
        }

        btnAiReport.setOnClickListener {
            if (btnAiReport.isEnabled) {
                val bundle = Bundle().apply {
                    putString("feeling", lastFeeling ?: "")
                    putStringArrayList("keywords", ArrayList(lastKeywords ?: emptyList()))
                    putString("analysis", lastAnalysis ?: "")
                }
                val frag = AIReportFragment().apply { arguments = bundle }
                navigateTo(frag)
            }
        }

        fun View.applyScaleClick(action: () -> Unit) {
            this.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
                action()
            }
        }

        view.findViewById<Button>(R.id.btn_dream).applyScaleClick { navigateTo(DreamFragment()) }
        view.findViewById<Button>(R.id.btn_calendar).applyScaleClick { navigateTo(CalendarFragment()) }
        view.findViewById<Button>(R.id.btn_fortune).applyScaleClick { navigateTo(FortuneFragment()) }
        view.findViewById<ImageButton>(R.id.btn_settings).applyScaleClick { navigateTo(SettingsFragment()) }

        return view
    }

    private fun navigateTo(fragment: Fragment) {
        val current = parentFragmentManager.findFragmentById(R.id.fragment_container)
        if (current?.javaClass == fragment.javaClass) return

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, fragment)
            .disallowAddToBackStack()
            .commit()
    }

    private fun analyzeWeeklyDreams(
        view: View,
        weekDreams: List<String>,
        aiReportTitle: TextView,
        aiReportSummary: TextView,
        btnAiReport: MaterialButton
    ) {
        when {
            weekDreams.isEmpty() -> {
                aiReportTitle.text = getString(R.string.ai_no_dream)
                aiReportSummary.text = getString(R.string.ai_report_guide)
                btnAiReport.isEnabled = true
                btnAiReport.alpha = 1f
            }
            weekDreams.size < 5 -> {
                aiReportTitle.text = getString(R.string.ai_not_enough_data)
                aiReportSummary.text = getString(R.string.ai_need_more)
                btnAiReport.isEnabled = true
                btnAiReport.alpha = 1f
            }
            else -> {
                aiReportTitle.text = getString(R.string.ai_loading)
                aiReportSummary.text = getString(R.string.ai_loading2)
                btnAiReport.isEnabled = true
                btnAiReport.alpha = 1f

                requestWeeklyDreamAnalysisFromGPT(weekDreams,
                    onResult = { feeling, keywords, analysis ->
                        aiReportTitle.text = getString(R.string.ai_report_summary)
                        aiReportSummary.text = getString(
                            R.string.ai_emotion_keywords_ai,
                            feeling,
                            keywords.joinToString(", "),
                            analysis
                        )
                        btnAiReport.isEnabled = true
                        btnAiReport.alpha = 1f
                        lastFeeling = feeling
                        lastKeywords = keywords
                        lastAnalysis = analysis

                        uid?.let {
                            FirestoreManager.saveWeeklyReport(it, feeling, keywords, analysis)
                        }
                    },
                    onError = {
                        aiReportTitle.text = getString(R.string.ai_report_fail)
                        aiReportSummary.text = getString(R.string.ai_retry)
                        btnAiReport.isEnabled = true
                        btnAiReport.alpha = 1f
                    }
                )
            }
        }
    }

    private fun getThisWeekDreamList(): List<String> {
        val dreams = mutableListOf<String>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val weekDates = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        for (i in 0..6) {
            weekDates.add(sdf.format(calendar.time))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        for (dateKey in weekDates) {
            val jsonArr = try {
                JSONArray(prefs.getString(dateKey, "[]"))
            } catch (_: Exception) {
                JSONArray()
            }
            for (i in 0 until jsonArr.length()) {
                val dreamObj = jsonArr.optJSONObject(i)
                dreamObj?.getString("dream")?.let { dreams.add(it) }
            }
        }
        return dreams
    }

    private fun requestWeeklyDreamAnalysisFromGPT(
        dreamList: List<String>,
        onResult: (feeling: String, keywords: List<String>, analysis: String) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        val prompt = buildString {
            appendLine("아래는 한 사용자가 최근 일주일간 기록한 꿈 내용들입니다.")
            dreamList.forEachIndexed { idx, dream -> appendLine("[꿈 기록 ${idx+1}] $dream") }
            appendLine("""
                1. 이 꿈들의 전반적인 감정(예: 불안, 행복, 긍정, 슬픔, 두려움, 짜증, 기대, 충만감, 지침 , 분노, 외로움, 평온, 기대 등등 다양하게)을 한 단어로 요약해줘.
                2. 반복적으로 등장하는 핵심 키워드(명사) 2~3개를 추출해줘.
                3. 전체 꿈을 기반으로 한 전문가처럼 심리 분석 코멘트를 만들어줘.
                출력 예시:
                감정: 행복
                키워드: 여행, 강아지, 바다
                AI 분석: 당신의 꿈은 밝은 에너지가 느껴지며, 즐거움과 여유를 반영하고 있어요.
            """.trimIndent()
            )
        }

        val requestBody = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("temperature", 0.7)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("max_tokens", 300)
        }.toString().toRequestBody("application/json".toMediaType())

        OkHttpClient().newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread { onError?.invoke(e) }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = if (response.isSuccessful) {
                    try {
                        JSONObject(response.body?.string() ?: "")
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } catch (_: Exception) {
                        ""
                    }
                } else {
                    ""
                }

                val feeling = Regex("""감정:\s*([^\n]+)""").find(responseText)?.groupValues?.get(1)?.trim() ?: "불명"
                val keywords = Regex("""키워드:\s*([^\n]+)""").find(responseText)
                    ?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
                val analysis = Regex("""AI ?분석:\s*([^\n]+)""").find(responseText)?.groupValues?.get(1)?.trim() ?: ""

                requireActivity().runOnUiThread {
                    if (feeling != "불명" && keywords.isNotEmpty() && analysis.isNotBlank()) {
                        onResult(feeling, keywords, analysis)
                    } else {
                        onError?.invoke(Exception("AI 분석 파싱 실패"))
                    }
                }
            }
        })
    }
}
