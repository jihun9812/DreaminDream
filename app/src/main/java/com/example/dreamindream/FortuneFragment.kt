// file: app/src/main/java/com/example/dreamindream/FortuneFragment.kt
package com.example.dreamindream

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.*
import android.view.animation.AnimationUtils
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.example.dreamindream.ads.AdManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/* ─────────────────────────────
 * Helpers / Formats
 * ───────────────────────────── */
private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

private fun normalizeDate(src: String): String {
    val s = src.trim(); if (s.isBlank()) return ""
    if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)) return s
    val cleaned = s.replace('.', '-').replace('/', '-')
        .replace("년","-").replace("월","-")
        .replace(Regex("일\\s*\\(.+\\)"),"").replace("일","")
        .replace(Regex("\\s+"),"").trim('-')
    val parts = when {
        Regex("^\\d{8}$").matches(cleaned) -> listOf(cleaned.substring(0,4), cleaned.substring(4,6), cleaned.substring(6,8))
        cleaned.count{it=='-'}==2 -> cleaned.split('-')
        else -> emptyList()
    }
    return if (parts.size==3) "%04d-%02d-%02d".format(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) else ""
}

/* ─────────────────────────────
 * 프로필 동기화 모델/헬퍼 (Firestore → Prefs)
 * ───────────────────────────── */
private data class FortuneProfile(
    val nickname: String,
    val birthdateIso: String, // YYYY-MM-DD
    val gender: String,
    val mbti: String? = null,
    val birthTime: String? = null
)

private fun normalizeToIso(src: String?): String {
    if (src.isNullOrBlank()) return ""
    val s = src.trim()
        .replace('.', '-').replace('/', '-')
        .replace("년", "-").replace("월", "-")
        .replace(Regex("일\\s*\\(.+\\)"), "").replace("일","")
        .replace(Regex("\\s+"),"").trim('-')
    return when {
        Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s) -> s
        Regex("^\\d{8}$").matches(s) -> "%s-%s-%s".format(s.substring(0,4), s.substring(4,6), s.substring(6,8))
        s.count { it == '-' } == 2 -> s.split('-').let {
            "%04d-%02d-%02d".format(it[0].toInt(), it[1].toInt(), it[2].toInt())
        }
        else -> ""
    }
}

private fun mapToProfile(map: Map<String, Any?>): FortuneProfile? {
    val nickname = (map["nickname"] as? String)?.trim().orEmpty()
    val gender = (map["gender"] as? String)?.trim().orEmpty()
    val b1 = (map["birthdate_iso"] as? String)?.trim()
    val b2 = (map["birthdate"] as? String)?.trim()
    val birthIso = normalizeToIso(b1 ?: b2 ?: "")
    val mbti = (map["mbti"] as? String)?.trim()
    val birthTime = (map["birth_time"] as? String)?.trim()
    if (nickname.isBlank() || birthIso.isBlank() || gender.isBlank()) return null
    return FortuneProfile(nickname, birthIso, gender, mbti, birthTime)
}
private const val TAG = "FortuneFragment"

class FortuneFragment : Fragment() {

    // ===== Config =====
    private val apiKey = BuildConfig.OPENAI_API_KEY
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val http by lazy { OkHttpClient() }

    // ===== Views =====
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var prefs: SharedPreferences
    private lateinit var fortuneCard: MaterialCardView
    private lateinit var resultText: TextView
    private lateinit var loadingView: LottieAnimationView
    private lateinit var fortuneButton: MaterialButton


    private lateinit var chips: ChipGroup
    private lateinit var viewLuckyColor: View
    private lateinit var tvLuckyNumber: TextView
    private lateinit var tvLuckyTime: TextView

    private lateinit var barPos: LinearProgressIndicator
    private lateinit var barNeu: LinearProgressIndicator
    private lateinit var barNeg: LinearProgressIndicator
    private lateinit var tvPos: TextView
    private lateinit var tvNeu: TextView
    private lateinit var tvNeg: TextView

    private lateinit var btnCopy: TextView
    private lateinit var btnShare: TextView
    private lateinit var btnDeep: MaterialButton

    private lateinit var layoutChecklist: LinearLayout
    private var sectionsContainer: LinearLayout? = null

