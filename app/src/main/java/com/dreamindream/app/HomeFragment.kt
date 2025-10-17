package com.dreamindream.app

import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.transition.Fade
import android.transition.Slide
import android.graphics.drawable.GradientDrawable
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.*
import android.view.animation.AnimationUtils
import android.view.Gravity
import android.content.res.ColorStateList
import android.widget.*
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.max
import kotlin.math.min

class HomeFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid }

    private var lastFeeling: String? = null
    private var lastKeywords: List<String>? = null
    private var lastAnalysis: String? = null
    private var lastScore: Int? = null

    private var fadePlayed = false
    private fun isAlive(): Boolean = isAdded && view != null

    companion object {
        private var homeAnimPlayed = false
        private const val ARG_FROM_LOGIN = "from_login"
        private const val KEY_PENDING_HOME_FADE = "pending_home_fade"
    }

    // ---------- Edge-to-Edge: 시스템바 패딩(상/하) 적용 ----------
    private data class InitialPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun View.recordInitialPadding() =
        InitialPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

    private fun View.requestApplyInsetsWhenAttached() {
        if (isAttachedToWindow) {
            requestApplyInsets()
        } else {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    v.requestApplyInsets()
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }

    private fun View.applySystemBarsPadding(top: Boolean = true, bottom: Boolean = true) {
        val start = recordInitialPadding()
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left   = start.left,
                top    = if (top)    start.top + sys.top else start.top,
                right  = start.right,
                bottom = if (bottom) start.bottom + sys.bottom else start.bottom
            )
            insets
        }
        requestApplyInsetsWhenAttached()
    }
    // ----------------------------------------------------------

    private fun dailyFallbacks(): List<String> = listOf(
        getString(R.string.daily_fallback_1),
        getString(R.string.daily_fallback_2),
        getString(R.string.daily_fallback_3),
        getString(R.string.daily_fallback_4),
        getString(R.string.daily_fallback_5)
    )

    /** 감정 + 키워드 한 줄 생성 (키워드는 상위 3개, 중복 제거) */
    private fun buildWeeklySummaryLine(
        topEmotion: String?,
        keywords: List<String>?
    ): String {
        val emo = topEmotion?.trim().orEmpty()
        val kw = keywords
            ?.asSequence()
            ?.filter { it.isNotBlank() }
            ?.map { it.trim() }
            ?.distinct()
            ?.take(3)
            ?.joinToString(", ")
            .orEmpty()

        return when {
            emo.isNotEmpty() && kw.isNotEmpty() -> "감정: $emo • 키워드: $kw"
            emo.isNotEmpty() -> "감정: $emo"
            kw.isNotEmpty() -> "키워드: $kw"
            else -> getString(R.string.home_ai_sub)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_home, container, false)

        val userId = uid ?: ""
        prefs = requireContext().getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)

        // 데일리 메시지
        val aiMessage = v.findViewById<TextView>(R.id.ai_message)
        prepareDailyMessageTextView(aiMessage)
        aiMessage.text = getString(R.string.ai_msg_loading)
        DailyMessageManager.getMessage(requireContext()) { msg ->
            if (!isAlive()) return@getMessage
            val safeMsg = msg?.trim()?.takeIf { it.isNotEmpty() && !it.contains("불러올 수 없어요") }
                ?: dailyFallbacks().random()
            aiMessage.post { if (isAlive()) aiMessage.text = safeMsg }
        }

        // AI 리포트 카드
        val aiReportTitle = v.findViewById<TextView>(R.id.ai_report_title)
        val aiReportSummary = v.findViewById<TextView>(R.id.ai_report_summary)
        val btnAiReport = v.findViewById<MaterialButton>(R.id.btn_ai_report)
        val aiReportCard = v.findViewById<View>(R.id.ai_report_card)

// 🌈 Dream in Dream 홈 타이틀 (상단 제목)
        val homeTitle = v.findViewById<TextView>(R.id.home_title)

