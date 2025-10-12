// app/src/main/java/com/dreamindream/app/AIReportFragment.kt
package com.dreamindream.app

import android.content.SharedPreferences
import android.os.*
import android.util.Log
import android.view.*
import android.view.ViewGroup
import android.widget.*
import androidx.core.text.HtmlCompat
import java.util.Locale
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
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.graphics.text.LineBreaker
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
        private const val RELOAD_DEBOUNCE_MS = 80L
        private const val EMPTY_DIALOG_DELAY_MS = 1_000L
        private const val MIN_ENTRIES_FOR_REPORT = 2
    }

    // ---------- UI ----------
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

    // ---------- State ----------
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

    // ---------- network ----------
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(OKHTTP_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    // ---------- helpers ----------
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
    @ColorInt private fun color(hex: String): Int = android.graphics.Color.parseColor(hex)

    // ---------- typography ----------
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

    private fun sanitizeModelHtml(raw: String) =
        raw.trim()
            .replace(Regex("^\\s*```(?:\\w+)?\\s*"), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .replace(Regex("&nbsp;+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?is)</?xliff:g[^>]*>"), "")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")

    // ---------- emotion/theme labels ----------
    private enum class Emo { POS, CALM, VITAL, FLOW, NEUT, CONF, ANX, DEP, FAT, DEP_FAT }
    private enum class ThemeK { REL, ACH, CHG, RISK, GROW, OTHER }

    private fun parseEmoLabel(raw: String): Emo? = when (raw.trim().lowercase(Locale.ROOT)) {
        "긍정","positive","pos" -> Emo.POS
        "평온","calm" -> Emo.CALM
        "활력","vitality" -> Emo.VITAL
        "몰입","flow" -> Emo.FLOW
        "중립","neutral" -> Emo.NEUT
        "혼란","confusion","confused" -> Emo.CONF
        "불안","anxiety","anxious" -> Emo.ANX
        "우울","depression","depressed" -> Emo.DEP
        "피로","fatigue","tired" -> Emo.FAT
        "우울/피로","depression/fatigue" -> Emo.DEP_FAT
        else -> null
    }
    private fun emoLabelLocal(e: Emo): String = when (e) {
        Emo.POS -> getString(R.string.emo_positive)
        Emo.CALM -> getString(R.string.emo_calm)
        Emo.VITAL -> getString(R.string.emo_vitality)
        Emo.FLOW -> getString(R.string.emo_flow)
        Emo.NEUT -> getString(R.string.emo_neutral)
        Emo.CONF -> getString(R.string.emo_confusion)
        Emo.ANX -> getString(R.string.emo_anxiety)
        Emo.DEP_FAT -> getString(R.string.emo_depression_fatigue)
        Emo.DEP -> getString(R.string.emo_depression)
        Emo.FAT -> getString(R.string.emo_fatigue)
    }
    private fun parseThemeLabel(raw: String): ThemeK? = when (raw.trim().lowercase(Locale.ROOT)) {
        "관계","relationship","relationships" -> ThemeK.REL
        "성취","achievement","achievements" -> ThemeK.ACH
        "변화","change","changes" -> ThemeK.CHG
        "불안요인","risk","risks","stressor","stressors" -> ThemeK.RISK
        "자기성장","growth","self-growth","self growth","personal growth" -> ThemeK.GROW
        "기타","other","others" -> ThemeK.OTHER
        else -> null
    }
    private fun themeLabelLocal(t: ThemeK): String = when (t) {
        ThemeK.REL -> getString(R.string.theme_rel)
        ThemeK.ACH -> getString(R.string.theme_achieve)
        ThemeK.CHG -> getString(R.string.theme_change)
        ThemeK.RISK -> getString(R.string.theme_risk)
        ThemeK.GROW -> getString(R.string.theme_growth)
        ThemeK.OTHER -> getString(R.string.theme_other)
    }

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
        adView.loadAd(AdRequest.Builder().build()) // 항상 로드

        AdManager.initialize(requireContext())
        AdManager.loadRewarded(requireContext())

        targetWeekKey = arguments?.getString("weekKey") ?: FirestoreManager.thisWeekKey()
        setupBarChart(emotionChart); setupBarChart(themeChart)

        val uidPart = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        prefs = requireContext().getSharedPreferences("weekly_report_cache_$uidPart", android.content.Context.MODE_PRIVATE)

        prefillFromCache(targetWeekKey)

        // Warm-up aggregation immediately (idempotent) so report is ready by the time user scrolls
        FirebaseAuth.getInstance().currentUser?.uid?.let { uidNow ->
            ReportWarmup.warmUpThisWeek(requireContext().applicationContext, uidNow)
        }

        // Show skeleton immediately to avoid blank screen perception
        if (!reportCard.isShown) {
            showReport(true)
            keywordsText.text = getString(R.string.keywords_format, getString(R.string.emo_neutral), "…")
            aiComment.text = HtmlCompat.fromHtml(
                "<i>${getString(R.string.preparing_analysis)}</i>",
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        }

        if (prefs.getBoolean("pro_done_${targetWeekKey}", false)) proCompleted = true
        applyProButtonState()

        chartInfoBtn.setOnClickListener {
            if (!isUiAlive()) return@setOnClickListener
            val msg = getString(
                R.string.chart_info_message,
                MIN_ENTRIES_FOR_REPORT,
                MIN_ENTRIES_FOR_REPORT + 1,
                4
            ).trimIndent()
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

    /** 메인스레드 보장 */
    private fun showProSpinner(show: Boolean, textOverride: String? = null) = runOnUi {
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

                val hasBasic = feeling.isNotBlank() && keywords.isNotEmpty() && analysis.isNotBlank()
                val hasDist  = emoDist.isNotEmpty() && themeDist.isNotEmpty() && (emoDist.sum() > 0f || themeDist.sum() > 0f)

                // 최신 데이터로 재집계가 필요한 경우
                if (hasBasic && hasDist && (stale || sourceCount != dreamCount)) {
                    FirestoreManager.aggregateDreamsForWeek(userId, weekKey, requireContext()) { ok ->
                        if (!isUiAlive()) return@aggregateDreamsForWeek
                        if (ok) { isReloading = false; reloadForWeekInternal(weekKey) }
                        else {
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

    // ---------- empty dialog ----------
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

    // ---------- fallback ----------
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

    // ---------- bind ----------
    private fun bindUI(
        weekKey: String,
        feeling: String, keywords: List<String>, analysis: String,
        emoLabels: List<String>, emoDist: List<Float>,
        themeLabels: List<String>, themeDist: List<Float>
    ) {
        if (!isUiAlive()) return
        showReport(true)

        // 라벨 현지화
        val feelingLocalized = parseEmoLabel(feeling)?.let { emoLabelLocal(it) } ?: feeling
        val emoLocalized = emoLabels.map { lab -> parseEmoLabel(lab)?.let { emoLabelLocal(it) } ?: lab }
        val themeLocalized = themeLabels.map { lab -> parseThemeLabel(lab)?.let { themeLabelLocal(it) } ?: lab }

        // 상단 키워드: 모델이 준 리스트를 3개로 압축(명사성만 간단 필터)
        val displayKw = reduceKeywordsForDisplay(keywords).take(3)
        keywordsText.text = getString(R.string.keywords_format, feelingLocalized, displayKw.joinToString(", "))

        // 본문
        aiComment.text = buildRichAnalysis(analysis)

        // 감정 분포 1차 보정(모델 키워드 + 해석문 기반)
        val (emoL1, emoD1) = refineEmotionDistByEvidenceSync(emoLocalized, emoDist, keywords, analysis)

        // KPI/차트 갱신
        val (pos1, neu1, neg1) = computeKpis(emoL1, emoD1)
        kpiPositive.text = String.format("%.1f%%", pos1)
        kpiNeutral.text  = String.format("%.1f%%", neu1)
        kpiNegative.text = String.format("%.1f%%", neg1)
        renderPercentBars(emotionChart, emoL1, emoD1, ::richEmotionColor)

        val (tL, tD) = ensureTopNThemes(themeLocalized, themeDist, 5)
        renderPercentBars(themeChart, tL.map { wrapByWords(it, 9) }, tD, ::richThemeColor)

        // 캐시 저장
        prefs.edit()
            .putString("last_feeling_$weekKey", feeling)
            .putString("last_keywords_$weekKey", keywords.joinToString("|"))
            .putString("last_analysis_$weekKey", sanitizeModelHtml(analysis).take(5000))
            .putString("last_emo_labels_$weekKey", emoL1.joinToString("|"))
            .putString("last_emo_dist_$weekKey", emoD1.joinToString(","))
            .putString("last_theme_labels_$weekKey", tL.joinToString("|"))
            .putString("last_theme_dist_$weekKey", tD.joinToString(","))
            .apply()

        lastEmoLabels = emoL1; lastEmoDist = emoD1
        lastThemeLabels = tL;  lastThemeDist = tD
        lastFeeling = feeling
        lastKeywords = keywords

        applyProButtonState()

        // 2차 보정(꿈 전문문 포함)
        refineEmotionDistWithDreamsAsync(
            weekKey = weekKey,
            baseLabels = emoL1,
            baseDist = emoD1,
            keywords = keywords,
            analysis = analysis
        )
    }

    // ---------- Pro CTA / Prefetch ----------
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
            adGateInProgress = true; adEarned = false
            setProPending(3_000L); applyProButtonState()

            showProSpinner(true, getString(R.string.preparing_analysis))
            textStatus.text = getString(R.string.preparing_ad)
            progress.visibility = View.VISIBLE

            aiComment.setHtml(aiComment.text.toString() + "<br/><i style='opacity:.7'>${getString(R.string.updating_ellipsis)}</i>")
            startPrefetchPro(userId)

            AdManager.showRewarded(
                requireActivity(),
                onRewardEarned = {
                    if (!isUiAlive()) return@showRewarded
                    adEarned = true
                    textStatus.text = getString(R.string.reward_confirmed)
                    bs.dismiss()
                    prefetchResult?.let { applyProResult(userId, it) }
                    AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    if (!isUiAlive()) return@showRewarded
                    adGateInProgress = false
                    bs.dismiss()
                    prefetchResult?.let {
                        applyProResult(userId, it)
                        AdManager.loadRewarded(requireContext()); return@showRewarded
                    }
                    Snackbar.make(reportCard, getString(R.string.preparing_analysis), Snackbar.LENGTH_SHORT).show()
                    applyProButtonState()
                },
                onFailed = { reason ->
                    if (!isUiAlive()) return@showRewarded
                    adGateInProgress = false
                    Snackbar.make(reportCard, getString(R.string.ad_error_with_reason, reason), Snackbar.LENGTH_SHORT).show()
                    prefetchResult?.let { applyProResult(userId, it) }
                    applyProButtonState()
                }
            )
        }
        bs.setContentView(v); bs.show()
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
            val prompt = buildProPrompt(dreams, interps)

            val body = JSONObject().apply {
                put("model", "gpt-4.1-mini")
                put("temperature", 0.3) // 키워드 일관성 위해 낮춤
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                put("max_tokens", 1100)
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
                        JSONObject(bodyText)
                            .getJSONArray("choices").getJSONObject(0)
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

    // ---------- Pro 결과 적용(JSON 파싱) ----------
    private data class ProPayload(val keywords: List<String>, val emotion: String, val analysis: String)

    private fun extractJsonBlock(text: String): String {
        val noFence = text.trim()
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .trim()
        val start = noFence.indexOf('{')
        val end = noFence.lastIndexOf('}')
        return if (start >= 0 && end > start) noFence.substring(start, end + 1) else noFence
    }

    private fun parseProJsonOrNull(raw: String): ProPayload? {
        return try {
            val json = JSONObject(extractJsonBlock(raw))
            val ks = json.optJSONArray("keywords")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it, "").takeIf { s -> s.isNotBlank() } }
            } ?: emptyList()
            val emo = json.optString("emotion", "").trim()
            val ana = json.optString("analysis", "").trim()
            if (ks.isEmpty() || ana.isBlank()) null else ProPayload(ks, emo, ana)
        } catch (_: Throwable) { null }
    }

    /** 메인 스레드 보장 */
    private fun applyProResult(userId: String, contentRaw: String) = runOnUi {
        proInFlight = false; adGateInProgress = false; prefetchResult = null
        showProSpinner(false)

        val payload = parseProJsonOrNull(contentRaw)
        if (payload != null) {
            // 1) 감정 라벨 현지화
            val feelingLabel = parseEmoLabel(payload.emotion)?.let { emoLabelLocal(it) }
                ?: getString(R.string.emo_neutral)

            // 2) 본문/키워드 즉시 반영
            val displayKw = reduceKeywordsForDisplay(payload.keywords).take(3)
            keywordsText.text = getString(R.string.keywords_format, feelingLabel, displayKw.joinToString(", "))
            aiComment.text = buildRichAnalysis(payload.analysis)

            // 3) 감정 분포 보정 → KPI/차트 갱신
            val baseLabels = if (lastEmoLabels.isEmpty()) resources.getStringArray(R.array.emo_labels_default).toList() else lastEmoLabels
            val baseDist   = if (lastEmoDist.isEmpty()) List(baseLabels.size) { 0f } else lastEmoDist
            val (emoL1, emoD1) = refineEmotionDistByEvidenceSync(baseLabels, baseDist, payload.keywords, payload.analysis)
            val (pos1, neu1, neg1) = computeKpis(emoL1, emoD1)
            kpiPositive.text = String.format("%.1f%%", pos1)
            kpiNeutral.text  = String.format("%.1f%%", neu1)
            kpiNegative.text = String.format("%.1f%%", neg1)
            renderPercentBars(emotionChart, emoL1, emoD1, ::richEmotionColor)

            // 4) 캐시 저장
            prefs.edit()
                .putBoolean("pro_done_${targetWeekKey}", true)
                .putString("last_analysis_${targetWeekKey}", sanitizeModelHtml(payload.analysis).take(5000))
                .putString("last_feeling_${targetWeekKey}", feelingLabel)
                .putString("last_keywords_${targetWeekKey}", payload.keywords.joinToString("|"))
                .putString("last_emo_labels_${targetWeekKey}", emoL1.joinToString("|"))
                .putString("last_emo_dist_${targetWeekKey}", emoD1.joinToString(","))
                .apply()

            // 5) 내부 상태 갱신
            analysisTitle.text = getString(R.string.ai_pro_title)
            proCompleted = true; proNeedRefresh = false
            btnPro.text = getString(R.string.pro_completed); btnPro.isEnabled = false; btnPro.alpha = 0.65f
            applyProButtonState()

            lastFeeling  = feelingLabel
            lastKeywords = payload.keywords
            lastEmoLabels = emoL1; lastEmoDist = emoD1

            // 6) Firestore 저장
            try {
                FirestoreManager.saveProUpgrade(
                    uid = userId, weekKey = targetWeekKey,
                    feeling = feelingLabel, keywords = payload.keywords,
                    analysis = sanitizeModelHtml(payload.analysis), model = "gpt-4.1-mini"
                ) {
                    if (!isUiAlive()) return@saveProUpgrade
                    Snackbar.make(reportCard, getString(R.string.pro_applied), Snackbar.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) { /* ignore */ }

        } else {
            // JSON 파싱 실패 → 기존 텍스트 해석만 적용(키워드는 유지)
            val content = contentRaw.ifBlank { "" }
            if (content.isNotBlank()) {
                aiComment.text = buildRichAnalysis(content)
                prefs.edit()
                    .putBoolean("pro_done_${targetWeekKey}", true)
                    .putString("last_analysis_${targetWeekKey}", sanitizeModelHtml(content).take(5000))
                    .apply()
                analysisTitle.text = getString(R.string.ai_pro_title)
                proCompleted = true; proNeedRefresh = false
                btnPro.text = getString(R.string.pro_completed); btnPro.isEnabled = false; btnPro.alpha = 0.65f
                applyProButtonState()
            } else {
                btnPro.isEnabled = true; btnPro.text = getString(R.string.retry)
                Snackbar.make(reportCard, getString(R.string.pro_result_unparsable), Snackbar.LENGTH_SHORT).show()
                applyProButtonState()
            }
        }
    }

    /** 메인 스레드 보장 */
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

    // ---------- prompt : JSON 통합(키워드·감정·해석) ----------
    private fun buildProPrompt(dreams: List<String>, interps: List<String>): String {
        val lang = if (resources.configuration.locales[0].language.lowercase(Locale.ROOT) == "ko") "Korean" else "English"
        return """
        당신은 꿈 해몽 전문 AI 어시스턴트입니다. 아래 사용자 꿈 텍스트만 근거로, 주차 전체를 대표하는 **핵심 키워드**와 **주 감정**, **요약 해석**을 추출하세요.

        출력은 반드시 JSON 한 덩어리만, 아래 스키마를 따르세요:
        {
          "keywords": ["단어1","단어2","단어3"],   // 3~5개, 모두가 꿈 내용을 보면 동의할 핵심 명사/상징/사건만
          "emotion": "긍정" | "평온" | "중립" | "불안" | "우울" | "피로", // 가장 지배적인 감정 하나
          "analysis": "이 주차의 꿈들을 기반으로 한 간결한 요약 (<=500자)"
        }

        규칙:
        - 인칭대명사(당신, 나, 자기, 자신 등), 일반 형용사(새로운, 좋은 등), 메타 단어(기분, 상태, 감정, 분석 등)는 keywords에서 제외.
        - 복합 개념은 하나로 묶어 표기: 예) "로또 100억", "회사 면접", "미국 여행".
        - 오직 꿈 텍스트의 의미적 핵심만 keywords에 포함. 장식어/대명사 금지.
        - ${lang}로 작성. 반드시 JSON만 출력. 설명문/코드블록 금지.

        꿈:
        ${dreams.mapIndexed { i, s -> "[꿈 ${i+1}] $s" }.joinToString("\n")}

        해몽 메모(있다면 참고만, 가중치는 낮게):
        ${interps.joinToString("\n")}
        """.trimIndent()
    }

    // ---------- local evidence / kpis / themes ----------
    private fun ensureEmoVector(labelsIn: List<String>, distIn: List<Float>): Pair<List<String>, MutableList<Float>> {
        val std = resources.getStringArray(R.array.emo_labels_default).toList()
        val m = MutableList(std.size) { 0f }
        for (i in labelsIn.indices) {
            val idx = std.indexOf(labelsIn[i]).takeIf { it >= 0 } ?: continue
            m[idx] = distIn.getOrNull(i) ?: 0f
        }
        return std to m
    }
    private fun normalizeTo100(vec: MutableList<Float>) {
        val sum = vec.sum().coerceAtLeast(1e-6f)
        for (i in vec.indices) vec[i] = vec[i] * (100f / sum)
    }
    private fun tokenizeAll(vararg texts: String): List<String> {
        val list = texts.toList().filter { it.isNotBlank() }
        if (list.isEmpty()) return emptyList()
        val joined = list.joinToString(" ")
        return joined
            .lowercase(Locale.ROOT)
            .replace(Regex("[_/|]"), " ")
            .split(Regex("[^a-z0-9ㄱ-ㅎ가-힣]+"))
            .filter { it.length >= 2 }
    }
    private fun buildEvidenceDist(tokens: List<String>): MutableList<Float> {
        val std = resources.getStringArray(R.array.emo_labels_default).toList()
        val idxPOS     = std.indexOf(getString(R.string.emo_positive))
        val idxCALM    = std.indexOf(getString(R.string.emo_calm))
        val idxVITAL   = std.indexOf(getString(R.string.emo_vitality))
        val idxFLOW    = std.indexOf(getString(R.string.emo_flow))
        val idxNEUT    = std.indexOf(getString(R.string.emo_neutral))
        val idxCONF    = std.indexOf(getString(R.string.emo_confusion))
        val idxANX     = std.indexOf(getString(R.string.emo_anxiety))
        val idxDEPFAT  = std.indexOf(getString(R.string.emo_depression_fatigue))
        val idxDEP     = std.indexOf(getString(R.string.emo_depression))
        val idxFAT     = std.indexOf(getString(R.string.emo_fatigue))

        fun s(id: Int) = if (id >= 0) id else 0
        val posW = s(idxPOS); val calmW = s(idxCALM); val vitalW = s(idxVITAL); val flowW = s(idxFLOW)
        val neutW = s(idxNEUT); val confW = s(idxCONF); val anxW = s(idxANX)
        val depfatW = if (idxDEPFAT >= 0) idxDEPFAT else s(idxDEP)
        val depW = s(idxDEP); val fatW = s(idxFAT)

        val dictPos = resources.getStringArray(R.array.emotion_dict_positive).map { it.lowercase(Locale.ROOT) }.toSet() +
                resources.getStringArray(R.array.evidence_scene_positive).map { it.lowercase(Locale.ROOT) }
        val dictCalm = resources.getStringArray(R.array.emotion_dict_calm).map { it.lowercase(Locale.ROOT) }.toSet()
        val dictVital = resources.getStringArray(R.array.emotion_dict_vitality).map { it.lowercase(Locale.ROOT) }.toSet() +
                setOf("win","pass","합격","성공","성취","승진")
        val dictFlow = resources.getStringArray(R.array.emotion_dict_flow).map { it.lowercase(Locale.ROOT) }.toSet()

        val dictNeut = resources.getStringArray(R.array.emotion_dict_neutral).map { it.lowercase(Locale.ROOT) }.toSet()
        val dictConf = resources.getStringArray(R.array.emotion_dict_confusion).map { it.lowercase(Locale.ROOT) }.toSet()

        val dictAnx  = resources.getStringArray(R.array.emotion_dict_anxiety).map { it.lowercase(Locale.ROOT) }.toSet() +
                resources.getStringArray(R.array.evidence_scene_negative).map { it.lowercase(Locale.ROOT) }
        val dictDepF = resources.getStringArray(R.array.emotion_dict_depression_fatigue).map { it.lowercase(Locale.ROOT) }.toSet()

        val strongNeg = resources.getStringArray(R.array.evidence_neg_strong_markers).map { it.lowercase(Locale.ROOT) }.toSet()

        var pos=0; var calm=0; var vital=0; var flow=0
        var neut=0; var conf=0; var anx=0; var depf=0; var dep=0; var fat=0
        var strongHit=0

        for (t in tokens) {
            val tk = t.lowercase(Locale.ROOT)
            if (tk in strongNeg) strongHit++

            when {
                tk in dictPos   -> pos++
                tk in dictCalm  -> calm++
                tk in dictVital -> vital++
                tk in dictFlow  -> flow++

                tk in dictNeut  -> neut++
                tk in dictConf  -> conf++

                tk in dictAnx   -> anx++
                tk in dictDepF  -> depf++
            }
            if (tk in setOf("tired","피곤","exhausted","지침","burnout","무기력")) fat++
            if (tk in setOf("sad","슬픔","우울","lonely","절망","hopeless")) dep++
        }

        val vec = MutableList(std.size) { 0f }
        fun add(i: Int, v: Int, w: Float = 1f) { if (i in vec.indices) vec[i] += v * w }

        add(posW, pos, 1.0f); add(calmW, calm, 1.0f); add(vitalW, vital, 1.0f); add(flowW, flow, 0.8f)
        add(neutW, neut, 0.6f); add(confW, conf, 0.9f)
        add(anxW, anx, 1.4f); add(depfatW, depf, 1.2f)
        add(depW, dep, 1.0f); add(fatW, fat, 1.0f)

        if (strongHit > 0) {
            if (anxW in vec.indices) vec[anxW] *= 1.35f
            if (depfatW in vec.indices) vec[depfatW] *= 1.15f
            if (depW in vec.indices) vec[depW] *= 1.10f
            if (posW in vec.indices) vec[posW] *= 0.88f
            if (calmW in vec.indices) vec[calmW] *= 0.9f
        }

        if (vec.all { it == 0f }) vec[posW] = 1f
        normalizeTo100(vec)
        return vec
    }

    private fun refineEmotionDistByEvidenceSync(
        labelsLocalized: List<String>,
        distIn: List<Float>,
        keywords: List<String>,
        analysisHtmlOrMd: String
    ): Pair<List<String>, List<Float>> {

        val (stdLabels, baseVec) = ensureEmoVector(labelsLocalized, distIn)
        val analysisPlain = HtmlCompat.fromHtml(
            sanitizeModelHtml(analysisHtmlOrMd),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()

        val tokens = tokenizeAll(keywords.joinToString(" "), analysisPlain)
        if (tokens.isEmpty()) return stdLabels to baseVec

        val evidence = buildEvidenceDist(tokens)

        val strongNeg = resources.getStringArray(R.array.evidence_neg_strong_markers).map { it.lowercase(Locale.ROOT) }.toSet()
        val strongHit = tokens.any { it.lowercase(Locale.ROOT) in strongNeg }
        val alpha = if (strongHit) 0.5f else 0.7f
        val beta  = 1f - alpha

        for (i in baseVec.indices) {
            baseVec[i] = (baseVec[i] * alpha) + (evidence[i] * beta)
        }
        normalizeTo100(baseVec)
        return stdLabels to baseVec.toList()
    }

    private fun refineEmotionDistWithDreamsAsync(
        weekKey: String,
        baseLabels: List<String>,
        baseDist: List<Float>,
        keywords: List<String>,
        analysis: String
    ) {
        val userId = uid ?: return
        FirestoreManager.collectWeekEntriesLimited(userId, weekKey, limit = 6) { entries, _ ->
            if (!isUiAlive()) return@collectWeekEntriesLimited
            if (entries.isNullOrEmpty()) return@collectWeekEntriesLimited

            val dreams = entries.mapNotNull { it.dream }.filter { it.isNotBlank() }
            val interps = entries.mapNotNull { it.interp }.filter { it.isNotBlank() }

            val tokens = tokenizeAll(
                keywords.joinToString(" "),
                HtmlCompat.fromHtml(sanitizeModelHtml(analysis), HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),
                dreams.joinToString(" "),
                interps.joinToString(" ")
            )
            if (tokens.isEmpty()) return@collectWeekEntriesLimited

            val evidence = buildEvidenceDist(tokens)
            val (_, distWork) = ensureEmoVector(baseLabels, baseDist)

            val strongNeg = resources.getStringArray(R.array.evidence_neg_strong_markers).map { it.lowercase(Locale.ROOT) }.toSet()
            val strongHit = tokens.any { it.lowercase(Locale.ROOT) in strongNeg }
            val a = if (strongHit) 0.45f else 0.6f
            val b = 1f - a
            for (i in distWork.indices) {
                distWork[i] = (distWork[i] * a) + (evidence[i] * b)
            }
            normalizeTo100(distWork)

            val delta = baseDist.zip(distWork).maxOfOrNull { (x, y) -> kotlin.math.abs(x - y) } ?: 0f
            if (delta >= 3f) {
                runOnUi {
                    val (pos, neu, neg) = computeKpis(baseLabels, distWork)
                    kpiPositive.text = String.format("%.1f%%", pos)
                    kpiNeutral.text  = String.format("%.1f%%", neu)
                    kpiNegative.text = String.format("%.1f%%", neg)
                    renderPercentBars(emotionChart, baseLabels, distWork, ::richEmotionColor)
                }
                prefs.edit()
                    .putString("last_emo_labels_$weekKey", baseLabels.joinToString("|"))
                    .putString("last_emo_dist_$weekKey", distWork.joinToString(","))
                    .apply()
                lastEmoLabels = baseLabels; lastEmoDist = distWork
            }
        }
    }

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
        val used = HashSet<Int>()
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

    private fun reduceKeywordsForDisplay(src: List<String>): List<String> {
        val lang = resources.configuration.locales[0].language.lowercase(Locale.ROOT)
        val cleaned = src.map { it.trim() }.filter { it.isNotBlank() }
        return if (lang == "ko") {
            // 간단 필터: 조사/어미/형용사성 접미 제거 + 대명사/메타용어 배제
            val stop = setOf("당신","자신","자기","본인","나","너","우리",
                "새로운","좋은","나쁜","커다란","작은","많은","적은",
                "기분","감정","상태","분석","내용")
            val josa = listOf("은","는","이","가","을","를","와","과","의","에","에서","으로","에게","도","만","까지","부터","처럼","같이")
            val out = LinkedHashSet<String>()
            for (kw in cleaned) {
                var t = kw
                for (j in josa) if (t.endsWith(j) && t.length - j.length >= 2) { t = t.dropLast(j.length); break }
                if (t.endsWith("적인") || t.endsWith("스러운") || t.endsWith("하다") || t.endsWith("했다")) continue
                if (t in stop) continue
                if (t.length >= 2) out += t
                if (out.size >= 5) break
            }
            out.toList()
        } else {
            val stop = setOf("you","your","yours","i","me","we","they","he","she",
                "new","good","bad","great","nice","analysis","content","feeling","feelings","state","status")
            val out = LinkedHashSet<String>()
            for (kw in cleaned) {
                val t = kw.lowercase(Locale.ROOT)
                if (t in stop) continue
                val base = when {
                    t.endsWith("ies") && t.length > 4 -> t.dropLast(3) + "y"
                    t.endsWith("s") && t.length > 3 -> t.dropLast(1)
                    else -> t
                }
                if (base.length >= 2) out += base
                if (out.size >= 5) break
            }
            out.toList()
        }
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
}
