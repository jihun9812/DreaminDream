package com.dreamindream.app

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.dreamindream.app.chart.renderPercentBars
import com.dreamindream.app.chart.richEmotionColor
import com.dreamindream.app.chart.richThemeColor
import com.dreamindream.app.chart.setupBarChart
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
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.graphics.text.LineBreaker

class AIReportFragment : Fragment() {

    companion object {
        private const val TAG = "AIReport"
        private const val OKHTTP_TIMEOUT = 20L
        private const val PRO_WATCHDOG_MS = 30_000L
        private const val RELOAD_DEBOUNCE_MS = 300L
        private const val EMPTY_DIALOG_DELAY_MS = 1_000L
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
    private var targetWeekKey: String = FirestoreManager.thisWeekKey()
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

    // PRO metrics from model (stable until new dreams)
    private var proHasMetrics: Boolean = false
    private var proKpiPos: Float = 0f
    private var proKpiNeu: Float = 0f
    private var proKpiNeg: Float = 0f
    private var proDreamsUsed: Int = 0

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
    private fun isUiAlive(): Boolean = isAdded && view != null
    private inline fun runOnUi(crossinline block: () -> Unit) {
        if (!isUiAlive()) return
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { if (isUiAlive()) block() }
    }

    private fun TextView.setHtml(html: String) {
        text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    // 컬러 팔레트: 로컬라이즈된 헤더 텍스트 기준
    private val SECTION_COLORS: Map<String, Int> by lazy {
        mapOf(
            getString(R.string.section_title_weekly_deep_main) to color("#FDCA60"),
            getString(R.string.section_title_deep)            to color("#FDCA60"),
            getString(R.string.section_title_ai_pro)          to color("#FDCA60"),

            getString(R.string.section_title_summary3)        to color("#90CAF9"),
            getString(R.string.section_title_quant)           to color("#A7FFEB"),
            getString(R.string.section_title_emotion_pattern) to color("#FFAB91"),
            getString(R.string.section_title_symbols)         to color("#F48FB1"),
            getString(R.string.section_title_myeongri)        to color("#B39DDB"),
            getString(R.string.section_title_outlook)         to color("#81C784"),
            getString(R.string.section_title_checklist)       to color("#FFE082"),

            getString(R.string.section_title_emotion_percent) to color("#FFE082"),
            getString(R.string.section_title_emotion)         to color("#FFE082"),
            getString(R.string.section_title_theme_percent)   to color("#FFE082"),
            getString(R.string.section_title_theme)           to color("#FFE082"),

            getString(R.string.section_title_key_summary)     to color("#90CAF9"),
            getString(R.string.section_title_insights)        to color("#F48FB1")
        )
    }

    @ColorInt private fun color(hex: String): Int = Color.parseColor(hex)

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
        btnPro = v.findViewById(R.id.btn_pro_upgrade)
        proSpinner      = v.findViewById(R.id.pro_spinner)

        // 버튼 스타일
        val r = 12f * resources.displayMetrics.density
        btnPro.isAllCaps = false
        btnPro.setTextColor(Color.BLACK)
        btnPro.backgroundTintList = null
        btnPro.rippleColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        btnPro.background = GradientDrawable().apply {
            cornerRadius = r
            colors = intArrayOf(Color.parseColor("#FFFEDCA6"), Color.parseColor("#FF8BAAFF"))
            orientation = GradientDrawable.Orientation.TL_BR
        }
        ResourcesCompat.getFont(requireContext(), R.font.pretendard_medium)?.let { tf ->
            btnPro.typeface = Typeface.create(tf, Typeface.NORMAL)
        }
        btnPro.textSize = 12f

        reportCard.visibility = View.GONE
        reportCard.alpha = 0f

        aiComment.includeFontPadding = false
        aiComment.setLineSpacing(1f.dp, 1.10f)
        if (Build.VERSION.SDK_INT >= 26) {
            if (Build.VERSION.SDK_INT >= 29) {
                aiComment.justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            } else {
                @SuppressLint("WrongConstant")
                aiComment.justificationMode = 1
            }
        }
        if (Build.VERSION.SDK_INT >= 23) {
            if (Build.VERSION.SDK_INT >= 29) {
                aiComment.breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
            } else {
                @SuppressLint("WrongConstant")
                aiComment.breakStrategy = 0
            }
            @SuppressLint("WrongConstant")
            aiComment.hyphenationFrequency = 1
        }

        // Ads
        MobileAds.initialize(requireContext())
        adView = v.findViewById(R.id.adView_ai)
        adView.loadAd(AdRequest.Builder().build())
        AdManager.initialize(requireContext())
        AdManager.loadRewarded(requireContext())

        targetWeekKey = arguments?.getString("weekKey") ?: FirestoreManager.thisWeekKey()
        setupBarChart(emotionChart); setupBarChart(themeChart)

        val uidPart = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        prefs = requireContext().getSharedPreferences("weekly_report_cache_$uidPart", android.content.Context.MODE_PRIVATE)

        prefillFromCache(targetWeekKey)

        if (prefs.getBoolean("pro_done_${targetWeekKey}", false)) proCompleted = true
        applyProButtonState()

        chartInfoBtn.setOnClickListener {
            if (!isUiAlive()) return@setOnClickListener
            val msg = getString(R.string.chart_info_message, MIN_ENTRIES_FOR_REPORT, MIN_ENTRIES_FOR_REPORT + 1, 4).trimIndent()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.chart_info_title))
                .setMessage(msg)
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }

