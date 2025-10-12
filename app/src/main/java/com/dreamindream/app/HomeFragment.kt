package com.dreamindream.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.*
import android.view.animation.AnimationUtils
import android.view.Gravity
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private val uid by lazy { FirebaseAuth.getInstance().currentUser?.uid }

    private var lastFeeling: String? = null
    private var lastKeywords: List<String>? = null
    private var lastAnalysis: String? = null
    private var lastScore: Int? = null

    // 페이드인 1회 실행 제어
    private var fadePlayed = false
    private fun isAlive(): Boolean = isAdded && view != null

    companion object {
        private var homeAnimPlayed = false
        private const val ARG_FROM_LOGIN = "from_login"
        private const val KEY_PENDING_HOME_FADE = "pending_home_fade"
    }

    private fun dailyFallbacks(): List<String> = listOf(
        getString(R.string.daily_fallback_1),
        getString(R.string.daily_fallback_2),
        getString(R.string.daily_fallback_3),
        getString(R.string.daily_fallback_4),
        getString(R.string.daily_fallback_5)
    )

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
            val safeMsg = msg?.trim().takeUnless {
                it.isNullOrEmpty() || it.contains("불러올 수 없어요")
            } ?: dailyFallbacks().random()
            aiMessage.post { if (isAlive()) aiMessage.text = safeMsg }
        }

        // AI 리포트 카드
        val aiReportTitle = v.findViewById<TextView>(R.id.ai_report_title)
        val aiReportSummary = v.findViewById<TextView>(R.id.ai_report_summary)
        val btnAiReport = v.findViewById<MaterialButton>(R.id.btn_ai_report)
        val aiReportCard = v.findViewById<View>(R.id.ai_report_card)

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
            if (cachedFeeling != null && !cachedKeywords.isNullOrEmpty()) {
                getString(
                    R.string.ai_emotion_keywords_simple,
                    cachedFeeling,
                    cachedKeywords.joinToString(", ")
                )
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
                    FirestoreManager.loadWeeklyReportFull(
                        requireContext(),
                        currentUid, thisWeekKey
                    ) { f, k, a, emoL, emoD, thL, thD, sourceCount, rebuiltAt, tier, proAt, stale ->
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
        v.findViewById<ImageButton>(R.id.btn_settings).applyScaleClick { navigateTo(SettingsFragment()) }

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 로그인 진입 표시 (arguments) 또는 로그인 성공 시 세팅한 pending 플래그가 있으면 페이드인 실행
        val fromLogin = arguments?.getBoolean(ARG_FROM_LOGIN, false) == true
        val pending = if (this::prefs.isInitialized)
            prefs.getBoolean(KEY_PENDING_HOME_FADE, false) else false

        playHomeFadeIfNeeded(view, fromLogin, pending)
    }

    /** Pre-draw 시점에 fade_in을 확실히 걸어준다 (네가 res/anim/fade_in.xml을 조절) */
    private fun playHomeFadeIfNeeded(root: View, fromLogin: Boolean, pending: Boolean) {
        if (fadePlayed || !(fromLogin || pending)) return

        // 혹시 모를 잔여 애니메이션 제거
        root.clearAnimation()

        val vto = root.viewTreeObserver
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!root.viewTreeObserver.isAlive) return true
                root.viewTreeObserver.removeOnPreDrawListener(this)

                // 여기서 네가 만든 fade_in.xml을 사용
                val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
                root.startAnimation(anim)

                fadePlayed = true
                if (pending) prefs.edit().putBoolean(KEY_PENDING_HOME_FADE, false).apply()
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
        val ctx = aiReportTitle.context
        val top3 = keywords.take(3)

        aiReportSummary.animate().alpha(0f).setDuration(120).withEndAction {
            aiReportTitle.text = ctx.getString(R.string.ai_report_summary)
            aiReportSummary.text = ctx.getString(
                R.string.ai_emotion_keywords_simple,
                feeling,
                top3.joinToString(", ")
            )
            aiReportSummary.animate().alpha(1f).setDuration(120).start()
        }.start()

        lastFeeling = feeling
        lastKeywords = top3
        lastAnalysis = analysis
        lastScore = score
        btnAiReport.isEnabled = true
        btnAiReport.alpha = 1f

        prefs.edit()
            .putString("home_feeling", feeling)
            .putString("home_keywords", top3.joinToString("|"))
            .apply()
    }

    private fun dp(value: Int): Int {
        val dm = resources.displayMetrics
        return (value * dm.density).toInt()
    }
}
