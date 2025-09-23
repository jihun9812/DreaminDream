package com.example.dreamindream


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.SoundPool
import android.os.Bundle
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class FortuneFragment : Fragment() {

    // Views
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var fortuneCard: MaterialCardView
    private lateinit var fortuneButton: MaterialButton
    private lateinit var loadingView: LottieAnimationView
    private lateinit var resultText: TextView

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

    // State
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: FortuneStorage
    private lateinit var api: FortuneApi
    private var lastPayload: JSONObject? = null
    private var isExpanded = false

    // Anim state
    private var suppressButtonState = false
    private var breathingAnim: ValueAnimator? = null

    // Sound
    private var soundPool: SoundPool? = null
    private var sfxClickId: Int = 0
    private var sfxChimeId: Int = 0

    companion object { private var fortuneIntroPlayed = false }

    // UI helpers
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gradientBg(vararg colors: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = dp(16).toFloat() }

    private val BTN_GRAD = intArrayOf(Color.parseColor("#9B8CFF"), Color.parseColor("#6F86FF"))
    private val BTN_DISABLED = Color.parseColor("#475166")
    private val TRAIT_TITLES = setOf("창의성", "소통", "적응력", "결단력")
    private val GOLD = Color.parseColor("#FDCA60")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = FortuneStorage(requireContext())
        prefs = storage.prefs
        api = FortuneApi(requireContext(), storage)
        initSound()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_fortune, container, false)

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

        // 초기 프레임 깜빡임 방지
        setPendingFortune()

        // 칩 골드 틴트
        applyGoldToTraitChips(rootLayout)

        // FS → Prefs 동기화 후 초기 UI
        storage.syncProfileFromFirestore { decideInitialUi(v) }

        // 복사/공유
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

        // 버튼 클릭
        fortuneButton.setOnClickListener {
            if (!storage.isProfileComplete()) {
                showProfileRequiredDialog()
                return@setOnClickListener
            }
            if (storage.isFortuneSeenToday()) {
                Toast.makeText(requireContext(),"오늘은 이미 확인했어요. 내일 다시 이용해주세요.",Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            playSfx(sfxClickId, 0.8f)
            popSparkles(it, count = 12)
            playPressBounce(it)
            stopBreathing()

            suppressButtonState = true
            fortuneButton.isClickable = false
            fortuneButton.visibility = View.GONE

            relayoutCardToTop()
            fortuneCard.visibility = View.VISIBLE
            expandFortuneCard(v)

            setCardContentVisible(false)
            showLoading(true)

            val u = storage.loadUserInfoStrict()
            val seed = storage.seedForToday(u)
            api.fetchDaily(u, seed,
                onSuccess = { payload ->
                    suppressButtonState = false
                    lastPayload = payload
                    bindFromPayload(payload)
                    storage.cacheTodayPayload(payload)
                    storage.markSeenToday()

                    showLoading(false)
                    setCardContentVisible(true)
                    view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    playSfx(sfxChimeId, 0.9f)

                    v.findViewById<ScrollView>(R.id.resultScrollView)?.post {
                        val sv = v.findViewById<ScrollView>(R.id.resultScrollView)
                        sv?.scrollTo(0, 0); sv?.fullScroll(View.FOCUS_UP)
                    }
                    expandFortuneCard(v)

                    FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                        FirestoreManager.saveDailyFortune(uid, storage.todayKey(), payload)
                    }
                },
                onError = { userMsg, seedPreset ->
                    suppressButtonState = false
                    showLoading(false)
                    setCardContentVisible(true)
                    resultText.text = userMsg
                    setEmotionBars(seedPreset.first, seedPreset.second, seedPreset.third)
                    fortuneButton.visibility = View.VISIBLE
                    applyPrimaryButtonStyle()
                }
            )
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

    private fun decideInitialUi(v: View) {
        storage.getCachedTodayPayload()?.let { last ->
            lastPayload = last
            fortuneButton.visibility = View.GONE
            relayoutCardToTop()
            bindFromPayload(last)
            setCardContentVisible(true)
            expandFortuneCard(v)
            return
        }
        if (storage.isFortuneSeenToday()) {
            fortuneButton.visibility = View.GONE
            return
        }
        applyPrimaryButtonStyle()
    }

    // ------- 애니메이션 -------

    private fun playFortuneButtonIntro(btn: View) {
        btn.visibility = View.VISIBLE
        btn.alpha = 0f
        btn.scaleX = 0.60f
        btn.scaleY = 0.60f
        btn.rotation = 0f

        val grow = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btn, View.SCALE_X, 0.60f, 1.18f),
                ObjectAnimator.ofFloat(btn, View.SCALE_Y, 0.60f, 1.18f),
                ObjectAnimator.ofFloat(btn, View.ALPHA,   0f,    1f)
            )
            duration = 280
            interpolator = AccelerateDecelerateInterpolator()
        }

        val settle1 = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btn, View.SCALE_X, 1.18f, 0.92f),
                ObjectAnimator.ofFloat(btn, View.SCALE_Y, 1.18f, 0.92f)
            )
            duration = 160
            interpolator = DecelerateInterpolator()
        }

        val settle2 = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btn, View.SCALE_X, 0.92f, 1.06f, 1.0f),
                ObjectAnimator.ofFloat(btn, View.SCALE_Y, 0.92f, 1.06f, 1.0f)
            )
            duration = 220
            interpolator = OvershootInterpolator(1.9f)
        }

        AnimatorSet().apply {
            playSequentially(grow, settle1, settle2)
            start()
        }

        fortuneIntroPlayed = true
        startBreathing(btn)
    }

    private fun startBreathing(btn: View) {
        stopBreathing()
        breathingAnim = ValueAnimator.ofFloat(1.0f, 0.94f, 1.06f, 1.0f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { a ->
                val s = a.animatedValue as Float
                btn.scaleX = s
                btn.scaleY = s
            }
            start()
        }
    }

    private fun stopBreathing() { breathingAnim?.cancel(); breathingAnim = null }

    private fun playPressBounce(v: View) {
        val press = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, View.SCALE_X, v.scaleX, 0.90f),
                ObjectAnimator.ofFloat(v, View.SCALE_Y, v.scaleY, 0.90f)
            )
            duration = 90
            interpolator = DecelerateInterpolator()
        }
        val release = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, View.SCALE_X, 0.90f, 1.07f, 1.0f),
                ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.90f, 1.07f, 1.0f)
            )
            duration = 130
            interpolator = OvershootInterpolator(2.0f)
        }
        AnimatorSet().apply { playSequentially(press, release); start() }
    }

    // ✨ 스파클
    private fun popSparkles(anchor: View, count: Int = 10) {
        val root = rootLayout
        if (!isAdded || root.width == 0 || root.height == 0) return

        val rootLoc = IntArray(2); root.getLocationOnScreen(rootLoc)
        val btnLoc  = IntArray(2); anchor.getLocationOnScreen(btnLoc)
        val cx = (btnLoc[0] - rootLoc[0]) + anchor.width / 2f
        val cy = (btnLoc[1] - rootLoc[1]) + anchor.height / 2f

        val palette = intArrayOf(
            Color.parseColor("#FFE27A"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#FFD3A3"),
            Color.parseColor("#A6C8FF"),
            Color.parseColor("#FF8AE2")
        )

        repeat(count) { i ->
            val size = dp(10 + Random.nextInt(10))
            val dot = View(requireContext()).apply {
                layoutParams = ConstraintLayout.LayoutParams(size, size)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(palette[i % palette.size])
                }
                scaleX = 0f; scaleY = 0f; alpha = 1f
                x = cx - size / 2f; y = cy - size / 2f
                elevation = 100f
            }
            root.addView(dot)

            val baseAngle = (360f / count) * i
            val jitter = Random.nextFloat() * 40f - 20f
            val angle = baseAngle + jitter
            val rad   = Math.toRadians(angle.toDouble())
            val radius = dp(36 + Random.nextInt(44))

            val endX = (cx + cos(rad) * radius).toFloat() - size / 2f
            val endY = (cy + sin(rad) * radius).toFloat() - size / 2f

            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(dot, View.SCALE_X, 0f, 1f, 0f),
                    ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0f, 1f, 0f),
                    ObjectAnimator.ofFloat(dot, View.X, dot.x, endX),
                    ObjectAnimator.ofFloat(dot, View.Y, dot.y, endY),
                    ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 1f, 0f)
                )
                duration = 650L
                startDelay = (i * 12L)
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        root.removeView(dot)
                    }
                })
                start()
            }
        }
    }

    // ------- Bind & Helpers -------

    private fun bindFromPayload(obj: JSONObject) {
        val kwArr = obj.optJSONArray("keywords") ?: JSONArray()
        val kws = (0 until kwArr.length()).mapNotNull { kwArr.optString(it).takeIf { it.isNotBlank() } }
        setKeywords(kws)

        val lucky = obj.optJSONObject("lucky")
        setLucky(
            lucky?.optString("colorHex").orEmpty(),
            lucky?.optInt("number", 7) ?: 7,
            lucky?.optString("time").orEmpty()
        )

        val emo = obj.optJSONObject("emotions")
        setEmotionBars(
            emo?.optInt("positive", 60) ?: 60,
            emo?.optInt("neutral", 25) ?: 25,
            emo?.optInt("negative", 15) ?: 15
        )

        val rendered = renderSectionCards(obj)
        resultText.visibility = if (rendered) View.GONE else View.VISIBLE
        if (!rendered) resultText.text = api.formatSections(obj)

        val items = api.sanitizeChecklist(
            (0 until (obj.optJSONArray("checklist")?.length() ?: 0))
                .mapNotNull { obj.optJSONArray("checklist")?.optString(it) }
        )
        setChecklist(items)
    }

    private fun setPendingFortune() {
        fortuneButton.visibility = View.INVISIBLE
        fortuneCard.visibility = View.GONE
        loadingView.visibility = View.GONE
    }

    private fun applyGoldToTraitChips(root: ViewGroup) {
        fun dfs(v: View) {
            when (v) {
                is ChipGroup -> tintChipsGoldInGroup(v)
                is ViewGroup -> (0 until v.childCount).forEach { dfs(v.getChildAt(it)) }
            }
        }
        dfs(root)
    }

    private fun tintChipsGoldInGroup(group: ChipGroup) {
        var hasTrait = false
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (TRAIT_TITLES.contains(chip.text?.toString()?.trim())) hasTrait = true
        }
        if (!hasTrait) return

        val base = GOLD
        val bg = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(adjustAlpha(base, 0.32f), adjustAlpha(base, 0.18f))
        )
        val stroke = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(base, adjustAlpha(base, 0.60f))
        )
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (!TRAIT_TITLES.contains(chip.text?.toString()?.trim())) continue
            chip.isCheckable = true
            chip.chipBackgroundColor = bg
            chip.chipStrokeWidth = resources.displayMetrics.density * 1f
            chip.chipStrokeColor = stroke
            chip.rippleColor = ColorStateList.valueOf(adjustAlpha(base, 0.25f))
            chip.setTextColor(Color.parseColor("#0C1830"))
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int =
        ColorUtils.setAlphaComponent(color, (Color.alpha(color) * factor).toInt())

    private fun applyPrimaryButtonStyle() {
        if (suppressButtonState) return
        with(fortuneButton) {
            visibility = View.VISIBLE; isEnabled = true
            setBackgroundResource(R.drawable.fortune_button_bg); backgroundTintList = null
            setTextColor(Color.WHITE); text = "운세\n보기"
            alpha = 1f; scaleX = 1f; scaleY = 1f; rotation = 0f
        }
        moveButtonCentered()
        if (!fortuneIntroPlayed) playFortuneButtonIntro(fortuneButton) else startBreathing(fortuneButton)
    }

    private fun lockFortuneButtonForToday() {
        if (suppressButtonState) return
        fortuneButton.visibility = View.VISIBLE
        fortuneButton.isEnabled = false
        fortuneButton.background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(BTN_DISABLED) }
        fortuneButton.setTextColor(Color.parseColor("#B3C1CC"))
        fortuneButton.text = "내일 다시"
        moveButtonTop()
        stopBreathing()
    }

    private fun showProfileRequiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("설정이 필요해요")
            .setMessage("닉네임·생년월일·성별만 저장하면 맞춤 운세를 볼 수 있어요.\n(출생시간·MBTI는 선택)")
            .setPositiveButton("확인", null)
            .show()
    }

    private fun setLucky(colorHex: String, number: Int, timeRaw: String) {
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            val c = runCatching { Color.parseColor(colorHex) }.getOrNull() ?: Color.parseColor("#FFD54F")
            setColor(c)
        }
        viewLuckyColor.background = dot
        tvLuckyNumber.text = number.toString()
        tvLuckyTime.text = api.humanizeLuckyTime(timeRaw).ifBlank { api.pickLuckyTimeFallback() }
    }

    private fun setEmotionBars(pos: Int, neu: Int, neg: Int) {
        fun v(x: Int) = x.coerceIn(0,100)
        barPos.setProgressCompat(v(pos), true)
        barNeu.setProgressCompat(v(neu), true)
        barNeg.setProgressCompat(v(neg), true)
        tvPos.text = "${v(pos)}%"; tvNeu.text = "${v(neu)}%"; tvNeg.text = "${v(neg)}%"
    }

    private fun setKeywords(list: List<String>) {
        chips.removeAllViews()
        list.take(4).forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label; isCheckable = false; isClickable = false
                setTextColor(Color.WHITE)
                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#334E68"))
            }
            chips.addView(chip)
        }
    }

    private fun setChecklist(items: List<String>) {
        val todayKey = storage.todayPersonaKey()
        layoutChecklist.removeAllViews()
        layoutChecklist.visibility = View.VISIBLE
        items.forEachIndexed { idx, text ->
            val cb = CheckBox(requireContext()).apply {
                this.text = "• $text"
                setTextColor(Color.parseColor("#F0F4F8")); textSize = 14f
                isChecked = prefs.getBoolean("fortune_check_${todayKey}_$idx", false)
                setPadding(8, 10, 8, 10)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("fortune_check_${todayKey}_$idx", checked).apply()
                }
            }
            layoutChecklist.addView(cb)
        }
    }

    private fun renderSectionCards(obj: JSONObject): Boolean {
        val container = sectionsContainer ?: return false
        container.removeAllViews()
        val sections = obj.optJSONObject("sections") ?: JSONObject()
        val lottoNums = obj.optJSONArray("lottoNumbers")

        fun addCard(title: String, key: String) {
            val s = sections.optJSONObject(key) ?: JSONObject()
            val score = s.optInt("score", -1).coerceIn(40, 100)
            val bodyText = s.optString("text").ifBlank { s.optString("advice") }.trim()

            val card = layoutInflater.inflate(R.layout.item_fortune_section, container, false) as MaterialCardView
            val tvTitle = card.findViewById<TextView>(R.id.tvSectionTitle); tvTitle.text = title

            val badge = card.findViewById<TextView>(R.id.tvScoreBadge)
            val color = api.scoreColor(score)
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
        tvScore.background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(api.scoreColor(score)) }
        tvBody.text = api.buildSectionDetails(title, score, text, advice)

        val dlg = MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        ).setView(content).create().apply { window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }

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

    private fun beginTransitionIfAllowed(target: ViewGroup, set: TransitionSet? = null) {
        if (suppressButtonState) return
        if (set != null) TransitionManager.beginDelayedTransition(target, set)
        else TransitionManager.beginDelayedTransition(target)
    }

    // ------- Layout/Loading -------
    private fun relayoutCardToTop() {
        beginTransitionIfAllowed(rootLayout)
        val lp = fortuneCard.layoutParams as ConstraintLayout.LayoutParams
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        lp.endToEnd     = ConstraintLayout.LayoutParams.PARENT_ID
        lp.topToTop     = ConstraintLayout.LayoutParams.PARENT_ID
        lp.topMargin    = dp(10)
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        lp.bottomToTop    = ConstraintLayout.LayoutParams.UNSET
        fortuneCard.layoutParams = lp
        fortuneCard.requestLayout()
    }

    private fun moveButtonCentered() {
        beginTransitionIfAllowed(rootLayout)
        val lp = fortuneButton.layoutParams as ConstraintLayout.LayoutParams
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        lp.endToEnd     = ConstraintLayout.LayoutParams.PARENT_ID
        lp.topToTop     = ConstraintLayout.LayoutParams.PARENT_ID
        lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        lp.topMargin = 0; lp.bottomMargin = 0
        lp.verticalBias = 0.48f
        fortuneButton.layoutParams = lp
        fortuneButton.requestLayout()
    }

    private fun moveButtonTop() {
        beginTransitionIfAllowed(rootLayout)
        val lp = fortuneButton.layoutParams as ConstraintLayout.LayoutParams
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        lp.endToEnd     = ConstraintLayout.LayoutParams.PARENT_ID
        lp.topToTop     = ConstraintLayout.LayoutParams.PARENT_ID
        lp.topMargin    = dp(24)
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        lp.bottomToTop    = ConstraintLayout.LayoutParams.UNSET
        lp.verticalBias   = 0f
        fortuneButton.layoutParams = lp
        fortuneButton.requestLayout()
    }

    private fun showLoading(show: Boolean, animRes: Int? = null) {
        if (show) {
            animRes?.let { loadingView.setAnimation(it) }
            loadingView.alpha=0f; loadingView.visibility=View.VISIBLE
            loadingView.scaleX=0.3f; loadingView.scaleY=0.3f
            loadingView.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(450).setInterpolator(BounceInterpolator())
                .withEndAction { loadingView.playAnimation() }
                .start()
            resultText.text=""
            fortuneButton.isEnabled=false
            stopBreathing()
        } else {
            loadingView.cancelAnimation()
            loadingView.visibility=View.GONE
            fortuneButton.isEnabled=true
        }
    }

    private fun expandFortuneCard(view: View) {
        if (isExpanded) return; isExpanded = true
        val scroll = view.findViewById<ScrollView>(R.id.resultScrollView)
        val set = TransitionSet().apply { addTransition(Slide(Gravity.TOP)); addTransition(Fade(Fade.IN)); duration=450 }
        beginTransitionIfAllowed(rootLayout as ViewGroup, set)
        fortuneCard.visibility = View.VISIBLE
        val targetH = (resources.displayMetrics.heightPixels * 0.80f).toInt()
        val curH = max(scroll?.height ?: 160, 160)
        ValueAnimator.ofInt(curH, targetH).apply {
            duration = 700; startDelay=150; interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                scroll?.layoutParams?.height = (a.animatedValue as Int)
                scroll?.requestLayout()
            }
            start()
        }
    }

    private fun setCardContentVisible(visible: Boolean) {
        val vis = if (visible) View.VISIBLE else View.INVISIBLE
        chips.visibility = vis; viewLuckyColor.visibility = vis; tvLuckyNumber.visibility = vis; tvLuckyTime.visibility = vis
        barPos.visibility = vis; barNeu.visibility = vis; barNeg.visibility = vis
        tvPos.visibility = vis;  tvNeu.visibility = vis;  tvNeg.visibility = vis
        sectionsContainer?.visibility = vis
        resultText.visibility = vis
        layoutChecklist.visibility = vis
        btnCopy.visibility = vis; btnShare.visibility = vis; btnDeep.visibility = vis
    }

    // ------- Ad gate / Deep -------
    private fun openDeepWithGate() {
        val key = "fortune_deep_unlocked_${storage.todayPersonaKey()}"
        if (prefs.getBoolean(key, false)) { openDeepNow(); return }

        val bs = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val btnCancel = v.findViewById<Button>(R.id.btnCancel)
        val btnWatch = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWatchAd)
        val textStatus = v.findViewById<TextView>(R.id.textStatus)
        val progress = v.findViewById<android.widget.ProgressBar>(R.id.progressAd)

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
        val today = storage.todayPersonaKey()
        storage.getCachedDeep(today)?.let {
            api.showDeepDialog(requireContext(), it, lastPayload)
            return
        }
        btnDeep.isEnabled=false; btnDeep.alpha=0.7f; btnDeep.text="생성 중…"

        val u = storage.loadUserInfoStrict(); val seed = storage.seedForToday(u)
        api.fetchDeep(u, lastPayload!!, seed) { deep ->
            btnDeep.isEnabled=true; btnDeep.alpha=1f; btnDeep.text="심화 분석 보기"
            deep?.let { storage.cacheDeep(today, it) }
            api.showDeepDialog(requireContext(), deep ?: JSONObject(), lastPayload)
        }
    }

    // ------- Sound -------
    private fun initSound() {
        soundPool = SoundPool.Builder().setMaxStreams(2).build()
        sfxClickId = loadSfxFirstFound("sfx_fortune_click_dreamy", "sfx_fortune_click")
        sfxChimeId = loadSfxFirstFound("sfx_fortune_chime_dreamy", "sfx_fortune_chime")
    }

    private fun loadSfxFirstFound(vararg names: String): Int {
        val ctx = requireContext()
        for (n in names) {
            val resId = ctx.resources.getIdentifier(n, "raw", ctx.packageName)
            if (resId != 0) return soundPool?.load(ctx, resId, 1) ?: 0
        }
        return 0
    }

    private fun playSfx(id: Int, volume: Float = 1f) {
        if (id != 0) soundPool?.play(id, volume, volume, 1, 0, 1f)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBreathing()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
        soundPool = null
    }
}