        btnHistory.setOnClickListener {
            if (!isUiAlive()) return@setOnClickListener
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

    override fun onResume() {
        super.onResume()
        applyProButtonState()
    }

    override fun onDestroyView() {
        cancelPrefetch("destroy")
        cancelEmptyDialogSchedule()
        super.onDestroyView()
    }

    // ---------- spinner & empty dialog helpers ----------
    /** Show/Hide spinner and optionally override CTA text */
    private fun showProSpinner(show: Boolean, textOverride: String? = null) = runOnUi {
        proSpinner.isVisible = show
        if (reportCard.isVisible) reportCard.alpha = if (show) 0.92f else 1f
        textOverride?.let { btnPro.text = it }
        if (show) btnPro.isEnabled = false
    }

    private fun scheduleEmptyDialog() {
        if (!isUiAlive()) return
        if (emptyDialogShown || emptyDialogScheduled) return
        emptyDialogScheduled = true
        emptyDialogRunnable = Runnable {
            if (!isUiAlive()) return@Runnable
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

    // ---------- cache/prefill ----------
    private fun prefillFromCache(weekKey: String) {
        prefs.getString("last_feeling_${weekKey}", null)?.let { cf ->
            val ck = prefs.getString("last_keywords_${weekKey}", null)?.split("|")?.filter { it.isNotBlank() }
            val ca = prefs.getString("last_analysis_${weekKey}", null)

            val el = prefs.getString("last_emo_labels_${weekKey}", null)?.split("|") ?: emptyList()
            val ed = prefs.getString("last_emo_dist_${weekKey}", null)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()
            val tl = prefs.getString("last_theme_labels_${weekKey}", null)?.split("|") ?: emptyList()
            val td = prefs.getString("last_theme_dist_${weekKey}", null)?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: emptyList()

            // PRO KPIs (stable)
            prefs.getString("pro_kpi_${weekKey}", null)?.let { line ->
                val parts = line.split(",")
                if (parts.size >= 3) {
                    proHasMetrics = true
                    proKpiPos = parts[0].toFloatOrNull() ?: 0f
                    proKpiNeu = parts[1].toFloatOrNull() ?: 0f
                    proKpiNeg = parts[2].toFloatOrNull() ?: 0f
                }
            }

            lastFeeling = cf; lastKeywords = ck ?: emptyList()
            lastEmoLabels = el; lastEmoDist = ed
            lastThemeLabels = tl; lastThemeDist = td

            val caText = ca.orEmpty()
            if ((lastKeywords.isNotEmpty() || caText.isNotBlank()) && (el.isNotEmpty() || proHasMetrics)) {
                bindUI(weekKey, cf, lastKeywords, caText, el, ed, tl, td)
            }
        }
    }

    // ---------- UI toggle ----------
    private fun showReport(has: Boolean) = runOnUi {
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
    ) = runOnUi {
        val baseTitle = if (autoSwitchedFrom != null) getString(R.string.report_title_prev_week) else getString(R.string.report_title_this_week)
        val metaLine = if (sourceCount != null && rebuiltAt != null && rebuiltAt > 0L)
            getString(R.string.report_meta_format, sourceCount, formatAgo(rebuiltAt)) else ""

        weekLabel.text = when {
            autoSwitchedFrom != null -> baseTitle + "\n" + getString(R.string.report_switched_due_to_lack)
            metaLine.isNotBlank()    -> baseTitle + "\n" + metaLine
            else                     -> baseTitle
        }

        val needRefresh = (stale == true) || (tier == "pro" && (proAt ?: 0L) < (rebuiltAt ?: 0L))
        proNeedRefresh = needRefresh
        proCompleted = (tier == "pro" && !needRefresh) || prefs.getBoolean("pro_done_${targetWeekKey}", false)
        applyProButtonState()
    }

    private fun beginLoading() = runOnUi { if (reportCard.isVisible) reportCard.alpha = 0.92f }
    private fun endLoading()   = runOnUi { if (reportCard.isVisible) reportCard.alpha = 1f; applyProButtonState() }

    private fun isProPending(): Boolean {
        val until = prefs.getLong("pro_pending_until_${targetWeekKey}", 0L)
        return SystemClock.elapsedRealtime() < until
    }
    private fun setProPending(ttlMs: Long) {
        val until = SystemClock.elapsedRealtime() + ttlMs
        prefs.edit().putLong("pro_pending_until_${targetWeekKey}", until).apply()
    }

    private fun applyProButtonState() = runOnUi {
        val enabledBase = BuildConfig.OPENAI_API_KEY.isNotBlank() &&
                !proInFlight &&
                lastDreamCount >= MIN_ENTRIES_FOR_REPORT &&
                !isProPending() &&
                !adGateInProgress

        when {
            proCompleted && !proNeedRefresh -> {
                btnPro.text = getString(R.string.pro_completed)
                btnPro.isEnabled = false; btnPro.alpha = 0.65f
                analysisTitle.text = getString(R.string.ai_pro_title)
            }
            proCompleted && proNeedRefresh -> {
                btnPro.text = getString(R.string.pro_refresh)
                btnPro.isEnabled = enabledBase; btnPro.alpha = if (enabledBase) 1f else 0.65f
                analysisTitle.text = getString(R.string.ai_pro_title)
            }
            else -> {
                btnPro.text = getString(R.string.pro_cta)
                btnPro.isEnabled = enabledBase; btnPro.alpha = if (enabledBase) 1f else 0.65f
                analysisTitle.text = getString(R.string.ai_basic_title)
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
            if (!isUiAlive()) return@postDelayed
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
            if (!isUiAlive()) return
            fallbackFromArgsOrEmpty(); endLoading(); isReloading = false; return
        }

        FirestoreManager.countDreamEntriesForWeek(userId, weekKey) { dreamCount ->
            if (!isUiAlive()) return@countDreamEntriesForWeek
            lastDreamCount = dreamCount; applyProButtonState()

            if (dreamCount < MIN_ENTRIES_FOR_REPORT) {
                showReport(false)
                scheduleEmptyDialog()
                endLoading(); isReloading = false
                return@countDreamEntriesForWeek
            }

            FirestoreManager.loadWeeklyReportFull(
                requireContext(), userId, weekKey
            ) { feeling, keywords, analysis,
                emoLabels, emoDist, themeLabels, themeDist,
                sourceCount, lastRebuiltAt, tier, proAt, stale ->

                if (!isUiAlive()) return@loadWeeklyReportFull

                // load persisted PRO KPIs
                proHasMetrics = false
                prefs.getString("pro_kpi_${weekKey}", null)?.let { line ->
                    val p = line.split(",")
                    if (p.size >= 3) {
                        proHasMetrics = true
                        proKpiPos = p[0].toFloatOrNull() ?: 0f
                        proKpiNeu = p[1].toFloatOrNull() ?: 0f
                        proKpiNeg = p[2].toFloatOrNull() ?: 0f
                    }
                }

                val hasBasic = feeling.isNotBlank() && (analysis.isNotBlank() || proHasMetrics)
                val hasDist  = (emoDist.isNotEmpty() || proHasMetrics) && themeDist.isNotEmpty()

                if (hasBasic && hasDist && (stale || sourceCount != dreamCount)) {
                    FirestoreManager.aggregateDreamsForWeek(userId, weekKey, requireContext()) { ok ->
                        if (!isUiAlive()) return@aggregateDreamsForWeek
                        if (ok) {
                            isReloading = false
                            reloadForWeekInternal(weekKey)
                        } else {
                            lastFeeling = feeling; lastKeywords = keywords
                            lastEmoLabels = emoLabels; lastEmoDist = emoDist
                            lastThemeLabels = themeLabels; lastThemeDist = themeDist
                            updateHeader(sourceCount, lastRebuiltAt, tier, proAt, true)
                            bindUI(weekKey, feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
                            endLoading(); isReloading = false
                        }
                    }
                    return@loadWeeklyReportFull
                }

                val needBuild = !hasBasic || !hasDist
                if (needBuild) {
                    FirestoreManager.aggregateDreamsForWeek(userId, weekKey, requireContext()) { ok ->
                        if (!isUiAlive()) return@aggregateDreamsForWeek
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

    private fun fallbackFromArgsOrEmpty() {
        if (!isUiAlive()) return
        val feeling   = arguments?.getString("feeling").orEmpty()
        val keywords  = arguments?.getStringArrayList("keywords") ?: arrayListOf()
        val analysis  = arguments?.getString("analysis").orEmpty()

        val emoLabels = resources.getStringArray(R.array.emo_labels_default).toList()
        val emoDist   = List(emoLabels.size) { 0f }
        val themeLabels = resources.getStringArray(R.array.theme_labels_default).toList()
        val themeDist   = List(themeLabels.size) { 0f }

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
        if (!isUiAlive()) return
        showReport(true)

        val feelingLocalized = feeling.trim()
        run {
            val kw1 = keywords.asSequence().filter { it.isNotBlank() }.map { it.trim() }.distinct().take(1).toList()
            keywordsText.text = getString(R.string.keywords_format, feelingLocalized, kw1.joinToString(", "))
        }

        aiComment.text = buildRichAnalysis(analysis)
        applyRtlForCurrentLocale()

        // ----- KPI source priority: PRO metrics if available -----
        val (pos, neu, neg) = if (proHasMetrics) {
            Triple(proKpiPos, proKpiNeu, proKpiNeg)
        } else {
            computeKpis(emoLabels, emoDist)
        }
        kpiPositive.text = String.format(Locale.US, "%.1f%%", pos)
        kpiNeutral.text  = String.format(Locale.US, "%.1f%%", neu)
        kpiNegative.text = String.format(Locale.US, "%.1f%%", neg)

        // ----- Chart: if PRO KPIs exist, map to three buckets; else render raw -----
        renderPercentBars(emotionChart, emoLabels, emoDist, ::richEmotionColor)


        val (tL, tD) = ensureTopNThemes(themeLabels, themeDist, 5)
        renderPercentBars(themeChart, tL.map { wrapByWords(it, 9) }, tD, ::richThemeColor)

        // cache
        prefs.edit()
            .putString("last_feeling_$weekKey", feeling)
            .putString("last_keywords_$weekKey", keywords.joinToString("|"))
            .putString("last_analysis_$weekKey", sanitizeModelHtml(analysis).take(5000))
            .putString("last_emo_labels_$weekKey", emoLabels.joinToString("|"))
            .putString("last_emo_dist_$weekKey", emoDist.joinToString(","))
            .putString("last_theme_labels_$weekKey", tL.joinToString("|"))
            .putString("last_theme_dist_$weekKey", tD.joinToString(","))
            .apply()

        lastEmoLabels = emoLabels; lastEmoDist = emoDist
        lastThemeLabels = tL;  lastThemeDist = tD
        lastFeeling = feeling
        lastKeywords = keywords

        applyProButtonState()
    }

    // ---------- pro flow ----------
    private fun onProCtaClicked() {
        if (!isUiAlive()) return
        val userId = uid ?: run {
            Snackbar.make(reportCard, getString(R.string.login_required), Snackbar.LENGTH_SHORT).show()
            return
        }
        if (BuildConfig.OPENAI_API_KEY.isBlank()) {
            Snackbar.make(reportCard, getString(R.string.api_key_missing), Snackbar.LENGTH_SHORT).show()
            return
        }
        if (lastDreamCount < MIN_ENTRIES_FOR_REPORT) {
            Snackbar.make(reportCard, getString(R.string.pro_requires_min, MIN_ENTRIES_FOR_REPORT), Snackbar.LENGTH_SHORT).show()
            applyProButtonState(); return
        }
        openProWithGate(userId)
    }
    private var proStarted = false
    private fun openProWithGate(userId: String) {
        if (!isUiAlive()) return
        val bs = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val btnCancel  = v.findViewById<Button>(R.id.btnCancel)
        val btnWatch   = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWatchAd)
        val textStatus = v.findViewById<TextView>(R.id.textStatus)
        val progress   = v.findViewById<ProgressBar>(R.id.progressAd)

        btnCancel.setOnClickListener { bs.dismiss() }

        btnWatch.setOnClickListener {
            if (!isUiAlive()) return@setOnClickListener

            adGateInProgress = true
            adEarned = false
            setProPending(3_000L)
            applyProButtonState()

            // 광고 시작 상태 표시
            textStatus.text = getString(R.string.preparing_ad)
            progress.visibility = View.VISIBLE

            // ✅ 광고가 끝난 뒤에만 OpenAI 요청 시작
            AdManager.showRewarded(
                requireActivity(),
                onRewardEarned = {
                    if (!isUiAlive()) return@showRewarded
                    adEarned = true

                    // 분석 시작 UI 업데이트
                    textStatus.text = getString(R.string.preparing_analysis)
                    showProSpinner(true, getString(R.string.preparing_analysis))
                    aiComment.setHtml(
                        aiComment.text.toString() +
                                "<br/><i style='opacity:.7'>${getString(R.string.updating_ellipsis)}</i>"
                    )

                    // 이제 네트워크 호출 시작
                    startPrefetchPro(userId)

                    // 바텀시트 닫기 및 다음 광고 로드
                    bs.dismiss()
                    AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    if (!isUiAlive()) return@showRewarded
                    adGateInProgress = false
                    bs.dismiss()

                    // 광고는 끝났지만 아직 결과가 안 왔을 수 있음
                    prefetchResult?.let {
                        applyProResult(userId, it)
                        AdManager.loadRewarded(requireContext())
                    } ?: run {
                        Snackbar.make(
                            reportCard,
                            getString(R.string.preparing_analysis),
                            Snackbar.LENGTH_SHORT
                        ).show()
                        applyProButtonState()
                    }
                },
                onFailed = { reason ->
                    if (!isUiAlive()) return@showRewarded
                    adGateInProgress = false
                    bs.dismiss()

                    Snackbar.make(
                        reportCard,
                        getString(R.string.ad_error_with_reason, reason),
                        Snackbar.LENGTH_SHORT
                    ).show()

                    // 광고 실패여도 결과가 준비됐으면 적용
                    prefetchResult?.let { applyProResult(userId, it) }
                    applyProButtonState()
                }
            )
        }
        bs.setContentView(v)
        bs.show()
    }


    private fun startPrefetchPro(userId: String) {
        if (!isUiAlive()) return
        if (prefetchCall != null || proInFlight) return
        proInFlight = true; applyProButtonState()
        runOnUi { btnPro.isEnabled = false; btnPro.text = getString(R.string.pro_in_progress) }
        showProSpinner(true)

        FirestoreManager.collectWeekEntriesLimited(userId, targetWeekKey, limit = 4) { entries, totalCount ->
            if (!isUiAlive()) return@collectWeekEntriesLimited
            if (totalCount < MIN_ENTRIES_FOR_REPORT) {
                cancelPrefetch("not-enough-entries")
                Snackbar.make(reportCard, getString(R.string.pro_requires_min, MIN_ENTRIES_FOR_REPORT), Snackbar.LENGTH_SHORT).show()
                return@collectWeekEntriesLimited
            }

            val dreams = entries.mapNotNull { it.dream }.filter { it.isNotBlank() }
            val interps = entries.mapNotNull { it.interp }.filter { it.isNotBlank() }

            val sys = systemMessageForPro()
            val prompt = buildProPrompt(dreams, interps)
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", sys))
                .put(JSONObject().put("role", "user").put("content", prompt))

            val body = JSONObject().apply {
                put("model", "gpt-4.1-mini")
                put("temperature", 0.5)
                put("messages", messages)
                put("max_tokens", 1800)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .build()

            val watchdog = Runnable {
                if (!isUiAlive()) return@Runnable
                if (proInFlight) {
                    cancelPrefetch("timeout")
                    Snackbar.make(reportCard, getString(R.string.pro_timeout), Snackbar.LENGTH_LONG).show()
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
                    runOnUi { Snackbar.make(reportCard, getString(R.string.network_error_pro), Snackbar.LENGTH_SHORT).show() }
                }
                override fun onResponse(call: Call, response: Response) {
                    if (prefetchCall !== call) { response.close(); return }
                    val bodyText = response.body?.string().orEmpty()
                    val isOk = response.isSuccessful
                    val code = response.code
                    response.close()

                    if (!isOk) {
                        val errMsg = try { JSONObject(bodyText).optJSONObject("error")?.optString("message").orEmpty() } catch (_: Exception) { "" }
                        cancelPrefetch("http-$code")
                        runOnUi {
                            val shown = if (errMsg.isBlank()) getString(R.string.please_try_again) else errMsg
                            Snackbar.make(reportCard, getString(R.string.server_response_error, code, shown), Snackbar.LENGTH_LONG).show()
                        }
                        return
                    }

                    val raw = try {
                        JSONObject(bodyText).getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content").trim()
                    } catch (_: Exception) { "" }

                    prefetchCall = null
                    prefetchWatchdog?.let { mainHandler.removeCallbacks(it) }; prefetchWatchdog = null
                    prefetchResult = raw

                    runOnUi {
                        if (adEarned || !adGateInProgress) applyProResult(userId, raw)
                    }
                }
            })
        }
    }

    private fun applyProResult(userId: String, contentRaw: String) = runOnUi {
        proInFlight = false; adGateInProgress = false; prefetchResult = null
        showProSpinner(false)

        val content = contentRaw.ifBlank { "" }
        if (content.isNotBlank()) {
            // Parse PRO metrics from HTML comment
            parseProMetricsFromHtml(content)?.let { m ->
                proHasMetrics = true
                proKpiPos = m.pos; proKpiNeu = m.neu; proKpiNeg = m.neg; proDreamsUsed = m.used
                // persist KPIs (stable until new dreams)
                prefs.edit().putString("pro_kpi_${targetWeekKey}", "${m.pos},${m.neu},${m.neg}").apply()
            }

            aiComment.text = buildRichAnalysis(content)
            applyRtlForCurrentLocale()

            prefs.edit()
                .putBoolean("pro_done_${targetWeekKey}", true)
                .putString("last_analysis_${targetWeekKey}", sanitizeModelHtml(content).take(5000))
                .putString("last_feeling_${targetWeekKey}", lastFeeling)
                .putString("last_keywords_${targetWeekKey}", lastKeywords.joinToString("|"))
                .apply()

            analysisTitle.text = getString(R.string.ai_pro_title)
            proCompleted = true; proNeedRefresh = false
            btnPro.text = getString(R.string.pro_completed); btnPro.isEnabled = false; btnPro.alpha = 0.65f
            applyProButtonState()

            try {
                FirestoreManager.saveProUpgrade(
                    uid = userId, weekKey = targetWeekKey,
                    feeling = lastFeeling, keywords = lastKeywords,
                    analysis = sanitizeModelHtml(content), model = "gpt-4.1-mini"
                ) { /* no-op */ }
            } catch (_: Throwable) { /* ignore */ }
        } else {
            btnPro.isEnabled = true; btnPro.text = getString(R.string.retry)
            Snackbar.make(reportCard, getString(R.string.pro_result_unparsable), Snackbar.LENGTH_SHORT).show()
            applyProButtonState()
        }

        // Re-bind to reflect KPIs immediately
        bindUI(targetWeekKey, lastFeeling, lastKeywords, content, lastEmoLabels, lastEmoDist, lastThemeLabels, lastThemeDist)
    }

    private fun cancelPrefetch(reason: String) {
        Log.d(TAG, "cancelPrefetch: $reason")
        prefetchCall?.cancel()
        prefetchCall = null
        prefetchResult = null
        prefetchWatchdog?.let { mainHandler.removeCallbacks(it) }; prefetchWatchdog = null
        proInFlight = false; adGateInProgress = false

        runOnUi {
            showProSpinner(false)
            btnPro.isEnabled = true
            btnPro.text = getString(R.string.pro_cta)
            applyProButtonState()
        }
    }

    // ---------- prompts ----------
    /** system prompt uses localized strings and forces an HTML comment with machine-readable KPIs */
    private fun systemMessageForPro(): String {
        val intro  = getString(R.string.week_prompt_intro)
        val rules  = getString(R.string.week_prompt_rules)
        val langLine = try { getString(R.string.week_prompt_lang_line, currentPromptLanguage()) }
        catch (_: Exception) { "Answer in ${currentPromptLanguage()} only." }

        // exact headings from resources (already localized)
        val hMain      = getString(R.string.section_title_weekly_deep_main)
        val hSummary   = getString(R.string.section_title_summary3)
        val hPattern   = getString(R.string.section_title_emotion_pattern)
        val hSymbols   = getString(R.string.section_title_symbols)
        val hOutlook   = getString(R.string.section_title_outlook)
        val hChecklist = getString(R.string.section_title_checklist)
        val hQuant     = getString(R.string.section_title_quant)

        // force JSON payload in an HTML comment at the END
        val jsonSpec = """
            At the very end, append ONE HTML comment with a compact JSON payload in ENGLISH keys:
            <!--JSON:{"kpi":{"positive":P,"neutral":N,"negative":M},"used":D}-->
            - P,N,M are numbers in percent (sum ~ 100).
            - D is the number of dreams actually analyzed (2-4).
            - Do NOT add code fences. Do NOT add extra comments.
        """.trimIndent()

        return buildString {
            appendLine(intro)
            appendLine()
            appendLine(langLine)
            appendLine()
            appendLine("Use EXACTLY these section headings in this order (no extra or missing):")
            appendLine("- $hMain")
            appendLine("- $hSummary")
            appendLine("- $hPattern")
            appendLine("- $hSymbols")
            appendLine("- $hOutlook")
            appendLine("- $hChecklist")
            appendLine("- $hQuant")
            appendLine()
            appendLine(rules)
            appendLine()
            appendLine("Write in HTML (<p>,<ul>,<li>), no Markdown code blocks.")
            appendLine(jsonSpec)
        }
    }

    private fun buildProPrompt(dreams: List<String>, interps: List<String>): String {
        val intro  = getString(R.string.week_prompt_intro)
        val rules  = getString(R.string.week_prompt_rules)
        val langLine = try { getString(R.string.week_prompt_lang_line, currentPromptLanguage()) }
        catch (_: Exception) { "Answer in ${currentPromptLanguage()} only." }

        return buildString {
            appendLine(intro)
            appendLine(langLine)
            appendLine(rules)
            appendLine()
            dreams.forEachIndexed { i, s -> appendLine("[Dream ${i+1}] $s") }
            interps.forEachIndexed { i, s -> appendLine("[Interpretation ${i+1}] $s") }
            appendLine()
            appendLine("Use the above 2-4 items ONLY. Base KPIs strictly on these dreams.")
        }
    }

    /** e.g., locale-native name like العربية / हिन्दी / 中文 */
    private fun currentPromptLanguage(): String {
        val loc = resources.configuration.locales[0]
        return loc.getDisplayLanguage(loc).trim().ifBlank { "English" }
    }

    // ---------- HTML + styling ----------
    private fun sanitizeModelHtml(raw: String) =
        raw.trim()
            .replace(Regex("^\\s*```(?:\\w+)?\\s*"), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .replace(Regex("&nbsp;+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?is)</?xliff:g[^>]*>"), "")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")

    private fun compactTypography(raw: String): String = raw
        .trim()
        .replace(Regex("\\u00A0+"), " ")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .replace(Regex("(?m)^\\s*-(?=\\S)"), "• ")
        .replace(Regex("(?m)^\\s*•(?=\\S)"), "• ")
        .replace(Regex("(?m)^\\s*•\\s+"), "• ")
        .replace(Regex("\\n\\s*-(?=\\S)"), "\n• ")
        .replace(Regex("\\n\\s*•\\s*(?=\\S)"), "\n• ")
        .replace(Regex("\\s+([,.:;!?])"), "$1")
        .replace(Regex("([,.:;!?])(\\S)"), "$1 $2")
        .replace(Regex("\\s+([:：])"), "$1")
        .replace(Regex("([:：])[ \\t]*(\\S)"), "$1 $2")

    private fun buildRichAnalysis(htmlOrMd: String): CharSequence {
        val sanitized = sanitizeModelHtml(htmlOrMd)
        val compact = compactTypography(sanitized)
        val spanned = HtmlCompat.fromHtml(compact, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val sb = SpannableStringBuilder(spanned)
        val plain = sb.toString()

        SECTION_COLORS.forEach { (key, col) ->
            val k = Regex.escape(key)

            val rLine = Regex("(?m)(^|[•*\\-–—]\\s+|\\d+\\.\\s*)($k)(?=\\s*(?:[:：]|\\n|$))")
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

    // ---------- KPI parsing from model HTML comment ----------
    private data class ProMetrics(val pos: Float, val neu: Float, val neg: Float, val used: Int)
    private fun parseProMetricsFromHtml(html: String): ProMetrics? {
        val m = Regex("<!--\\s*JSON\\s*:\\s*(\\{.*?\\})\\s*-->", RegexOption.IGNORE_CASE).find(html)
            ?: return null
        return try {
            val obj = JSONObject(m.groupValues[1])
            val kpi = obj.getJSONObject("kpi")
            val p = kpi.getDouble("positive").toFloat()
            val n = kpi.getDouble("neutral").toFloat()
            val g = kpi.getDouble("negative").toFloat()
            val used = obj.optInt("used", 0)
            if (p < 0 || n < 0 || g < 0) null else ProMetrics(p, n, g, used)
        } catch (_: JSONException) { null }
    }

    // ---------- analysis helpers ----------
    private fun computeKpis(labels: List<String>, dist: List<Float>): Triple<Float, Float, Float> {
        val pos = sumByLabels(labels, dist, listOf(
            getString(R.string.emo_positive),
            getString(R.string.emo_calm),
            getString(R.string.emo_vitality),
            getString(R.string.emo_flow)
        ))
        val neu = sumByLabels(labels, dist, listOf(getString(R.string.emo_neutral)))
        val neg = sumByLabels(labels, dist, listOf(
            getString(R.string.emo_confusion),
            getString(R.string.emo_anxiety),
            getString(R.string.emo_depression),
            getString(R.string.emo_fatigue),
            getString(R.string.emo_depression_fatigue)
        ))
        val sum = (pos + neu + neg)
        if (sum <= 0f) return Triple(0f, 0f, 0f)
        fun r1(x: Float) = kotlin.math.round(x * 10f) / 10f
        val scale = 100f / sum
        return Triple(r1(pos * scale), r1(neu * scale), r1(neg * scale))
    }
    private fun sumByLabels(labels: List<String>, dist: List<Float>, targets: List<String>): Float {
        if (labels.size != dist.size || labels.isEmpty()) return 0f
        var s = 0f
        val used = HashSet<Int>();
        for (t in targets) {
            val idx = labels.indexOf(t)
            if (idx >= 0 && used.add(idx)) s += dist[idx].coerceAtLeast(0f)
        }
        return s
    }
    private fun ensureTopNThemes(labels: List<String>, dist: List<Float>, n: Int): Pair<List<String>, List<Float>> {
        if (labels.isEmpty() || dist.isEmpty()) {
            val def = resources.getStringArray(R.array.theme_labels_default).toList().take(n)
            return def to List(def.size) { 0f }
        }
        val pairs = labels.zip(dist).sortedByDescending { it.second }.take(n).toMutableList()
        while (pairs.size < n) pairs += getString(R.string.theme_other) to 0f
        return pairs.map { it.first } to pairs.map { it.second }
    }
    private fun wrapByWords(s: String, maxPerLine: Int = 9): String {
        val words = s.replace("-", " ").split(" ")
        val out = StringBuilder()
        var line = StringBuilder()
        for (w in words) {
            if (line.isNotEmpty() && line.length + 1 + w.length > maxPerLine) {
                out.append(line.toString().trim()).append('\n')
                line = StringBuilder()
            }
            line.append(w).append(' ')
        }
        out.append(line.toString().trim())
        return out.toString()
    }
    private fun formatAgo(ts: Long): String {
        if (ts <= 0) return ""
        val d = (System.currentTimeMillis() - ts) / 1000
        return when {
            d < 60    -> "${d}${getString(R.string.time_seconds_suffix)}"
            d < 3600  -> "${d/60}${getString(R.string.time_minutes_suffix)}"
            d < 86400 -> "${d/3600}${getString(R.string.time_hours_suffix)}"
            else      -> "${d/86400}${getString(R.string.time_days_suffix)}"
        }
    }

    private fun openDreamFragment() {
        if (!isUiAlive()) return
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

    // ---------- RTL ----------
    private fun applyRtlForCurrentLocale() {
        val loc = resources.configuration.locales[0]
        val isRtl = TextUtils.getLayoutDirectionFromLocale(loc) == View.LAYOUT_DIRECTION_RTL
        fun View.applyDir() {
            layoutDirection = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            if (this is TextView) {
                textDirection = if (isRtl) View.TEXT_DIRECTION_ANY_RTL else View.TEXT_DIRECTION_LTR
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
        }
        listOf<View>(aiComment, analysisTitle, weekLabel, keywordsText).forEach { it.applyDir() }
    }
}
