// app/src/main/java/com/example/dreamindream/AIReportFragment.kt
package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.dreamindream.ads.AdManager
import com.example.dreamindream.chart.renderPercentBars
import com.example.dreamindream.chart.richEmotionColor
import com.example.dreamindream.chart.richThemeColor
import com.example.dreamindream.chart.setupBarChart
import com.github.mikephil.charting.charts.BarChart
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
    private lateinit var proSpinner: CircularProgressIndicator

    // State
    private lateinit var prefs: SharedPreferences
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val TAG = "AIReport"

    private var targetWeekKey: String = WeekUtils.weekKey()
    private var lastDreamCount = 0
    private var lastProClickAt = 0L

    private var lastFeeling: String = ""
    private var lastKeywords: List<String> = emptyList()
    private var lastEmoLabels: List<String> = emptyList()
    private var lastEmoDist: List<Float> = emptyList()
    private var lastThemeLabels: List<String> = emptyList()
    private var lastThemeDist: List<Float> = emptyList()

    // 진행 상태
    private var adGateInProgress = false
    private var adEarned = false
    private var proInFlight = false
    private var proCompleted = false
    private var proNeedRefresh = false

    // 호출 핸들
    private var prefetchCall: Call? = null
    private var prefetchResult: String? = null
    private var prefetchWatchdog: Runnable? = null

    // Configs
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }
    private val OKHTTP_TIMEOUT = 20L
    private val PRO_WATCHDOG_MS = 30_000L
    private val RELOAD_DEBOUNCE_MS = 350L
    private val PRO_PENDING_TTL_MS = 3_000L

    // Debounce
    private var reloadScheduled = false
    private var isReloading = false

    // Network
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ------ 섹션 컬러 팔레트 & 틴팅 ------
    private val sectionColorMap = linkedMapOf(
        "주간 꿈 심화 분석" to "#FDCA60",
        "요약"           to "#90CAF9",
        "수치 요약"      to "#A7FFEB",
        "감정 패턴 해석"  to "#FFAB91",
        "상징·장면 해석"  to "#F48FB1",
        "명리 관점"      to "#B39DDB", // (가볍게) 등 꼬리표 포함 매칭
        "1~2주 전망"     to "#81C784",
        "체크리스트"      to "#FFE082",
        "오늘의 체크"     to "#80DEEA"
    )

    private fun tintSectionTitles(html: String): String {
        var s = html
        sectionColorMap.forEach { (key, color) ->
            val pattern = Regex("(<p>\\s*<b>)(${Regex.escape(key)}[^<]*)(</b>)", RegexOption.IGNORE_CASE)
            s = pattern.replace(s) { m ->
                "${m.groupValues[1]}<font color=\"$color\">${m.groupValues[2]}</font>${m.groupValues[3]}"
            }
        }
        return s
    }

    private fun ensureTinted(html: String): String {
        return if (html.contains("<font color=", ignoreCase = true)) html else tintSectionTitles(html)
    }

    private fun sanitizeModelHtml(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("^\\s*```(?:\\w+)?\\s*"), "")
        s = s.replace(Regex("\\s*```\\s*$"), "")
        s = s.replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
        return s
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_ai_report, container, false)

        emptyIconLayout = v.findViewById(R.id.empty_icon_layout)
        reportCard      = v.findViewById(R.id.report_card)
        weekLabel       = v.findViewById(R.id.week_label)
        btnHistory      = v.findViewById(R.id.btn_history)
        keywordsText    = v.findViewById(R.id.text_keywords)
        aiComment       = v.findViewById(R.id.text_ai_comment)
        analysisTitle   = v.findViewById(R.id.analysis_title)
        chartInfoBtn    = v.findViewById(R.id.btn_chart_info)
        emotionChart    = v.findViewById(R.id.emotion_bar_chart)
        themeChart      = v.findViewById(R.id.theme_bar_chart)
        kpiPositive     = v.findViewById(R.id.kpi_positive)
        kpiNeutral      = v.findViewById(R.id.kpi_neutral)
        kpiNegative     = v.findViewById(R.id.kpi_negative)
        btnPro          = v.findViewById(R.id.btn_pro_upgrade)
        adView          = v.findViewById(R.id.adView_ai)
        proSpinner      = v.findViewById(R.id.pro_spinner)
        proSpinner.isIndeterminate = true

        MobileAds.initialize(requireContext())
        adView.loadAd(AdRequest.Builder().build())
        AdManager.initialize(requireContext())
        AdManager.loadRewarded(requireContext())

        reportCard.apply {
            visibility = View.VISIBLE
            scaleX = 0.96f; scaleY = 0.96f; alpha = 0f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).start()
        }

        targetWeekKey = arguments?.getString("weekKey") ?: WeekUtils.weekKey()
        setupBarChart(emotionChart)
        setupBarChart(themeChart)

        val uidPart = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        prefs = requireContext().getSharedPreferences("weekly_report_cache_$uidPart", Context.MODE_PRIVATE)
        prefillFromCache(targetWeekKey)

        if (prefs.getBoolean("pro_done_${targetWeekKey}", false)) proCompleted = true
        applyProButtonState()

        chartInfoBtn.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("차트 안내")
                .setMessage("• 꿈 기록 → 감정 8가지·테마 4가지\n• 2개 이상 기록 시 리포트\n• 심화 분석으로 깊이 있는 해석")
                .setPositiveButton("확인", null).show()
        }
        btnHistory.setOnClickListener {
            WeeklyHistoryBottomSheet.showOnce(
                childFragmentManager,
                currentWeekKey = targetWeekKey,
                onPick = { scheduleReload(it) },
                maxItems = 26
            )
        }
        btnPro.setOnClickListener { onProCtaClicked() }

        updateHeader()
        scheduleReload(targetWeekKey)
        return v
    }

    override fun onDestroyView() {
        cancelPrefetch("destroy")
        super.onDestroyView()
    }

    // -------- Spinner --------
    private fun showProSpinner(show: Boolean, textOverride: String? = null) {
        if (!this::proSpinner.isInitialized) return
        proSpinner.isVisible = show
        reportCard.alpha = if (show) 0.92f else 1f
        if (show) btnPro.isEnabled = false
        textOverride?.let { btnPro.text = it }
    }

    // -------- Cache/Header --------
    private fun prefillFromCache(weekKey: String) {
        prefs.getString("last_feeling_${weekKey}", null)?.let { cf ->
            val ck = prefs.getString("last_keywords_${weekKey}", null)?.split("|")?.filter { it.isNotBlank() }
            val ca = prefs.getString("last_analysis_${weekKey}", null)

            val el = prefs.getString("last_emo_labels_${weekKey}", null)?.split("|") ?: emptyList()
            val ed = prefs.getString("last_emo_dist_${weekKey}", null)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()
            val tl = prefs.getString("last_theme_labels_${weekKey}", null)?.split("|") ?: emptyList()
            val td = prefs.getString("last_theme_dist_${weekKey}", null)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()

            lastFeeling = cf
            lastKeywords = ck ?: emptyList()
            lastEmoLabels = el; lastEmoDist = ed
            lastThemeLabels = tl; lastThemeDist = td

            if (!lastKeywords.isNullOrEmpty() && !ca.isNullOrBlank()) {
                showReport(true)
                keywordsText.text = "감정: $cf • 키워드: ${lastKeywords.joinToString(", ")}"
                val caColored = ensureTinted(ca!!)
                aiComment.text = HtmlCompat.fromHtml(caColored, HtmlCompat.FROM_HTML_MODE_LEGACY)

                if (el.isNotEmpty() && ed.isNotEmpty() && tl.isNotEmpty() && td.isNotEmpty()) {
                    val (p, n, m) = computeKpis(el, ed)
                    kpiPositive.text = String.format("%.1f%%", p)
                    kpiNeutral.text  = String.format("%.1f%%", n)
                    kpiNegative.text = String.format("%.1f%%", m)
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

    private fun updateHeader(sourceCount: Int? = null, rebuiltAt: Long? = null, tier: String? = null, proAt: Long? = null, stale: Boolean? = null) {
        val titleLine = "이번 주 분석 리포트"
        val metaLine = if (sourceCount != null && rebuiltAt != null && rebuiltAt > 0L) "${sourceCount}건 · ${formatAgo(rebuiltAt)}" else ""
        weekLabel.text = if (metaLine.isNotBlank()) "$titleLine\n$metaLine" else titleLine

        val needRefresh = (stale == true) || (tier == "pro" && (proAt ?: 0L) < (rebuiltAt ?: 0L))
        proNeedRefresh = needRefresh
        proCompleted = (tier == "pro" && !needRefresh) || prefs.getBoolean("pro_done_${targetWeekKey}", false)
        applyProButtonState()
    }

    private fun beginLoading() { reportCard.alpha = 0.92f }
    private fun endLoading() { reportCard.alpha = 1f; applyProButtonState() }

    // -------- CTA State --------
    private fun isProPending(): Boolean {
        val until = prefs.getLong("pro_pending_until_${targetWeekKey}", 0L)
        return SystemClock.elapsedRealtime() < until
    }
    private fun setProPending(ttlMs: Long) {
        val until = SystemClock.elapsedRealtime() + ttlMs
        prefs.edit().putLong("pro_pending_until_${targetWeekKey}", until).apply()
    }
    private fun applyProButtonState() {
        val enabledBase = apiKey.isNotBlank() && !proInFlight && lastDreamCount >= 2 && !isProPending() && !adGateInProgress
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

    // -------- Debounced Reload --------
    private fun scheduleReload(weekKey: String) {
        targetWeekKey = weekKey
        if (reloadScheduled) return
        reloadScheduled = true
        mainHandler.postDelayed({
            reloadScheduled = false
            reloadForWeekInternal(weekKey)
        }, RELOAD_DEBOUNCE_MS)
    }

    // -------- Load / Bind --------
    private fun reloadForWeekInternal(weekKey: String) {
        if (isReloading) return
        isReloading = true
        beginLoading()

        if (prefs.getBoolean("pro_done_$weekKey", false)) proCompleted = true

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
        aiComment.text = HtmlCompat.fromHtml(ensureTinted(analysis), HtmlCompat.FROM_HTML_MODE_LEGACY)

        val (pos, neu, neg) = computeKpis(emoLabels, emoDist)
        kpiPositive.text = String.format("%.1f%%", pos)
        kpiNeutral.text  = String.format("%.1f%%", neu)
        kpiNegative.text = String.format("%.1f%%", neg)

        renderPercentBars(emotionChart, emoLabels, emoDist, ::richEmotionColor)
        renderPercentBars(themeChart,   themeLabels, themeDist, ::richThemeColor)

        prefs.edit()
            .putString("last_feeling_$weekKey", feeling)
            .putString("last_keywords_$weekKey", keywords.joinToString("|"))
            .putString("last_analysis_$weekKey", analysis.take(5000))
            .putString("last_emo_labels_$weekKey", emoLabels.joinToString("|"))
            .putString("last_emo_dist_$weekKey", emoDist.joinToString(","))
            .putString("last_theme_labels_$weekKey", themeLabels.joinToString("|"))
            .putString("last_theme_dist_$weekKey", themeDist.joinToString(","))
            .apply()
        applyProButtonState()
    }

    // -------- Pro CTA --------
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

        // 새로고침이면 바로 선호출
        if (proCompleted && proNeedRefresh) {
            startPrefetchPro(userId)
            return
        }

        openProWithGate(userId)
    }

    private fun openProWithGate(userId: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProClickAt < 800) return
        lastProClickAt = now
        if (adGateInProgress || isProPending()) return

        val bs = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val btnCancel = v.findViewById<Button>(R.id.btnCancel)
        val btnWatch  = v.findViewById<MaterialButton>(R.id.btnWatchAd)
        val textStatus = v.findViewById<TextView>(R.id.textStatus)
        val progress = v.findViewById<ProgressBar>(R.id.progressAd)

        btnCancel.setOnClickListener { bs.dismiss() }
        btnWatch.setOnClickListener {
            adGateInProgress = true
            adEarned = false
            setProPending(PRO_PENDING_TTL_MS)
            applyProButtonState()

            // 즉시 로딩 표시 + 선호출
            showProSpinner(true, "분석 준비 중…")
            btnWatch.isEnabled = false
            progress.visibility = View.VISIBLE
            textStatus.text = "광고 준비 중…"

            startPrefetchPro(userId)

            AdManager.showRewarded(
                requireActivity(),
                onRewardEarned = {
                    adEarned = true
                    textStatus.text = "보상 확인됨"
                    bs.dismiss()
                    prefetchResult?.let { applyProResult(userId, it) } // 이미 완료면 즉시 표시
                    AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    adGateInProgress = false
                    cancelPrefetch("ad-closed")
                    applyProButtonState()
                    btnWatch.isEnabled = true
                    progress.visibility = View.GONE
                    textStatus.text = "광고가 닫혔어요. 보상이 확인되지 않았습니다."
                },
                onFailed = { reason ->
                    adGateInProgress = false
                    cancelPrefetch("ad-failed:$reason")
                    Snackbar.make(reportCard, "광고 오류($reason) - 잠시 후 다시 시도해주세요.", Snackbar.LENGTH_SHORT).show()
                    applyProButtonState()
                }
            )
        }
        bs.setContentView(v); bs.show()
    }

    // -------- 선호출/적용 --------
    private fun startPrefetchPro(userId: String) {
        if (prefetchCall != null || proInFlight) return
        proInFlight = true
        applyProButtonState()
        btnPro.isEnabled = false
        btnPro.text = "심화 분석 중..."
        showProSpinner(true)

        // 1) 주간 엔트리 4개 수집 후 프롬프트 구성
        FirestoreManager.collectWeekEntriesLimited(userId, targetWeekKey, limit = 4) { entries, totalCount ->
            if (!isAdded) return@collectWeekEntriesLimited

            if (totalCount < 2) {
                cancelPrefetch("not-enough-entries")
                Snackbar.make(reportCard, "심화 분석은 이번 주 꿈 2개 이상일 때 제공됩니다.", Snackbar.LENGTH_SHORT).show()
                return@collectWeekEntriesLimited
            }

            val dreams = entries.map { it.dream }.filter { it.isNotBlank() }
            val interps = entries.map { it.interp }.filter { it.isNotBlank() }
            val prompt = buildProPrompt(dreams, interps)

            // 2) OpenAI 호출
            val body = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("temperature", 0.6)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                put("max_tokens", 900)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .build()

            val watchdog = Runnable {
                if (proInFlight) {
                    cancelPrefetch("timeout")
                    Snackbar.make(reportCard, "심화 분석이 시간 초과됐어요. 네트워크 확인 후 다시 시도해주세요.", Snackbar.LENGTH_LONG).show()
                }
            }
            prefetchWatchdog = watchdog
            mainHandler.postDelayed(watchdog, PRO_WATCHDOG_MS)

            val call = httpClient.newCall(req)
            prefetchCall = call

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (prefetchCall !== call) return
                    cancelPrefetch("network-fail")
                    mainHandler.post {
                        Snackbar.make(reportCard, "네트워크 오류로 심화 분석에 실패했어요.", Snackbar.LENGTH_SHORT).show()
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    if (prefetchCall !== call) { response.close(); return }
                    val bodyText = response.body?.string().orEmpty()
                    val isOk = response.isSuccessful
                    response.close()

                    if (!isOk) {
                        val errMsg = try {
                            JSONObject(bodyText).optJSONObject("error")?.optString("message").orEmpty()
                        } catch (_: Exception) { "" }
                        cancelPrefetch("http-${response.code}")
                        mainHandler.post {
                            Snackbar.make(reportCard, "서버 응답 오류(${response.code}): ${errMsg.ifBlank { "잠시 후 다시 시도" }}", Snackbar.LENGTH_LONG).show()
                        }
                        return
                    }

                    val raw = try {
                        JSONObject(bodyText)
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content").trim()
                    } catch (_: Exception) { "" }

                    val content = tintSectionTitles(sanitizeModelHtml(raw))

                    prefetchCall = null
                    prefetchWatchdog?.let { mainHandler.removeCallbacks(it) }
                    prefetchWatchdog = null
                    prefetchResult = content

                    if (adEarned || !adGateInProgress) {
                        applyProResult(userId, content)
                    }
                }
            })
        }
    }

    private fun applyProResult(userId: String, content: String) {
        proInFlight = false
        adGateInProgress = false
        prefetchResult = null

        mainHandler.post {
            showProSpinner(false)
            if (content.isNotBlank()) {
                aiComment.text = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
                prefs.edit().putBoolean("pro_done_${targetWeekKey}", true).apply()
                analysisTitle.text = "AI 심화분석"
                proCompleted = true
                proNeedRefresh = false
                btnPro.text = "심화 분석 완료"
                btnPro.isEnabled = false
                btnPro.alpha = 0.65f
                applyProButtonState()

                try {
                    FirestoreManager.saveProUpgrade(
                        uid = userId,
                        weekKey = targetWeekKey,
                        feeling = lastFeeling,
                        keywords = lastKeywords,
                        analysis = content,
                        model = "gpt-4o-mini"
                    ) { Snackbar.make(reportCard, "심화 분석이 적용되었어요.", Snackbar.LENGTH_SHORT).show() }
                } catch (_: Throwable) { /* optional */ }
            } else {
                btnPro.isEnabled = true
                btnPro.text = "다시 시도"
                Snackbar.make(reportCard, "심화 분석 결과를 이해하지 못했어요.", Snackbar.LENGTH_SHORT).show()
                applyProButtonState()
            }
        }
    }

    private fun cancelPrefetch(reason: String) {
        Log.d(TAG, "cancelPrefetch: $reason")
        prefetchCall?.cancel()
        prefetchCall = null
        prefetchResult = null
        prefetchWatchdog?.let { mainHandler.removeCallbacks(it) }
        prefetchWatchdog = null
        proInFlight = false
        adGateInProgress = false
        showProSpinner(false)
        btnPro.isEnabled = true
        btnPro.text = "심화 분석"
    }

    // -------- Prompt Utils --------
    private fun buildProPrompt(dreams: List<String>, interps: List<String>): String {
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

        return buildString {
            appendLine("아래는 사용자의 이번 주 꿈 요약 데이터다. 숫자는 반드시 그대로 반영해라.")
            appendLine(numericBlock)
            appendLine()
            dreams.forEachIndexed { i, s -> appendLine("[꿈 ${i+1}] $s") }
            interps.forEachIndexed { i, s -> appendLine("[해몽 ${i+1}] $s") }
            appendLine()
            appendLine(
                """
                역할: 한국어만 사용하는 ‘주간 꿈 심화 분석가’.
                톤: 담백·정확하되 스토리텔링으로 부드럽게 연결.
                출력 형식: 순수 HTML fragment. 코드블록/백틱/마크다운 금지(```html, ``` 절대 X).
                각 섹션은 <p>/<ul>만 사용, 굵은 제목(<b>)으로 표기.

                섹션(이 순서):
                1) <p><b>주간 꿈 심화 분석</b></p>
                2) <p><b>요약</b> — 3문장</p>
                3) <p><b>수치 요약</b><br/>감정(긍정/중립/부정): ${fmt(pos)}% / ${fmt(neu)}% / ${fmt(neg)}%<br/>테마: ${
                    if (themePairs.isNotEmpty()) themePairs.joinToString(" / ") { (lab, v) -> "$lab ${fmt(v)}%" } else "—"
                }</p>
                4) <p><b>감정 패턴 해석</b></p>
                5) <p><b>상징·장면 해석</b></p>
                6) <p><b>명리 관점(가볍게)</b></p>
                7) <p><b>1~2주 전망</b></p>
                8) <ul><li>체크리스트 1</li><li>체크리스트 2</li><li>체크리스트 3</li></ul>
                9) <p><b>오늘의 체크</b> — 한 줄</p>
                """.trimIndent()
            )
        }
    }

    // -------- Charts / Utils --------
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
