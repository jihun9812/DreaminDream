// app/src/main/java/com/example/dreamindream/AIReportFragment.kt
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
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.content.res.ResourcesCompat
import android.content.res.ColorStateList
import android.graphics.Typeface
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
        private const val MIN_ENTRIES_FOR_REPORT = 2
    }

    // ---------- 키워드 정규화/축약 ----------
    /** 과거 저장된 키워드(한/영 섞임)를 UI 언어로 맞춘 뒤 명사 위주로 3개까지 압축 */
    private fun reduceKeywordsForDisplay(src: List<String>): List<String> {
        val lang = resources.configuration.locales[0].language.lowercase(Locale.ROOT)
        val norm = normalizeKeywordsForLocale(src) // 현재 UI 언어로 1차 정규화

        return if (lang == "ko") {
            selectTop3KoNouns(norm)
        } else {
            selectTop3EnNouns(norm)
        }.ifEmpty {
            norm.take(3)
        }
    }

    // 한→영 / 영→한 간단 매핑(기존 데이터 보정용)
    private fun normalizeKeywordsForLocale(src: List<String>): List<String> {
        val lang = resources.configuration.locales[0].language.lowercase(java.util.Locale.ROOT)
        val enFromKo = mapOf(
            "개" to "dog", "고양이" to "cat", "사람들" to "people", "괴물" to "monster",
            "도망" to "escape", "차" to "car", "두려움" to "fear", "스트레스" to "stress",
            "희망" to "hope", "자기 보호" to "self-protection", "금전" to "money",
            "시험" to "exam", "변화" to "change", "관계" to "relationship", "성취" to "achievement",
            "경계" to "boundary", "경계선" to "boundary", "태양" to "sun", "상어" to "shark",
            "압박" to "pressure", "과부하" to "overload", "불안" to "anxiety"
        )
        val koFromEn = enFromKo.entries.associate { (k, v) -> v to k }
        val cleaned = src.map { it.trim() }.filter { it.isNotBlank() }
        return when (lang) {
            "ko" -> cleaned.map { koFromEn[it.lowercase(Locale.ROOT)] ?: it }
            else -> cleaned.map { enFromKo[it] ?: it }
        }
    }

    // 영어: 토큰화…
    private fun selectTop3EnNouns(src: List<String>): List<String> {
        val stop = setOf(
            "the","a","an","and","or","of","to","in","on","for","with","at","by","from","as","that","this","these","those",
            "very","more","most","much","many","some","any","other","others",
            "be","am","is","are","was","were","been","being",
            "have","has","had","do","does","did","can","could","should","would","may","might","will","shall",
            "feel","feels","felt","feeling","think","know","see","go","went","gone","make","made","get","got","put","take","taking",
            "good","bad","great","little","big","small","huge","giant","new","old","high","low","deep",
            "horrible","awful","terrible","scary","frightening","beautiful","nice","lovely","amazing","awesome",
            "happy","sad","anxious","depressed","tired","urgent","normal","common","general"
        )
        fun singularize(w: String): String {
            val s = w.lowercase(Locale.ROOT)
            return when {
                s.endsWith("ies") && s.length > 4 -> s.dropLast(3) + "y"
                s.endsWith("sses") || s.endsWith("ss") -> s
                s.endsWith("s") && s.length > 3 -> s.dropLast(1)
                else -> s
            }
        }
        fun tokenOk(t: String): Boolean {
            if (t.isBlank()) return false
            if (t.any { !it.isLetter() && it != '-' }) return false
            if (t.length <= 1) return false
            val low = t.lowercase(Locale.ROOT)
            if (low in stop) return false
            if (low.endsWith("ly")) return false
            if (low.endsWith("ing") && low !in setOf("morning","evening","feeling")) return false
            return true
        }
        val seen = LinkedHashSet<String>()
        for (kw in src) {
            val tokens = kw.replace("/", " ").replace(",", " ").replace("-", " ").split(Regex("\\s+"))
            for (t in tokens) {
                if (!tokenOk(t)) continue
                val base = singularize(t)
                if (seen.add(base) && seen.size >= 3) break
            }
            if (seen.size >= 3) break
        }
        return seen.toList()
    }

    // 한국어…
    private fun selectTop3KoNouns(src: List<String>): List<String> {
        val stop = setOf("것","등","수","때","오늘","이번주","이번","저번주","저번","사람","사람들","우리","나","너")
        fun isNounLike(s: String): Boolean {
            val t = s.trim()
            if (t.isBlank()) return false
            if (t in stop) return false
            if (t.endsWith("하다") || t.endsWith("합니다") || t.endsWith("했다")) return false
            if (t.endsWith("적인") || t.endsWith("스러운") || t.endsWith("스럽다") || t.endsWith("스럽게")) return false
            if (t.length <= 1) return false
            return true
        }
        val seen = LinkedHashSet<String>()
        for (kw in src) {
            val tokens = kw.replace("/", " ").replace("-", " ").split(Regex("\\s+"))
            for (t in tokens) {
                val tt = t.replace(Regex("[^ㄱ-ㅎ가-힣a-zA-Z0-9-]"), "")
                if (isNounLike(tt)) {
                    if (seen.add(tt)) {
                        if (seen.size >= 3) break
                    }
                }
            }
            if (seen.size >= 3) break
        }
        return seen.toList()
    }    // ---------- 신규: 꿈 원문에서 '명사 키워드' 뽑기 ----------
    /** 이번 주 실제 꿈 N개(최대 4개)로부터 Top-3 명사 키워드를 추출해서 상단 텍스트를 업데이트 */
    private fun recomputeKeywordsFromDreamsAsync(weekKey: String, feelingLocalized: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // 우리 화면에서 마지막 집계된 개수가 있으면 그 범위 내에서 2~4개로 제한
        val totalHint = lastDreamCount
        val limit = kotlin.math.min(kotlin.math.max(totalHint, 0), 4).coerceAtLeast(2)
        FirestoreManager.collectWeekEntriesLimited(userId, weekKey, limit) { entries, _ ->
            if (!isUiAlive()) return@collectWeekEntriesLimited
            if (entries.isNullOrEmpty()) return@collectWeekEntriesLimited
            val dreams = entries.mapNotNull { it.dream }.filter { it.isNotBlank() }
            val interps = entries.mapNotNull { it.interp }.filter { it.isNotBlank() }
            val top = extractKeywordsFromDreams(dreams + interps, 3)
            if (top.isNotEmpty()) runOnUi {
                keywordsText.text = getString(R.string.keywords_format, feelingLocalized, top.joinToString(", "))
            }
        }
    }

    /** 한국어/영어 모두 지원하는 꿈 텍스트 명사 키워드 추출기(간이 규칙 기반) */
    private fun extractKeywordsFromDreams(texts: List<String>, topN: Int = 3): List<String> {
        val lang = resources.configuration.locales[0].language.lowercase(java.util.Locale.ROOT)
        return if (lang == "ko") {
            // ---- 한국어: 조사/어미 제거 + 불용어 제거 + 빈도 상위 N ----
            val jos = listOf("은","는","이","가","을","를","와","과","의","에","에서","으로","에게","도","만","까지","부터","조차","처럼","같이")
            val stop = setOf("것","등","수","때","오늘","이번주","이번","저번주","저번","사람","사람들","우리","있는","나","너","예상","예상치","분석","감정","기분","상태")
            val freq = HashMap<String, Int>()
            fun cleanKo(token: String): String {
                var t = token
                if (t.length >= 2) {
                    for (j in jos) {
                        if (t.endsWith(j) && t.length - j.length >= 2) { t = t.dropLast(j.length); break }
                    }
                }
                return t
            }
            for (txt in texts) {
                val tokens = Regex("[가-힣]{2,}").findAll(txt).map { it.value }.toList()
                for (raw in tokens) {
                    var w = cleanKo(raw)
                    if (w.endsWith("기")) continue
                    if (w.endsWith("적인") || w.endsWith("스러운")) continue
                    if (w.endsWith("하다") || w.endsWith("했다") || w.endsWith("합니다")) continue
                    if (w in stop) continue
                    if (w.length <= 1) continue
                    freq[w] = (freq[w] ?: 0) + 1
                }
            }
            freq.entries.sortedByDescending { it.value }.map { it.key }.distinct().take(topN)
        } else {
            // ---- 영어: 불용어 + 어형 보정 + 빈도 상위 N ----
            val stop = setOf(
                "the","a","an","and","or","of","to","in","on","for","with","at","by","from","as","that","this","these","those",
                "very","more","most","much","many","some","any","other","others",
                "be","am","is","are","was","were","been","being",
                "have","has","had","do","does","did","can","could","should","would","may","might","will","shall",
                "feel","feels","felt","feeling","think","know","see","go","went","gone","make","made","get","got","put","take","taking",
                "good","bad","great","little","big","small","huge","giant","new","old","high","low","deep",
                "horrible","awful","terrible","scary","frightening","beautiful","nice","lovely","amazing","awesome",
                "happy","sad","anxious","depressed","tired","urgent","normal","common","general"
            )
            fun singularize(s: String): String = when {
                s.endsWith("ies") && s.length > 4 -> s.dropLast(3) + "y"
                s.endsWith("sses") || s.endsWith("ss") -> s
                s.endsWith("s") && s.length > 3 -> s.dropLast(1)
                else -> s
            }
            val freq = HashMap<String, Int>()
            for (txt in texts) {
                val tokens = txt.lowercase(java.util.Locale.ROOT).replace("/", " ")
                    .split(Regex("[^a-z]+")).filter { it.length >= 2 }
                for (t in tokens) {
                    if (t in stop) continue
                    if (t.endsWith("ly")) continue
                    if (t.endsWith("ing") && t !in setOf("morning","evening","feeling")) continue
                    val base = singularize(t)
                    freq[base] = (freq[base] ?: 0) + 1
                }
            }
            freq.entries.sortedByDescending { it.value }.map { it.key }.distinct().take(topN)
        }
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

    private fun isUiAlive(): Boolean = isAdded && view != null

    /** ✅ 메인스레드 보장 래퍼 */
    private inline fun runOnUi(crossinline block: () -> Unit) {
        if (!isUiAlive()) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { if (isUiAlive()) block() }
        }
    }

    // 컬러 팔레트
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

    @ColorInt private fun color(hex: String): Int = android.graphics.Color.parseColor(hex)

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

    // ---------- 라벨 정규화 ----------
    private enum class Emo { POS, CALM, VITAL, FLOW, NEUT, CONF, ANX, DEP, FAT, DEP_FAT }
    private enum class ThemeK { REL, ACH, CHG, RISK, GROW, OTHER }

    private fun parseEmoLabel(raw: String): Emo? = when (raw.trim().lowercase(Locale.ROOT)) {
        "긍정" -> Emo.POS
        "평온" -> Emo.CALM
        "활력" -> Emo.VITAL
        "몰입" -> Emo.FLOW
        "중립" -> Emo.NEUT
        "혼란" -> Emo.CONF
        "불안" -> Emo.ANX
        "우울" -> Emo.DEP
        "피로" -> Emo.FAT
        "우울/피로","우울 / 피로" -> Emo.DEP_FAT
        "positive" -> Emo.POS
        "calm" -> Emo.CALM
        "vitality" -> Emo.VITAL
        "flow" -> Emo.FLOW
        "neutral" -> Emo.NEUT
        "confusion" -> Emo.CONF
        "anxiety" -> Emo.ANX
        "depression","depressed" -> Emo.DEP
        "fatigue","tired" -> Emo.FAT
        "depression/fatigue","depression - fatigue","depression & fatigue" -> Emo.DEP_FAT
        else -> null
    }

    private fun parseThemeLabel(raw: String): ThemeK? = when (raw.trim().lowercase(Locale.ROOT)) {
        "관계" -> ThemeK.REL
        "성취" -> ThemeK.ACH
        "변화" -> ThemeK.CHG
        "불안요인" -> ThemeK.RISK
        "자기성장" -> ThemeK.GROW
        "기타" -> ThemeK.OTHER
        "relationship","relationships" -> ThemeK.REL
        "achievement","achievements" -> ThemeK.ACH
        "change","changes" -> ThemeK.CHG
        "risk","risks","risk factor","risk factors","stressor","stressors" -> ThemeK.RISK
        "self-growth","self growth","growth","personal growth" -> ThemeK.GROW
        "other","others" -> ThemeK.OTHER
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
        btnPro = v.findViewById(R.id.btn_pro_upgrade)

// --- 스타일 통일 (Deep 분석 버튼과 동일) ---
        val r = 12f * resources.displayMetrics.density
        btnPro.isAllCaps = false
        btnPro.setTextColor(Color.BLACK)
        btnPro.backgroundTintList = null
        btnPro.rippleColor = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        btnPro.background = GradientDrawable().apply {
            cornerRadius = r
            colors = intArrayOf(
                Color.parseColor("#FFFEDCA6"),  // 연한 골드
                Color.parseColor("#FF8BAAFF")   // 은은한 보라
            )
            orientation = GradientDrawable.Orientation.TL_BR
            gradientType = GradientDrawable.LINEAR_GRADIENT
            shape = GradientDrawable.RECTANGLE
        }
        ResourcesCompat.getFont(requireContext(), R.font.pretendard_medium)?.let { tf ->
            btnPro.typeface = Typeface.create(tf, Typeface.NORMAL)
        }
        btnPro.textSize = 12f

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
        adView.loadAd(AdRequest.Builder().build()) // 구독 제거: 항상 로드

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

    /** ✅ 메인 스레드 보장 */
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

                // ---------- 최신 데이터로 재집계가 필요한 경우 ----------
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

    // ---------- empty dialog auto-open ----------
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

        // (0) 라벨 현지화
        val feelingLocalized = parseEmoLabel(feeling)?.let { emoLabelLocal(it) } ?: feeling
        val emoLocalized = emoLabels.map { lab -> parseEmoLabel(lab)?.let { emoLabelLocal(it) } ?: lab }
        val themeLocalized = themeLabels.map { lab -> parseThemeLabel(lab)?.let { themeLabelLocal(it) } ?: lab }

        // (1) 상단 키워드: 명사 3개로 요약
        run {
            val kw3 = reduceKeywordsForDisplay(keywords)
            keywordsText.text = getString(R.string.keywords_format, feelingLocalized, kw3.joinToString(", "))
        }
        recomputeKeywordsFromDreamsAsync(weekKey, feelingLocalized)


        // (2) 본문
        aiComment.text = buildRichAnalysis(analysis)

        // (3) 1차 보정
        val (emoL1, emoD1) = refineEmotionDistByEvidenceSync(emoLocalized, emoDist, keywords, analysis)

        // (4) KPI/차트 갱신
        val (pos1, neu1, neg1) = computeKpis(emoL1, emoD1)
        kpiPositive.text = String.format("%.1f%%", pos1)
        kpiNeutral.text  = String.format("%.1f%%", neu1)
        kpiNegative.text = String.format("%.1f%%", neg1)

        renderPercentBars(emotionChart, emoL1, emoD1, ::richEmotionColor)
        val (tL, tD) = ensureTopNThemes(themeLocalized, themeDist, 5)
        renderPercentBars(themeChart, tL.map { wrapByWords(it, 9) }, tD, ::richThemeColor)

        // (5) 캐시 저장
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

        // (6) 2차 보정(비동기)
        refineEmotionDistWithDreamsAsync(
            weekKey = weekKey,
            baseLabels = emoL1,
            baseDist = emoD1,
            keywords = keywords,
            analysis = analysis
        )
    }

    // ---------- pro CTA / prefetch ----------
    private fun onProCtaClicked() {
        if (!isUiAlive()) return

        // 구독/프리미엄 제거: 항상 광고 게이트 경유
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

                    // 이미 받아둔 결과 즉시 적용
                    prefetchResult?.let { applyProResult(userId, it) }
                    // 다음 광고 미리 로드
                    AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    if (!isUiAlive()) return@showRewarded
                    // ★ 중요: 취소하지 않음. 광고가 닫혀도 분석은 계속 진행
                    adGateInProgress = false
                    bs.dismiss()

                    // 결과가 준비돼 있으면 바로 적용
                    prefetchResult?.let {
                        applyProResult(userId, it)
                        AdManager.loadRewarded(requireContext())
                        return@showRewarded
                    }

                    // 아직이면 대기. 응답이 오면 startPrefetchPro의 onResponse에서 자동 적용됨
                    Snackbar.make(reportCard, getString(R.string.preparing_analysis), Snackbar.LENGTH_SHORT).show()
                    applyProButtonState()
                },
                onFailed = { reason ->
                    if (!isUiAlive()) return@showRewarded
                    // 실패해도 취소하지 않고 기다림
                    adGateInProgress = false
                    Snackbar.make(reportCard, getString(R.string.ad_error_with_reason, reason), Snackbar.LENGTH_SHORT).show()

                    // 이미 결과가 있으면 바로 적용
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
        runOnUi {
            btnPro.isEnabled = false
            btnPro.text = getString(R.string.pro_in_progress)
        }
        showProSpinner(true)

        FirestoreManager.collectWeekEntriesLimited(userId, targetWeekKey, limit = 4) { entries, totalCount ->
            if (!isUiAlive()) return@collectWeekEntriesLimited
            if (totalCount < MIN_ENTRIES_FOR_REPORT) {
                cancelPrefetch("not-enough-entries")
                Snackbar.make(reportCard, getString(R.string.pro_requires_min, MIN_ENTRIES_FOR_REPORT), Snackbar.LENGTH_SHORT).show()
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
                    runOnUi {
                        Snackbar.make(reportCard, getString(R.string.network_error_pro), Snackbar.LENGTH_SHORT).show()
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

    /** ✅ 메인 스레드 보장 */
    private fun applyProResult(userId: String, contentRaw: String) = runOnUi {
        proInFlight = false; adGateInProgress = false; prefetchResult = null
        showProSpinner(false)

        val content = contentRaw.ifBlank { "" }
        if (content.isNotBlank()) {
            aiComment.text = buildRichAnalysis(content)

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
                ) {
                    if (!isUiAlive()) return@saveProUpgrade
                    Snackbar.make(reportCard, getString(R.string.pro_applied), Snackbar.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) { /* ignore */ }
        } else {
            btnPro.isEnabled = true; btnPro.text = getString(R.string.retry)
            Snackbar.make(reportCard, getString(R.string.pro_result_unparsable), Snackbar.LENGTH_SHORT).show()
            applyProButtonState()
        }
    }

    /** ✅ 메인 스레드 보장 */
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

    // ---------- prompt ----------
    private fun buildProPrompt(dreams: List<String>, interps: List<String>): String {
        val (pos, neu, neg) = computeKpis(lastEmoLabels, lastEmoDist)
        val themePairs = lastThemeLabels.zip(lastThemeDist)
        fun fmt(v: Float) = String.format("%.1f", v)

        val emoTitle   = getString(R.string.section_title_emotion_percent).replace("%%", "%")
        val themeTitle = getString(R.string.section_title_theme_percent).replace("%%", "%")
        val sep        = getString(R.string.ea_heading_sep)

        val numeric = buildString {
            appendLine("• $emoTitle $sep ${getString(R.string.kpi_positive)} ${fmt(pos)} / ${getString(R.string.kpi_neutral)} ${fmt(neu)} / ${getString(R.string.kpi_negative)} ${fmt(neg)}")
            if (themePairs.isNotEmpty()) {
                append("• $themeTitle $sep ")
                append(themePairs.joinToString(" / ") { (lab, x) -> "$lab ${fmt(x)}" })
            }
        }.trim()

        val intro  = getString(R.string.week_prompt_intro)
        val rules  = getString(R.string.week_prompt_rules)
        val langLine  = getString(R.string.week_prompt_lang_line, currentPromptLanguage())

        return buildString {
            appendLine(intro)
            appendLine(getString(R.string.pro_meta_emotion_label) + ":")
            appendLine(getString(R.string.pro_meta_keywords_label) + ":")
            appendLine(getString(R.string.pro_meta_score_label) + ":")
            appendLine(getString(R.string.pro_meta_ai_label) + ":")
            appendLine(numeric); appendLine()
            dreams.forEachIndexed { i, s -> appendLine("[꿈 ${i+1}] $s") }
            interps.forEachIndexed { i, s -> appendLine("[해몽 ${i+1}] $s") }
            appendLine()
            appendLine(langLine)
            appendLine(rules)
        }
    }

    private fun currentPromptLanguage(): String {
        val lang = resources.configuration.locales[0].language.lowercase(java.util.Locale.ROOT)
        return if (lang == "ko") "Korean" else "English"
    }

    // ---------- utils ----------
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
        FirestoreManager.collectWeekEntriesLimited(userId, weekKey, limit = 6) { entries, totalCount ->
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
