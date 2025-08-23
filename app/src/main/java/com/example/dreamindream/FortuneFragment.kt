package com.example.dreamindream

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.text.buildSpannedString
import androidx.core.text.bold
import androidx.core.text.color
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.example.dreamindream.ads.AdManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

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

    // ===== Style & utils =====
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gradientBg(vararg colors: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = dp(16).toFloat() }
    private fun roundedBg(color: Int) = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(color) }

    private val BTN_GRAD = intArrayOf(Color.parseColor("#9B8CFF"), Color.parseColor("#6F86FF"))
    private val BTN_DISABLED = Color.parseColor("#475166")

    private val luckyPalette = listOf(
        "#8E9BFF","#5BD1D7","#FFB86B","#6EDCA4","#FF8BA7",
        "#B28DFF","#7AC4FF","#FFC75F","#52E5A8","#FF9999",
        "#7FD1B9","#A1C4FD","#FECF6A","#98E0FF","#C3A9FF"
    )
    private val sectionTitleColor = Color.parseColor("#9FB8FF")
    private val badgeGreen = Color.parseColor("#17D499")
    private val badgeYellow = Color.parseColor("#FFC75F")
    private val badgeRed = Color.parseColor("#FF6B7B")

    private fun sectionColor(key: String): Int = when (key) {
        "overall" -> Color.parseColor("#7AC4FF")
        "love"    -> Color.parseColor("#FF8BA7")
        "study"   -> Color.parseColor("#A1C4FD")
        "work"    -> Color.parseColor("#9FB8FF")
        "money"   -> Color.parseColor("#FFC75F")
        "lotto"   -> Color.parseColor("#C3A9FF")
        else      -> Color.parseColor("#9CB6C7")
    }
    private fun scoreBadgeColor(score: Int): Int = when {
        score >= 80 -> badgeGreen
        score >= 60 -> badgeYellow
        else -> badgeRed
    }

    // ===== Lifecycle =====
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_fortune, container, false)

        // Bind views
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

        // Prefs (user-scoped)
        val fallbackId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID) ?: "device"
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: fallbackId
        prefs = requireContext().getSharedPreferences("user_info_$userId", Context.MODE_PRIVATE)

        // Ads
        v.findViewById<AdView>(R.id.adView_fortune)?.loadAd(AdRequest.Builder().build())
        AdManager.initialize(requireContext())
        AdManager.loadRewarded(requireContext())

        // Button style/position
        if (isFortuneSeenToday()) {
            lockFortuneButtonForToday()
            moveButtonTop()
        } else {
            applyPrimaryButtonStyle()
            moveButtonCentered()
        }

        // Restore cache
        prefs.getString("fortune_payload_${todayKey()}", null)?.let { raw ->
            runCatching { JSONObject(raw) }.onSuccess {
                lastPayload = it
                bindFromPayload(it)
                expandFortuneCard(v)
            }
        }

        fun View.scaleClick(action: () -> Unit) = setOnClickListener {
            runCatching { startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up)) }
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            action()
        }

        btnCopy.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("fortune", resultText.text))
            Toast.makeText(requireContext(), "복사됨", Toast.LENGTH_SHORT).show()
        }
        btnShare.setOnClickListener {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, resultText.text.toString())
            }
            startActivity(android.content.Intent.createChooser(send, "공유"))
        }

        fortuneButton.scaleClick {
            if (isFortuneSeenToday()) {
                Toast.makeText(requireContext(), "오늘은 이미 확인했어요. 내일 다시 이용해주세요.", Toast.LENGTH_SHORT).show()
                return@scaleClick
            }
            moveButtonTop()
            showLoading(true)
            val u = loadUserInfoSoft()
            val seed = seedForToday(u)
            fetchFortune(u, todayKey(), seed, v)
        }

        btnDeep.setOnClickListener {
            if (lastPayload == null) {
                Toast.makeText(requireContext(), "먼저 ‘행운보기’를 실행해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openDeepWithGate()
        }

        return v
    }

    // ===== UI helpers =====
    private fun applyPrimaryButtonStyle() {
        fortuneButton.isEnabled = true
        fortuneButton.background = gradientBg(*BTN_GRAD)
        fortuneButton.setTextColor(Color.WHITE)
        fortuneButton.text = getString(R.string.btn_fortune)
    }
    private fun lockFortuneButtonForToday() {
        fortuneButton.isEnabled = false
        fortuneButton.background = roundedBg(BTN_DISABLED)
        fortuneButton.setTextColor(Color.parseColor("#B3C1CC"))
        fortuneButton.text = "내일 다시"
    }
    private fun moveButtonCentered() {
        val set = ConstraintSet().apply { clone(rootLayout) }
        set.clear(R.id.fortuneButton, ConstraintSet.TOP)
        set.clear(R.id.fortuneButton, ConstraintSet.BOTTOM)
        set.connect(R.id.fortuneButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(R.id.fortuneButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.connect(R.id.fortuneButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(R.id.fortuneButton, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.setVerticalBias(R.id.fortuneButton, 0.48f)
        TransitionManager.beginDelayedTransition(rootLayout)
        set.applyTo(rootLayout)
    }
    private fun moveButtonTop() {
        val set = ConstraintSet().apply { clone(rootLayout) }
        set.clear(R.id.fortuneButton, ConstraintSet.BOTTOM)
        set.connect(R.id.fortuneButton, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(R.id.fortuneButton, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.connect(R.id.fortuneButton, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dp(24))
        TransitionManager.beginDelayedTransition(rootLayout)
        set.applyTo(rootLayout)
    }
    private fun showLoading(show: Boolean) {
        if (show) {
            loadingView.alpha = 0f
            loadingView.visibility = View.VISIBLE
            loadingView.scaleX = 0.3f; loadingView.scaleY = 0.3f
            loadingView.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(700).setInterpolator(BounceInterpolator()).start()
            loadingView.playAnimation()
            fortuneCard.visibility = View.GONE
            resultText.text = ""
            fortuneButton.isEnabled = false
        } else {
            loadingView.cancelAnimation()
            loadingView.visibility = View.GONE
            fortuneButton.isEnabled = true
        }
    }
    private fun expandFortuneCard(view: View) {
        if (isExpanded) return
        isExpanded = true
        val scroll = view.findViewById<ScrollView>(R.id.resultScrollView)
        val set = TransitionSet().apply {
            addTransition(Slide(Gravity.TOP)); addTransition(Fade(Fade.IN))
            duration = 450; ordering = TransitionSet.ORDERING_TOGETHER
        }
        TransitionManager.beginDelayedTransition(rootLayout as ViewGroup, set)
        fortuneCard.visibility = View.VISIBLE
        val targetH = (resources.displayMetrics.heightPixels * 0.80f).toInt()
        val curH = max(scroll?.height ?: 160, 160)
        ValueAnimator.ofInt(curH, targetH).apply {
            duration = 700; startDelay = 150; interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val h = a.animatedValue as Int
                scroll?.layoutParams?.height = h
                scroll?.requestLayout()
            }
            start()
        }
    }

    // ===== Bind =====
    private fun setLucky(colorHex: String, number: Int, time: String) {
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.parseColor("#FFD54F")))
        }
        viewLuckyColor.background = dot
        tvLuckyNumber.text = number.toString()
        tvLuckyTime.text = time
        viewLuckyColor.animate().scaleX(1.08f).scaleY(1.08f).setDuration(220)
            .withEndAction { viewLuckyColor.animate().scaleX(1f).scaleY(1f).setDuration(180).start() }
            .start()
    }
    private fun setEmotionBars(pos: Int, neu: Int, neg: Int) {
        fun v(x: Int) = when { x <= 0 -> 0; x in 1..7 -> 8; else -> x }.coerceIn(0, 100)
        barPos.setProgressCompat(v(pos), true)
        barNeu.setProgressCompat(v(neu), true)
        barNeg.setProgressCompat(v(neg), true)
        tvPos.text = "${pos.coerceIn(0, 100)}%"
        tvNeu.text = "${neu.coerceIn(0, 100)}%"
        tvNeg.text = "${neg.coerceIn(0, 100)}%"
    }
    private fun setKeywords(list: List<String>) {
        chips.removeAllViews()
        list.take(4).forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = false
                isClickable = false
                setTextColor(Color.WHITE)
                setChipBackgroundColor(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#334E68"))
                )
            }
            chips.addView(chip)
        }
    }
    private fun setChecklist(items: List<String>) {
        val today = todayKey()
        layoutChecklist.removeAllViews()
        layoutChecklist.visibility = View.VISIBLE
        items.take(3).forEachIndexed { idx, text ->
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

    // ===== Section Cards =====
// 섹션 카드 (총운/연애운/학업운/직장운/재물운/로또)
    private fun renderSectionCards(obj: JSONObject): Boolean {
        val container = sectionsContainer ?: return false
        container.removeAllViews()

        val sections = obj.optJSONObject("sections") ?: JSONObject()
        val lottoNums = obj.optJSONArray("lottoNumbers")

        fun addCard(title: String, key: String) {
            val s = sections.optJSONObject(key) ?: JSONObject()
            val score = s.optInt("score", -1).coerceIn(0, 100)
            val bodyText = s.optString("text").ifBlank { s.optString("advice") }.trim() // ← 이름 충돌 방지

            // 카드 컨테이너
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                background = requireContext().getDrawable(R.drawable.bg_deep_section)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(12) }
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }

            // 헤더: 타이틀 + 스페이서 + 점수 배지
            val header = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvTitle = TextView(requireContext()).apply {
                this.text = title
                setTextColor(Color.parseColor("#EAF3FF"))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.02f
            }

            val spacer = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            }

            val tvBadge = TextView(requireContext()).apply {
                this.text = "${score}점"
                setTextColor(Color.BLACK)
                textSize = 12f
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = GradientDrawable().apply {
                    cornerRadius = dp(999).toFloat()
                    setColor(scoreBadgeColor(score))
                }
                visibility = if (key == "lotto") View.GONE else View.VISIBLE
            }

            header.addView(tvTitle)
            header.addView(spacer)
            header.addView(tvBadge)

            // 진행바
            val indicator = LinearProgressIndicator(requireContext()).apply {
                setIndeterminate(false)
                setMax(100)
                setProgressCompat(score, true)
                setIndicatorColor(sectionColor(key))
                try { setTrackColor(Color.parseColor("#223C4C")) } catch (_: Exception) { /* 기본값 유지 */ }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(10) }
                visibility = if (key == "lotto") View.GONE else View.VISIBLE
            }

            // 본문
            val tvBody = TextView(requireContext()).apply {
                setTextColor(Color.parseColor("#CFE3F2"))
                textSize = 14f
                setLineSpacing(0f, 1.15f)
                maxLines = 4
                ellipsize = android.text.TextUtils.TruncateAt.END

                val body = if (key == "lotto") {
                    if (lottoNums != null && lottoNums.length() == 6) {
                        val arr = (0 until 6).map { lottoNums.optInt(it) }.sorted()
                        "번호: ${arr.joinToString(", ")}"
                    } else "번호: -"
                } else bodyText.ifBlank { "간단한 조언이 준비되어 있어요." }

                this.text = neutralizeCorporateTerms(body)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = if (indicator.visibility == View.VISIBLE) dp(10) else dp(6) }
            }

            // 조립 + 약한 페이드 인
            card.addView(header)
            card.addView(indicator)
            card.addView(tvBody)
            container.addView(card)

            card.alpha = 0f; card.translationY = 12f
            card.animate().alpha(1f).translationY(0f).setDuration(260).start()
        }

        addCard("총운", "overall")
        addCard("연애운", "love")
        addCard("학업운", "study")
        addCard("직장운", "work")
        addCard("재물운", "money")
        addCard("로또운", "lotto")
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
            emo?.optInt("neutral", 30) ?: 30,
            emo?.optInt("negative", 10) ?: 10
        )

        val cl = obj.optJSONArray("checklist") ?: JSONArray()
        setChecklist((0 until cl.length()).mapNotNull { cl.optString(it).takeIf { it.isNotBlank() } })

        val rendered = renderSectionCards(obj)
        if (!rendered) {
            resultText.text = formatSections(obj)
            resultText.visibility = View.VISIBLE
        } else resultText.visibility = View.GONE
    }

    // 백업 텍스트 포맷
    private fun formatSections(obj: JSONObject) = buildSpannedString {
        val sections = obj.optJSONObject("sections") ?: JSONObject()
        val lottoNums = obj.optJSONArray("lottoNumbers")
        fun line(label: String, key: String) {
            val s = sections.optJSONObject(key) ?: JSONObject()
            val score = s.optInt("score", -1)
            val text = s.optString("text").ifBlank { s.optString("advice") }
            color(sectionTitleColor) { bold { append(label) } }
            if (score >= 0) append(" (${score}점)")
            if (key == "lotto") {
                if (lottoNums != null && lottoNums.length() == 6) {
                    val arr = (0 until 6).map { lottoNums.optInt(it) }.sorted()
                    append("  번호: ${arr.joinToString(", ")}\n")
                } else append("\n")
                return
            }
            if (text.isNotBlank()) { append(" - "); append(text.trim()) }
            append("\n")
        }
        line("총운","overall"); line("연애운","love"); line("학업운","study")
        line("직장운","work"); line("재물운","money"); line("로또운","lotto")
    }

    // ===== Networking: Daily =====
    private fun fetchFortune(u: UserInfo, today: String, seed: Int, view: View, attempt: Int = 1) {
        if (!isAdded) return
        val body = buildDailyRequest(u, seed)
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (!isAdded) return
                requireActivity().runOnUiThread { onFetchFailed(u, today, seed, view, attempt, e.message ?: "io") }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val raw = response.body?.string().orEmpty()
                if (!isAdded) return
                if (!response.isSuccessful) {
                    requireActivity().runOnUiThread { onFetchFailed(u, today, seed, view, attempt, "http ${response.code}") }
                    return
                }
                requireActivity().runOnUiThread {
                    showLoading(false)
                    val payload = try {
                        val root = JSONObject(raw)
                        val msg = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        val tc = msg.optJSONArray("tool_calls")
                        if (tc != null && tc.length() > 0) {
                            val args = tc.getJSONObject(0).getJSONObject("function").getString("arguments")
                            validateAndFill(JSONObject(args), seed)
                        } else {
                            val fc = msg.optJSONObject("function_call")
                            if (fc != null) validateAndFill(JSONObject(fc.optString("arguments", "{}")), seed)
                            else parsePayloadAlways(msg.optString("content"), seed)
                        }
                    } catch (_: Exception) { parsePayloadAlways(raw, seed) }

                    val adjusted = finalizePayload(payload)
                    lastPayload = adjusted
                    bindFromPayload(adjusted)

                    prefs.edit()
                        .putString("fortune_payload_$today", adjusted.toString())
                        .putBoolean("fortune_seen_$today", true)
                        .apply()
                    lockFortuneButtonForToday()

                    val scroll = view.findViewById<ScrollView>(R.id.resultScrollView)
                    scroll?.post { scroll.scrollTo(0, 0); scroll.fullScroll(View.FOCUS_UP) }
                    expandFortuneCard(view)
                }
            }
        })
    }
    private fun onFetchFailed(u: UserInfo, today: String, seed: Int, view: View, attempt: Int, reason: String) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            showLoading(false)
            if (attempt == 1) { fetchFortune(u, today, seed, view, attempt = 2); return@runOnUiThread }
            resultText.text = getString(R.string.error_fetch_failed) + " ($reason)"
            val (p, n, ng) = seededEmotions(seed)
            setEmotionBars(p, n, ng)
            expandFortuneCard(view)
            applyPrimaryButtonStyle(); moveButtonCentered()
        }
    }

    private fun buildDailyRequest(u: UserInfo, seed: Int): JSONObject {
        val systemMsg = """
            당신은 프리미엄 운세 컨설턴트입니다.
            오직 도구 daily_fortune를 호출해 결과를 반환하세요.
            메시지/코드블록/설명 없이 tool arguments만 생성하십시오.
        """.trimIndent()
        return JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.6)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role","system").put("content", systemMsg))
                put(JSONObject().put("role","user").put("content", buildUserPrompt(u, seed)))
            })
            put("tools", JSONArray().put(JSONObject().apply {
                put("type","function")
                put("function", JSONObject().apply {
                    put("name","daily_fortune")
                    put("description","오늘의 맞춤 운세를 구조화해 반환")
                    put("parameters", fortuneSchema())
                })
            }))
            put("tool_choice", JSONObject().apply {
                put("type","function")
                put("function", JSONObject().put("name","daily_fortune"))
            })
            put("max_tokens", 1500)
        }
    }

    // ===== Schema & Prompt =====
    private fun fortuneSchema(): JSONObject {
        val obj = JSONObject()
        obj.put("type","object")
        obj.put("required", JSONArray(listOf("lucky","message","sections","keywords","emotions","checklist","tomorrow")))
        obj.put("properties", JSONObject().apply {
            put("lucky", JSONObject().apply {
                put("type","object")
                put("required", JSONArray(listOf("colorHex","number","time")))
                put("properties", JSONObject().apply {
                    put("colorHex", JSONObject().put("type","string").put("pattern","#[0-9A-Fa-f]{6}"))
                    put("number", JSONObject().put("type","integer").put("minimum",1).put("maximum",99))
                    put("time", JSONObject().put("type","string"))
                })
            })
            put("message", JSONObject().put("type","string"))
            put("sections", JSONObject().apply {
                put("type","object")
                put("required", JSONArray(listOf("overall","love","study","work","money","lotto")))
                fun sec() = JSONObject().apply {
                    put("type","object")
                    put("required", JSONArray(listOf("score","text","advice")))
                    put("properties", JSONObject().apply {
                        put("score", JSONObject().put("type","integer").put("minimum",0).put("maximum",100))
                        put("text", JSONObject().put("type","string"))
                        put("advice", JSONObject().put("type","string"))
                    })
                }
                put("overall", sec()); put("love", sec()); put("study", sec())
                put("work", sec()); put("money", sec()); put("lotto", sec())
            })
            put("keywords", JSONObject().apply {
                put("type","array"); put("items", JSONObject().put("type","string"))
                put("minItems",1); put("maxItems",4)
            })
            put("emotions", JSONObject().apply {
                put("type","object")
                put("required", JSONArray(listOf("positive","neutral","negative")))
                put("properties", JSONObject().apply {
                    put("positive", JSONObject().put("type","integer").put("minimum",0).put("maximum",100))
                    put("neutral", JSONObject().put("type","integer").put("minimum",0).put("maximum",100))
                    put("negative", JSONObject().put("type","integer").put("minimum",0).put("maximum",100))
                })
            })
            put("lottoNumbers", JSONObject().apply {
                put("type","array")
                put("items", JSONObject().put("type","integer").put("minimum",1).put("maximum",45))
                put("minItems",6); put("maxItems",6)
            })
            put("checklist", JSONObject().apply {
                put("type","array"); put("items", JSONObject().put("type","string"))
                put("minItems",3); put("maxItems",3)
            })
            put("tomorrow", JSONObject().apply {
                put("type","object"); put("required", JSONArray(listOf("long")))
                put("properties", JSONObject().apply { put("long", JSONObject().put("type","string")) })
            })
        })
        return obj
    }

    private fun buildUserPrompt(u: UserInfo, seed: Int): String {
        val today = todayKey()
        val weekday = SimpleDateFormat("EEEE", Locale.KOREAN).format(Date())
        val userAge = ageOf(u.birth); val tag = ageTag(userAge)
        val (pMin, pMax, nMin, nMax, ngMin, ngMax) = emotionRange(seed)
        val avoidColors = JSONArray(getRecentLuckyColors())
        val avoidTimes = JSONArray(getRecentLuckyTimes())
        val palette = JSONArray(luckyPalette)
        return """
[사용자]
nickname: "${u.nickname}"
mbti: "${u.mbti}"
birthdate: "${u.birth}"
birth_time: "${u.birthTime}"
gender: "${u.gender}"
date: "$today ($weekday)"
age: $userAge
age_tag: $tag
seed: $seed

[스타일 가이드]
- 과장 금지, 명료/따뜻/현실적. 전 연령(5~80세) 누구나 이해하는 표현만 사용.
- 회사 전용 단어 금지: "회의/미팅/면담/이메일/보고서/결재/메신저" ⇒ "연락/상담/노트/정리/알림" 사용.
- 각 섹션 text는 2~3문장, 60~150자.
- lotto 섹션의 text/advice는 비우거나 매우 짧게. 번호는 lottoNumbers에만.

[데이터 제약]
- emotions: positive $pMin~$pMax, neutral $nMin~$nMax, negative $ngMin~$ngMax.
- lucky.colorHex: palette에서 선택, 최근 사용 회피(avoidColors/avoidTimes).
- lucky.time: "오전/오후 X시".
- checklist: 시간/수치 포함, 12~18자, 전연령 문구 3개.

[tomorrow.long]
- 한국어 400~650자. "아침/오후/저녁" 소제목.
- 실행 지시를 시간·분량·횟수로 제시.
- 오늘 낮은 점수 영역 보완 액션 포함.

[힌트]
palette: $palette
avoidColors: $avoidColors
avoidTimes: $avoidTimes
        """.trimIndent()
    }

    // ===== Parse & Normalize =====
    private fun parsePayloadAlways(content: String, seed: Int): JSONObject {
        val txt = content.trim()
        try { return validateAndFill(JSONObject(txt), seed) } catch (_: Exception) {}
        Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```").find(txt)?.let {
            try { return validateAndFill(JSONObject(it.groupValues[1]), seed) } catch (_: Exception) {}
        }
        extractJsonObject(txt)?.let { raw ->
            try { return validateAndFill(JSONObject(raw), seed) } catch (_: Exception) {}
        }
        return buildFallbackFromText(txt, seed)
    }
    private fun extractJsonObject(text: String): String? {
        var depth = 0; var start = -1
        for (i in text.indices) {
            when (text[i]) {
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> { depth--; if (depth == 0 && start >= 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun validateAndFill(obj: JSONObject, seed: Int): JSONObject {
        if (!obj.has("message")) obj.put("message", "")
        if (obj.optJSONArray("keywords") == null) obj.put("keywords", JSONArray())

        val lucky = (obj.optJSONObject("lucky") ?: JSONObject()).apply {
            if (!has("colorHex")) put("colorHex", pickLuckyColorFallback())
            if (!has("number")) put("number", pickLuckyNumberFallback(seed))
            if (!has("time")) put("time", pickLuckyTimeFallback())
        }
        obj.put("lucky", lucky)

        val (p, n, ng) = seededEmotions(seed)
        val emo = (obj.optJSONObject("emotions") ?: JSONObject()).apply {
            put("positive", optInt("positive", p).coerceIn(0, 100))
            put("neutral", optInt("neutral", n).coerceIn(0, 100))
            put("negative", optInt("negative", ng).coerceIn(0, 100))
        }
        obj.put("emotions", emo)

        val age = ageOf(prefs.getString("birthdate", "1990-01-01") ?: "1990-01-01")
        val defaults = fallbackChecklist(ageTag(age))
        val cl = (obj.optJSONArray("checklist") ?: JSONArray())
        val list = mutableListOf<String>()
        for (i in 0 until cl.length()) cl.optString(i)?.let { if (it.isNotBlank()) list += it }
        while (list.size < 3) list += defaults[list.size]
        obj.put("checklist", JSONArray(list.take(3)))

        val secIn = obj.optJSONObject("sections")
        if (secIn == null) {
            val sec = JSONObject()
            sec.put("overall", JSONObject().put("score",80).put("text","새 출발의 기운이 도와줍니다. 오늘은 가볍게 정리하고 중요한 일 하나만 확실히 끝내면 좋겠습니다.").put("advice",""))
            sec.put("love",   JSONObject().put("score",75).put("text","대화가 통하는 날. 가족·친구·연인 누구와도 차분히 마음을 나누면 관계가 한층 편안해집니다.").put("advice",""))
            sec.put("study",  JSONObject().put("score",82).put("text","짧은 집중이 더 효율적입니다. 15~25분 몰입 후 5분 쉬기 패턴으로 부담 없이 진도를 빼보세요.").put("advice",""))
            sec.put("work",   JSONObject().put("score",78).put("text","협력·학습·집안일 등 ‘함께 하는 일’에서 주도성이 빛납니다. 먼저 의견을 정리해 제안하면 흐름이 좋아집니다.").put("advice",""))
            sec.put("money",  JSONObject().put("score",70).put("text","지출을 가볍게 점검하기 좋은 날. 자동이체·구독을 한 번 확인하고, 소액이라도 저축을 실행해 보세요.").put("advice",""))
            sec.put("lotto",  JSONObject().put("score",55).put("text","").put("advice",""))
            obj.put("sections", sec)
        } else {
            secIn.optJSONObject("lotto")?.put("text","")?.put("advice","")
            obj.put("sections", secIn)
        }

        val lotto = obj.optJSONArray("lottoNumbers")
        if (lotto == null || lotto.length() != 6) obj.put("lottoNumbers", JSONArray(genLottoNumbers(seed)))

        val luckyObj = obj.getJSONObject("lucky")
        val lTime = luckyObj.optString("time")
        val lNum = luckyObj.optInt("number")
        val lColor = luckyObj.optString("colorHex")

        val tomorrowObj = (obj.optJSONObject("tomorrow") ?: JSONObject())
        val longRaw = tomorrowObj.optString("long")
        val longFixed = normalizePlan(if (longRaw.isBlank()) makeTomorrowPlan(obj) else longRaw, lTime, lNum, lColor)
        tomorrowObj.put("long", longFixed)
        obj.put("tomorrow", tomorrowObj)

        return obj
    }

    private fun buildFallbackFromText(txt: String, seed: Int): JSONObject {
        val (p, n, ng) = seededEmotions(seed)
        val base = JSONObject().apply {
            put("message", txt.ifBlank { "오늘의 흐름을 간결하게 정리했습니다." })
            put("keywords", JSONArray())
            put("lucky", JSONObject().apply {
                put("colorHex", pickLuckyColorFallback())
                put("number", pickLuckyNumberFallback(seed))
                put("time", pickLuckyTimeFallback())
            })
            put("emotions", JSONObject().apply {
                put("positive", p); put("neutral", n); put("negative", ng)
            })
            put("checklist", JSONArray()); put("sections", JSONObject())
            put("lottoNumbers", JSONArray(genLottoNumbers(seed)))
        }
        base.put("tomorrow", JSONObject().put("long", makeTomorrowPlan(base)))
        return validateAndFill(base, seed)
    }

    // ===== Deep (reward gate & dialog) =====
    private fun openDeepWithGate() {
        val key = "fortune_deep_unlocked_${todayKey()}"
        if (prefs.getBoolean(key, false)) { openDeepNow(); return }
        val bs = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val btnCancel = v.findViewById<Button>(R.id.btnCancel)
        val btnWatch = v.findViewById<MaterialButton>(R.id.btnWatchAd)
        val textStatus = v.findViewById<TextView>(R.id.textStatus)
        val progress = v.findViewById<android.widget.ProgressBar>(R.id.progressAd)
        btnCancel.setOnClickListener { bs.dismiss() }
        btnWatch.setOnClickListener {
            btnWatch.isEnabled = false; progress.visibility = View.VISIBLE; textStatus.text = "광고 준비 중…"
            AdManager.showRewarded(
                activity = requireActivity(),
                onRewardEarned = {
                    prefs.edit().putBoolean(key, true).apply()
                    bs.dismiss(); openDeepNow(); AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    btnWatch.isEnabled = true; progress.visibility = View.GONE
                    textStatus.text = "광고가 닫혔어요. 다시 시도해 주세요."
                    AdManager.loadRewarded(requireContext())
                },
                onFailed = { reason ->
                    btnWatch.isEnabled = true; progress.visibility = View.GONE
                    textStatus.text = "광고 로드 실패 ($reason)"; AdManager.loadRewarded(requireContext())
                }
            )
        }
        bs.setContentView(v); bs.show()
    }
    private fun openDeepNow() {
        val today = todayKey()
        val cached = prefs.getString("fortune_deep_$today", null)
        if (cached != null) {
            runCatching { JSONObject(cached) }.onSuccess { showDeepDialogFromDeep(it) }
            return
        }
        btnDeep.isEnabled = false; btnDeep.alpha = 0.7f; btnDeep.text = "생성 중…"
        val u = loadUserInfoSoft(); val seed = seedForToday(u)
        fetchDeepAnalysis(u, lastPayload!!, seed) { result, _ ->
            btnDeep.isEnabled = true; btnDeep.alpha = 1f; btnDeep.text = "심화 분석 보기"
            if (result != null) {
                prefs.edit().putString("fortune_deep_$today", result.toString()).apply()
                showDeepDialogFromDeep(result)
            } else showDeepDialogFromPayload(lastPayload!!)
        }
    }
    private fun fetchDeepAnalysis(u: UserInfo, daily: JSONObject, seed: Int, cb: (JSONObject?, String?) -> Unit) {
        val body = JSONObject().apply {
            put("model","gpt-4o-mini"); put("temperature",0.5)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role","system").put("content","당신은 프리미엄 라이프 코치이자 운세 분석가입니다. 도구만 호출해 JSON을 반환하세요."))
                put(JSONObject().put("role","user").put("content", buildDeepPrompt(u, daily, seed)))
            })
            put("tools", JSONArray().put(JSONObject().apply {
                put("type","function")
                put("function", JSONObject().apply {
                    put("name","deep_fortune_analysis")
                    put("description","오늘의 운세 기반 심화 분석을 구조화해 반환")
                    put("parameters", deepSchema())
                })
            }))
            put("tool_choice", JSONObject().apply {
                put("type","function"); put("function", JSONObject().put("name","deep_fortune_analysis"))
            })
            put("max_tokens", 1800)
        }
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization","Bearer $apiKey")
            .addHeader("Content-Type","application/json").build()
        http.newCall(req).enqueue(object: Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { if (!isAdded) return; requireActivity().runOnUiThread { cb(null, e.message ?: "io") } }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val raw = response.body?.string().orEmpty(); if (!isAdded) return
                if (!response.isSuccessful) { requireActivity().runOnUiThread { cb(null, "http ${response.code}") }; return }
                try {
                    val root = JSONObject(raw)
                    val msg = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    val tc = msg.optJSONArray("tool_calls")
                    val args = if (tc != null && tc.length() > 0) {
                        tc.getJSONObject(0).getJSONObject("function").getString("arguments")
                    } else { msg.getJSONObject("function_call").getString("arguments") }
                    val deep = JSONObject(args)
                    requireActivity().runOnUiThread { cb(deep, null) }
                } catch (_: Exception) { requireActivity().runOnUiThread { cb(null, "parse") } }
            }
        })
    }
    private fun deepSchema(): JSONObject = JSONObject().apply {
        put("type","object")
        put("required", JSONArray(listOf("highlights","plan","luckyColorName","luckyTime","luckyNumber")))
        put("properties", JSONObject().apply {
            put("highlights", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")); put("minItems",3); put("maxItems",6) })
            put("plan", JSONObject().apply {
                put("type","object"); put("required", JSONArray(listOf("morning","afternoon","evening")))
                put("properties", JSONObject().apply {
                    put("morning", JSONObject().put("type","string"))
                    put("afternoon", JSONObject().put("type","string"))
                    put("evening", JSONObject().put("type","string"))
                })
            })
            put("tips", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")) })
            put("checklistAdjusted", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")) })
            put("luckyColorName", JSONObject().put("type","string"))
            put("luckyTime", JSONObject().put("type","string"))
            put("luckyNumber", JSONObject().put("type","integer"))
        })
    }
    private fun buildDeepPrompt(u: UserInfo, daily: JSONObject, seed: Int): String {
        val userAge = ageOf(u.birth); val tag = ageTag(userAge)
        val dailyCompact = JSONObject(daily.toString())
        return """
[입력 데이터]
user: { nickname:"${u.nickname}", mbti:"${u.mbti}", birthdate:"${u.birth}", gender:"${u.gender}", age:$userAge, age_tag:$tag, birth_time:"${u.birthTime}", seed:$seed }
daily_fortune_payload: $dailyCompact

[목표]
- 전 연령(5–80세)이 바로 따라할 수 있는 **심화 분석**을 JSON으로 반환.
- '오늘의 체크'를 반영하고 낮은 점수 영역을 보완.
- 회사 전용 표현 금지, 중립 표현 사용.

[출력 형식]
- function deep_fortune_analysis 호출.
- highlights 3~6개.
- plan: morning(09~12), afternoon(13~17), evening(19~22) 각 2~4줄.
- tips 3~5개, checklistAdjusted 3개.
- luckyColorName: 사람 이름(라벤더 등), 헥스코드 금지.
- luckyTime/Number: daily의 값 유지.
        """.trimIndent()
    }

    // ===== Dialog UIs =====
    private var dlg: android.app.Dialog? = null

    private fun showDeepDialogFromDeep(obj: JSONObject) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = requireContext().getDrawable(R.drawable.bg_deep_dialog)
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }
        val tvTitle = TextView(requireContext()).apply {
            text = "심화 분석"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val tvHighlights = TextView(requireContext()).apply {
            val arr = obj.optJSONArray("highlights") ?: JSONArray()
            val bullets = (0 until arr.length())
                .mapNotNull { arr.optString(it).takeIf { it.isNotBlank() } }
                .joinToString("\n") { "• $it" }
                .ifBlank { "• 오늘 흐름을 간결히 정리했어요." }
            text = bullets
            setTextColor(Color.parseColor("#CFE3F2"))
            textSize = 14f
        }
        val plan = obj.optJSONObject("plan") ?: JSONObject()
        val luckyColorName = obj.optString("luckyColorName", "행운색")
        val luckyTime = obj.optString("luckyTime", lastPayload?.optJSONObject("lucky")?.optString("time") ?: "")
        val luckyNum = obj.optInt("luckyNumber", lastPayload?.optJSONObject("lucky")?.optInt("number") ?: 0)

        val tvPlan = TextView(requireContext()).apply {
            val morning = neutralizeCorporateTerms(plan.optString("morning"))
            val afternoon = neutralizeCorporateTerms(plan.optString("afternoon"))
            val evening = neutralizeCorporateTerms(plan.optString("evening"))
            text = buildString {
                append("행운 시간: ").append(luckyTime).append("\n")
                append("행운 숫자 ").append(luckyNum).append("\n")
                append("행운색 ").append(luckyColorName).append("\n\n")
                append("아침(09~12)\n").append(morning).append("\n\n")
                append("오후(13~17)\n").append(afternoon).append("\n\n")
                append("저녁(19~22)\n").append(evening)
            }
            setTextColor(Color.parseColor("#CFE3F2"))
            textSize = 14f
        }
        val btnClose = MaterialButton(requireContext()).apply {
            text = "닫기"
            setOnClickListener { dlg?.dismiss() }
        }

        container.addView(tvTitle)
        container.addView(spaceView(8))
        container.addView(tvHighlights)
        container.addView(spaceView(12))
        container.addView(tvPlan)
        container.addView(spaceView(12))
        container.addView(btnClose)

        val builder = MaterialAlertDialogBuilder(requireContext()).setView(container)
        val dialog = builder.create()
        dlg = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        val w = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showDeepDialogFromPayload(obj: JSONObject) {
        val sections = obj.optJSONObject("sections") ?: JSONObject()
        fun labelOf(key: String) = when (key) {
            "overall" -> "총운"
            "love" -> "연애운"
            "study" -> "학업운"
            "work" -> "직장운"
            "money" -> "재물운"
            "lotto" -> "로또운"
            else -> key
        }
        fun pickTop(vararg keys: String) =
            keys.mapNotNull { k -> sections.optJSONObject(k)?.let { k to it } }
                .sortedByDescending { it.second.optInt("score", 0) }
                .take(3)

        val top3 = pickTop("overall","love","study","work","money")
            .joinToString("\n• ") { (k, v) -> "${labelOf(k)}: ${v.optInt("score", 0)} — ${v.optString("text")}" }
            .let { if (it.isBlank()) "데이터가 충분하지 않아요." else "• $it" }

        val lucky = obj.optJSONObject("lucky")
        val luckyTime = lucky?.optString("time").orEmpty()
        val luckyNum = lucky?.optInt("number", 0) ?: 0
        val luckyHex = lucky?.optString("colorHex").orEmpty()
        val plan = normalizePlan(
            obj.optJSONObject("tomorrow")?.optString("long").orEmpty().ifBlank { makeTomorrowPlan(obj) },
            luckyTime, luckyNum, luckyHex
        )

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = requireContext().getDrawable(R.drawable.bg_deep_dialog)
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }
        val tvTitle = TextView(requireContext()).apply {
            text = "심화 분석"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val tvHighlights = TextView(requireContext()).apply {
            text = top3
            setTextColor(Color.parseColor("#CFE3F2"))
            textSize = 14f
        }
        val tvPlan = TextView(requireContext()).apply {
            text = plan + "\n\n내일 대비\n· 가방/필수품 준비 · 알람 1개 추가 · 일정 확인"
            setTextColor(Color.parseColor("#CFE3F2"))
            textSize = 14f
        }
        val btnClose = MaterialButton(requireContext()).apply {
            text = "닫기"
            setOnClickListener { dlg?.dismiss() }
        }

        container.addView(tvTitle)
        container.addView(spaceView(8))
        container.addView(tvHighlights)
        container.addView(spaceView(12))
        container.addView(tvPlan)
        container.addView(spaceView(12))
        container.addView(btnClose)

        val builder = MaterialAlertDialogBuilder(requireContext()).setView(container)
        val dialog = builder.create()
        dlg = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        val w = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private fun spaceView(h: Int) = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
    }

    // ===== History & Lucky =====
    private fun genLottoNumbers(seed: Int): List<Int> {
        val r = Random(seed xor 0x9E3779B9u.toInt()); val set = LinkedHashSet<Int>()
        while (set.size < 6) set += (1 + r.nextInt(45))
        return set.toList().sorted()
    }
    private fun getRecentLuckyColors(limit: Int = 5): List<String> {
        val arr = JSONArray(prefs.getString("lucky_history_colors", "[]"))
        return (0 until arr.length()).mapNotNull { arr.optString(it) }.takeLast(limit)
    }
    private fun getRecentLuckyTimes(limit: Int = 5): List<String> {
        val arr = JSONArray(prefs.getString("lucky_history_times", "[]"))
        return (0 until arr.length()).mapNotNull { arr.optString(it) }.takeLast(limit)
    }
    private fun updateLuckyHistory(color: String, time: String) {
        fun push(key: String, value: String) {
            val arr = JSONArray(prefs.getString(key, "[]"))
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) list += arr.optString(i)
            list += value
            prefs.edit().putString(key, JSONArray(list.takeLast(10)).toString()).apply()
        }
        push("lucky_history_colors", color)
        push("lucky_history_times", time)
    }
    private fun finalizePayload(payload: JSONObject): JSONObject {
        payload.optJSONObject("lucky")?.let {
            val c = it.optString("colorHex"); val t = it.optString("time")
            if (c.isNotBlank() && t.isNotBlank()) updateLuckyHistory(c, t)
        }
        return avoidRepeats(payload)
    }
    private fun avoidRepeats(payload: JSONObject): JSONObject {
        val lucky = payload.optJSONObject("lucky") ?: JSONObject()
        var color = lucky.optString("colorHex"); var time = lucky.optString("time")
        val usedColors = getRecentLuckyColors(); val usedTimes = getRecentLuckyTimes()
        if (color.isBlank() || usedColors.contains(color)) {
            color = luckyPalette.firstOrNull { it !in usedColors } ?: luckyPalette.random()
            lucky.put("colorHex", color)
        }
        if (time.isBlank() || usedTimes.contains(time)) {
            val hours = (6..22).map { if (it < 12) "오전 ${it}시" else "오후 ${if (it == 12) 12 else it - 12}시" }
            lucky.put("time", hours.firstOrNull { it !in usedTimes } ?: "오후 3시")
        }
        payload.put("lucky", lucky); return payload
    }

    // ===== Neutral wording =====
    private fun neutralizeCorporateTerms(text: String): String {
        var s = text
        s = s.replace(Regex("회의|미팅|면담"), "상담/연락")
        s = s.replace(Regex("이메일"), "알림/메모")
        s = s.replace(Regex("보고서"), "노트 정리")
        s = s.replace(Regex("결재"), "확인")
        s = s.replace(Regex("메신저"), "연락")
        return s
    }
    private fun colorNameForHex(hex: String): String = when (hex.uppercase(Locale.ROOT)) {
        "#8E9BFF" -> "라일락 블루"; "#5BD1D7" -> "민트 블루"; "#FFB86B" -> "살구 오렌지"
        "#6EDCA4" -> "파스텔 그린"; "#FF8BA7" -> "로즈 핑크"; "#B28DFF" -> "연보라"
        "#7AC4FF" -> "스카이 블루"; "#FFC75F" -> "골든 옐로"; "#52E5A8" -> "민트 그린"
        "#FF9999" -> "코랄 핑크"; "#7FD1B9" -> "그린 민트"; "#A1C4FD" -> "라이트 블루"
        "#FECF6A" -> "샌디 옐로"; "#98E0FF" -> "베이비 블루"; "#C3A9FF" -> "라벤더"
        else -> "행운색"
    }
    private fun pickLuckyColorFallback(): String = luckyPalette.random()
    private fun pickLuckyTimeFallback(): String {
        val hours = (6..22).map { if (it < 12) "오전 ${it}시" else "오후 ${if (it == 12) 12 else it - 12}시" }
        return hours.random()
    }
    private fun pickLuckyNumberFallback(seed: Int): Int = (Random(seed).nextInt(95) + 3)

    // ===== Tomorrow plan helpers =====
    private fun makeTomorrowPlan(obj: JSONObject): String {
        val sec = obj.optJSONObject("sections") ?: JSONObject()
        val lucky = obj.optJSONObject("lucky")
        val luckyTime = lucky?.optString("time").orEmpty()
        val luckyNum = lucky?.optInt("number", 0) ?: 0
        val luckyColorHex = lucky?.optString("colorHex").orEmpty()
        val luckyColName = colorNameForHex(luckyColorHex)
        val worst = listOf("overall","love","study","work","money")
            .mapNotNull { k -> sec.optJSONObject(k)?.let { k to it.optInt("score", 0) } }
            .minByOrNull { it.second }?.first ?: "overall"

        val cl = obj.optJSONArray("checklist") ?: JSONArray()
        fun ck(i: Int, d: String) = cl.optString(i, d)

        return buildString {
            append("행운 시간: ").append(luckyTime).append("\n")
            append("행운 숫자 ").append(luckyNum).append("\n")
            append("행운색 ").append(luckyColName).append("\n\n")
            append("아침(09~12)\n")
            append("· 15분 정리 후 중요한 일 1가지부터 시작\n")
            append("· ").append(ck(0, "연락 1건")).append("\n\n")
            append("오후(13~17)\n")
            append("· 행운 숫자 ").append(luckyNum).append("을 타이머 시작·휴식 신호로 활용\n")
            append("· 학습/가사/개인 프로젝트 25분×2 진행\n\n")
            append("저녁(19~22)\n")
            append("· ").append(
                when (worst) {
                    "love"  -> "대화 리캡 2줄 + 감사 한 줄"
                    "study" -> "10분 복습 + 내일 키워드 3개"
                    "work"  -> "배운 점 3개 메모, 내일 첫 일거리 1개"
                    "money" -> "지출 3분 점검, 구독 1개 확인"
                    else    -> "10분 회고, 내일 체크 3가지 작성"
                }
            )
        }
    }
    private fun normalizePlan(plan: String, luckyTime: String, luckyNumber: Int, luckyColorHex: String): String {
        if (plan.isBlank()) return plan
        var s = plan
        s = if (Regex("행운\\s*시간").containsMatchIn(s))
            s.replace(Regex("행운\\s*시간[^\\n]*"), "행운 시간: $luckyTime") else "행운 시간: $luckyTime\n$s"
        s = if (Regex("행운\\s*숫자\\s*\\d+").containsMatchIn(s))
            s.replace(Regex("행운\\s*숫자\\s*\\d+"), "행운 숫자 $luckyNumber")
        else s.replace("오후(13~17)", "오후(13~17)\n· 행운 숫자 ${luckyNumber}을(를) 타이머 시작·휴식 신호로 활용 ($luckyTime)")
        val colorName = colorNameForHex(luckyColorHex)
        if (!Regex("행운 ?색").containsMatchIn(s)) s += "\n\n행운색 $colorName 소품/배경 활용"
        return neutralizeCorporateTerms(s)
    }

    // ===== User / Seed =====
    data class UserInfo(val nickname: String, val mbti: String, val birth: String, val gender: String, val birthTime: String)
    private fun loadUserInfoSoft(): UserInfo {
        val nn = prefs.getString("nickname", "사용자") ?: "사용자"
        val mb = prefs.getString("mbti", "N/A") ?: "N/A"
        val bd = prefs.getString("birthdate", "1990-01-01") ?: "1990-01-01"
        val gd = prefs.getString("gender", "미선택") ?: "미선택"
        val bt = prefs.getString("birth_time", "선택안함") ?: "선택안함"
        if (mb == "N/A" || gd == "미선택") {
            Toast.makeText(requireContext(), "개인정보가 일부 비어 있어 개인화가 제한됩니다.", Toast.LENGTH_SHORT).show()
        }
        return UserInfo(nn, mb, bd, gd, bt)
    }
    private fun seedForToday(u: UserInfo): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val base = "$uid|${u.nickname}|${u.birth}|${u.gender}|${todayKey()}"
        val md = MessageDigest.getInstance("MD5").digest(base.toByteArray())
        return ((md[0].toInt() and 0xFF) shl 24) or ((md[1].toInt() and 0xFF) shl 16) or ((md[2].toInt() and 0xFF) shl 8) or (md[3].toInt() and 0xFF)
    }
    private fun todayKey(): String = dateFmt.format(Date())
    private fun isFortuneSeenToday(): Boolean = prefs.getBoolean("fortune_seen_${todayKey()}", false)

    // ===== Emotions helpers =====
    private data class Sextuple(val a: Int, val b: Int, val c: Int, val d: Int, val e: Int, val f: Int)
    private fun emotionRange(seed: Int): Sextuple {
        val r = Random(seed)
        val pMin = 35 + r.nextInt(10); val pMax = pMin + 20 + r.nextInt(16)
        val nMin = 15 + r.nextInt(10); val nMax = nMin + 20 + r.nextInt(6)
        val ngMin = 5 + r.nextInt(10); val ngMax = ngMin + 10 + r.nextInt(8)
        return Sextuple(pMin, pMax, nMin, nMax, ngMin, ngMax)
    }
    private fun seededEmotions(seed: Int): Triple<Int, Int, Int> {
        val r = Random(seed)
        val pos = 40 + r.nextInt(40); val neg = 5 + r.nextInt(25)
        val neu = 100 - (pos + neg).coerceAtMost(95)
        return Triple(pos, neu.coerceIn(10, 50), neg)
    }

    // ===== Age helpers =====
    private fun ageOf(birth: String): Int = try {
        val d = dateFmt.parse(birth)!!
        val cNow = Calendar.getInstance()
        val cBirth = Calendar.getInstance().apply { time = d }
        var age = cNow.get(Calendar.YEAR) - cBirth.get(Calendar.YEAR)
        if (cNow.get(Calendar.DAY_OF_YEAR) < cBirth.get(Calendar.DAY_OF_YEAR)) age--
        age.coerceIn(0, 120)
    } catch (_: Exception) { 25 }

    private fun ageTag(age: Int): String = when {
        age <= 12 -> "child"
        age <= 18 -> "teen"
        age <= 25 -> "student"
        age <= 64 -> "adult"
        else      -> "senior"
    }
    private fun fallbackChecklist(tag: String): List<String> = when (tag) {
        "child"   -> listOf("그림 10분", "정리정돈 5분", "좋아하는 책 10쪽")
        "teen"    -> listOf("과제 20분", "산책/운동 15분", "연락 1건(가족/친구)")
        "student" -> listOf("공부/프로젝트 25분", "스트레칭 10분", "지출 5분 점검")
        "adult"   -> listOf("집안일 15분", "건강 관리 20분", "중요 연락 1건")
        else      -> listOf("가벼운 스트레칭 10분", "약·건강 확인", "안부 연락 1통")
    }
}
