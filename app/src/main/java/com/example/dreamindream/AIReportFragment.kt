// app/src/main/java/com/example/dreamindream/AIReportFragment.kt
package com.example.dreamindream

import android.content.SharedPreferences
import android.os.*
import android.util.Log
import android.view.*
import android.view.ViewGroup
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
import android.graphics.text.LineBreaker
import android.text.Layout
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.ColorInt
import android.annotation.SuppressLint

class AIReportFragment : Fragment() {

    companion object {
        private const val TAG = "AIReport"
        private const val OKHTTP_TIMEOUT = 20L
        private const val PRO_WATCHDOG_MS = 30_000L
        private const val RELOAD_DEBOUNCE_MS = 300L
        private const val EMPTY_DIALOG_DELAY_MS = 1_000L

        /** 리포트 생성 최소 기록 개수(“이번 주 꿈 N개 이상” 규칙) */
        private const val MIN_ENTRIES_FOR_REPORT = 2
    }

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
    private var targetWeekKey: String = WeekUtils.weekKey()
    private var lastDreamCount = 0

    private var lastFeeling = ""
    private var lastKeywords: List<String> = emptyList()
    private var lastEmoLabels: List<String> = emptyList()
    private var lastEmoDist: List<Float> = emptyList()
    private var lastThemeLabels: List<String> = emptyList()
    private var lastThemeDist: List<Float> = emptyList()

    private var adGateInProgress = false
    private var adEarned = false
    private var proInFlight = false
    private var proCompleted = false
    private var proNeedRefresh = false

    private var prefetchCall: Call? = null
    private var prefetchResult: String? = null
    private var prefetchWatchdog: Runnable? = null

    private var reloadScheduled = false
    private var isReloading = false
    private var emptyDialogScheduled = false
    private var emptyDialogShown = false
    private var emptyDialogRunnable: Runnable? = null
    private var autoSwitchedFrom: String? = null

    // network
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    // ---------- small utils ----------
    private fun View.fadeInIfHidden(dur: Long = 140L) {
        if (!isVisible) { alpha = 0f; visibility = View.VISIBLE; animate().alpha(1f).setDuration(dur).start() }
    }
    private fun View.hideGone() { animate().cancel(); visibility = View.GONE; alpha = 0f }
    private val Float.dp get() = this * resources.displayMetrics.density

