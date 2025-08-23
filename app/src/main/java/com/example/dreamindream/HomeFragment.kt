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
    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid }
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }

    // 카드 요약 표시용 상태
    private var lastFeeling: String? = null
    private var lastKeywords: List<String>? = null
    private var lastAnalysis: String? = null
    private var lastScore: Int? = null

    // 샘플 (디버그에서만)
    private val SAMPLE_FEELING = "긍정↑"
    private val SAMPLE_KEYWORDS = listOf("성장", "기회", "안정")
    private val SAMPLE_ANALYSIS = "이번 주 전반 에너지는 상승세입니다. 소통이 잘 풀리고 작은 실험에서 성과가 납니다. 계획을 밀고 가세요."
    private val SAMPLE_SCORE = 82
    private val SHOW_SAMPLE_IN_REPORT = BuildConfig.DEBUG
    private var showingSampleOnCard = false

    private val dailyMsgFallbacks = listOf(
        "오늘은 마음의 속도를 잠시 늦춰 보세요.",
        "완벽함보다 한 걸음이 더 중요해요.",
        "걱정은 내려놓고 할 수 있는 일부터 시작해요.",
        "오늘의 선택이 내일의 나를 만듭니다.",
        "당신의 리듬을 믿고 천천히 가도 괜찮아요."
    )

    private var weeklyAnalysisCall: Call? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val userId = uid ?: ""
        prefs = requireContext().getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)

        // 오늘의 메시지
        val aiMessage = view.findViewById<TextView>(R.id.ai_message)
        aiMessage.text = getString(R.string.ai_msg_loading)
        DailyMessageManager.getMessage(requireContext()) { msg ->
            if (!isAdded) return@getMessage
            val safeMsg = msg?.trim().takeUnless { it.isNullOrEmpty() || it.contains("불러올 수 없어요") }
                ?: dailyMsgFallbacks.random()
            aiMessage.post { aiMessage.text = safeMsg }
        }

        // 홈 카드(주간 요약)
        val aiReportTitle = view.findViewById<TextView>(R.id.ai_report_title)
        val aiReportSummary = view.findViewById<TextView>(R.id.ai_report_summary)
        val btnAiReport = view.findViewById<MaterialButton>(R.id.btn_ai_report)
        val aiReportCard = view.findViewById<View>(R.id.ai_report_card)

        val weekDreams = getThisWeekDreamList()

        // 파이어스토어에 있으면 우선 사용, 없으면 해석 요청
        uid?.let {
            FirestoreManager.loadWeeklyReport(it) { feeling, keywords, analysis ->
                if (feeling.isNotBlank() && keywords.isNotEmpty() && analysis.isNotBlank()) {
                    applyHomeSummary(aiReportTitle, aiReportSummary, btnAiReport, feeling, keywords, analysis, null)
                } else {
                    analyzeWeeklyDreams(view, weekDreams, aiReportTitle, aiReportSummary, btnAiReport)
                }
            }
        } ?: run {
            analyzeWeeklyDreams(view, weekDreams, aiReportTitle, aiReportSummary, btnAiReport)
        }

        btnAiReport.setOnClickListener { openAIReport(btnAiReport) }
        aiReportCard.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            openAIReport(btnAiReport)
        }

        fun View.applyScaleClick(action: () -> Unit) {
            setOnClickListener {
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

    override fun onDestroyView() {
        weeklyAnalysisCall?.cancel()
        weeklyAnalysisCall = null
        super.onDestroyView()
    }

    private fun openAIReport(btnAiReport: MaterialButton) {
        if (showingSampleOnCard && SHOW_SAMPLE_IN_REPORT) {
            val bundle = Bundle().apply {
                putString("feeling", SAMPLE_FEELING)
                putStringArrayList("keywords", ArrayList(SAMPLE_KEYWORDS))
                putString("analysis", SAMPLE_ANALYSIS)
                putInt("score", SAMPLE_SCORE)
                putBoolean("is_sample", true)
            }
            navigateTo(AIReportFragment().apply { arguments = bundle })
            return
        } else if (showingSampleOnCard && !SHOW_SAMPLE_IN_REPORT) {
            Toast.makeText(requireContext(), "이번 주 꿈을 2개 이상 기록하면 AI 리포트를 볼 수 있어요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!lastFeeling.isNullOrBlank()) {
            val bundle = Bundle().apply {
                putString("feeling", lastFeeling ?: "")
                putStringArrayList("keywords", ArrayList(lastKeywords ?: emptyList()))
                putString("analysis", lastAnalysis ?: "")
                lastScore?.let { putInt("score", it) }
                putBoolean("is_sample", false)
            }
            navigateTo(AIReportFragment().apply { arguments = bundle })
        } else {
            Toast.makeText(requireContext(), "이번 주 꿈을 2개 이상 기록하면 AI 리포트를 볼 수 있어요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateTo(fragment: Fragment) {
        val current = parentFragmentManager.findFragmentById(R.id.fragment_container)
        if (current?.javaClass == fragment.javaClass) return

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left,  R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, fragment)
            .disallowAddToBackStack()
            .commit()
    }

    private fun analyzeWeeklyDreams(
        root: View,
        weekDreams: List<String>,
        aiReportTitle: TextView,
        aiReportSummary: TextView,
        btnAiReport: MaterialButton
    ) {
        when {
            weekDreams.isEmpty() || weekDreams.size < 2 -> {
                if (BuildConfig.DEBUG) {
                    applyHomeSample(aiReportTitle, aiReportSummary, btnAiReport)
                } else {
                    aiReportTitle.text = getString(R.string.ai_report_summary)
                    aiReportSummary.text = getString(R.string.ai_report_guide)
                    btnAiReport.isEnabled = true; btnAiReport.alpha = 1f
                    showingSampleOnCard = false
                }
            }
            else -> {
                aiReportTitle.text = getString(R.string.ai_loading)
                aiReportSummary.text = getString(R.string.ai_loading2)
                btnAiReport.isEnabled = true; btnAiReport.alpha = 1f
                showingSampleOnCard = false

                requestWeeklyDreamAnalysisFromGPT(
                    weekDreams,
                    onResult = { feeling, keywords, analysis, score ->
                        applyHomeSummary(aiReportTitle, aiReportSummary, btnAiReport, feeling, keywords, analysis, score)
                        uid?.let { FirestoreManager.saveWeeklyReport(it, feeling, keywords, analysis) } // 점수는 저장 스킵(스키마 영향)
                    },
                    onError = {
                        aiReportTitle.text = getString(R.string.ai_report_fail)
                        aiReportSummary.text = getString(R.string.ai_retry)
                        btnAiReport.isEnabled = true; btnAiReport.alpha = 1f
                    }
                )
            }
        }
    }

    private fun applyHomeSummary(
        aiReportTitle: TextView,
        aiReportSummary: TextView,
        btnAiReport: MaterialButton,
        feeling: String,
        keywords: List<String>,
        analysis: String,
        score: Int?
    ) {
        val top3 = keywords.take(3)
        aiReportTitle.text = getString(R.string.ai_report_summary)
        aiReportSummary.text = "감정: $feeling\n키워드: ${top3.joinToString(", ")}"
        lastFeeling = feeling
        lastKeywords = top3
        lastAnalysis = analysis
        lastScore = score
        btnAiReport.isEnabled = true
        btnAiReport.alpha = 1f
        showingSampleOnCard = false
    }

    private fun applyHomeSample(aiReportTitle: TextView, aiReportSummary: TextView, btnAiReport: MaterialButton) {
        aiReportTitle.text = getString(R.string.ai_report_summary)
        aiReportSummary.text = "감정: $SAMPLE_FEELING\n키워드: ${SAMPLE_KEYWORDS.joinToString(", ")}"
        btnAiReport.isEnabled = true
        btnAiReport.alpha = 1f
        showingSampleOnCard = true
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
            val jsonArr = try { JSONArray(prefs.getString(dateKey, "[]")) } catch (_: Exception) { JSONArray() }
            for (i in 0 until jsonArr.length()) {
                val dreamObj = jsonArr.optJSONObject(i)
                dreamObj?.getString("dream")?.let { dreams.add(it) }
            }
        }
        return dreams
    }

    /** GPT 콜: 점수(0~100)까지 받아온다 */
    private fun requestWeeklyDreamAnalysisFromGPT(
        dreamList: List<String>,
        onResult: (feeling: String, keywords: List<String>, analysis: String, score: Int?) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        val prompt = buildString {
            appendLine("아래는 한 사용자가 최근 일주일간 기록한 꿈 내용들입니다.")
            dreamList.forEachIndexed { idx, dream -> appendLine("[꿈 기록 ${idx + 1}] $dream") }
            appendLine(
                """
                아래 형식 **그대로** 한국어로만 출력해. 다른 말 절대 금지.
                감정: <한 단어 또는 '긍정/중립/부정' + ↑/↓ 가능>
                키워드: <명사 2~3개, 쉼표로 구분>
                점수: <0~100 사이 정수 한 개>   # 전반 긍정성/에너지/안정감 종합 점수
                AI 분석: <2~4문장 요약 분석>
                """.trimIndent()
            )
        }

        val requestBody = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.6)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("max_tokens", 320)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        weeklyAnalysisCall?.cancel()
        weeklyAnalysisCall = OkHttpClient().newCall(request)

        weeklyAnalysisCall!!.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread { if (isAdded) onError?.invoke(e) }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val content = if (resp.isSuccessful) {
                        try {
                            JSONObject(resp.body?.string() ?: "")
                                .getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content").trim()
                        } catch (_: Exception) { "" }
                    } else ""

                    val feeling = Regex("""감정:\s*([^\n]+)""").find(content)?.groupValues?.get(1)?.trim()
                    val keywords = Regex("""키워드:\s*([^\n]+)""").find(content)
                        ?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
                    val score = Regex("""점수:\s*(\d{1,3})""").find(content)?.groupValues?.get(1)?.toIntOrNull()
                    val analysis = Regex("""AI ?분석:\s*(.*)""", RegexOption.DOT_MATCHES_ALL)
                        .find(content)?.groupValues?.get(1)?.trim().orEmpty()

                    activity?.runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        if (!feeling.isNullOrBlank() && keywords.isNotEmpty() && analysis.isNotBlank()) {
                            onResult(feeling, keywords, analysis, score)
                        } else onError?.invoke(Exception("AI 분석 파싱 실패"))
                    }
                }
            }
        })
    }
}
