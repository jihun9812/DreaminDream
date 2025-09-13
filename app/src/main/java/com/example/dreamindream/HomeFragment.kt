package com.example.dreamindream

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

    // 홈 요약 캐시(게스트 모드 툴팁용)
    private var lastFeeling: String? = null
    private var lastKeywords: List<String>? = null
    private var lastAnalysis: String? = null
    private var lastScore: Int? = null

    private fun isAlive(): Boolean = isAdded && view != null

    companion object { private var homeAnimPlayed = false }

    // 데일리 메시지 폴백
    private val dailyMsgFallbacks = listOf(
        "오늘은 마음의 속도를 잠시 늦춰 보세요.",
        "완벽함보다 한 걸음이 더 중요해요.",
        "걱정은 내려놓고 할 수 있는 일부터 시작해요.",
        "오늘의 선택이 내일의 나를 만듭니다.",
        "당신의 리듬을 믿고 천천히 가도 괜찮아요."
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_home, container, false)

        val userId = uid ?: ""
        prefs = requireContext().getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)

        // ---------- 데일리 메시지 ----------
        val aiMessage = v.findViewById<TextView>(R.id.ai_message)
        prepareDailyMessageTextView(aiMessage)
        aiMessage.text = getString(R.string.ai_msg_loading)

        DailyMessageManager.getMessage(requireContext()) { msg ->
            if (!isAlive()) return@getMessage
            val safeMsg = msg?.trim().takeUnless {
                it.isNullOrEmpty() || it.contains("불러올 수 없어요")
            } ?: dailyMsgFallbacks.random()
            aiMessage.post { if (isAlive()) aiMessage.text = safeMsg }
        }

        // ---------- AI 리포트 홈 카드 ----------
        val aiReportTitle = v.findViewById<TextView>(R.id.ai_report_title)
        val aiReportSummary = v.findViewById<TextView>(R.id.ai_report_summary)
        val btnAiReport = v.findViewById<MaterialButton>(R.id.btn_ai_report)
        val aiReportCard = v.findViewById<View>(R.id.ai_report_card)

        // 카드/요약 뷰는 클릭 불가를 강제(이 화면에선 버튼만 진입 허용)
        aiReportCard.isClickable = false
        aiReportCard.isFocusable = false
        aiReportCard.setOnClickListener(null)
        aiReportTitle.isClickable = false
        aiReportTitle.isFocusable = false
        aiReportSummary.isClickable = false
        aiReportSummary.isFocusable = false

        // 캐시 즉시 반영(빠릿)
        val cachedFeeling = prefs.getString("home_feeling", null)
        val cachedKeywords = prefs.getString("home_keywords", null)
            ?.split("|")?.filter { it.isNotBlank() }
        aiReportTitle.text = getString(R.string.ai_report_summary)
        aiReportSummary.text = if (cachedFeeling != null && !cachedKeywords.isNullOrEmpty())
            "감정: $cachedFeeling\n키워드: ${cachedKeywords.joinToString(", ")}"
        else "분석 준비 중…"

        // Fortune 스타일 입장 애니메이션(앱 세션당 1회)
        if (!homeAnimPlayed) {
            animateHomeCardLikeFortune(aiReportCard); homeAnimPlayed = true
        } else {
            aiReportCard.visibility = View.VISIBLE
        }

        // 파이어베이스 연동: 이번 주 데이터 상태에 따라 홈 요약 갱신
        val thisWeekKey = WeekUtils.weekKey()
        val currentUid = uid
        if (currentUid == null) {
            // 게스트도 버튼은 열리게
            btnAiReport.isEnabled = true
            btnAiReport.alpha = 1f
        } else {
            FirestoreManager.countDreamEntriesForWeek(currentUid, thisWeekKey) { count ->
                if (!isAlive()) return@countDreamEntriesForWeek
                if (count < 2) {
                    // 2개 미만: 가이드 문구
                    val ctx = aiReportTitle.context
                    aiReportTitle.text = ctx.getString(R.string.ai_report_summary)
                    aiReportSummary.text = ctx.getString(R.string.ai_report_guide)
                    btnAiReport.isEnabled = true
                    btnAiReport.alpha = 1f
                } else {
                    // 2개 이상: 리포트 로드/필요시 재집계
                    FirestoreManager.loadWeeklyReportFull(
                        currentUid, thisWeekKey
                    ) { f, k, a, emoL, emoD, thL, thD, sourceCount, rebuiltAt, tier, proAt, stale ->
                        if (!isAlive()) return@loadWeeklyReportFull

                        val needRebuild = f.isBlank() || k.isEmpty() || a.isBlank() || stale || (sourceCount != count)
                        if (needRebuild) {
                            FirestoreManager.aggregateDreamsForWeek(currentUid, thisWeekKey) {
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

        // ---------- 클릭 네비게이션 ----------
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

    // ---------- 유틸: 데일리 메시지 텍스트뷰 멀티라인 안전 세팅 ----------
    private fun prepareDailyMessageTextView(tv: TextView) {
        tv.apply {
            isSelected = false
            setHorizontallyScrolling(false)
            isSingleLine = false
            ellipsize = null
            maxLines = 5
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + dp(2))
        }
    }

    // Fortune 스타일 홈 카드 입장 애니
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

    // AI 리포트 열기(버튼 전용)
    private fun openAIReport() {
        val thisWeekKey = WeekUtils.weekKey()
        val userId = uid
        if (userId == null) {
            // 게스트: 최근 캐시가 있으면 전달
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
                    Toast.makeText(it, "이번 주 꿈을 2개 이상 기록하면 AI 리포트를 볼 수 있어요.", Toast.LENGTH_SHORT).show()
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

    // 공통 네비(애니메이션 유지)
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

    // 홈 요약 적용 + 캐시
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
            aiReportSummary.text = "감정: $feeling\n키워드: ${top3.joinToString(", ")}"
            aiReportSummary.animate().alpha(1f).setDuration(120).start()
        }.start()

        lastFeeling = feeling
        lastKeywords = top3
        lastAnalysis = analysis
        lastScore = score
        btnAiReport.isEnabled = true
        btnAiReport.alpha = 1f

        // 로컬 캐시
        prefs.edit()
            .putString("home_feeling", feeling)
            .putString("home_keywords", top3.joinToString("|"))
            .apply()
    }

    // dp 변환
    private fun dp(value: Int): Int {
        val dm = resources.displayMetrics
        return (value * dm.density).toInt()
    }
}