// 타이틀 그라디언트 색상 적용
        val paint = homeTitle.paint
        val width = paint.measureText(homeTitle.text.toString())
        val textShader = LinearGradient(
            0f, 0f, width, homeTitle.textSize,
            intArrayOf(
                Color.parseColor("#F9B84A"), // 진골드 (따뜻함, 명확한 톤)
                Color.parseColor("#7B61FF")  // 진보라 (약간 네온 느낌)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        homeTitle.paint.shader = textShader

        val r = 18f * resources.displayMetrics.density
        btnAiReport.isAllCaps = false
        btnAiReport.setTextColor(Color.BLACK)
        btnAiReport.backgroundTintList = null
        btnAiReport.rippleColor = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        btnAiReport.background = GradientDrawable().apply {
            cornerRadius = r
            colors = intArrayOf(Color.parseColor("#FFFEDCA6"), Color.parseColor("#FF8BAAFF"))
            orientation = GradientDrawable.Orientation.TL_BR
        }
        ResourcesCompat.getFont(requireContext(), R.font.pretendard_medium)?.let { tf ->
            btnAiReport.typeface = Typeface.create(tf, Typeface.NORMAL)
        }
        btnAiReport.textSize = 12f
        aiReportCard.isClickable = false
        aiReportCard.isFocusable = false
        aiReportCard.setOnClickListener(null)
        aiReportTitle.isClickable = false
        aiReportTitle.isFocusable = false
        aiReportSummary.isClickable = false
        aiReportSummary.isFocusable = false

        val cachedFeeling = prefs.getString("home_feeling", null)
        val cachedKeywords = prefs.getString("home_keywords", null)
            ?.split("|")?.filter { it.isNotBlank() }
        aiReportTitle.text = getString(R.string.ai_report_summary)
        aiReportSummary.text =
            if (!cachedFeeling.isNullOrBlank() || !cachedKeywords.isNullOrEmpty()) {
                buildWeeklySummaryLine(cachedFeeling, cachedKeywords)
            } else {
                getString(R.string.preparing_analysis)
            }

        if (!homeAnimPlayed) {
            animateHomeCardLikeFortune(aiReportCard); homeAnimPlayed = true
        } else {
            aiReportCard.visibility = View.VISIBLE
        }

        val thisWeekKey = WeekUtils.weekKey()
        val currentUid = uid
        if (currentUid == null) {
            btnAiReport.isEnabled = true
            btnAiReport.alpha = 1f
        } else {
            FirestoreManager.countDreamEntriesForWeek(currentUid, thisWeekKey) { count ->
                if (!isAlive()) return@countDreamEntriesForWeek
                if (count < 2) {
                    val ctx = aiReportTitle.context
                    aiReportTitle.text = ctx.getString(R.string.ai_report_summary)
                    aiReportSummary.text = ctx.getString(R.string.ai_report_guide)
                    btnAiReport.isEnabled = true
                    btnAiReport.alpha = 1f
                } else {
                    // 불필요 경고 없애기: 사용하지 않는 파라미터는 '_'로 명시
                    FirestoreManager.loadWeeklyReportFull(
                        requireContext(),
                        currentUid, thisWeekKey
                    ) { f, k, a, _emoL, _emoD, _thL, _thD, sourceCount, _rebuiltAt, _tier, _proAt, stale ->
                        if (!isAlive()) return@loadWeeklyReportFull
                        val needRebuild = f.isBlank() || k.isEmpty() || a.isBlank() || stale || (sourceCount != count)
                        if (needRebuild) {
                            FirestoreManager.aggregateDreamsForWeek(currentUid, thisWeekKey, requireContext()) {
                                if (!isAlive()) return@aggregateDreamsForWeek
                                FirestoreManager.loadWeeklyReport(currentUid, thisWeekKey) { ff, kk, aa, sc2 ->
                                    if (!isAlive()) return@loadWeeklyReport
                                    aiReportTitle.post {
                                        if (isAlive()) applyHomeSummary(aiReportTitle, aiReportSummary, btnAiReport, ff, kk, aa, sc2)
                                    }
                                }
                            }
                        } else {
                            FirestoreManager.loadWeeklyReport(currentUid, thisWeekKey) { ff, kk, aa, sc2 ->
                                if (!isAlive()) return@loadWeeklyReport
                                aiReportTitle.post {
                                    if (isAlive()) applyHomeSummary(aiReportTitle, aiReportSummary, btnAiReport, ff, kk, aa, sc2)
                                }
                            }
                        }

                        // ---- AI 리포트와 동일: 꿈 원문/해석 가져와 키워드 재계산 후 덮어쓰기 ----
                        val limit = min(max(count, 0), 4).coerceAtLeast(2)
                        FirestoreManager.collectWeekEntriesLimited(currentUid, thisWeekKey, limit) { entries, _ ->
                            if (!isAlive()) return@collectWeekEntriesLimited
                            if (entries.isNullOrEmpty()) return@collectWeekEntriesLimited
                            val dreams = entries.mapNotNull { it.dream }.filter { it.isNotBlank() }
                            val interps = entries.mapNotNull { it.interp }.filter { it.isNotBlank() }
                            val recomputedTop3 = extractKeywordsFromDreams(dreams + interps, 3)
                            if (recomputedTop3.isNotEmpty()) {
                                aiReportTitle.post {
                                    if (isAlive()) applyHomeSummary(aiReportTitle, aiReportSummary, btnAiReport, f, recomputedTop3, a, lastScore)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 클릭 네비(원래 방식 유지)
        btnAiReport.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            openAIReport()
        }
        fun View.applyScaleClick(action: () -> Unit) {
            setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
                action()
            }
        }
        v.findViewById<Button>(R.id.btn_dream).applyScaleClick { navigateTo(DreamFragment()) }
        v.findViewById<Button>(R.id.btn_calendar).applyScaleClick { navigateTo(CalendarFragment()) }
        v.findViewById<Button>(R.id.btn_fortune).applyScaleClick { navigateTo(FortuneFragment()) }
        v.findViewById<ImageButton>(R.id.btn_settings).applyScaleClick { navigateTo(SettingsFragment()) }



        v.applySystemBarsPadding(top = true, bottom = true)

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fromLogin = arguments?.getBoolean(ARG_FROM_LOGIN, false) == true
        val pending = if (this::prefs.isInitialized)
            prefs.getBoolean(KEY_PENDING_HOME_FADE, false) else false
        playHomeFadeIfNeeded(view, fromLogin, pending)
    }

    private fun playHomeFadeIfNeeded(root: View, fromLogin: Boolean, pending: Boolean) {
        if (fadePlayed || !(fromLogin || pending)) return

        root.clearAnimation()
        val vto = root.viewTreeObserver
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!root.viewTreeObserver.isAlive) return true
                root.viewTreeObserver.removeOnPreDrawListener(this)
                val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
                root.startAnimation(anim)
                fadePlayed = true
                if (pending) prefs.edit { putBoolean(KEY_PENDING_HOME_FADE, false) }
                if (fromLogin) arguments?.putBoolean(ARG_FROM_LOGIN, false)
                return true
            }
        }
        vto.addOnPreDrawListener(listener)
    }

    private fun prepareDailyMessageTextView(tv: TextView) {
        tv.apply {
            isSelected = false
            setHorizontallyScrolling(false)
            isSingleLine = false
            ellipsize = null
            setLineSpacing(dp(2).toFloat(), 1.05f)
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + dp(2))
        }
    }

    private fun animateHomeCardLikeFortune(card: View) {
        val parent = card.parent as? ViewGroup ?: return
        val set = TransitionSet().apply {
            addTransition(Slide(Gravity.TOP))
            addTransition(Fade(Fade.IN))
            duration = 450
        }
        TransitionManager.beginDelayedTransition(parent, set)
        card.visibility = View.VISIBLE
        card.scaleX = 0.96f; card.scaleY = 0.96f; card.alpha = 0f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).start()
    }

    // ---- 네비게이션: 원래 로직 유지 ----
    private fun openAIReport() {
        val thisWeekKey = WeekUtils.weekKey()
        val userId = uid
        if (userId == null) {
            if (!lastFeeling.isNullOrBlank()) {
                navigateTo(
                    AIReportFragment().apply {
                        arguments = Bundle().apply {
                            putString("weekKey", thisWeekKey)
                            putString("feeling", lastFeeling!!)
                            putStringArrayList("keywords", ArrayList(lastKeywords ?: emptyList()))
                            putString("analysis", lastAnalysis ?: "")
                            lastScore?.let { putInt("score", it) }
                            putBoolean("is_sample", false)
                        }
                    }
                )
            } else {
                context?.let {
                    Toast.makeText(it, getString(R.string.toast_need_two_dreams), Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        navigateTo(
            AIReportFragment().apply {
                arguments = Bundle().apply { putString("weekKey", thisWeekKey) }
            }
        )
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

    private fun applyHomeSummary(
        aiReportTitle: TextView,
        aiReportSummary: TextView,
        btnAiReport: MaterialButton,
        feeling: String,
        keywords: List<String>,
        analysis: String,
        score: Int?
    ) {
        if (!isAlive()) return
        val top3 = keywords.take(3)

        aiReportSummary.animate().alpha(0f).setDuration(120).withEndAction {
            aiReportTitle.text = getString(R.string.ai_report_summary)
            aiReportSummary.text = buildWeeklySummaryLine(feeling, top3)
            aiReportSummary.animate().alpha(1f).setDuration(120).start()
        }.start()

        btnAiReport.isEnabled = true
        btnAiReport.alpha = 1f

        lastFeeling = feeling
        lastKeywords = top3
        lastAnalysis = analysis
        lastScore = score

        prefs.edit {
            putString("home_feeling", feeling)
            putString("home_keywords", top3.joinToString("|"))
        }
    }

    private fun dp(v: Int): Int {
        val dm = resources.displayMetrics
        return (v * dm.density).toInt()
    }

    // ----------------- AI 리포트와 동일한 키워드 파이프라인 -----------------

    /** 저장된 키워드들을 현재 UI 언어로 정규화한 뒤 명사성 위주 Top-3 */
    @Suppress("unused")
    private fun reduceKeywordsForDisplay(src: List<String>): List<String> {
        val lang = resources.configuration.locales[0].language.lowercase(java.util.Locale.ROOT)
        val norm = normalizeKeywordsForLocale(src)
        return if (lang == "ko") selectTop3KoNouns(norm) else selectTop3EnNouns(norm)
            .ifEmpty { norm.take(3) }
    }

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
            "ko" -> cleaned.map { koFromEn[it.lowercase(java.util.Locale.ROOT)] ?: it }
            else -> cleaned.map { enFromKo[it] ?: it }
        }
    }

    private fun selectTop3EnNouns(src: List<String>): List<String> {
        val stop = setOf(
            "the","a","an","and","or","of","to","in","on","for","with","at","by","from","as","that","this","these","those",
            "very","more","most","much","many","some","any","other","others",
            "be","am","is","are","was","were","been","being",
            "have","has","had","do","does","did","can","could","should","would","may","might","will","shall",
            "feel","feels","felt","feeling","think","know","see","saw","seen","go","goes","going","went","gone","make","made","get","got","put","take","taking",
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
        fun tokenOk(t: String): Boolean {
            if (t.isBlank()) return false
            if (t.any { !it.isLetter() && it != '-' }) return false
            if (t.length <= 1) return false
            val low = t.lowercase(java.util.Locale.ROOT)
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

    private fun selectTop3KoNouns(src: List<String>): List<String> {
        val stop = setOf("것","등","수","때","오늘","이번주","이번","저번주","저번","사람","사람들","우리","나","너")
        fun isNounLike(s: String): Boolean {
            val t = s.trim()
            if (t.isBlank()) return false
            if (!Regex("^[가-힣]+$").matches(t)) return false
            if (t.length <= 1) return false
            if (t in stop) return false
            if (t.endsWith("하기") || t.endsWith("적인") || t.endsWith("스러운")) return false
            if (t.endsWith("하다") || t.endsWith("했다") || t.endsWith("합니다")) return false
            return true
        }
        val seen = LinkedHashSet<String>()
        for (kw in src) {
            val tokens = kw.replace("/", " ").replace(",", " ").split(Regex("\\s+"))
            for (t in tokens) {
                val c = t.trim()
                if (isNounLike(c) && seen.add(c) && seen.size >= 3) break
            }
            if (seen.size >= 3) break
        }
        return seen.toList()
    }

    /** 꿈 원문/해석에서 간이 규칙으로 키워드 Top-N 추출 */
    private fun extractKeywordsFromDreams(texts: List<String>, topN: Int = 3): List<String> {
        val lang = resources.configuration.locales[0].language.lowercase(java.util.Locale.ROOT)
        return if (lang == "ko") {
            val josa = listOf("은","는","이","가","을","를","와","과","의","에","에서","으로","에게","도","만","까지","부터","조차","처럼","같이")
            val stop = setOf("것","등","수","때","오늘","이번주","이번","저번주","저번","사람","사람들","우리","나","너","예상","예상치","분석","감정","기분","상태")
            val freq = HashMap<String, Int>()
            fun cleanKo(token: String): String {
                var t = token
                if (t.length >= 2) {
                    for (j in josa) {
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
            val stop = setOf(
                "the","a","an","and","or","of","to","in","on","for","with","at","by","from","as","that","this","these","those",
                "very","more","most","much","many","some","any","other","others",
                "be","am","is","are","was","were","been","being",
                "have","has","had","do","does","did","can","could","should","would","may","might","will","shall",
                "feel","feels","felt","feeling","think","know","see","saw","seen","go","goes","going","went","gone","make","made","get","got","put","take","taking",
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
}
