// file: app/src/main/java/com/example/dreamindream/AIReportFragment.kt
package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView           // ✅ 추가
import com.example.dreamindream.ads.AdManager
import com.example.dreamindream.chart.setupBarChart
import com.example.dreamindream.chart.renderPercentBars
import com.example.dreamindream.chart.richEmotionColor
import com.example.dreamindream.chart.richThemeColor
import com.github.mikephil.charting.charts.BarChart
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIReportFragment : Fragment() {

    // UI
    private lateinit var emptyIconLayout: View
    private lateinit var reportCard: View
    private lateinit var adView: AdView
    private lateinit var weekLabel: TextView
    private lateinit var btnHistory: TextView
    private lateinit var keywordsText: TextView
    private lateinit var aiComment: TextView
    private lateinit var analysisTitle: TextView
    private lateinit var chartInfoBtn: ImageButton
    private lateinit var emotionChart: BarChart
    private lateinit var themeChart: BarChart
    private lateinit var kpiPositive: TextView
    private lateinit var kpiNeutral: TextView
    private lateinit var kpiNegative: TextView
    private lateinit var btnPro: MaterialButton

    // ✅ PRO 로더
    private lateinit var proLoader: LottieAnimationView

    // State
    private var proInFlight = false
    private var proCompleted = false
    private var proNeedRefresh = false
    private var lastDreamCount = 0
    private var lastProClickAt = 0L

    private var targetWeekKey: String = WeekUtils.weekKey()
    private lateinit var prefs: SharedPreferences

    private var lastFeeling: String = ""
    private var lastKeywords: List<String> = emptyList()

    private var lastEmoLabels: List<String> = emptyList()
    private var lastEmoDist: List<Float> = emptyList()
    private var lastThemeLabels: List<String> = emptyList()
    private var lastThemeDist: List<Float> = emptyList()

    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }

    // 성능/중복방지
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var reloadScheduled = false
    private var isReloading = false

    private val PRO_PENDING_TTL_MS = 3_000L
    private val RELOAD_DEBOUNCE_MS = 350L
    private val OKHTTP_TIMEOUT = 20L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_ai_report, container, false)

        emptyIconLayout = v.findViewById(R.id.empty_icon_layout)
        reportCard = v.findViewById(R.id.report_card)
        weekLabel = v.findViewById(R.id.week_label)
        btnHistory = v.findViewById(R.id.btn_history)
        keywordsText = v.findViewById(R.id.text_keywords)
        aiComment = v.findViewById(R.id.text_ai_comment)
        analysisTitle = v.findViewById(R.id.analysis_title)
        chartInfoBtn = v.findViewById(R.id.btn_chart_info)
        emotionChart = v.findViewById(R.id.emotion_bar_chart)
        themeChart = v.findViewById(R.id.theme_bar_chart)
        kpiPositive = v.findViewById(R.id.kpi_positive)
        kpiNeutral  = v.findViewById(R.id.kpi_neutral)
        kpiNegative = v.findViewById(R.id.kpi_negative)
        btnPro = v.findViewById(R.id.btn_pro_upgrade)
        adView = v.findViewById(R.id.adView_ai)

        // ✅ Lottie 로더 바인딩
        proLoader = v.findViewById(R.id.pro_loader)

        val uidPart = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        prefs = requireContext().getSharedPreferences("weekly_report_cache_$uidPart", Context.MODE_PRIVATE)

        MobileAds.initialize(requireContext())
        adView.loadAd(AdRequest.Builder().build())
        AdManager.initialize(requireContext())
        AdManager.loadRewarded(requireContext())

        reportCard.visibility = View.VISIBLE
        reportCard.scaleX = 0.96f; reportCard.scaleY = 0.96f; reportCard.alpha = 0f
        reportCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).start()

        targetWeekKey = arguments?.getString("weekKey") ?: WeekUtils.weekKey()

        setupBarChart(emotionChart)
        setupBarChart(themeChart)

        // 캐시 즉시 표시 (체감 속도↑)
        prefillFromCache(targetWeekKey)

        if (prefs.getBoolean("pro_done_${targetWeekKey}", false)) {
            proCompleted = true
        }
        applyProButtonState()

        chartInfoBtn.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("차트 안내")
                .setMessage("• 꿈 기록 → 감정 8가지·테마 4가지\n• 2개 이상 기록 시 리포트\n• 명리 심화 분석으로 깊이 있는 해석")
                .setPositiveButton("확인", null).show()
        }

        btnHistory.setOnClickListener {
            WeeklyHistoryBottomSheet.showOnce(
                childFragmentManager, currentWeekKey = targetWeekKey,
                onPick = { scheduleReload(it) }, maxItems = 26
            )
        }
        btnPro.setOnClickListener { onProCtaClicked() }

        updateHeader()
        scheduleReload(targetWeekKey)
        return v
    }

    // ----- 로더 제어 -----
    private fun showProLoader(show: Boolean) {
        if (!this::proLoader.isInitialized) return
        proLoader.visibility = if (show) View.VISIBLE else View.GONE
        if (show) proLoader.playAnimation() else proLoader.cancelAnimation()
        reportCard.alpha = if (show) 0.6f else 1f
        btnPro.isEnabled = !show
    }

    // ----- Prefill / Header / Loading -----
    private fun prefillFromCache(weekKey: String) {
        prefs.getString("last_feeling_${weekKey}", null)?.let { cf ->
            val ck = prefs.getString("last_keywords_${weekKey}", null)
                ?.split("|")?.filter { it.isNotBlank() }
            val ca = prefs.getString("last_analysis_${weekKey}", null)

            val el = prefs.getString("last_emo_labels_${weekKey}", null)?.split("|") ?: emptyList()
            val ed = prefs.getString("last_emo_dist_${weekKey}", null)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()
            val tl = prefs.getString("last_theme_labels_${weekKey}", null)?.split("|") ?: emptyList()
            val td = prefs.getString("last_theme_dist_${weekKey}", null)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()
            lastEmoLabels = el; lastEmoDist = ed
            lastThemeLabels = tl; lastThemeDist = td

            if (!ck.isNullOrEmpty() && !ca.isNullOrBlank()) {
                showReport(true)
                keywordsText.text = "감정: $cf • 키워드: ${ck.joinToString(", ")}"
                aiComment.text = HtmlCompat.fromHtml(ca, HtmlCompat.FROM_HTML_MODE_LEGACY)

                if (el.isNotEmpty() && ed.isNotEmpty() && tl.isNotEmpty() && td.isNotEmpty()) {
                    val (pos, neu, neg) = computeKpis(el, ed)
                    kpiPositive.text = String.format("%.1f%%", pos)
                    kpiNeutral.text  = String.format("%.1f%%", neu)
                    kpiNegative.text = String.format("%.1f%%", neg)

                    renderPercentBars(emotionChart, el, ed, ::richEmotionColor)
                    renderPercentBars(themeChart,   tl, td, ::richThemeColor)
                }
            }
        }
    }

    private fun showReport(has: Boolean) {
        emptyIconLayout.isVisible = !has
        reportCard.isVisible = has
    }

    private fun updateHeader(
        sourceCount: Int? = null, rebuiltAt: Long? = null,
        tier: String? = null, proAt: Long? = null, stale: Boolean? = null
    ) {
        val titleLine = "이번 주 분석 리포트"
        val metaLine = if (sourceCount != null && rebuiltAt != null && rebuiltAt > 0L)
            "${sourceCount}건 · ${formatAgo(rebuiltAt)}" else ""
        weekLabel.text = if (metaLine.isNotBlank()) "$titleLine\n$metaLine" else titleLine

        val needRefresh = (stale == true) || (tier == "pro" && (proAt ?: 0L) < (rebuiltAt ?: 0L))
        proNeedRefresh = needRefresh
        proCompleted = (tier == "pro" && !needRefresh) || prefs.getBoolean("pro_done_${targetWeekKey}", false)
        applyProButtonState()
    }

    private fun beginLoading() { reportCard.alpha = 0.6f }
    private fun endLoading() { reportCard.alpha = 1f; applyProButtonState() }

    // ----- CTA State -----
    private fun isProPending(): Boolean {
        val until = prefs.getLong("pro_pending_until_${targetWeekKey}", 0L)
        return SystemClock.elapsedRealtime() < until
    }
    private fun setProPending(ttlMs: Long) {
        val until = SystemClock.elapsedRealtime() + ttlMs
        prefs.edit().putLong("pro_pending_until_${targetWeekKey}", until).apply()
    }
    private fun applyProButtonState() {
        val enabledBase = apiKey.isNotBlank() && !proInFlight && lastDreamCount >= 2 && !isProPending()
        when {
            proCompleted && !proNeedRefresh -> {
                btnPro.text = "심화 분석 완료"
                btnPro.isEnabled = false
                btnPro.alpha = 0.65f
                analysisTitle.text = "AI 심화분석"
            }
            proCompleted && proNeedRefresh -> {
                btnPro.text = "심화 분석 (새로고침)"
                btnPro.isEnabled = enabledBase
                btnPro.alpha = if (enabledBase) 1f else 0.65f
                analysisTitle.text = "AI 심화분석"
            }
            else -> {
                btnPro.text = "심화 분석"
                btnPro.isEnabled = enabledBase
                btnPro.alpha = if (enabledBase) 1f else 0.65f
                analysisTitle.text = "AI 분석"
            }
        }
    }

    // ----- Debounced reload -----
    private fun scheduleReload(weekKey: String) {
        targetWeekKey = weekKey
        if (reloadScheduled) return
        reloadScheduled = true
        mainHandler.postDelayed({
            reloadScheduled = false
            reloadForWeekInternal(weekKey)
        }, RELOAD_DEBOUNCE_MS)
    }

    // ----- Load / Bind -----
    private fun reloadForWeekInternal(weekKey: String) {
        if (isReloading) return
        isReloading = true
        beginLoading()

        if (prefs.getBoolean("pro_done_$weekKey", false)) {
            proCompleted = true
        }

        val userId = uid ?: run {
            fallbackFromArgsOrEmpty(); endLoading(); isReloading = false; return
        }

        FirestoreManager.countDreamEntriesForWeek(userId, weekKey) { dreamCount ->
            lastDreamCount = dreamCount
            applyProButtonState()

            if (dreamCount < 2) {
                showReport(false)
                analysisTitle.text = "AI 분석"
                endLoading(); isReloading = false
                Snackbar.make(requireView(), "이번 주 꿈을 2개 이상 기록하면 리포트를 볼 수 있어요.", Snackbar.LENGTH_SHORT).show()
                return@countDreamEntriesForWeek
            }

            showReport(true)

            FirestoreManager.loadWeeklyReportFull(
                userId, weekKey
            ) { feeling, keywords, analysis,
                emoLabels, emoDist, themeLabels, themeDist,
                sourceCount, lastRebuiltAt, tier, proAt, stale ->

                val hasBasic = feeling.isNotBlank() && keywords.isNotEmpty() && analysis.isNotBlank()
                val hasDist = emoDist.isNotEmpty() && themeDist.isNotEmpty() && (emoDist.sum() > 0f || themeDist.sum() > 0f)

                val onlyMarkRefresh = hasBasic && hasDist && (stale || (sourceCount != dreamCount))
                if (onlyMarkRefresh) {
                    lastFeeling = feeling
                    lastKeywords = keywords
                    lastEmoLabels = emoLabels
                    lastEmoDist = emoDist
                    lastThemeLabels = themeLabels
                    lastThemeDist = themeDist
                    updateHeader(sourceCount, lastRebuiltAt, tier, proAt, stale)
                    bindUI(weekKey, feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
                    endLoading(); isReloading = false
                    return@loadWeeklyReportFull
                }

                val needBuild = !hasBasic || !hasDist
                if (needBuild) {
                    FirestoreManager.aggregateDreamsForWeek(userId, weekKey) { ok ->
                        if (ok) {
                            isReloading = false
                            reloadForWeekInternal(weekKey)
                        } else {
                            fallbackFromArgsOrEmpty(); endLoading(); isReloading = false
                        }
                    }
                    return@loadWeeklyReportFull
                }

                lastFeeling = feeling
                lastKeywords = keywords
                lastEmoLabels = emoLabels
                lastEmoDist = emoDist
                lastThemeLabels = themeLabels
                lastThemeDist = themeDist

                updateHeader(sourceCount, lastRebuiltAt, tier, proAt, stale)

                reportCard.animate().alpha(0.85f).setDuration(120).withEndAction {
                    bindUI(weekKey, feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
                    reportCard.animate().alpha(1f).setDuration(120).start()
                    applyProButtonState()
                    endLoading(); isReloading = false
                }.start()
            }
        }
    }

    private fun fallbackFromArgsOrEmpty() {
        val feeling = arguments?.getString("feeling").orEmpty()
        val keywords = arguments?.getStringArrayList("keywords") ?: arrayListOf()
        val analysis = arguments?.getString("analysis").orEmpty()
        val emoLabels = listOf("긍정","평온","활력","몰입","중립","혼란","불안","우울/피로")
        val emoDist = List(emoLabels.size) { 0f }
        val themeLabels = listOf("관계","성취","변화","불안요인")
        val themeDist = List(themeLabels.size) { 0f }

        lastEmoLabels = emoLabels; lastEmoDist = emoDist
        lastThemeLabels = themeLabels; lastThemeDist = themeDist

        bindUI(targetWeekKey, feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
    }

    private fun bindUI(
        weekKey: String,
        feeling: String, keywords: List<String>, analysis: String,
        emoLabels: List<String>, emoDist: List<Float>,
        themeLabels: List<String>, themeDist: List<Float>
    ) {
        showReport(true)
        keywordsText.text = "감정: $feeling • 키워드: ${keywords.joinToString(", ")}"
        aiComment.text = HtmlCompat.fromHtml(analysis, HtmlCompat.FROM_HTML_MODE_LEGACY)

        val (pos, neu, neg) = computeKpis(emoLabels, emoDist)
        kpiPositive.text = String.format("%.1f%%", pos)
        kpiNeutral.text  = String.format("%.1f%%", neu)
        kpiNegative.text = String.format("%.1f%%", neg)

        renderPercentBars(emotionChart, emoLabels, emoDist, ::richEmotionColor)
        renderPercentBars(themeChart,   themeLabels, themeDist, ::richThemeColor)

        prefs.edit()
            .putString("last_feeling_$weekKey", feeling)
            .putString("last_keywords_$weekKey", keywords.joinToString("|"))
            .putString("last_analysis_$weekKey", analysis.take(2000))
            .putString("last_emo_labels_$weekKey", emoLabels.joinToString("|"))
            .putString("last_emo_dist_$weekKey", emoDist.joinToString(","))
            .putString("last_theme_labels_$weekKey", themeLabels.joinToString("|"))
            .putString("last_theme_dist_$weekKey", themeDist.joinToString(","))
            .apply()
        applyProButtonState()
    }

    // ----- Pro -----
    private fun onProCtaClicked() {
        val userId = uid ?: run {
            Snackbar.make(reportCard, "로그인이 필요해요.", Snackbar.LENGTH_SHORT).show(); return
        }
        if (apiKey.isBlank()) {
            Snackbar.make(reportCard, "API 키가 설정되지 않았어요. 관리자에게 문의하세요.", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (lastDreamCount < 2) {
            Snackbar.make(reportCard, "심화 분석은 이번 주 꿈 2개 이상일 때 제공됩니다.", Snackbar.LENGTH_SHORT).show()
            applyProButtonState(); return
        }
        // 리프레시 케이스: 광고 없이 바로
        if (proCompleted && proNeedRefresh) {
            showProLoader(true)                 // ✅ 바로 로더 ON
            proceedProUpgrade(userId)
            return
        }
        openProWithGate(userId)
    }

    private fun openProWithGate(userId: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProClickAt < 800) return
        lastProClickAt = now
        if (proInFlight || isProPending()) return

        val bs = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val btnCancel = v.findViewById<Button>(R.id.btnCancel)
        val btnWatch  = v.findViewById<MaterialButton>(R.id.btnWatchAd)
        val textStatus = v.findViewById<TextView>(R.id.textStatus)
        val progress = v.findViewById<ProgressBar>(R.id.progressAd)

        btnCancel.setOnClickListener { bs.dismiss() }
        btnWatch.setOnClickListener {
            proInFlight = true; setProPending(PRO_PENDING_TTL_MS); applyProButtonState()
            btnWatch.isEnabled = false; progress.visibility = View.VISIBLE
            textStatus.text = "광고 준비 중…"

            AdManager.showRewarded(
                requireActivity(),
                onRewardEarned = {
                    progress.visibility = View.GONE
                    textStatus.text = "보상 확인됨"
                    bs.dismiss()

                    // ✅ 즉시 로더 ON + 버튼 상태
                    showProLoader(true)
                    btnPro.isEnabled = false; btnPro.text = "심화 분석 중..."

                    proceedProUpgrade(userId)
                    AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    proInFlight = false; applyProButtonState()
                    btnWatch.isEnabled = true; progress.visibility = View.GONE
                    textStatus.text = "광고가 닫혔어요. 보상이 확인되지 않았습니다."
                },
                onFailed = { reason ->
                    proInFlight = false
                    Snackbar.make(reportCard, "광고 오류($reason) - 잠시 후 다시 시도해주세요.", Snackbar.LENGTH_SHORT).show()
                    applyProButtonState()
                }
            )
        }
        bs.setContentView(v); bs.show()
    }

    private fun proceedProUpgrade(userId: String) {
        if (proInFlight) return
        proInFlight = true; applyProButtonState()
        btnPro.isEnabled = false; btnPro.text = "심화 분석 중..."

        // ✅ 안전망: 로더가 꺼져 있으면 켠다
        showProLoader(true)

        FirestoreManager.collectWeekEntriesLimited(userId, targetWeekKey, limit = 4) { entries, totalCount ->
            if (totalCount < 2) {
                proInFlight = false; applyProButtonState()
                showProLoader(false) // ✅ OFF
                Snackbar.make(reportCard, "심화 분석은 이번 주 꿈 2개 이상일 때 제공됩니다.", Snackbar.LENGTH_SHORT).show()
                return@collectWeekEntriesLimited
            }

            val dreams = entries.map { it.dream }.filter { it.isNotBlank() }
            val interps = entries.map { it.interp }.filter { it.isNotBlank() }

            val (pos, neu, neg) = computeKpis(lastEmoLabels, lastEmoDist)
            val themePairs = lastThemeLabels.zip(lastThemeDist)
            fun fmt(v: Float) = String.format("%.1f", v)

            val numericBlock = buildString {
                appendLine("• 감정 분포(%) — 긍정 ${fmt(pos)} / 중립 ${fmt(neu)} / 부정 ${fmt(neg)}")
                if (themePairs.isNotEmpty()) {
                    append("• 테마 비중(%) — ")
                    append(themePairs.joinToString(" / ") { (lab, v) -> "$lab ${fmt(v)}" })
                }
            }.trim()

            val prompt = buildString {
                appendLine("다음은 사용자의 한 주 꿈 원문과 각 꿈에 대한 해몽 텍스트입니다. (최대 4개, 최신순)")
                dreams.forEachIndexed { i, s -> appendLine("[꿈 ${i+1}] $s") }
                interps.forEachIndexed { i, s -> appendLine("[해몽 ${i+1}] $s") }
                appendLine()
                appendLine("아래는 이번 주 분석에서 산출된 수치입니다. 반드시 그대로 인용하세요.")
                appendLine(numericBlock)
                appendLine()
                appendLine(
                    """
                    당신은 한국어만 사용하는 ‘명리·상징 기반 꿈해몽가’이자 스토리텔러입니다.
                    톤&스타일: 간결, 신뢰감, 대기업 리포트 느낌(섹션 헤더 명확, 불릿 최소).
                    지침:
                    - 위의 수치(%)를 <b>수치 요약</b> 섹션에서 명확히 제시하고 첫 단락 근처에서도 자연스럽게 1회 언급
                    - 감정/테마 이름은 그대로 사용(값은 위 수치 활용)
                    - 과장 금지, 현실적인 1~2주 전망/주의
                    - HTML로 출력, 문단 간 여백 유지(모바일 가독성)
                    출력(HTML):
                    <p><b>요약</b> — 한 문단으로 핵심 정리</p>
                    <p><b>수치 요약</b><br/>감정(긍정/중립/부정): ${fmt(pos)}% / ${fmt(neu)}% / ${fmt(neg)}%<br/>테마: ${
                        if (themePairs.isNotEmpty())
                            themePairs.joinToString(" / ") { (lab, v) -> "$lab ${fmt(v)}%" }
                        else "—"
                    }</p>
                    <p><b>명리 관점</b> — 사주적 상징 연결과 흐름</p>
                    <p><b>상징 해석</b> — 반복 상징을 하나의 이야기로</p>
                    <p><b>미래 전망(1~2주)</b> — 기대/주의 포인트</p>
                    <ul><li>가이드 1</li><li>가이드 2</li><li>가이드 3</li></ul>
                    <p><b>결론</b> — 한 문단 마무리</p>
                    """.trimIndent()
                )
            }

            val body = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("temperature", 0.6)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                put("max_tokens", 700)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    proInFlight = false; applyProButtonState()
                    view?.post {
                        showProLoader(false) // ✅ OFF
                        Snackbar.make(reportCard, "네트워크 오류로 심화 분석에 실패했어요.", Snackbar.LENGTH_SHORT).show()
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val content = try {
                            JSONObject(it.body?.string() ?: "")
                                .getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content").trim()
                        } catch (_: Exception) { "" }

                        view?.post {
                            if (content.isNotBlank()) {
                                aiComment.text = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
                                prefs.edit().putBoolean("pro_done_${targetWeekKey}", true).apply()
                                analysisTitle.text = "AI 심화분석"
                                proCompleted = true; proNeedRefresh = false
                                btnPro.text = "심화 분석 완료"; btnPro.isEnabled = false; btnPro.alpha = 0.65f
                                applyProButtonState()

                                FirestoreManager.saveProUpgrade(
                                    uid = userId,
                                    weekKey = targetWeekKey,
                                    feeling = lastFeeling,
                                    keywords = lastKeywords,
                                    analysis = content,
                                    model = "gpt-4o-mini"
                                ) {
                                    Snackbar.make(reportCard, "심화 분석이 적용되었어요.", Snackbar.LENGTH_SHORT).show()
                                }
                                proInFlight = false
                                showProLoader(false) // ✅ 성공 후 OFF
                            } else {
                                proInFlight = false; applyProButtonState()
                                showProLoader(false) // ✅ OFF
                                Snackbar.make(reportCard, "심화 분석 결과를 이해하지 못했어요.", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
        }
    }

    // ----- Charts / KPIs -----
    private fun computeKpis(labels: List<String>, dist: List<Float>): Triple<Float, Float, Float> {
        val pos = sumOf(labels, dist, listOf("긍정","평온","활력","몰입"))
        val neu = sumOf(labels, dist, listOf("중립"))
        val neg = sumOf(labels, dist, listOf("혼란","불안","우울/피로","우울","피로"))
        return Triple(pos, neu, neg)
    }
    private fun sumOf(labels: List<String>, dist: List<Float>, targets: List<String>): Float {
        var s = 0f
        targets.forEach { t ->
            val i = labels.indexOf(t)
            if (i in labels.indices && i in dist.indices) s += dist[i]
        }
        return s
    }

    private fun formatAgo(ts: Long): String {
        if (ts <= 0) return ""
        val d = (System.currentTimeMillis() - ts) / 1000
        return when {
            d < 60 -> "${d}s"
            d < 3600 -> "${d/60}m"
            d < 86400 -> "${d/3600}h"
            else -> "${d/86400}d"
        }
    }

    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
}
