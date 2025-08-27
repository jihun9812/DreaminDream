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

    // 홈 카드 요약 캐시(실데이터만 사용)
    private var lastFeeling: String? = null
    private var lastKeywords: List<String>? = null
    private var lastAnalysis: String? = null
    private var lastScore: Int? = null

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

        // 홈 카드
        val aiReportTitle = view.findViewById<TextView>(R.id.ai_report_title)
        val aiReportSummary = view.findViewById<TextView>(R.id.ai_report_summary)
        val btnAiReport = view.findViewById<MaterialButton>(R.id.btn_ai_report)
        val aiReportCard = view.findViewById<View>(R.id.ai_report_card)

        val weekDreams = getThisWeekDreamList()
        val weekInterps = getThisWeekInterpretationList()

        // 파이어스토어에 이번 주 리포트 있으면 우선 사용, 없으면 즉시 분석
        uid?.let {
            FirestoreManager.loadWeeklyReport(it) { feeling, keywords, analysis ->
                if (feeling.isNotBlank() && keywords.isNotEmpty() && analysis.isNotBlank()) {
                    applyHomeSummary(aiReportTitle, aiReportSummary, btnAiReport, feeling, keywords, analysis, null)
                } else {
                    analyzeWeeklyDreams(view, weekDreams, weekInterps, aiReportTitle, aiReportSummary, btnAiReport)
                }
            }
        } ?: run {
            analyzeWeeklyDreams(view, weekDreams, weekInterps, aiReportTitle, aiReportSummary, btnAiReport)
        }

        // 열기
        btnAiReport.setOnClickListener { openAIReport() }
        aiReportCard.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            openAIReport()
        }

        // 네비게이션 단축
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

    /** 가장 최근 주 리포트로 화면 이동 (없으면 안내) */
    private fun openAIReport() {
        val userId = uid
        if (userId == null) {
            if (!lastFeeling.isNullOrBlank()) {
                navigateTo(AIReportFragment().apply {
                    arguments = Bundle().apply {
                        putString("feeling", lastFeeling!!)
                        putStringArrayList("keywords", ArrayList(lastKeywords ?: emptyList()))
                        putString("analysis", lastAnalysis ?: "")
                        lastScore?.let { putInt("score", it) }
                        putBoolean("is_sample", false)
                    }
                })
            } else {
                Toast.makeText(requireContext(), "이번 주 꿈을 2개 이상 기록하면 AI 리포트를 볼 수 있어요.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        FirestoreManager.getLatestWeeklyReportKey(userId) { latest ->
            if (latest != null) {
                navigateTo(AIReportFragment().apply {
                    arguments = Bundle().apply { putString("weekKey", latest) }
                })
            } else if (!lastFeeling.isNullOrBlank()) {
                navigateTo(AIReportFragment().apply {
                    arguments = Bundle().apply {
                        putString("feeling", lastFeeling!!)
                        putStringArrayList("keywords", ArrayList(lastKeywords ?: emptyList()))
                        putString("analysis", lastAnalysis ?: "")
                        lastScore?.let { putInt("score", it) }
                        putBoolean("is_sample", false)
                    }
                })
            } else {
                Toast.makeText(requireContext(), "이번 주 꿈을 2개 이상 기록하면 AI 리포트를 볼 수 있어요.", Toast.LENGTH_SHORT).show()
            }
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

    // ───────────────── 분석/요약 ─────────────────
    private fun analyzeWeeklyDreams(
        root: View,
        weekDreams: List<String>,
        weekInterps: List<String>,
        aiReportTitle: TextView,
        aiReportSummary: TextView,
        btnAiReport: MaterialButton
    ) {
        if (weekDreams.size < 2) {
            aiReportTitle.text = getString(R.string.ai_report_summary)
            aiReportSummary.text = getString(R.string.ai_report_guide)
            btnAiReport.isEnabled = true
            btnAiReport.alpha = 1f
            return
        }

        aiReportTitle.text = getString(R.string.ai_loading)
        aiReportSummary.text = getString(R.string.ai_loading2)
        btnAiReport.isEnabled = true
        btnAiReport.alpha = 1f

        requestWeeklyDreamAnalysisFromGPT(
            dreamList = weekDreams,
            interpList = weekInterps,
            onResult = { feeling, keywords, analysis, score ->
                applyHomeSummary(aiReportTitle, aiReportSummary, btnAiReport, feeling, keywords, analysis, score)
                uid?.let { FirestoreManager.saveWeeklyReport(it, feeling, keywords, analysis) }
            },
            onError = {
                aiReportTitle.text = getString(R.string.ai_report_fail)
                aiReportSummary.text = getString(R.string.ai_retry)
                btnAiReport.isEnabled = true
                btnAiReport.alpha = 1f
            }
        )
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
    }

    /** 이번 주 원문 꿈 리스트(길이 제한으로 200자 컷) */
    private fun getThisWeekDreamList(): List<String> {
        val dreams = mutableListOf<String>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val weekDates = mutableListOf<String>()
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        for (i in 0..6) {
            weekDates.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        for (dateKey in weekDates) {
            val arr = try { JSONArray(prefs.getString(dateKey, "[]")) } catch (_: Exception) { JSONArray() }
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                obj?.optString("dream")?.takeIf { it.isNotBlank() }?.let { dreams += it.take(200) }
            }
        }
        return dreams
    }

    /** 이번 주 ‘해몽/분석’ 텍스트 리스트(없으면 빈 리스트, 300자 컷) */
    private fun getThisWeekInterpretationList(): List<String> {
        val result = mutableListOf<String>()
        val keys = listOf(
            "meaning", "interpretation", "analysis", "aiMeaning", "ai_interpretation",
            "aiAnalysis", "ai_result", "result", "comment"
        )
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val weekDates = mutableListOf<String>()
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        for (i in 0..6) {
            weekDates.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        for (dateKey in weekDates) {
            val arr = try { JSONArray(prefs.getString(dateKey, "[]")) } catch (_: Exception) { JSONArray() }
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val interp = keys.firstNotNullOfOrNull { k -> obj.optString(k).takeIf { !it.isNullOrBlank() } }
                if (!interp.isNullOrBlank()) result += interp.take(300)
            }
        }
        return result
    }

    /** GPT 콜: 주간 요약 + 점수 (감정 라벨 다양 / 키워드는 해몽에서 ‘명사’만) */
    private fun requestWeeklyDreamAnalysisFromGPT(
        dreamList: List<String>,
        interpList: List<String>,
        onResult: (feeling: String, keywords: List<String>, analysis: String, score: Int?) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        // 해몽이 하나도 없으면 원문에서 폴백(지시문에 명시)
        val hasInterps = interpList.any { it.isNotBlank() }

        val prompt = buildString {
            appendLine("아래는 사용자의 최근 1주 꿈 기록과, 각 꿈에 대한 해몽/분석 텍스트입니다.")
            dreamList.forEachIndexed { idx, dream -> appendLine("[꿈 원문 ${idx + 1}] $dream") }
            if (hasInterps) {
                interpList.forEachIndexed { idx, t -> appendLine("[해몽 텍스트 ${idx + 1}] $t") }
            }
            appendLine(
                """
                ★ 출력 형식(한국어, 다른 말 금지):
                감정: <정서 한 단어 + 필요 시 ↑/↓, 예: 기쁨, 설렘, 차분, 평온, 활력, 몰입, 자신감, 안도, 호기심, 피곤, 권태, 무기력, 혼란, 답답함, 불안, 분노, 좌절, 슬픔, 외로움 등>
                키워드: <명사 2~3개, 쉼표로 구분>
                점수: <0~100 사이 정수 한 개>
                AI 분석: <2~4문장 요약 분석>

                ★ 규칙:
                - **키워드는 반드시 ‘해몽 텍스트들’에서만 추출**하고, **일반명사**로만 제시(동사/형용사/숫자/이모지 금지).
                - ‘꿈/사람/장소/이야기/상황’과 같은 포괄 명사는 제외하고, 의미가 있는 핵심 개념만.
                - 해몽 텍스트가 하나도 없을 경우에만 원문 꿈에서 추출.
                - 감정/점수/분석은 전체 흐름을 종합해 작성.
                """.trimIndent()
            )
        }

        val requestBody = JSONObject().apply {
            put("model", "gpt-4.1-mini")
            put("temperature", 0.8)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("max_tokens", 360)
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