    // ===== State =====
    private var lastPayload: JSONObject? = null
    private var isExpanded = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun roundedBg(color: Int) = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(color) }
    private fun gradientBg(vararg colors: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = dp(16).toFloat() }

    private val BTN_GRAD = intArrayOf(Color.parseColor("#9B8CFF"), Color.parseColor("#6F86FF"))
    private val BTN_DISABLED = Color.parseColor("#475166")

    // 단색 팔레트(파스텔 배제)
    private val luckyPalette = listOf("#1E88E5","#3949AB","#43A047","#FB8C00","#E53935","#8E24AA","#546E7A","#00897B","#FDD835","#6D4C41")

    /* ===== UID / Pref 이름 ===== */
    private fun currentUserKey(ctx: Context): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        return uid ?: "guest-" + (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "device")
    }
    private fun profilePrefName(ctx: Context): String = "dreamindream_profile_${currentUserKey(ctx)}"

    /* ===== Prefs: 계정별 저장소 (+ 레거시 흡수) ===== */
    private fun resolvePrefs(): SharedPreferences {
        val ctx = requireContext()
        val fixed = ctx.getSharedPreferences(profilePrefName(ctx), Context.MODE_PRIVATE)

        if (fixed.all.isEmpty()) {
            // 레거시 → 현재 계정 파일로 한 번만 흡수
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val deviceId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "device"
            val legacyNow = ctx.getSharedPreferences("user_info_${uid ?: deviceId}", Context.MODE_PRIVATE)
            val legacyUid = uid?.let { ctx.getSharedPreferences("user_info_$it", Context.MODE_PRIVATE) }
            val legacyDev = ctx.getSharedPreferences("user_info_$deviceId", Context.MODE_PRIVATE)

            val merged = mutableMapOf<String, Any?>()
            listOfNotNull(legacyNow, legacyUid, legacyDev).forEach { sp ->
                if (sp.all.isNotEmpty()) merged.putAll(sp.all)
            }
            if (merged.isNotEmpty()) {
                val e = fixed.edit()
                merged.forEach { (k, v) ->
                    when (v) {
                        is String -> e.putString(k, v)
                        is Int -> e.putInt(k, v)
                        is Boolean -> e.putBoolean(k, v)
                        is Float -> e.putFloat(k, v)
                        is Long -> e.putLong(k, v)
                    }
                }
                e.apply()
            }
        }
        return fixed
    }

    /* ===== Firestore → Prefs 동기화 (진입 시 1회) ===== */
    private fun writeProfileToPrefs(p: FortuneProfile) {
        prefs.edit().apply {
            putString("nickname", p.nickname)
            putString("birthdate_iso", p.birthdateIso)
            putString("birthdate", p.birthdateIso) // 통일
            putString("gender", p.gender)
            if (!p.mbti.isNullOrBlank()) putString("mbti", p.mbti)
            if (!p.birthTime.isNullOrBlank()) putString("birth_time", p.birthTime)
        }.apply()
    }

    private fun syncProfileFromFirestore(then: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { then(); return }
        FirestoreManager.getUserProfile(uid) { data ->
            data?.let { mapToProfile(it) }?.let { writeProfileToPrefs(it) }
            then()
        }
    }

    /* ===== Seeded helpers ===== */
    private fun seededEmotions(seed: Int): Triple<Int, Int, Int> {
        val r = Random(seed); val pos = 40 + r.nextInt(46); val neg = 5 + r.nextInt(26); val neu = (100 - pos - neg).coerceIn(10,50)
        return Triple(pos, neu, neg)
    }
    private fun seededSectionScores(seed: Int): Map<String,Int> {
        val r = Random(seed); val base = 60 + r.nextInt(21)
        val map = mutableMapOf(
            "overall" to (base + r.nextInt(15)-7).coerceIn(40,100),
            "love"    to (base + r.nextInt(20)-10).coerceIn(40,100),
            "study"   to (base + r.nextInt(20)-10).coerceIn(40,100),
            "work"    to (base + r.nextInt(20)-10).coerceIn(40,100),
            "money"   to (base + r.nextInt(20)-10).coerceIn(40,100),
            "lotto"   to (50 + r.nextInt(16)).coerceIn(40,100)
        )
        val low = map.keys.random(r); val high = (map.keys - low).random(r)
        map[low] = 40 + r.nextInt(16); map[high] = 85 + r.nextInt(16)
        return map
    }

    /* ===== 시간/인물 표현 제거 유틸 + 체크리스트 정제 ===== */
    private fun stripTimePhrases(src: String): String {
        var s = src
        s = s.replace(Regex("(오전|오후)\\s*\\d{1,2}시(\\s*~\\s*(오전|오후)?\\s*\\d{1,2}시)?"), "")
        s = s.replace(Regex("\\d{1,2}시\\s*(까지|전)?"), "")
        s = s.replace(Regex("(오늘|내일)?\\s*(아침|오전|점심|오후|저녁|밤)"), "")
        s = s.replace(Regex("\\s{2,}"), " ")
        return s.trim()
    }
    private fun neutralizeCorporateTerms(text: String): String {
        var s = text
        s = s.replace(Regex("회의|미팅|면담"),"상담/정리")
        s = s.replace(Regex("이메일"),"알림/메모")
        s = s.replace(Regex("보고서"),"노트 정리")
        s = s.replace(Regex("결재"),"확인")
        s = s.replace(Regex("메신저"),"연락")
        return s
    }
    private fun neutralizeChecklistText(src: String): String {
        var t = src.trim()
        t = neutralizeCorporateTerms(t)
        t = t.replace(Regex("숙제|과제"), "할 일")
            .replace(Regex("수업|강의|학원"), "학습")
            .replace(Regex("시험|퀴즈"), "정리")
            .replace(Regex("레포트|리포트|보고서"), "노트 정리")
            .replace(Regex("제출"), "확인")
        // 사람/연락 지시 회피
        if (Regex("연락|전화|메시지|문자|DM|카톡|카카오").containsMatchIn(t)) {
            t = "알림 1건 정리"
        }
        // 시간/마감 제거
        t = stripTimePhrases(t)

        // 장식 제거 + 간결화
        t = t.replace(Regex("^•\\s*"), "").replace(Regex("\\s{2,}"), " ").trim()
        if (t.length > 18) t = t.take(18)
        if (t.length < 4) t = "핵심 할 일 1개 완료"
        t = t.replace(Regex("할 ?일.*(마무리|끝내기)"), "핵심 할 일 1개 완료")
        return t
    }
    private fun sanitizeChecklist(items: List<String>): List<String> {
        val out = items.map { neutralizeChecklistText(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        return if (out.size == 3) out else out + buildEssentialChecklist(lastPayload ?: JSONObject()).drop(out.size).take(3 - out.size)
    }

    /** 오늘의 체크(시간 제거, 포괄적 3개) */
    private fun buildEssentialChecklist(payload: JSONObject): List<String> {
        val sections = payload.optJSONObject("sections") ?: JSONObject()
        val weakest = listOf("overall","love","study","work","money")
            .map { it to (sections.optJSONObject(it)?.optInt("score", 70) ?: 70) }
            .minByOrNull { it.second }?.first ?: "overall"

        val base = mutableListOf<String>()
        base += "핵심 작업 1개 완료"
        base += "알림·메모 3분 정리"
        base += when (weakest) {
            "study" -> "노트 5줄 요약"
            "work"  -> "내일 첫 작업 1줄 적기"
            "money" -> "지출 1건 확인"
            "love"  -> "감사 1가지 기록"
            else    -> "가벼운 스트레칭 5분"
        }
        return base.take(3)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_fortune, container, false)

        prefs = resolvePrefs()

        // Bind
        rootLayout      = v.findViewById(R.id.root_fortune_layout)
        fortuneCard     = v.findViewById(R.id.fortuneCard)
        fortuneButton   = v.findViewById(R.id.fortuneButton)
        resultText      = v.findViewById(R.id.fortune_result)
        loadingView     = v.findViewById(R.id.fortune_loading)

        chips           = v.findViewById(R.id.chipsFortune)
        viewLuckyColor  = v.findViewById(R.id.viewLuckyColor)
        tvLuckyNumber   = v.findViewById(R.id.tvLuckyNumber)
        tvLuckyTime     = v.findViewById(R.id.tvLuckyTime)

        barPos          = v.findViewById(R.id.barPos)
        barNeu          = v.findViewById(R.id.barNeu)
        barNeg          = v.findViewById(R.id.barNeg)
        tvPos           = v.findViewById(R.id.tvPos)
        tvNeu           = v.findViewById(R.id.tvNeu)
        tvNeg           = v.findViewById(R.id.tvNeg)

        btnCopy         = v.findViewById(R.id.btnCopy)
        btnShare        = v.findViewById(R.id.btnShare)
        btnDeep         = v.findViewById(R.id.btnDeep)
        layoutChecklist = v.findViewById(R.id.layoutChecklist)
        sectionsContainer = v.findViewById(R.id.sectionsContainer)

        // Ads
        v.findViewById<AdView>(R.id.adView_fortune)?.loadAd(AdRequest.Builder().build())
        AdManager.initialize(requireContext())
        AdManager.loadRewarded(requireContext())

        // 버튼 상태 기본값
        if (isFortuneSeenToday()) { lockFortuneButtonForToday(); moveButtonTop() }
        else { applyPrimaryButtonStyle(); moveButtonCentered() }

        // Firestore → Prefs 동기화 후 캐시 복원
        syncProfileFromFirestore { restoreCachedPayload(v) }

        fun View.scaleClick(run: () -> Unit) = setOnClickListener {
            startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            run()
        }

        btnCopy.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("fortune", resultText.text))
            Toast.makeText(requireContext(),"복사됨",Toast.LENGTH_SHORT).show()
        }
        btnShare.setOnClickListener {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, resultText.text.toString())
            }; startActivity(android.content.Intent.createChooser(send, "공유"))
        }

        // ✅ 버튼 → 로딩 → API 호출
        fortuneButton.scaleClick {
            if (!isProfileComplete()) { showProfileRequiredDialog(); return@scaleClick }
            if (isFortuneSeenToday()) {
                Toast.makeText(requireContext(),"오늘은 이미 확인했어요. 내일 다시 이용해주세요.",Toast.LENGTH_SHORT).show()
                return@scaleClick
            }
            fortuneButton.visibility = View.GONE
            relayoutCardToTop()
            fortuneCard.visibility = View.VISIBLE
            expandFortuneCard(v)

            // 로딩 중엔 로티만 보이도록 카드 컨텐츠 숨김
            setCardContentVisible(false)
            showLoading(true)

            val u = loadUserInfoStrict(); val seed = seedForToday(u)
            // 바로 호출 (프리뷰 없이)
            fetchFortune(u, todayKey(), seed, v)
        }

        btnDeep.setOnClickListener {
            if (lastPayload == null) {
                Toast.makeText(requireContext(),"먼저 ‘행운 보기’를 실행해주세요.",Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openDeepWithGate()
        }

        return v
    }

    private fun restoreCachedPayload(v: View) {
        prefs.getString("fortune_payload_${todayPersonaKey()}", null)?.let { raw ->
            runCatching { JSONObject(raw) }.onSuccess { last ->
                lastPayload = last
                fortuneButton.visibility = View.GONE
                relayoutCardToTop()
                bindFromPayload(last)
                setCardContentVisible(true)
                expandFortuneCard(v)
            }
        }
    }

    /* ===== Persona / Keys ===== */
    private fun personaKey(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val nn = prefs.getString("nickname","") ?: ""
        val mb = prefs.getString("mbti","") ?: ""
        val bd = prefs.getString("birthdate_iso","") ?: normalizeDate(prefs.getString("birthdate","") ?: "")
        val gd = prefs.getString("gender","") ?: ""
        val bt = prefs.getString("birth_time","") ?: ""
        val src = "uid:$uid|$nn|$mb|$bd|$gd|$bt"
        val md = MessageDigest.getInstance("MD5").digest(src.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }
    private fun todayKey(): String = dateFmt.format(Date())
    private fun todayPersonaKey(): String = "${todayKey()}_${personaKey()}"
    private fun isFortuneSeenToday(): Boolean = prefs.getBoolean("fortune_seen_${todayPersonaKey()}", false)

    /* ===== Profile Gate ===== */
    private fun isProfileComplete(): Boolean {
        val nn = prefs.getString("nickname","") ?: ""
        val bd = prefs.getString("birthdate_iso","") ?: normalizeDate(prefs.getString("birthdate","") ?: "")
        val gd = prefs.getString("gender","") ?: ""
        val dateOk = bd.isNotBlank() && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(bd)
        return nn.isNotBlank() && dateOk && gd.isNotBlank()
    }
    private fun showProfileRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("설정이 필요해요")
            .setMessage("닉네임·생년월일·성별만 저장하면 맞춤 운세를 볼 수 있어요.\n(출생시간·MBTI는 선택)")
            .setPositiveButton("확인", null)
            .show()
    }

    /* ===== Button/Loading/Expand ===== */
    private fun applyPrimaryButtonStyle() {
        fortuneButton.isEnabled = true
        fortuneButton.background = gradientBg(Color.parseColor("#9B8CFF"), Color.parseColor("#6F86FF"))
        fortuneButton.setTextColor(Color.WHITE)
        fortuneButton.text = "운세\n보기"
    }
    private fun lockFortuneButtonForToday() {
        fortuneButton.isEnabled = false
        fortuneButton.background = roundedBg(BTN_DISABLED)
        fortuneButton.setTextColor(Color.parseColor("#B3C1CC"))
        fortuneButton.text = "내일 다시"
    }
    private fun moveButtonCentered() {
        val set = ConstraintSet().apply { clone(rootLayout) }
        set.clear(R.id.fortuneButton, ConstraintSet.TOP); set.clear(R.id.fortuneButton, ConstraintSet.BOTTOM)
        set.connect(R.id.fortuneButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(R.id.fortuneButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.connect(R.id.fortuneButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(R.id.fortuneButton, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.setVerticalBias(R.id.fortuneButton, 0.48f)
        TransitionManager.beginDelayedTransition(rootLayout); set.applyTo(rootLayout)
    }
    private fun moveButtonTop() {
        val set = ConstraintSet().apply { clone(rootLayout) }
        set.clear(R.id.fortuneButton, ConstraintSet.BOTTOM)
        set.connect(R.id.fortuneButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(R.id.fortuneButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.connect(R.id.fortuneButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dp(24))
        TransitionManager.beginDelayedTransition(rootLayout); set.applyTo(rootLayout)
    }
    private fun relayoutCardToTop() {
        val set = ConstraintSet().apply { clone(rootLayout) }
        set.clear(R.id.fortuneCard, ConstraintSet.TOP)
        set.connect(R.id.fortuneCard, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dp(10))
        TransitionManager.beginDelayedTransition(rootLayout); set.applyTo(rootLayout)
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            loadingView.alpha=0f; loadingView.visibility=View.VISIBLE
            loadingView.scaleX=0.3f; loadingView.scaleY=0.3f
            loadingView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(BounceInterpolator()).start()
            loadingView.playAnimation()
            resultText.text=""
            fortuneButton.isEnabled=false
        } else {
            loadingView.cancelAnimation(); loadingView.visibility=View.GONE; fortuneButton.isEnabled=true
        }
    }
    private fun expandFortuneCard(view: View) {
        if (isExpanded) return; isExpanded = true
        val scroll = view.findViewById<ScrollView>(R.id.resultScrollView)
        val set = TransitionSet().apply { addTransition(Slide(Gravity.TOP)); addTransition(Fade(Fade.IN)); duration=450 }
        TransitionManager.beginDelayedTransition(rootLayout as ViewGroup, set)
        fortuneCard.visibility = View.VISIBLE
        val targetH = (resources.displayMetrics.heightPixels * 0.80f).toInt()
        the@ run {
            val curH = max(scroll?.height ?: 160, 160)
            ValueAnimator.ofInt(curH, targetH).apply {
                duration = 700; startDelay=150; interpolator = DecelerateInterpolator()
                addUpdateListener { a -> scroll?.layoutParams?.height = (a.animatedValue as Int); scroll?.requestLayout() }
                start()
            }
        }
    }

    /** 로딩 중 카드의 모든 컨텐츠 숨김/표시 */
    private fun setCardContentVisible(visible: Boolean) {
        val vis = if (visible) View.VISIBLE else View.INVISIBLE
        // 상단 KPI
        chips.visibility = vis
        viewLuckyColor.visibility = vis
        tvLuckyNumber.visibility = vis
        tvLuckyTime.visibility = vis

        barPos.visibility = vis; barNeu.visibility = vis; barNeg.visibility = vis
        tvPos.visibility = vis;  tvNeu.visibility = vis;  tvNeg.visibility = vis

        // 본문
        sectionsContainer?.visibility = vis
        resultText.visibility = vis
        layoutChecklist.visibility = vis

        // 하단 액션
        btnCopy.visibility = vis
        btnShare.visibility = vis
        btnDeep.visibility = vis
    }

    /* ===== Bind ===== */
    private fun setLucky(colorHex: String, number: Int, timeRaw: String) {
        val dot = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.parseColor("#FFD54F"))) }
        viewLuckyColor.background = dot
        tvLuckyNumber.text = number.toString()
        tvLuckyTime.text = humanizeLuckyTime(timeRaw).ifBlank { pickLuckyTimeFallback() }
    }
    private fun setEmotionBars(pos: Int, neu: Int, neg: Int) {
        fun v(x: Int) = x.coerceIn(0,100)
        barPos.setProgressCompat(v(pos), true); barNeu.setProgressCompat(v(neu), true); barNeg.setProgressCompat(v(neg), true)
        tvPos.text = "${pos.coerceIn(0,100)}%"; tvNeu.text = "${neu.coerceIn(0,100)}%"; tvNeg.text = "${neg.coerceIn(0,100)}%"
    }
    private fun setKeywords(list: List<String>) {
        chips.removeAllViews(); list.take(4).forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label; isCheckable = false; isClickable = false
                setTextColor(Color.WHITE)
                setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#334E68")))
            }; chips.addView(chip)
        }
    }
    private fun setChecklist(items: List<String>) {
        val today = todayPersonaKey()
        val safe = sanitizeChecklist(items)
        layoutChecklist.removeAllViews()
        layoutChecklist.visibility = View.VISIBLE
        safe.forEachIndexed { idx, text ->
            val cb = CheckBox(requireContext()).apply {
                this.text = "• $text"
                setTextColor(Color.parseColor("#F0F4F8"))
                textSize = 14f
                isChecked = prefs.getBoolean("fortune_check_${today}_$idx", false)
                setPadding(8, 10, 8, 10)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("fortune_check_${today}_$idx", checked).apply()
                }
            }
            layoutChecklist.addView(cb)
        }
    }

    /* ===== 색상/점수 ===== */
    private fun scoreColor(score: Int): Int = when {
        score >= 70 -> Color.parseColor("#17D7A0")
        score >= 40 -> Color.parseColor("#FFC107")
        else        -> Color.parseColor("#FF5252")
    }

    /* ===== 총운 산식 ===== */
    private fun calcTotalScore(sectionScores: List<Int>, luckyBoost: Int = 0): Int {
        if (sectionScores.isEmpty()) return 0
        val avg = sectionScores.average()
        val lowCnt = sectionScores.count { it < 50 }
        val boost = luckyBoost.coerceIn(0, 6)
        val raw = (avg + boost).coerceAtMost(if (lowCnt >= 2) 75.0 else 95.0)
        val minBound = (sectionScores.minOrNull() ?: 0) - 5
        val maxBound = (sectionScores.maxOrNull() ?: 100) + 8
        return raw.roundToInt().coerceIn(minBound, maxBound).coerceIn(0, 100)
    }

    /* ===== 섹션 카드 ===== */
    private fun renderSectionCards(obj: JSONObject): Boolean {
        val container = sectionsContainer ?: return false
        container.removeAllViews()

        val sections = obj.optJSONObject("sections") ?: JSONObject()
        val lottoNums = obj.optJSONArray("lottoNumbers")

        fun addCard(title: String, key: String) {
            val s = sections.optJSONObject(key) ?: JSONObject()
            val score = s.optInt("score", -1).coerceIn(40, 100)
            val bodyText = s.optString("text").ifBlank { s.optString("advice") }.trim()

            val card = layoutInflater.inflate(R.layout.item_fortune_section, container, false) as LinearLayout
            card.findViewById<TextView>(R.id.tvSectionTitle).text = title

            val badge = card.findViewById<TextView>(R.id.tvScoreBadge)
            val color = scoreColor(score)
            badge.apply {
                text = "${score}점"
                background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(color) }
                visibility = if (key == "lotto") View.GONE else View.VISIBLE
            }

            val prog = card.findViewById<LinearProgressIndicator>(R.id.sectionIndicator)
            prog.apply {
                visibility = if (key == "lotto") View.GONE else View.VISIBLE
                setProgressCompat(score, true)
                setIndicatorColor(color)
                trackColor = Color.parseColor("#2B3B4D")
            }

            val body = card.findViewById<TextView>(R.id.tvSectionBody)
            body.text = if (key == "lotto") {
                if (lottoNums != null && lottoNums.length() == 6) {
                    val arr = (0 until 6).map { lottoNums.optInt(it) }.sorted()
                    "번호: ${arr.joinToString(", ")}"
                } else "번호: -"
            } else bodyText.ifBlank { "오늘 흐름과 실행 팁을 확인해보세요." }

            if (key != "lotto") {
                card.isClickable = true
                card.setOnClickListener { openSectionDialog(title, score, s.optString("text"), s.optString("advice")) }
            }

            container.addView(card)
            card.alpha = 0f; card.translationY = 12f
            card.animate().alpha(1f).translationY(0f).setDuration(260).start()
        }

        addCard("총운","overall"); addCard("연애운","love"); addCard("학업운","study")
        addCard("직장운","work"); addCard("재물운","money"); addCard("로또운","lotto")
        return true
    }

    private fun bindFromPayload(obj: JSONObject) {
        val kwArr = obj.optJSONArray("keywords") ?: JSONArray()
        val kws = (0 until kwArr.length()).mapNotNull { kwArr.optString(it).takeIf { it.isNotBlank() } }
        if (kws.isNotEmpty()) setKeywords(kws)

        val lucky = obj.optJSONObject("lucky")
        setLucky(
            lucky?.optString("colorHex").orEmpty().ifBlank { luckyPalette.random() },
            lucky?.optInt("number", 7) ?: 7,
            lucky?.optString("time").orEmpty().ifBlank { pickLuckyTimeFallback() }
        )

        val emo = obj.optJSONObject("emotions")
        setEmotionBars(
            emo?.optInt("positive", 60) ?: 60,
            emo?.optInt("neutral", 25) ?: 25,
            emo?.optInt("negative", 15) ?: 15
        )

        val rendered = renderSectionCards(obj)
        resultText.visibility = if (rendered) View.GONE else View.VISIBLE
        if (!rendered) resultText.text = formatSections(obj)

        // ✅ 오늘의 체크 (항상 3개, 시간/개인지시 제거)
        val aiChecklist = obj.optJSONArray("checklist")
        val items = (0 until (aiChecklist?.length() ?: 0))
            .mapNotNull { aiChecklist?.optString(it)?.takeIf { it.isNotBlank() } }
        setChecklist(sanitizeChecklist(items))
    }

    private fun formatSections(obj: JSONObject): CharSequence {
        val sb = StringBuilder()
        val sections = obj.optJSONObject("sections") ?: JSONObject()
        val lottoNums = obj.optJSONArray("lottoNumbers")
        fun line(label: String, key: String) {
            val s = sections.optJSONObject(key) ?: JSONObject()
            val score = s.optInt("score", -1)
            val text = s.optString("text").ifBlank { s.optString("advice") }
            sb.append(label); if (score >= 0) sb.append(" (${score}점)")
            if (key == "lotto") {
                if (lottoNums != null && lottoNums.length() == 6) {
                    val arr = (0 until 6).map { lottoNums.optInt(it) }.sorted()
                    sb.append("  번호: ${arr.joinToString(", ")}\n")
                } else sb.append("\n"); return
            }
            if (text.isNotBlank()) { sb.append(" - ").append(text.trim()) }
            sb.append("\n")
        }
        line("총운","overall"); line("연애운","love"); line("학업운","study")
        line("직장운","work"); line("재물운","money"); line("로또운","lotto")
        return sb.toString()
    }

    /* ===== Networking (Daily) ===== */
    private fun fetchFortune(u: UserInfo, today: String, seed: Int, view: View, attempt: Int = 1) {
        if (!isAdded) return

        // ── 요청 페이로드 미리 생성
        val body = buildDailyRequest(u, seed)

        // 로그: 요청 직전(누가/언제/시드/모델/메시지 수)
        val model = body.optString("model", "unknown")
        val messagesCount = body.optJSONArray("messages")?.length() ?: -1
        Log.d(TAG, "🚀 fetchFortune start | today=$today, seed=$seed, user=${u.nickname}, model=$model, messages=$messagesCount, attempt=$attempt")

        // 디버그용으로 JSON 일부만 찍기 (민감정보/키 제외, 800자 제한)
        runCatching {
            val safePreview = body.toString().take(800)
            Log.v(TAG, "🧾 request body preview: ${safePreview}")
        }

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        val t0 = System.currentTimeMillis()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                val dt = System.currentTimeMillis() - t0
                Log.e(TAG, "❌ onFailure ($dt ms) | attempt=$attempt | error=${e.message}", e)

                if (!isAdded) return
                requireActivity().runOnUiThread {
                    onFetchFailed(u, today, seed, view, attempt, e.message ?: "io")
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val raw = response.body?.string().orEmpty()
                val dt = System.currentTimeMillis() - t0

                if (!isAdded) return

                // HTTP 상태 로그
                Log.d(TAG, "📩 onResponse ($dt ms) | code=${response.code} | len=${raw.length}")

                if (!response.isSuccessful) {
                    // 본문 일부 미리보기(길이 제한)
                    Log.e(TAG, "⚠️ HTTP error ${response.code} | bodyPreview=${raw.take(400)}")
                    requireActivity().runOnUiThread {
                        onFetchFailed(u, today, seed, view, attempt, "http ${response.code}")
                    }
                    return
                }

                // 응답 JSON 미리보기 (길이 제한)
                Log.v(TAG, "🧾 response body preview: ${raw.take(800)}")

                requireActivity().runOnUiThread {
                    val payload = try {
                        val root = JSONObject(raw)
                        val msg = root.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")

                        // tool_calls 우선
                        val tc = msg.optJSONArray("tool_calls")
                        if (tc != null && tc.length() > 0) {
                            val args = tc.getJSONObject(0)
                                .getJSONObject("function")
                                .getString("arguments")
                            Log.d(TAG, "🔧 tool_calls detected. args preview=${args.take(400)}")
                            validateAndFill(JSONObject(args), seed)
                        } else {
                            // function_call (구형) 또는 content JSON
                            val fc = msg.optJSONObject("function_call")
                            if (fc != null) {
                                val args = fc.optString("arguments", "{}")
                                Log.d(TAG, "🧩 function_call detected. args preview=${args.take(400)}")
                                validateAndFill(JSONObject(args), seed)
                            } else {
                                val content = msg.optString("content")
                                Log.d(TAG, "✉️ content fallback. preview=${content.take(400)}")
                                parsePayloadAlways(content, seed)
                            }
                        }
                    } catch (ex: Exception) {
                        Log.w(TAG, "🟨 parse fallback path due to exception: ${ex.message}")
                        parsePayloadAlways(raw, seed)
                    }

                    // 최종 보정 + 체크리스트 정제
                    val adjusted = finalizePayload(payload, seed).apply {
                        val cl = optJSONArray("checklist")
                        val cleaned = sanitizeChecklist(
                            (0 until (cl?.length() ?: 0)).mapNotNull { cl?.optString(it) }
                        )
                        put("checklist", JSONArray().apply { cleaned.forEach { put(it) } })
                    }

                    // 바인딩
                    lastPayload = adjusted
                    bindFromPayload(adjusted)

                    // 캐시 & 오늘 사용 플래그
                    prefs.edit()
                        .putString("fortune_payload_${todayPersonaKey()}", adjusted.toString())
                        .putBoolean("fortune_seen_${todayPersonaKey()}", true)
                        .apply()

                    // 버튼/레이아웃 정리
                    fortuneButton.visibility = View.GONE
                    relayoutCardToTop()

                    // Firestore 저장
                    FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                        saveDailyFortuneToFirestore(uid, today, adjusted)
                    }

                    // 로딩 종료 → 카드 컨텐츠 표시
                    showLoading(false)
                    setCardContentVisible(true)

                    view.findViewById<ScrollView>(R.id.resultScrollView)?.post {
                        val sv = view.findViewById<ScrollView>(R.id.resultScrollView)
                        sv?.scrollTo(0, 0); sv?.fullScroll(View.FOCUS_UP)
                    }
                    expandFortuneCard(view)

                    // 최종 로그: 핵심 KPI 요약
                    val sec = adjusted.optJSONObject("sections")
                    val overall = sec?.optJSONObject("overall")?.optInt("score", -1)
                    val lucky = adjusted.optJSONObject("lucky")
                    Log.i(
                        TAG,
                        "✅ bind done | overall=$overall, luckyColor=${lucky?.optString("colorHex")}, " +
                                "luckyNum=${lucky?.optInt("number")}, luckyTime=${lucky?.optString("time")}"
                    )
                }
            }
        })
    }


    private fun onFetchFailed(
        u: UserInfo,
        today: String,
        seed: Int,
        view: View,
        attempt: Int,
        reason: String
    ) {
        if (!isAdded) return

        // 1) 로그: 왜 실패했는지 남기기
        Log.w(TAG, "⚠️ onFetchFailed | attempt=$attempt | reason=$reason | today=$today, user=${u.nickname}")

        requireActivity().runOnUiThread {
            // 2) 재시도 1회: 로딩 유지 + 카드 컨텐츠 가림(깜빡임 방지)
            if (attempt == 1) {
                // 재시도 직전에 로딩 모드로 전환
                setCardContentVisible(false)
                showLoading(true)
                // 즉시 2차 시도
                fetchFortune(u, today, seed, view, attempt = 2)
                return@runOnUiThread
            }

            // 3) 최종 실패: 로딩 해제 + 컨텐츠 보이기
            showLoading(false)
            setCardContentVisible(true)

            // 사용자 친화적 메시지로 매핑
            val userMsg = when {
                reason.startsWith("http 401") -> "인증 오류가 발생했어요. 설정의 API 키를 확인해주세요. ($reason)"
                reason.startsWith("http 403") -> "접근이 거부되었어요. 권한/결제 상태를 확인해주세요. ($reason)"
                reason.startsWith("http 404") -> "서버 주소를 찾지 못했어요. 잠시 후 다시 시도해주세요. ($reason)"
                reason.startsWith("http 429") -> "호출 한도가 초과되었어요. 잠시 기다렸다가 다시 시도해주세요. ($reason)"
                reason.startsWith("http 5")   -> "서버가 혼잡해요. 잠시 후 다시 시도해주세요. ($reason)"
                reason.contains("timeout", ignoreCase = true) -> "네트워크가 지연되고 있어요. 연결 상태를 확인한 뒤 다시 시도해주세요. ($reason)"
                else -> "네트워크 오류가 발생했어요. ($reason)"
            }

            // 결과 영역에 표시
            resultText.text = userMsg

            // 감정 바는 시드 기반 프리셋으로 채워서 '빈 화면' 느낌 방지
            val (p, n, ng) = seededEmotions(seed)
            setEmotionBars(p, n, ng)

            // 버튼 복구(다시 시도할 수 있게 센터로)
            fortuneButton.visibility = View.VISIBLE
            applyPrimaryButtonStyle()
            moveButtonCentered()
            expandFortuneCard(view)

            // 추가로 사용자에게 토스트도 한 번(가볍게)
            Toast.makeText(requireContext(), userMsg, Toast.LENGTH_SHORT).show()
        }
    }


    /* ===== Schema & Prompts ===== */
    private fun fortuneSchema(): JSONObject {
        val obj = JSONObject()
        obj.put("type","object")
        obj.put("required", JSONArray().apply {
            put("lucky"); put("sections"); put("keywords"); put("emotions"); put("checklist"); put("tomorrow")
        })
        obj.put("properties", JSONObject().apply {
            put("lucky", JSONObject().apply {
                put("type","object")
                put("required", JSONArray().apply { put("colorHex"); put("number"); put("time") })
                put("properties", JSONObject().apply {
                    put("colorHex", JSONObject().put("type","string").put("pattern","#[0-9A-Fa-f]{6}"))
                    put("number", JSONObject().put("type","integer").put("minimum",10).put("maximum",99))
                    put("time", JSONObject().put("type","string"))
                })
            })
            put("sections", JSONObject().apply {
                put("type","object")
                put("required", JSONArray().apply { put("overall"); put("love"); put("study"); put("work"); put("money"); put("lotto") })
                fun sec() = JSONObject().apply {
                    put("type","object")
                    put("required", JSONArray().apply { put("score"); put("text"); put("advice") })
                    put("properties", JSONObject().apply {
                        put("score", JSONObject().put("type","integer").put("minimum",40).put("maximum",100))
                        put("text", JSONObject().put("type","string"))
                        put("advice", JSONObject().put("type","string"))
                    })
                }
                put("overall", sec()); put("love", sec()); put("study", sec()); put("work", sec()); put("money", sec()); put("lotto", sec())
            })
            put("keywords", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")); put("minItems",1); put("maxItems",4) })
            put("emotions", JSONObject().apply {
                put("type","object"); put("required", JSONArray().apply { put("positive"); put("neutral"); put("negative") })
                put("properties", JSONObject().apply {
                    put("positive", JSONObject().put("type","integer").put("minimum",20).put("maximum",90))
                    put("neutral",  JSONObject().put("type","integer").put("minimum",10).put("maximum",50))
                    put("negative", JSONObject().put("type","integer").put("minimum",5).put("maximum",35))
                })
            })
            put("lottoNumbers", JSONObject().apply {
                put("type","array"); put("items", JSONObject().put("type","integer").put("minimum",1).put("maximum",45)); put("minItems",6); put("maxItems",6)
            })
            put("checklist", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")); put("minItems",3); put("maxItems",3) })
            put("tomorrow", JSONObject().apply { put("type","object"); put("required", JSONArray().apply { put("long") }); put("properties", JSONObject().apply { put("long", JSONObject().put("type","string")) }) })
        })
        return obj
    }

    private fun styleTokens(seed: Int): String {
        val bank = listOf(
            "차분한","단단한","선명한","기민한","유연한","담백한","리더십","분석적","균형감","민첩함",
            "집중","꾸준함","정갈함","실용","낙관","침착","절제","명료","차분집중"
        )
        val r = Random(seed)
        return (0 until 3).map { bank[r.nextInt(bank.size)] }.distinct().joinToString(",")
    }

    private fun buildUserPrompt(u: UserInfo, seed: Int): String {
        val today = todayKey(); val weekday = SimpleDateFormat("EEEE", Locale.KOREAN).format(Date())
        val userAge = ageOf(u.birth); val tag = ageTag(userAge)
        val avoidColors = JSONArray(getRecentLuckyColors()); val avoidTimes = JSONArray(getRecentLuckyTimes())
        val avoidNumbers = JSONArray(getRecentLuckyNumbers())
        val palette = JSONArray(luckyPalette)
        val tone = styleTokens(seed)
        return """
[사용자]
nickname:"${u.nickname}", mbti:"${u.mbti}", birthdate:"${u.birth}", birth_time:"${u.birthTime}", gender:"${u.gender}"
date:"$today ($weekday)", age:$userAge, age_tag:$tag, seed:$seed, tone:"$tone"

[출력 가이드(엄격)]
- **금지어:** ‘리듬’ 사용 금지.
- **학생/학교 어휘 금지:** 숙제/과제/수업/강의/시험/퀴즈/레포트/제출 등 금지.
- **연락 지시 금지:** 전화/메시지/DM/카톡/연락/초대 등 금지.
- **checklist 규칙:** 개인지칭·특정 인물 금지, **시간/마감 표현 금지(오전/오후/몇 시/까지/전 X)**, 오늘 바로 실행 가능한 3개(12~18자).
- 모든 섹션 score는 40~100, .
- 각 섹션 text는 2~3문장(80~160자)로 **조금 더 상세히**, 성숙하고 담백한 톤 + 실행 팁 1개. tone="$tone"를 글결에 살짝 반영.
- lucky.colorHex는 palette에서 선택, 최근(5일) 중복 회피(avoidColors/avoidTimes).
- lucky.number는 10~99, 최근(5일) 회피(avoidNumbers).
- lucky.time은 ‘오전/오후 HH시(~HH시)’ 표기(자시/축시 금지).
- emotions는 현실적 분포, lottoNumbers는 6개(1~45).

[심화 유도]
- 내일 대비 계획을 tomorrow.long(400~700자)에 ‘아침/오후/저녁’ 소제목으로.
- 오늘 최저 점수 영역 보완 액션(정량) 포함.

palette:$palette, avoidColors:$avoidColors, avoidTimes:$avoidTimes, avoidNumbers:$avoidNumbers
        """.trimIndent()
    }

    /* ===== Parse / Normalize ===== */
    private fun defaultSectionCopy(key: String, score: Int, luckyTime: String, luckyNumber: Int): Pair<String,String> {
        val mood = when {
            score >= 85 -> "상승세가 뚜렷합니다."
            score >= 70 -> "흐름이 안정적입니다."
            score >= 55 -> "기복이 있으니 속도를 조절하세요."
            else -> "기대치보다 낮아 기본을 단단히 하는 날입니다."
        }
        val text = when (key) {
            "love"  -> "관계에서는 $mood 말보다 태도가 신뢰를 만듭니다. 감정선을 과장하지 말고, 편안한 주제로 속도를 맞춰보세요."
            "study" -> "학습 집중도는 $mood 분량을 줄여도 꾸준함을 유지하면 성과가 납니다. 핵심 키워드를 먼저 붙잡아 보세요."
            "work"  -> "업무 흐름은 $mood 목표를 하나로 좁히면 효율이 올라갑니다. 산만함을 줄이고 우선순위를 재정렬하세요."
            "money" -> "재정 운은 $mood 즉흥지출을 삼가고 지출 1건만 점검해도 균형을 지킬 수 있습니다."
            "overall"-> "오늘 전반은 $mood 작은 성취를 차곡차곡 쌓기 좋습니다. 욕심을 덜고 기본 루틴을 지키면 안정감이 커집니다."
            else    -> mood
        }
        val advice = when (key) {
            "love"  -> "자극적인 화제 대신 편안한 대화로 분위기 안정시키기."
            "study" -> "집중 구간 2회, 핵심 1개만 끝내기."
            "work"  -> "한가지에 집중하기."
            "money" -> "필요 지출만 남기고 오늘 1건 점검하기."
            "overall"-> "큰 목표보다는 확실한 한 가지에 집중하기."
            else    -> "체크리스트 1개를 지금 실행해보세요."
        }
        return text to advice
    }

    private fun parsePayloadAlways(content: String, seed: Int): JSONObject {
        val txt = content.trim()
        try { return validateAndFill(JSONObject(txt), seed) } catch (_: Exception) {}
        Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```").find(txt)?.let { try { return validateAndFill(JSONObject(it.groupValues[1]), seed) } catch (_: Exception) {} }
        extractJsonObject(txt)?.let { raw -> try { return validateAndFill(JSONObject(raw), seed) } catch (_: Exception) {} }
        return buildFallbackFromText(txt, seed)
    }
    private fun extractJsonObject(text: String): String? {
        var d = 0; var s = -1
        for (i in text.indices) when (text[i]) { '{' -> { if (d==0) s=i; d++ }; '}' -> { d--; if (d==0 && s>=0) return text.substring(s, i+1) } }
        return null
    }

    private fun validateAndFill(obj: JSONObject, seed: Int): JSONObject {
        // Lucky (HEX는 팔레트 강제, 숫자·시간 히스토리 회피)
        val lucky = (obj.optJSONObject("lucky") ?: JSONObject()).apply {
            val chosenHex = optString("colorHex").takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) && luckyPalette.contains(it.uppercase()) }
                ?: pickLuckyColorFallback()
            var num = optInt("number", pickLuckyNumberFallback(seed)).coerceIn(10, 99)
            if (getRecentLuckyNumbers().contains(num)) {
                // 최근 숫자와 겹치면 seed기반 변형
                num = ((num + (abs(seed) % 7) + 11) % 90) + 10
            }
            val t = humanizeLuckyTime(optString("time").ifBlank { pickLuckyTimeFallback() })
            put("colorHex", chosenHex)
            put("number", num)
            put("time", t)
        }
        obj.put("lucky", lucky)

        // Emotions
        val (p, n, ng) = seededEmotions(seed)
        val emo = (obj.optJSONObject("emotions") ?: JSONObject()).apply {
            put("positive", optInt("positive", p).coerceIn(20, 90))
            put("neutral",  optInt("neutral",  n).coerceIn(10, 50))
            put("negative", optInt("negative", ng).coerceIn(5, 35))
        }
        obj.put("emotions", emo)

        // Sections
        val secIn = obj.optJSONObject("sections")
        val sec = secIn ?: JSONObject()
        val keys = listOf("overall","love","study","work","money","lotto")
        if (secIn == null) { seededSectionScores(seed).forEach { (k,v) -> sec.put(k, JSONObject().put("score", v).put("text","").put("advice","")) } }
        val lt = lucky.optString("time"); val ln = lucky.optInt("number", pickLuckyNumberFallback(seed))
        keys.forEach { k ->
            val s = sec.optJSONObject(k) ?: JSONObject().also { sec.put(k, it) }
            val sc = s.optInt("score", 70).coerceIn(40, 100)
            s.put("score", sc)
            val curText = s.optString("text").trim(); val curAdv = s.optString("advice").trim()
            if (k != "lotto" && (curText.isBlank() || curAdv.isBlank())) {
                val (t, a) = defaultSectionCopy(k, sc, lt, ln)
                if (curText.isBlank()) s.put("text", t)
                if (curAdv.isBlank())  s.put("advice", a)
            }
        }
        // 총운 재계산
        val baseKeys = listOf("love","study","work","money")
        val baseScores = baseKeys.mapNotNull { sec.optJSONObject(it)?.optInt("score", 70) }.ifEmpty { listOf(70,70,70,70) }
        val overallScore = calcTotalScore(baseScores, luckyBoost = 0)
        sec.optJSONObject("overall")?.put("score", overallScore)
        sec.optJSONObject("lotto")?.put("text","")?.put("advice","")
        obj.put("sections", sec)

        // Lotto
        val lotto = obj.optJSONArray("lottoNumbers")
        if (lotto == null || lotto.length() != 6) obj.put("lottoNumbers", JSONArray().apply { genLottoNumbers(seed).forEach { put(it) } })

        // Checklist (정제)
        val cl = obj.optJSONArray("checklist")
        val itemsRaw = (0 until (cl?.length() ?: 0)).mapNotNull { cl?.optString(it) }
        val itemsClean = sanitizeChecklist(itemsRaw)
        obj.put("checklist", JSONArray().apply { itemsClean.forEach { put(it) } })

        // Tomorrow plan
        val tObj = (obj.optJSONObject("tomorrow") ?: JSONObject())
        val longFixed = normalizePlan(tObj.optString("long"), lt, ln, lucky.optString("colorHex"))
            .ifBlank { makeTomorrowPlan(obj) }
        tObj.put("long", longFixed); obj.put("tomorrow", tObj)

        return obj
    }

    private fun buildFallbackFromText(txt: String, seed: Int): JSONObject {
        val (p, n, ng) = seededEmotions(seed)
        val base = JSONObject().apply {
            put("keywords", JSONArray())
            put("lucky", JSONObject().apply { put("colorHex", pickLuckyColorFallback()); put("number", pickLuckyNumberFallback(seed)); put("time", pickLuckyTimeFallback()) })
            put("emotions", JSONObject().apply { put("positive", p); put("neutral", n); put("negative", ng) })
            put("sections", JSONObject()); put("checklist", JSONArray(buildEssentialChecklist(JSONObject())))
            put("lottoNumbers", JSONArray().apply { genLottoNumbers(seed).forEach { put(it) } })
        }
        base.put("tomorrow", JSONObject().put("long", makeTomorrowPlan(base)))
        return validateAndFill(base, seed)
    }

    private fun finalizePayload(payload: JSONObject, seed: Int): JSONObject {
        payload.optJSONObject("lucky")?.let {
            val c = it.optString("colorHex"); val t = it.optString("time"); val n = it.optInt("number", -1)
            if (c.isNotBlank()) pushHistory("lucky_history_colors", c)
            if (t.isNotBlank()) pushHistory("lucky_history_times",  t)
            if (n in 10..99)      pushHistory("lucky_history_numbers", n.toString())
        }
        return payload
    }

    /* ===== Section dialog (상세 강화) ===== */
    /* ===== Section dialog (상세 강화) ===== */
    private fun buildSectionDetails(title: String, score: Int, text: String?, advice: String?): String {
        val base = text?.trim().orEmpty().ifBlank { "오늘의 흐름을 간결히 정리했어요." }
        val tip  = advice?.trim().orEmpty().let { if (it.isNotBlank()) "• $it" else "" }

        fun extraBy(title: String, score: Int): String {
            return when (title) {
                "총운" -> when {
                    score >= 85 -> "• 기회: 자신 있는 첫 걸음을 크게 끊으면 하루 전체가 따라옵니다."
                    score >= 70 -> "• 유지: 목표를 1개로 고정하고, 과감히 나머지는 내일로 미루세요."
                    score >= 55 -> "• 주의: 선택을 줄여 피로를 낮추고, 가벼운 완료 1개로 리듬을 만드세요."
                    else        -> "• 복구: 쉬운 일 10분만, 오늘은 감점 요소를 만들지 않는 것이 최우선."
                }
                "연애운" -> when {
                    score >= 85 -> "• 기회: 가벼운 칭찬·공감으로 분위기가 빠르게 따뜻해집니다."
                    score >= 70 -> "• 유지: 민감한 주제는 피하고, 편안한 대화로 속도를 맞추세요."
                    score >= 55 -> "• 주의: 과한 해석은 금물. 텍스트 길이를 줄이고 간결하게."
                    else        -> "• 복구: 기대치 낮추고 안부·감사 한 줄로 따뜻함만 남기기."
                }
                "학업운" -> when {
                    score >= 85 -> "• 기회: 가장 자신 있는 파트로 25분 집중 2회, 성취감 극대화."
                    score >= 70 -> "• 유지: 분량을 줄이되 핵심 개념 1개를 완전 정복."
                    score >= 55 -> "• 주의: 노트 5줄 요약만 남기는 ‘가벼운 완성’ 전략."
                    else        -> "• 복구: 예열용 문제 3개로 감을 되찾고 종료."
                }
                "직장운" -> when {
                    score >= 85 -> "• 기회: 임팩트 높은 태스크 1건을 먼저 끝내면 생산성이 급상승."
                    score >= 70 -> "• 유지: 범위를 좁혀 진행(서브태스크 2개 이내) → 전진감 확보."
                    score >= 55 -> "• 주의: 동시에 여러 일 금지. 체크리스트를 반으로 줄이기."
                    else        -> "• 복구: 난도 낮은 정리·정돈 1건으로 복귀 동력 만들기."
                }
                "재물운" -> when {
                    score >= 85 -> "• 기회: 소액이라도 수익/정산을 확정하면 체감 효과가 큽니다."
                    score >= 70 -> "• 유지: 오늘은 ‘지출 카테고리 하나’만 정리해 균형을 지키세요."
                    score >= 55 -> "• 주의: 충동지출 경계. 비필수 구매는 보류하고 ‘비필수 0건’ 원칙."
                    else        -> "• 복구: 지출 1건 확인·정리로 신뢰 회복부터."
                }
                "로또운" -> when {
                    score >= 85 -> "• 참고: 숫자에 기대기보다 재정 습관을 강화하면 운의 누수가 줄어듭니다."
                    score >= 70 -> "• 참고: 오락의 범위를 넘기지 않도록 상한선을 미리 정하세요."
                    score >= 55 -> "• 참고: 기대치를 낮추고 장기적인 저축·투자 습관에 비중."
                    else        -> "• 참고: 오늘은 휴식. 정보 탐색·분석에 시간을 쓰지 마세요."
                }
                else -> when {
                    score >= 85 -> "• 기회: 자신 있는 방식으로 시작을 끊으면 파급력이 큽니다."
                    score >= 70 -> "• 유지: 범위를 좁혀 꾸준함을 확보하세요."
                    score >= 55 -> "• 주의: 산만함을 줄이고 한 번에 하나만 처리하세요."
                    else        -> "• 복구: 쉬운 단계부터 작은 완성을 만들며 컨디션을 올리세요."
                }
            }
        }

        val extra = extraBy(title, score)
        return listOf(base, tip, extra).filter { it.isNotBlank() }.joinToString("\n\n")
    }


    private fun openSectionDialog(title: String, score: Int, text: String?, advice: String?) {
        val content = layoutInflater.inflate(R.layout.dialog_fortune_section, null)

        val rootCard = content.findViewById<MaterialCardView>(R.id.dialogRoot)
        val tvTitle  = content.findViewById<TextView>(R.id.tvSectionDialogTitle)
        val tvScore  = content.findViewById<TextView>(R.id.tvSectionDialogScore)
        val tvBody   = content.findViewById<TextView>(R.id.tvSectionDialogBody)
        val btnClose = content.findViewById<MaterialButton>(R.id.btnSectionDialogClose)

        content.findViewById<NestedScrollView>(R.id.scrollBody)

        tvTitle.text = title
        tvScore.text = "${score}점"
        tvScore.background = GradientDrawable().apply {
            cornerRadius = dp(999).toFloat()
            setColor(scoreColor(score))
        }

        tvBody.text = buildSectionDetails(title, score, text, advice)

        val dlg = MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        ).setView(content).create().apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        dlg.setOnShowListener {
            val dm = resources.displayMetrics
            dlg.window?.setLayout((dm.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            rootCard.scaleX = 0.96f; rootCard.scaleY = 0.96f; rootCard.alpha = 0f
            rootCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).start()
        }

        btnClose.setTextColor(Color.WHITE)
        btnClose.background = gradientBg(*BTN_GRAD)
        btnClose.setOnClickListener { dlg.dismiss() }
        dlg.show()
    }

    /* ===== Deep (reward gate & dialog) ===== */
    private fun openDeepWithGate() {
        val key = "fortune_deep_unlocked_${todayPersonaKey()}"
        if (prefs.getBoolean(key, false)) { openDeepNow(); return }

        val bs = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val btnCancel = v.findViewById<Button>(R.id.btnCancel)
        val btnWatch = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWatchAd)
        val textStatus = v.findViewById<TextView>(R.id.textStatus)
        val progress = v.findViewById<ProgressBar>(R.id.progressAd)

        btnCancel.setOnClickListener { bs.dismiss() }
        btnWatch.setOnClickListener {
            btnWatch.isEnabled = false
            progress.visibility = View.VISIBLE
            textStatus.text = "광고 준비 중…"

            AdManager.showRewarded(
                requireActivity(),
                onRewardEarned = {
                    prefs.edit().putBoolean(key, true).apply()
                    progress.visibility = View.GONE
                    textStatus.text = "보상 확인됨"
                    bs.dismiss()
                    openDeepNow()
                    AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    btnWatch.isEnabled = true
                    progress.visibility = View.GONE
                    textStatus.text = "광고가 닫혔어요. 보상을 받지 못했습니다."
                },
                onFailed = { reason ->
                    Toast.makeText(requireContext(),"광고 실패($reason) → 심화분석 바로 열기", Toast.LENGTH_SHORT).show()
                    bs.dismiss()
                    openDeepNow()
                }
            )
        }
        bs.setContentView(v)
        bs.show()
    }

    private fun openDeepNow() {
        if (lastPayload == null) {
            Toast.makeText(requireContext(),"먼저 ‘행운 보기’를 실행해주세요.",Toast.LENGTH_SHORT).show()
            return
        }
        val today = todayPersonaKey()
        val cached = prefs.getString("fortune_deep_$today", null)
        if (cached != null) { runCatching { JSONObject(cached) }.onSuccess { showDeepDialog(it) }; return }

        btnDeep.isEnabled=false; btnDeep.alpha=0.7f; btnDeep.text="생성 중…"
        val u = loadUserInfoStrict(); val seed = seedForToday(u)
        fetchDeepAnalysis(u, lastPayload!!, seed) { result, _ ->
            btnDeep.isEnabled=true; btnDeep.alpha=1f; btnDeep.text="심화 분석 보기"
            result?.let { prefs.edit().putString("fortune_deep_$today", it.toString()).apply() }
            showDeepDialog(result ?: JSONObject())
        }
    }

    private fun fetchDeepAnalysis(u: UserInfo, daily: JSONObject, seed: Int, cb: (JSONObject?, String?) -> Unit) {
        val body = JSONObject().apply {
            put("model","gpt-4.1-mini"); put("temperature",0.8)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role","system").put("content","당신은 프리미엄 라이프 코치이자 운세 분석가입니다. 도구만 호출해 JSON을 반환하세요."))
                put(JSONObject().put("role","user").put("content", buildDeepPrompt(u, daily, seed)))
            })
            put("tools", JSONArray().put(JSONObject().apply {
                put("type","function")
                put("function", JSONObject().apply {
                    put("name","deep_fortune_analysis")
                    put("description","오늘 운세 기반의 심화 분석(전문가 톤) 반환")
                    put("parameters", deepSchema())
                })
            }))
            put("tool_choice", JSONObject().apply { put("type","function"); put("function", JSONObject().put("name","deep_fortune_analysis")) })
            put("max_tokens", 2200)
        }
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization","Bearer $apiKey")
            .addHeader("Content-Type","application/json").build()

        http.newCall(req).enqueue(object: Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { if (!isAdded) return; requireActivity().runOnUiThread { cb(null, e.message) } }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val raw = response.body?.string().orEmpty(); if (!isAdded) return
                if (!response.isSuccessful) { requireActivity().runOnUiThread { cb(null, "http ${response.code}") }; return }
                try {
                    val root = JSONObject(raw)
                    val msg = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    val tc = msg.optJSONArray("tool_calls")
                    val args = if (tc != null && tc.length() > 0) tc.getJSONObject(0).getJSONObject("function").getString("arguments")
                    else msg.getJSONObject("function_call").getString("arguments")
                    val deep = JSONObject(args)
                    requireActivity().runOnUiThread { cb(deep, null) }
                } catch (_: Exception) { requireActivity().runOnUiThread { cb(null, "parse") } }
            }
        })
    }

    private fun deepSchema(): JSONObject = JSONObject().apply {
        put("type","object")
        put("required", JSONArray().apply {
            put("highlights"); put("plan"); put("tips"); put("luckyColorName"); put("luckyTime"); put("luckyNumber"); put("tomorrowPrep")
        })
        put("properties", JSONObject().apply {
            put("highlights", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")); put("minItems",3); put("maxItems",6) })
            put("plan", JSONObject().apply {
                put("type","object"); put("required", JSONArray().apply { put("morning"); put("afternoon"); put("evening") })
                put("properties", JSONObject().apply {
                    put("morning", JSONObject().put("type","string"))
                    put("afternoon", JSONObject().put("type","string"))
                    put("evening", JSONObject().put("type","string"))
                })
            })
            put("tips", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")); put("minItems",3); put("maxItems",6) })
            put("checklistAdjusted", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")) })
            put("tomorrowPrep", JSONObject().put("type","string"))
            put("luckyColorName", JSONObject().put("type","string"))
            put("luckyTime", JSONObject().put("type","string"))
            put("luckyNumber", JSONObject().put("type","integer"))
        })
    }

    private fun buildDeepPrompt(u: UserInfo, daily: JSONObject, seed: Int): String {
        val age = ageOf(u.birth)
        return """
[입력]
user:{nickname:"${u.nickname}", mbti:"${u.mbti}", birthdate:"${u.birth}", gender:"${u.gender}", age:$age, birth_time:"${u.birthTime}", seed:$seed}
daily:$daily

[요구]
- 전문가 톤의 심화 분석 JSON(function: deep_fortune_analysis).
- **금지어:** ‘리듬’ ,‘블록’ 사용 금지.
- highlights 3–6개(1문장).
- plan: 아침(09–12)/오후(13–17)/저녁(19–22) 2–4줄씩, 정량 지시.
- tips 3–6개 + checklistAdjusted 3개(시간/개인지시 금지).
- tomorrowPrep 250자미만 .
- luckyTime/Number는 daily 유지, colorName은 이름(헥스 금지).
- **연락 지시·학생 어휘·특정 시간/마감 금지.**
-현실적 으로 적기.
        """.trimIndent()
    }

    /* 이름 정규화 */
    private fun sanitizeColorName(nameRaw: String, hex: String): String {
        val m = nameRaw.trim().lowercase(Locale.ROOT)
        val map = mapOf(
            "blue" to "블루", "navy" to "인디고", "indigo" to "인디고", "green" to "그린",
            "orange" to "오렌지", "red" to "레드", "purple" to "퍼플", "violet" to "퍼플",
            "slate" to "슬레이트", "teal" to "틸", "cyan" to "틸", "yellow" to "옐로",
            "brown" to "브라운", "amber" to "옐로"
        )
        val allowed = setOf("블루","인디고","그린","오렌지","레드","퍼플","슬레이트","틸","옐로","브라운")
        val fromMap = map[m]
        return when {
            allowed.contains(nameRaw) -> nameRaw
            fromMap != null -> fromMap
            else -> colorNameForHex(hex)
        }
    }

    private fun showDeepDialog(deep: JSONObject) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_fortune_deep, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDeepTitle)
        val tvKpis  = dialogView.findViewById<TextView>(R.id.tvDeepKpis)
        val tvHigh  = dialogView.findViewById<TextView>(R.id.tvDeepHighlights)
        val tvPlan  = dialogView.findViewById<TextView>(R.id.tvDeepPlan)
        val tvTmr   = dialogView.findViewById<TextView>(R.id.tvDeepTomorrow)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnDeepClose)

        val lucky = lastPayload?.optJSONObject("lucky") ?: JSONObject()
        val colName = sanitizeColorName(deep.optString("luckyColorName"), lucky.optString("colorHex"))
        val time = humanizeLuckyTime(deep.optString("luckyTime", lucky.optString("time")))
        val num  = deep.optInt("luckyNumber", lucky.optInt("number"))

        tvTitle.text = "심화 분석"
        tvKpis.text  = "행운 시간: $time   |   행운 숫자: $num   |   행운색: $colName"

        val hl = (0 until (deep.optJSONArray("highlights")?.length() ?: 0))
            .mapNotNull { deep.optJSONArray("highlights")?.optString(it) }
            .joinToString("\n") { "• $it" }
            .ifBlank { "• 오늘 흐름을 간결히 정리했어요." }
        tvHigh.text = hl

        val plan = deep.optJSONObject("plan") ?: JSONObject()
        tvPlan.text = buildString {
            append("아침(09~12)\n").append(neutralizeCorporateTerms(plan.optString("morning"))).append("\n\n")
            append("오후(13~17)\n").append(neutralizeCorporateTerms(plan.optString("afternoon"))).append("\n\n")
            append("저녁(19~22)\n").append(neutralizeCorporateTerms(plan.optString("evening")))
        }

        val tomorrow = deep.optString("tomorrowPrep", "")
        val extra = buildTomorrowExtraTips(deep, lastPayload)
        tvTmr.text = if (tomorrow.isNotBlank()) "${neutralizeCorporateTerms(tomorrow)}\n\n$extra" else extra

        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            val dm = resources.displayMetrics
            dialog.window?.setLayout((dm.widthPixels*0.94f).toInt(), (dm.heightPixels*0.80f).toInt())
        }
        btnClose.setTextColor(Color.WHITE)
        btnClose.background = gradientBg(*BTN_GRAD)
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /* ===== Lucky helpers, etc. ===== */
    private fun genLottoNumbers(seed: Int): List<Int> {
        val r = Random(seed xor 0x9E3779B9u.toInt()); val set = LinkedHashSet<Int>()
        while (set.size < 6) set += (1 + r.nextInt(45)); return set.toList().sorted()
    }
    private fun getRecentLuckyColors(limit: Int = 5): List<String> {
        val arr = JSONArray(prefs.getString("lucky_history_colors", "[]")); return (0 until arr.length()).mapNotNull { arr.optString(it) }.takeLast(limit)
    }
    private fun getRecentLuckyTimes(limit: Int = 5): List<String> {
        val arr = JSONArray(prefs.getString("lucky_history_times", "[]")); return (0 until arr.length()).mapNotNull { arr.optString(it) }.takeLast(limit)
    }
    private fun getRecentLuckyNumbers(limit: Int = 5): List<Int> {
        val arr = JSONArray(prefs.getString("lucky_history_numbers", "[]"))
        return (0 until arr.length()).mapNotNull { arr.optString(it).toIntOrNull() }.takeLast(limit)
    }
    private fun pushHistory(key: String, value: String) {
        val arr = JSONArray(prefs.getString(key, "[]")); val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list += arr.optString(i); list += value
        prefs.edit().putString(key, JSONArray(list.takeLast(10)).toString()).apply()
    }

    private fun colorNameForHex(hex: String): String = when (hex.uppercase(Locale.ROOT)) {
        "#1E88E5" -> "블루"; "#3949AB" -> "인디고"; "#43A047" -> "그린"; "#FB8C00" -> "오렌지"; "#E53935" -> "레드"
        "#8E24AA" -> "퍼플"; "#546E7A" -> "슬레이트"; "#00897B" -> "틸"; "#FDD835" -> "옐로"; "#6D4C41" -> "브라운"
        else -> "행운색"
    }
    private fun pickLuckyColorFallback(): String = luckyPalette.random()
    private fun pickLuckyTimeFallback(): String {
        val hours = (6..22).map { if (it < 12) "오전 ${it}시" else "오후 ${if (it == 12) 12 else it - 12}시" }; return hours.random()
    }
    private fun pickLuckyNumberFallback(seed: Int): Int = (Random(seed).nextInt(90) + 10)

    private fun humanizeLuckyTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val t = raw.trim()
        Regex("(\\d{1,2}):(\\d{2})~(\\d{1,2}):(\\d{2})").find(t)?.let { m ->
            fun h(hh:String, mm:String): String { val H=hh.toInt(); val ampm=if (H in 0..11) "오전" else "오후"; val h12=when{H==0->12; H<=12->H; else->H-12}; return "$ampm ${h12}시" }
            val s = h(m.groupValues[1], m.groupValues[2]); val e = h(m.groupValues[3], m.groupValues[4]); return if (s==e) s else "$s~$e"
        }
        val map = mapOf("자시" to "오전 12시~오전 1시","축시" to "오전 1시~오전 3시","인시" to "오전 3시~오전 5시","묘시" to "오전 5시~오전 7시",
            "진시" to "오전 7시~오전 9시","사시" to "오전 9시~오전 11시","오시" to "오전 11시~오후 1시","미시" to "오후 1시~오후 3시","신시" to "오후 3시~오후 5시",
            "유시" to "오후 5시~오후 7시","술시" to "오후 7시~오후 9시","해시" to "오후 9시~오후 11시")
        map.entries.firstOrNull { t.contains(it.key) }?.let { return it.value }
        if (Regex("오전|오후").containsMatchIn(t)) return t
        return t
    }

    /* ===== User / Seed ===== */
    data class UserInfo(val nickname: String, val mbti: String, val birth: String, val gender: String, val birthTime: String)
    private fun loadUserInfoStrict(): UserInfo {
        val nn = prefs.getString("nickname","") ?: ""
        val mb = (prefs.getString("mbti","") ?: "").uppercase(Locale.ROOT)
        val bd = prefs.getString("birthdate_iso","") ?: normalizeDate(prefs.getString("birthdate","") ?: "")
        val gd = prefs.getString("gender","") ?: ""
        val bt = prefs.getString("birth_time","선택안함") ?: "선택안함"
        return UserInfo(nn, mb, bd, gd, bt)
    }
    private fun seedForToday(u: UserInfo): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val base = "$uid|${u.nickname}|${u.birth}|${u.gender}|${u.mbti}|${u.birthTime}|${todayKey()}"
        val md = MessageDigest.getInstance("MD5").digest(base.toByteArray())
        return ((md[0].toInt() and 0xFF) shl 24) or ((md[1].toInt() and 0xFF) shl 16) or ((md[2].toInt() and 0xFF) shl 8) or (md[3].toInt() and 0xFF)
    }

    /* ===== 나이 ===== */
    private fun ageOf(birthIso: String): Int = try {
        val d = ISO_FMT.parse(birthIso)!!; val now = Calendar.getInstance(); val bd = Calendar.getInstance().apply { time = d }
        var age = now.get(Calendar.YEAR) - bd.get(Calendar.YEAR); if (now.get(Calendar.DAY_OF_YEAR) < bd.get(Calendar.DAY_OF_YEAR)) age--; age.coerceIn(0,120)
    } catch (_: Exception) { 25 }
    private fun ageTag(age: Int): String = when { age <= 12 -> "child"; age <= 18 -> "teen"; age <= 25 -> "student"; age <= 64 -> "adult"; else -> "senior" }

    /* ===== Firestore 저장(선택) ===== */
    private fun saveDailyFortuneToFirestore(userId: String, dateKey: String, payload: JSONObject) {
        val db = FirebaseFirestore.getInstance()
        val overall = payload.optJSONObject("sections")?.optJSONObject("overall")?.optInt("score", 0) ?: 0
        val lucky = payload.optJSONObject("lucky")
        val data = hashMapOf(
            "date" to dateKey,
            "overall" to overall,
            "luckyColor" to (lucky?.optString("colorHex").orEmpty()),
            "luckyNumber" to (lucky?.optInt("number") ?: 0),
            "luckyTime" to (lucky?.optString("time").orEmpty()),
            "payload" to payload.toString(),
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("users").document(userId)
            .collection("fortunes").document(dateKey)
            .set(data, SetOptions.merge())
    }

    /* ─────── 추가: 누락 함수들 구현(그대로 유지) ─────── */

    private fun buildDailyRequest(u: UserInfo, seed: Int): JSONObject {
        return JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.7) // 변주 조금 더
            put("max_tokens", 2200)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    "당신은 프리미엄 라이프 코치이자 운세 분석가입니다. 항상 function 호출만으로 JSON을 반환하세요."))
                put(JSONObject().put("role", "user").put("content", buildUserPrompt(u, seed)))
            })
            put("tools", JSONArray().put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "daily_fortune")
                    put("description", "사용자 맞춤 하루 운세 JSON 반환")
                    put("parameters", fortuneSchema())
                })
            }))
            put("tool_choice", JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().put("name", "daily_fortune"))
            })
        }
    }

    private fun normalizePlan(
        raw: String?,
        luckyTime: String,
        luckyNumber: Int,
        colorHex: String
    ): String {
        var t = raw?.trim().orEmpty()
        t = t.replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("^#{1,6}\\s*"), "")
            .trim()
        t = neutralizeCorporateTerms(t)
            .replace(Regex("숙제|과제|수업|강의|시험|퀴즈|레포트|제출"), "정리")
            .replace(Regex("연락|전화|메시지|문자|카톡|DM|카카오"), "알림 확인")
            .let { stripTimePhrases(it) }
            .trim()

        val hasMorning = t.contains("아침")
        val hasAfternoon = t.contains("오후")
        val hasEvening = t.contains("저녁")
        if (!(hasMorning && hasAfternoon && hasEvening) || t.length < 80) {
            return makeTomorrowPlan(JSONObject().apply {
                put("lucky", JSONObject().put("time", luckyTime).put("number", luckyNumber).put("colorHex", colorHex))
            })
        }
        if (t.length > 900) t = t.take(900) + "…"
        return t
    }

    private fun makeTomorrowPlan(base: JSONObject): String {
        val lucky = base.optJSONObject("lucky") ?: JSONObject()
        val num  = lucky.optInt("number", 7)
        return buildString {
            append("• 오늘 흐름을 간결히 정리했어요.\n\n")
            append("아침(09~12)\n")
            append(" - 핵심 작업 1개 완료\n")
            append(" - 알림·메모 3분 정리\n\n")
            append("오후(13~17)\n")
            append(" - 20분 집중 2회로 꼭 한 가지 끝내기\n")
            append(" - 가벼운 스트레칭 5분\n\n")
            append("저녁(19~22)\n")
            append(" - 하루 리뷰 3줄, 내일 첫 작업 1줄 적기\n")
        }
    }

    private fun buildTomorrowExtraTips(deep: JSONObject, daily: JSONObject?): String {
        val tips = (0 until (deep.optJSONArray("tips")?.length() ?: 0))
            .mapNotNull { deep.optJSONArray("tips")?.optString(it) }
            .map { "• " + neutralizeCorporateTerms(stripTimePhrases(it)) }
        val adj = (0 until (deep.optJSONArray("checklistAdjusted")?.length() ?: 0))
            .mapNotNull { deep.optJSONArray("checklistAdjusted")?.optString(it) }
            .map { "• " + neutralizeChecklistText(it) }
        val fallback = daily?.optJSONArray("checklist")?.let { arr ->
            (0 until arr.length()).map { "• " + neutralizeChecklistText(arr.optString(it)) }
        } ?: emptyList()
        val lines = (tips + adj).ifEmpty { fallback }
        return if (lines.isNotEmpty()) lines.joinToString("\n") else "• 내일 아침 첫 10분은 오늘의 핵심 1개만 이어서 진행하세요."
    }
}