    private fun TextView.setHtml(html: String) {
        text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    // ▼ 심화 섹션 제목 색상 팔레트
    private val SECTION_COLORS: Map<String, Int> by lazy {
        mapOf(
            "주간 꿈 심화 분석" to color("#FDCA60"),
            "심화 분석"         to color("#FDCA60"),
            "AI 심화분석"       to color("#FDCA60"),

            "요약"             to color("#90CAF9"),
            "수치 요약"         to color("#A7FFEB"),
            "감정 패턴 해석"     to color("#FFAB91"),
            "상징·장면 해석"     to color("#F48FB1"),
            "명리 관점"         to color("#B39DDB"),
            "1~2주 전망"        to color("#81C784"),
            "체크리스트"         to color("#FFE082"),

            "감정 분포(%)"       to color("#FFE082"),
            "감정 분포"          to color("#FFE082"),
            "테마 비중(%)"       to color("#FFE082"),
            "테마 비중"          to color("#FFE082"),

            "핵심 요약"         to color("#90CAF9"),
            "인사이트"          to color("#F48FB1")
        )
    }

    @ColorInt private fun color(hex: String): Int = android.graphics.Color.parseColor(hex)

    private fun compactTypography(raw: String): String = raw
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .replace(Regex("^\\s*•\\s+", RegexOption.MULTILINE), "• ")
        .replace(Regex("\\s*\\n\\s*•\\s*", RegexOption.MULTILINE), "\n• ")
        .replace(Regex("\\s+([:：])"), "$1")
        .trim()

    private fun buildRichAnalysis(htmlOrMd: String): CharSequence {
        val sanitized = sanitizeModelHtml(htmlOrMd)
        val compact = compactTypography(sanitized)
        val spanned = HtmlCompat.fromHtml(compact, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val sb = SpannableStringBuilder(spanned)
        val plain = sb.toString()

        SECTION_COLORS.forEach { (key, col) ->
            val k = Regex.escape(key)

            val rLine = Regex("(?m)(^|•\\s|\\d+\\.\\s*)($k)(?=\\s*(?:[:：]|\\n|$))")
            rLine.findAll(plain).forEach { m ->
                val s = m.groups[2]?.range?.first ?: return@forEach
                val e = m.groups[2]?.range?.last?.plus(1) ?: return@forEach
                sb.setSpan(ForegroundColorSpan(col), s, e, 0)
                sb.setSpan(StyleSpan(Typeface.BOLD), s, e, 0)
            }

            val rInline = Regex("(?im)(?:^|\\s)($k)(?=\\s*[:：])")
            rInline.findAll(plain).forEach { m ->
                val s = m.groups[1]?.range?.first ?: return@forEach
                val e = m.groups[1]?.range?.last?.plus(1) ?: return@forEach
                sb.setSpan(ForegroundColorSpan(col), s, e, 0)
                sb.setSpan(StyleSpan(Typeface.BOLD), s, e, 0)
            }

            val rSolo = Regex("(?m)^($k)\\s*$")
            rSolo.findAll(plain).forEach { m ->
                val s = m.groups[1]?.range?.first ?: return@forEach
                val e = m.groups[1]?.range?.last?.plus(1) ?: return@forEach
                sb.setSpan(ForegroundColorSpan(col), s, e, 0)
                sb.setSpan(StyleSpan(Typeface.BOLD), s, e, 0)
            }
        }
        return sb
    }

    private fun sanitizeModelHtml(raw: String) =
        raw.trim()
            .replace(Regex("^\\s*```(?:\\w+)?\\s*"), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .replace(Regex("&nbsp;+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")

    // ---------- lifecycle ----------
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
        proSpinner      = v.findViewById(R.id.pro_spinner)

        reportCard.visibility = View.GONE
        reportCard.alpha = 0f

        // 텍스트 가독성
        aiComment.includeFontPadding = false
        aiComment.setLineSpacing(1f.dp, 1.10f)
        if (Build.VERSION.SDK_INT >= 26) {
            if (Build.VERSION.SDK_INT >= 29) {
                aiComment.justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            } else {
                @SuppressLint("WrongConstant")
                aiComment.justificationMode = 1 // INTER_WORD
            }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            if (Build.VERSION.SDK_INT >= 29) {
                aiComment.breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
            } else {
                @SuppressLint("WrongConstant")
                aiComment.breakStrategy = 0 // SIMPLE
            }
            @SuppressLint("WrongConstant")
            aiComment.hyphenationFrequency = 1 // HYPHENATION_FREQUENCY_NORMAL
        }

        // Ads
        MobileAds.initialize(requireContext())
        adView = v.findViewById(R.id.adView_ai)
        adView.loadAd(AdRequest.Builder().build())
        AdManager.initialize(requireContext())
        AdManager.loadRewarded(requireContext())

        targetWeekKey = arguments?.getString("weekKey") ?: WeekUtils.weekKey()
        setupBarChart(emotionChart); setupBarChart(themeChart)

        val uidPart = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        prefs = requireContext().getSharedPreferences("weekly_report_cache_$uidPart", android.content.Context.MODE_PRIVATE)

        // [NEW] 로그인 상태면 선집계(워밍업)
        FirebaseAuth.getInstance().currentUser?.uid?.let { ReportWarmup.warmUpThisWeek(it) }

        // cache-first
        prefillFromCache(targetWeekKey)

        if (prefs.getBoolean("pro_done_${targetWeekKey}", false)) proCompleted = true
        applyProButtonState()

        chartInfoBtn.setOnClickListener {
            val msg = """
        • 이번 주 꿈이 ${MIN_ENTRIES_FOR_REPORT}개 이상이면 리포트가 즉시 생성됩니다.
        • 생성 후 광고 1회 시청으로 ‘심화 분석’을 이용할 수 있어요.
        • 주 중에 꿈이 총 ${MIN_ENTRIES_FOR_REPORT + 1}개 이상이 되면 리포트가 새로고침되고,
          광고 1회 시청으로 심화 분석을 다시 실행할 수 있어요.
        • 막대는 감정 8가지·테마 5가지의 주간 비중(%)을 의미합니다.
        • 주간 기준: 월~일 (지난 주 리포트는 ‘분석 기록’에서 확인)
    """.trimIndent()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("차트 안내")
                .setMessage(msg)
                .setPositiveButton("확인", null)
                .show()
        }

        btnHistory.setOnClickListener {
            val hasReport = reportCard.isShown && lastDreamCount >= MIN_ENTRIES_FOR_REPORT
            if (hasReport) {
                WeeklyHistoryBottomSheet.showOnce(
                    fm = childFragmentManager,
                    currentWeekKey = targetWeekKey,
                    onPick = { picked ->
                        cancelEmptyDialogSchedule()
                        scheduleReload(picked)
                    },
                    maxItems = 26
                )
            } else {
                WeeklyHistoryDialogFragment.showOnce(
                    fm = childFragmentManager,
                    currentWeekKey = targetWeekKey,
                    onEmptyCta = { openDreamFragment() }
                )
            }
        }

        btnPro.setOnClickListener { onProCtaClicked() }

        updateHeader()
        scheduleReload(targetWeekKey)
        return v
    }

    override fun onDestroyView() {
        cancelPrefetch("destroy")
        cancelEmptyDialogSchedule()
        super.onDestroyView()
    }

    private fun showProSpinner(show: Boolean, textOverride: String? = null) {
        proSpinner.isVisible = show
        if (reportCard.isVisible) reportCard.alpha = if (show) 0.92f else 1f
        if (show) btnPro.isEnabled = false
        textOverride?.let { btnPro.text = it }
    }

    private fun prefillFromCache(weekKey: String) {
        prefs.getString("last_feeling_${weekKey}", null)?.let { cf ->
            val ck = prefs.getString("last_keywords_${weekKey}", null)?.split("|")?.filter { it.isNotBlank() }
            val ca = prefs.getString("last_analysis_${weekKey}", null)

            val el = prefs.getString("last_emo_labels_${weekKey}", null)?.split("|") ?: emptyList()
            val ed = prefs.getString("last_emo_dist_${weekKey}", null)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()
            val tl = prefs.getString("last_theme_labels_${weekKey}", null)?.split("|") ?: emptyList()
            val td = prefs.getString("last_theme_dist_${weekKey}", null)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()

            lastFeeling = cf; lastKeywords = ck ?: emptyList()
            lastEmoLabels = el; lastEmoDist = ed
            lastThemeLabels = tl; lastThemeDist = td

            val caText = ca.orEmpty()
            if (lastKeywords.isNotEmpty() && caText.isNotBlank()) {
                bindUI(weekKey, cf, lastKeywords, caText, el, ed, tl, td)
            }
        }
    }

    // ---------- UI toggle ----------
    private fun showReport(has: Boolean) {
        if (has) {
            reportCard.fadeInIfHidden()
            emptyIconLayout.isVisible = false
            cancelEmptyDialogSchedule()
        } else {
            reportCard.hideGone()
            emptyIconLayout.isVisible = true
        }
    }

    private fun updateHeader(
        sourceCount: Int? = null, rebuiltAt: Long? = null, tier: String? = null, proAt: Long? = null, stale: Boolean? = null
    ) {
        val baseTitle = if (autoSwitchedFrom != null) "저번 주 분석 리포트" else "이번 주 분석 리포트"
        val metaLine = if (sourceCount != null && rebuiltAt != null && rebuiltAt > 0L)
            "${sourceCount}건 · ${formatAgo(rebuiltAt)}" else ""
        weekLabel.text = when {
            autoSwitchedFrom != null -> "$baseTitle\n이번 주 기록이 부족해 자동 전환됨"
            metaLine.isNotBlank()    -> "$baseTitle\n$metaLine"
            else                     -> baseTitle
        }

        val needRefresh = (stale == true) || (tier == "pro" && (proAt ?: 0L) < (rebuiltAt ?: 0L))
        proNeedRefresh = needRefresh
        proCompleted = (tier == "pro" && !needRefresh) || prefs.getBoolean("pro_done_${targetWeekKey}", false)
        applyProButtonState()
    }

    private fun beginLoading() { if (reportCard.isVisible) reportCard.alpha = 0.92f }
    private fun endLoading()   { if (reportCard.isVisible) reportCard.alpha = 1f; applyProButtonState() }

    private fun isProPending(): Boolean {
        val until = prefs.getLong("pro_pending_until_${targetWeekKey}", 0L)
        return SystemClock.elapsedRealtime() < until
    }
    private fun setProPending(ttlMs: Long) {
        val until = SystemClock.elapsedRealtime() + ttlMs
        prefs.edit().putLong("pro_pending_until_${targetWeekKey}", until).apply()
    }
    private fun applyProButtonState() {
        val enabledBase = BuildConfig.OPENAI_API_KEY.isNotBlank() &&
                !proInFlight &&
                lastDreamCount >= MIN_ENTRIES_FOR_REPORT &&
                !isProPending() &&
                !adGateInProgress

        when {
            proCompleted && !proNeedRefresh -> {
                btnPro.text = "심화 분석 완료"
                btnPro.isEnabled = false; btnPro.alpha = 0.65f
                analysisTitle.text = "AI 심화분석"
            }
            proCompleted && proNeedRefresh -> {
                btnPro.text = "심화 분석 (새로고침)"
                btnPro.isEnabled = enabledBase; btnPro.alpha = if (enabledBase) 1f else 0.65f
                analysisTitle.text = "AI 심화분석"
            }
            else -> {
                btnPro.text = "심화 분석"
                btnPro.isEnabled = enabledBase; btnPro.alpha = if (enabledBase) 1f else 0.65f
                analysisTitle.text = "AI 분석"
            }
        }
    }

    // ---------- debounced reload ----------
    private fun scheduleReload(weekKey: String) {
        targetWeekKey = weekKey
        autoSwitchedFrom = null
        if (reloadScheduled) return
        reloadScheduled = true
        mainHandler.postDelayed({
            reloadScheduled = false
            reloadForWeekInternal(weekKey)
        }, RELOAD_DEBOUNCE_MS)
    }

    // ---------- load/bind ----------
    private fun reloadForWeekInternal(weekKey: String) {
        if (isReloading) return
        isReloading = true
        beginLoading()
        cancelEmptyDialogSchedule()

        if (prefs.getBoolean("pro_done_$weekKey", false)) proCompleted = true

        val userId = uid ?: run {
            fallbackFromArgsOrEmpty(); endLoading(); isReloading = false; return
        }

        FirestoreManager.countDreamEntriesForWeek(userId, weekKey) { dreamCount ->
            lastDreamCount = dreamCount; applyProButtonState()

            if (dreamCount < MIN_ENTRIES_FOR_REPORT) {
                showReport(false)
                scheduleEmptyDialog()
                endLoading(); isReloading = false
                return@countDreamEntriesForWeek
            }

            FirestoreManager.loadWeeklyReportFull(
                userId, weekKey
            ) { feeling, keywords, analysis,
                emoLabels, emoDist, themeLabels, themeDist,
                sourceCount, lastRebuiltAt, tier, proAt, stale ->

                val hasBasic = feeling.isNotBlank() && keywords.isNotEmpty() && analysis.isNotBlank()
                val hasDist  = emoDist.isNotEmpty() && themeDist.isNotEmpty() && (emoDist.sum() > 0f || themeDist.sum() > 0f)

                val onlyMarkRefresh = hasBasic && hasDist && (stale || (sourceCount != dreamCount))
                if (onlyMarkRefresh) {
                    lastFeeling = feeling; lastKeywords = keywords
                    lastEmoLabels = emoLabels; lastEmoDist = emoDist
                    lastThemeLabels = themeLabels; lastThemeDist = themeDist
                    updateHeader(sourceCount, lastRebuiltAt, tier, proAt, stale)
                    bindUI(weekKey, feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
                    endLoading(); isReloading = false; return@loadWeeklyReportFull
                }

                val needBuild = !hasBasic || !hasDist
                if (needBuild) {
                    FirestoreManager.aggregateDreamsForWeek(userId, weekKey) { ok ->
                        if (ok) { isReloading = false; reloadForWeekInternal(weekKey) }
                        else { fallbackFromArgsOrEmpty(); endLoading(); isReloading = false }
                    }
                    return@loadWeeklyReportFull
                }

                lastFeeling = feeling; lastKeywords = keywords
                lastEmoLabels = emoLabels; lastEmoDist = emoDist
                lastThemeLabels = themeLabels; lastThemeDist = themeDist

                updateHeader(sourceCount, lastRebuiltAt, tier, proAt, stale)
                bindUI(weekKey, feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
                endLoading(); isReloading = false
            }
        }
    }

    // ---------- empty dialog auto-open ----------
    private fun scheduleEmptyDialog() {
        if (emptyDialogShown || emptyDialogScheduled) return
        emptyDialogScheduled = true
        emptyDialogRunnable = Runnable {
            if (!isAdded) return@Runnable
            emptyDialogScheduled = false
            emptyDialogShown = true
            WeeklyHistoryDialogFragment.showOnce(
                fm = childFragmentManager,
                currentWeekKey = targetWeekKey,
                onEmptyCta = { openDreamFragment() }
            )
        }
        mainHandler.postDelayed(emptyDialogRunnable!!, EMPTY_DIALOG_DELAY_MS)
    }
    private fun cancelEmptyDialogSchedule() {
        emptyDialogRunnable?.let { mainHandler.removeCallbacks(it) }
        emptyDialogRunnable = null
        emptyDialogScheduled = false
    }

    // ---------- fallback ----------
    private fun fallbackFromArgsOrEmpty() {
        val feeling   = arguments?.getString("feeling").orEmpty()
        val keywords  = arguments?.getStringArrayList("keywords") ?: arrayListOf()
        val analysis  = arguments?.getString("analysis").orEmpty()
        val emoLabels = listOf("긍정","평온","활력","몰입","중립","혼란","불안","우울/피로")
        val emoDist   = List(emoLabels.size) { 0f }
        val themeLabels = listOf("관계","성취","변화","불안요인","자기성장")
        val themeDist   = List(themeLabels.size) { 0f }

        lastEmoLabels = emoLabels; lastEmoDist = emoDist
        lastThemeLabels = themeLabels; lastThemeDist = themeDist

        bindUI(targetWeekKey, feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
    }

    // ---------- bind ----------
    private fun bindUI(
        weekKey: String,
        feeling: String, keywords: List<String>, analysis: String,
        emoLabels: List<String>, emoDist: List<Float>,
        themeLabels: List<String>, themeDist: List<Float>
    ) {
        showReport(true)

        keywordsText.text = "감정: $feeling • 키워드: ${keywords.joinToString(", ")}"

        // ▼ 간격 압축 + 컬러링 적용
        aiComment.text = buildRichAnalysis(analysis)

        val (pos, neu, neg) = computeKpis(emoLabels, emoDist)
        kpiPositive.text = String.format("%.1f%%", pos)
        kpiNeutral.text  = String.format("%.1f%%", neu)
        kpiNegative.text = String.format("%.1f%%", neg)

        renderPercentBars(emotionChart, emoLabels, emoDist, ::richEmotionColor)
        val (tL, tD) = ensureTopNThemes(themeLabels, themeDist, 5)
        renderPercentBars(themeChart, tL, tD, ::richThemeColor)

        prefs.edit()
            .putString("last_feeling_$weekKey", feeling)
            .putString("last_keywords_$weekKey", keywords.joinToString("|"))
            .putString("last_analysis_$weekKey", analysis.take(5000))
            .putString("last_emo_labels_$weekKey", emoLabels.joinToString("|"))
            .putString("last_emo_dist_$weekKey", emoDist.joinToString(","))
            .putString("last_theme_labels_$weekKey", tL.joinToString("|"))
            .putString("last_theme_dist_$weekKey", tD.joinToString(","))
            .apply()
        applyProButtonState()
    }

    // ---------- pro CTA / prefetch ----------
    private fun onProCtaClicked() {
        val userId = uid ?: run {
            Snackbar.make(reportCard, "로그인이 필요해요.", Snackbar.LENGTH_SHORT).show(); return
        }
        if (BuildConfig.OPENAI_API_KEY.isBlank()) {
            Snackbar.make(reportCard, "API 키가 설정되지 않았어요. 관리자에게 문의하세요.", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (lastDreamCount < MIN_ENTRIES_FOR_REPORT) {
            Snackbar.make(reportCard, "심화 분석은 이번 주 꿈 ${MIN_ENTRIES_FOR_REPORT}개 이상일 때 제공됩니다.", Snackbar.LENGTH_SHORT).show()
            applyProButtonState(); return
        }
        if (proCompleted && proNeedRefresh) { startPrefetchPro(userId); return }
        openProWithGate(userId)
    }

    private fun openProWithGate(userId: String) {
        val bs = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val btnCancel  = v.findViewById<Button>(R.id.btnCancel)
        val btnWatch   = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWatchAd)
        val textStatus = v.findViewById<TextView>(R.id.textStatus)
        val progress   = v.findViewById<ProgressBar>(R.id.progressAd)

        btnCancel.setOnClickListener { bs.dismiss() }
        btnWatch.setOnClickListener {
            adGateInProgress = true; adEarned = false
            setProPending(3_000L); applyProButtonState()

            showProSpinner(true, "분석 준비 중…")
            textStatus.text = "광고 준비 중…"
            progress.visibility = View.VISIBLE

            aiComment.setHtml(aiComment.text.toString() + "<br/><i style='opacity:.7'>업데이트 중…</i>")
            startPrefetchPro(userId)

            AdManager.showRewarded(
                requireActivity(),
                onRewardEarned = {
                    adEarned = true
                    textStatus.text = "보상 확인됨"; bs.dismiss()
                    prefetchResult?.let { applyProResult(userId, it) }
                    AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    adGateInProgress = false
                    if (!adEarned) {
                        cancelPrefetch("ad-closed")
                        Snackbar.make(reportCard, "광고가 닫혔어요. 보상이 확인되지 않았습니다.", Snackbar.LENGTH_SHORT).show()
                        applyProButtonState()
                    }
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

    private fun startPrefetchPro(userId: String) {
        if (prefetchCall != null || proInFlight) return
        proInFlight = true; applyProButtonState()
        btnPro.isEnabled = false; btnPro.text = "심화 분석 중…"
        showProSpinner(true)

        FirestoreManager.collectWeekEntriesLimited(userId, targetWeekKey, limit = 4) { entries, totalCount ->
            if (!isAdded) return@collectWeekEntriesLimited
            if (totalCount < MIN_ENTRIES_FOR_REPORT) {
                cancelPrefetch("not-enough-entries")
                Snackbar.make(reportCard, "심화 분석은 이번 주 꿈 ${MIN_ENTRIES_FOR_REPORT}개 이상일 때 제공됩니다.", Snackbar.LENGTH_SHORT).show()
                return@collectWeekEntriesLimited
            }

            val dreams = entries.map { it.dream }.filter { it.isNotBlank() }
            val interps = entries.map { it.interp }.filter { it.isNotBlank() }
            val prompt = buildProPrompt(dreams, interps)

            val body = JSONObject().apply {
                put("model", "gpt-4.1-mini")
                put("temperature", 0.5)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                put("max_tokens", 1300)
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
                    val code = response.code
                    response.close()

                    if (!isOk) {
                        val errMsg = try {
                            JSONObject(bodyText).optJSONObject("error")?.optString("message").orEmpty()
                        } catch (_: Exception) { "" }
                        cancelPrefetch("http-$code")
                        mainHandler.post {
                            Snackbar.make(reportCard, "서버 응답 오류($code): ${errMsg.ifBlank { "잠시 후 다시 시도" }}", Snackbar.LENGTH_LONG).show()
                        }
                        return
                    }

                    val raw = try {
                        JSONObject(bodyText)
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content").trim()
                    } catch (_: Exception) { "" }

                    prefetchCall = null
                    prefetchWatchdog?.let { mainHandler.removeCallbacks(it) }; prefetchWatchdog = null
                    prefetchResult = raw

                    if (adEarned || !adGateInProgress) applyProResult(userId, raw)
                }
            })
        }
    }

    private fun applyProResult(userId: String, contentRaw: String) {
        proInFlight = false; adGateInProgress = false; prefetchResult = null
        mainHandler.post {
            showProSpinner(false)
            val content = contentRaw.ifBlank { "" }
            if (content.isNotBlank()) {
                // UI 반영
                aiComment.text = buildRichAnalysis(content)

                // [핵심] 로컬 캐시까지 즉시 갱신 → 재시작/오프라인에서도 프로 본문 유지
                prefs.edit()
                    .putBoolean("pro_done_${targetWeekKey}", true)
                    .putString("last_analysis_${targetWeekKey}", sanitizeModelHtml(content).take(5000))
                    .putString("last_feeling_${targetWeekKey}", lastFeeling)
                    .putString("last_keywords_${targetWeekKey}", lastKeywords.joinToString("|"))
                    .apply()

                analysisTitle.text = "AI 심화분석"
                proCompleted = true; proNeedRefresh = false
                btnPro.text = "심화 분석 완료"; btnPro.isEnabled = false; btnPro.alpha = 0.65f
                applyProButtonState()

                // Firestore 저장(merge)
                try {
                    FirestoreManager.saveProUpgrade(
                        uid = userId, weekKey = targetWeekKey,
                        feeling = lastFeeling, keywords = lastKeywords,
                        analysis = sanitizeModelHtml(content), model = "gpt-4.1-mini"
                    ) {
                        Snackbar.make(reportCard, "심화 분석이 적용되었어요.", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (_: Throwable) { /* ignore */ }
            } else {
                btnPro.isEnabled = true; btnPro.text = "다시 시도"
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
        prefetchWatchdog?.let { mainHandler.removeCallbacks(it) }; prefetchWatchdog = null
        proInFlight = false; adGateInProgress = false
        showProSpinner(false)
        btnPro.isEnabled = true; btnPro.text = "심화 분석"
    }

    // ---------- prompt ----------
    private fun buildProPrompt(dreams: List<String>, interps: List<String>): String {
        val (pos, neu, neg) = computeKpis(lastEmoLabels, lastEmoDist)
        val themePairs = lastThemeLabels.zip(lastThemeDist)
        fun fmt(v: Float) = String.format("%.1f", v)

        val numeric = buildString {
            appendLine("• 감정 분포(%) — 긍정 ${fmt(pos)} / 중립 ${fmt(neu)} / 부정 ${fmt(neg)}")
            if (themePairs.isNotEmpty()) {
                append("• 테마 비중(%) — ")
                append(themePairs.joinToString(" / ") { (lab, x) -> "$lab ${fmt(x)}" })
            }
        }.trim()

        return buildString {
            appendLine("아래는 사용자의 이번 주 꿈 요약 데이터다. 숫자는 반드시 그대로 반영해라.")
            appendLine(numeric); appendLine()
            dreams.forEachIndexed { i, s -> appendLine("[꿈 ${i+1}] $s") }
            interps.forEachIndexed { i, s -> appendLine("[해몽 ${i+1}] $s") }
            appendLine()
            appendLine(
                """
            역할: 한국어만 사용하는 ‘주간 꿈 심화 분석가’.
            출력: 순수 HTML fragment(<p>,<ul>), 코드블록 금지.
            금지: 의학적/심리학적 진단, 공포·불길 예언, 과격한 단정.
            톤: 불안 완화형, 실행가능한 조언 위주, 과장 금지.
            섹션(순서 고정):
            - 주간 꿈 심화 분석
            - 요약(3문장)
            - 수치 요약
            - 감정 패턴 해석
            - 상징·장면 해석
            - 명리 관점
            - 1~2주 전망  ← 반드시 포함. 최소 3문장. 
              • ‘안심 가이드(3가지)’를 불릿으로 포함(짧고 실행 가능)
              • “예언이 아닌 경향 기반 생활 가이드” 문장을 마지막에 덧붙일 것
            - 체크리스트
    """.trimIndent()
            )

        }
    }

    // ---------- utils ----------
    private fun computeKpis(labels: List<String>, dist: List<Float>): Triple<Float, Float, Float> {
        val pos = sumOf(labels, dist, listOf("긍정","평온","활력","몰입"))
        val neu = sumOf(labels, dist, listOf("중립"))
        val neg = sumOf(labels, dist, listOf("혼란","불안","우울/피로","우울","피로"))
        return Triple(pos, neu, neg)
    }
    private fun sumOf(labels: List<String>, dist: List<Float>, targets: List<String>): Float {
        var s = 0f
        targets.forEach { t -> labels.indexOf(t).takeIf { it >= 0 && it < dist.size }?.let { s += dist[it] } }
        return s
    }
    private fun ensureTopNThemes(labels: List<String>, dist: List<Float>, n: Int): Pair<List<String>, List<Float>> {
        if (labels.isEmpty() || dist.isEmpty()) return listOf("관계","성취","변화","불안요인","자기성장").take(n) to List(n) { 0f }
        val pairs = labels.zip(dist).sortedByDescending { it.second }.take(n).toMutableList()
        while (pairs.size < n) pairs += "기타" to 0f
        return pairs.map { it.first } to pairs.map { it.second }
    }
    private fun formatAgo(ts: Long): String {
        if (ts <= 0) return ""
        val d = (System.currentTimeMillis() - ts) / 1000
        return when {
            d < 60    -> "${d}s"
            d < 3600  -> "${d/60}m"
            d < 86400 -> "${d/3600}h"
            else      -> "${d/86400}d"
        }
    }

    private fun openDreamFragment() {
        val containerId = (view?.parent as? ViewGroup)?.id
            ?: resources.getIdentifier("nav_host_fragment", "id", requireContext().packageName)
                .takeIf { it != 0 }
            ?: throw IllegalStateException("Fragment container id not found")

        requireActivity().supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left,  R.anim.slide_out_right
            )
            .replace(containerId, DreamFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }
}
