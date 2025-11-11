// app/src/main/java/com/dreamindream/app/HomeFragment.kt
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
import android.view.MotionEvent

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

    /** 설정 아이콘: 효과 + 네비게이션 (축소→복원) */
    private fun ImageButton.effectsAndNavigate(onClick: () -> Unit) {
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(1.4f).scaleY(1.4f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    // 손 뗀 좌표가 버튼 내부면 클릭 처리
                    if (e.x in 0f..v.width.toFloat() && e.y in 0f..v.height.toFloat()) {
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    true
                }
                else -> false
            }
        }
        setOnClickListener { onClick() }
    }

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
            ?.take(1)
            ?.joinToString(", ")
            .orEmpty()

        val emoLabel = getString(R.string.label_emotion)
        val kwLabel  = getString(R.string.label_keywords)

        return when {
            emo.isNotEmpty() && kw.isNotEmpty() ->
                "$emoLabel: $emo • $kwLabel: $kw"
            emo.isNotEmpty() -> "$emoLabel: $emo"
            kw.isNotEmpty() -> "$kwLabel: $kw"
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

        // 홈 타이틀 그라디언트
        val homeTitle = v.findViewById<TextView>(R.id.home_title)
        val paint = homeTitle.paint
        val width = paint.measureText(homeTitle.text.toString())
        val textShader = LinearGradient(
            0f, 0f, width, homeTitle.textSize,
            intArrayOf(
                Color.parseColor("#F9B84A"),
                Color.parseColor("#7B61FF")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        homeTitle.paint.shader = textShader

        // 심화분석 버튼과 동일 스타일
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

        // 카드 클릭 막기(버튼만)
        aiReportCard.isClickable = false
        aiReportCard.isFocusable = false
        aiReportCard.setOnClickListener(null)
        aiReportTitle.isClickable = false
        aiReportTitle.isFocusable = false
        aiReportSummary.isClickable = false
        aiReportSummary.isFocusable = false

        // 캐시 우선 표시
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

        // 핵심: Home은 재계산 금지. 서버 저장값 그대로 사용.
        val thisWeekKey = WeekUtils.weekKey()
        val currentUid = uid
        if (currentUid == null) {
            btnAiReport.isEnabled = true
            btnAiReport.alpha = 1f
        } else {
            FirestoreManager.countDreamEntriesForWeek(currentUid, thisWeekKey) { count ->
                if (!isAlive()) return@countDreamEntriesForWeek
                if (count < 2) {
                    aiReportTitle.text = getString(R.string.ai_report_summary)
                    aiReportSummary.text = getString(R.string.ai_report_guide)
                    btnAiReport.isEnabled = true
                    btnAiReport.alpha = 1f
                } else {
                    // 서버의 keywordIds → 표시문구(로케일)로 변환된 결과를 그대로 수신
                    FirestoreManager.loadWeeklyReportFull(
                        requireContext(),
                        currentUid,
                        thisWeekKey
                    ) { f, k, a, emoL, emoD, thL, thD, src, rebuiltAt, tier, proAt, stale ->
                        if (!isAlive()) return@loadWeeklyReportFull
                        applyHomeSummary(aiReportTitle, aiReportSummary, btnAiReport, f, k, a, null)
                    }
                }
            }
        }

        // 클릭 네비
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

        // 설정 아이콘: 축소 효과 + 설정 화면 이동
        v.findViewById<ImageButton>(R.id.btn_settings)?.effectsAndNavigate {
            navigateTo(SettingsFragment())
        }

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

    // ---- 네비게이션 ----
    private fun openAIReport() {
        val thisWeekKey = WeekUtils.weekKey()
        val currentUid = uid
        if (currentUid == null) {
            Toast.makeText(requireContext(), R.string.login_required, Toast.LENGTH_SHORT).show()
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
        val top1 = keywords.take(1)

        aiReportSummary.animate().alpha(0f).setDuration(120).withEndAction {
            aiReportTitle.text = getString(R.string.ai_report_summary)
            aiReportSummary.text = buildWeeklySummaryLine(feeling, top1)
            aiReportSummary.animate().alpha(1f).setDuration(120).start()
        }.start()

        btnAiReport.isEnabled = true
        btnAiReport.alpha = 1f

        lastFeeling = feeling
        lastKeywords = top1
        lastAnalysis = analysis
        lastScore = score

        prefs.edit {
            putString("home_feeling", feeling)
            putString("home_keywords", top1.joinToString("|"))
        }
    }

    private fun dp(v: Int): Int {
        val dm = resources.displayMetrics
        return (v * dm.density).toInt()
    }
}
