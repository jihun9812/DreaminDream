package com.example.dreamindream

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

class FortuneFragment : Fragment() {

    // ====== Config ======
    private val apiKey = BuildConfig.OPENAI_API_KEY
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val http by lazy { OkHttpClient() }

    // ====== Views ======
    private lateinit var prefs: SharedPreferences
    private lateinit var resultText: TextView
    private lateinit var loadingView: LottieAnimationView
    private lateinit var button: MaterialButton
    private lateinit var fortuneCard: MaterialCardView
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
    private lateinit var layoutChecklist: LinearLayout
    private var btnDeep: MaterialButton? = null

    // ====== State ======
    private var lastPayload: JSONObject? = null
    private var isExpanded = false

    // ====== Style ======
    private val luckyPalette = listOf(
        "#8E9BFF", "#5BD1D7", "#FFB86B", "#6EDCA4", "#FF8BA7",
        "#B28DFF", "#7AC4FF", "#FFC75F", "#52E5A8", "#FF9999",
        "#7FD1B9", "#A1C4FD", "#FECF6A", "#98E0FF", "#C3A9FF"
    )
    private val sectionTitleColor = Color.parseColor("#9FB8FF")

    // ====== Helpers: Date / Prefs ======
    private fun todayKey(): String = dateFmt.format(Date())
    private fun isFortuneSeenToday(): Boolean =
        prefs.getBoolean("fortune_seen_${todayKey()}", false)

    private fun lockFortuneButtonForToday() {
        button.isEnabled = false
        button.alpha = 0.45f
        button.text = "내일 다시"
    }

    // ====== Lifecycle ======
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_fortune, container, false)

        // Bind
        resultText = view.findViewById(R.id.fortune_result)
        loadingView = view.findViewById(R.id.fortune_loading)
        button = view.findViewById(R.id.fortuneButton)
        fortuneCard = view.findViewById(R.id.fortuneCard)
        chips = view.findViewById(R.id.chipsFortune)
        viewLuckyColor = view.findViewById(R.id.viewLuckyColor)
        tvLuckyNumber = view.findViewById(R.id.tvLuckyNumber)
        tvLuckyTime = view.findViewById(R.id.tvLuckyTime)
        barPos = view.findViewById(R.id.barPos)
        barNeu = view.findViewById(R.id.barNeu)
        barNeg = view.findViewById(R.id.barNeg)
        tvPos = view.findViewById(R.id.tvPos)
        tvNeu = view.findViewById(R.id.tvNeu)
        tvNeg = view.findViewById(R.id.tvNeg)
        btnCopy = view.findViewById(R.id.btnCopy)
        btnShare = view.findViewById(R.id.btnShare)
        btnDeep = view.findViewById(R.id.btnDeep)
        layoutChecklist = view.findViewById(R.id.layoutChecklist)

        // Prefs (user-scoped)
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        prefs = requireContext().getSharedPreferences("user_info_$userId", Context.MODE_PRIVATE)

        // Ads
        view.findViewById<AdView>(R.id.adView_fortune).loadAd(AdRequest.Builder().build())
        AdManager.initialize(requireContext())
        AdManager.loadRewarded(requireContext())

        // Cached payload
        val today = todayKey()
        prefs.getString("fortune_payload_$today", null)?.let {
            runCatching { JSONObject(it) }.onSuccess { p ->
                lastPayload = p
                bindFromPayload(p, view)
                expandFortuneCard(view)
            }
        } ?: prefs.getString("fortune_$today", null)?.let {
            resultText.text = it
            setEmotionBars(60, 30, 10)
            expandFortuneCard(view)
        }

        if (isFortuneSeenToday()) lockFortuneButtonForToday()

        // Small util: Click animation
        fun View.scaleClick(action: () -> Unit) = setOnClickListener {
            startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            action()
        }

        // Copy & Share
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

        // Main button
        button.scaleClick {
            if (isFortuneSeenToday()) {
                Toast.makeText(requireContext(), "오늘은 이미 확인했어요. 내일 다시 이용해주세요.", Toast.LENGTH_SHORT).show()
                return@scaleClick
            }
            val u = loadUserInfoSoft()
            val seed = seedForToday(u)
            fetchFortune(u, today, seed, view)
        }

        // Deep button (rewarded once per day)
        btnDeep?.setOnClickListener {
            if (lastPayload == null) {
                Toast.makeText(requireContext(), "먼저 ‘행운보기’를 실행해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openDeepWithGate()
        }

        return view
    }

    // ====== Rewarded gate for deep dialog ======
    private fun openDeepWithGate() {
        val key = "fortune_deep_unlocked_${todayKey()}"
        if (prefs.getBoolean(key, false)) {
            showDeepDialogFromPayload(lastPayload!!)
            return
        }
        val bs = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val btnCancel = v.findViewById<Button>(R.id.btnCancel)
        val btnWatch = v.findViewById<MaterialButton>(R.id.btnWatchAd)
        val textStatus = v.findViewById<TextView>(R.id.textStatus)
        val progress = v.findViewById<android.widget.ProgressBar>(R.id.progressAd)

        btnCancel.setOnClickListener { bs.dismiss() }
        btnWatch.setOnClickListener {
            btnWatch.isEnabled = false
            progress.visibility = View.VISIBLE
            textStatus.text = "광고 준비 중…"
            AdManager.showRewarded(
                activity = requireActivity(),
                onRewardEarned = {
                    prefs.edit().putBoolean(key, true).apply()
                    bs.dismiss()
                    showDeepDialogFromPayload(lastPayload!!)
                    AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    btnWatch.isEnabled = true
                    progress.visibility = View.GONE
                    textStatus.text = "광고가 닫혔어요. 다시 시도해 주세요."
                    AdManager.loadRewarded(requireContext())
                },
                onFailed = { reason ->
                    btnWatch.isEnabled = true
                    progress.visibility = View.GONE
                    textStatus.text = "광고 로드 실패 ($reason)"
                    AdManager.loadRewarded(requireContext())
                }
            )
        }
        bs.setContentView(v)
        bs.show()
    }

    // ====== User / Seed ======
    data class UserInfo(
        val nickname: String,
        val mbti: String,
        val birth: String,
        val gender: String,
        val birthTime: String
    )

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

    private fun dayOfWeekKorean(): String =
        SimpleDateFormat("EEEE", Locale.KOREAN).format(Date())

    private fun seedForToday(u: UserInfo): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val base = "$uid|${u.nickname}|${u.birth}|${u.gender}|${todayKey()}"
        val md = MessageDigest.getInstance("MD5").digest(base.toByteArray())
        return ((md[0].toInt() and 0xFF) shl 24) or
                ((md[1].toInt() and 0xFF) shl 16) or
                ((md[2].toInt() and 0xFF) shl 8) or
                (md[3].toInt() and 0xFF)
    }

    // ====== Network ======
    private fun fetchFortune(u: UserInfo, today: String, seed: Int, view: View, attempt: Int = 1) {
        if (!isAdded) return

        val promptUser = buildUserPrompt(u, seed)
        val systemMsg = """
            당신은 프리미엄 운세 컨설턴트입니다. 오직 도구 daily_fortune를 호출해 결과를 반환하세요.
            메시지/코드블록/설명 없이 tool arguments만 생성하십시오.
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.6)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemMsg))
                put(JSONObject().put("role", "user").put("content", promptUser))
            })
            put("tools", JSONArray().put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "daily_fortune")
                    put("description", "오늘의 맞춤 운세를 구조화해 반환")
                    put("parameters", fortuneSchema())
                })
            }))
            // tool_choice만 사용 (function_call 제거: 400 방지)
            put("tool_choice", JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().put("name", "daily_fortune"))
            })
            put("max_tokens", 1500)
        }

        // Loading animation (UI thread)
        button.isEnabled = false
        button.alpha = 0.6f
        loadingView.apply {
            alpha = 0f
            translationY = -300f
            scaleX = 0.3f
            scaleY = 0.3f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(700)
                .setInterpolator(BounceInterpolator())
                .start()
            playAnimation()
        }
        fortuneCard.visibility = View.GONE
        resultText.text = ""

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    onFetchFailed(u, today, seed, view, attempt, e.message ?: "io")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string().orEmpty()
                if (!isAdded) return

                if (!response.isSuccessful) {
                    requireActivity().runOnUiThread {
                        onFetchFailed(u, today, seed, view, attempt, "http ${response.code}")
                    }
                    return
                }

                requireActivity().runOnUiThread {
                    loadingView.cancelAnimation()
                    loadingView.visibility = View.GONE

                    val payload = try {
                        val root = JSONObject(raw)
                        val msg = root.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")

                        val tc = msg.optJSONArray("tool_calls")
                        if (tc != null && tc.length() > 0) {
                            val args = tc.getJSONObject(0)
                                .getJSONObject("function")
                                .getString("arguments")
                            validateAndFill(JSONObject(args), seed)
                        } else {
                            val fc = msg.optJSONObject("function_call")
                            if (fc != null) {
                                validateAndFill(JSONObject(fc.optString("arguments", "{}")), seed)
                            } else {
                                parsePayloadAlways(msg.optString("content"), seed)
                            }
                        }
                    } catch (_: Exception) {
                        parsePayloadAlways(raw, seed)
                    }

                    val adjusted = finalizePayload(payload)
                    lastPayload = adjusted
                    bindFromPayload(adjusted, view)

                    // Cache & lock
                    prefs.edit()
                        .putString("fortune_payload_$today", adjusted.toString())
                        .putBoolean("fortune_seen_$today", true)
                        .apply()
                    lockFortuneButtonForToday()

                    view.findViewById<ScrollView?>(R.id.resultScrollView)?.let { s ->
                        s.post {
                            s.scrollTo(0, 0)
                            s.fullScroll(View.FOCUS_UP)
                        }
                    }

                    expandFortuneCard(view)
                    button.isEnabled = true
                    button.alpha = 1f
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
        requireActivity().runOnUiThread {
            loadingView.cancelAnimation()
            loadingView.visibility = View.GONE

            if (attempt == 1) {
                // 재시도도 반드시 메인 스레드에서 호출
                fetchFortune(u, today, seed, view, attempt = 2)
                return@runOnUiThread
            }

            resultText.text = getString(R.string.error_fetch_failed) + " ($reason)"
            val (p, n, ng) = seededEmotions(seed)
            setEmotionBars(p, n, ng)
            expandFortuneCard(view)
            button.isEnabled = true
            button.alpha = 1f
        }
    }

    // ====== Schema & Prompt ======
    private fun fortuneSchema(): JSONObject {
        val obj = JSONObject()
        obj.put("type", "object")
        obj.put(
            "required",
            JSONArray(listOf("lucky", "message", "sections", "keywords", "emotions", "checklist", "tomorrow"))
        )
        obj.put("properties", JSONObject().apply {
            put("lucky", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray(listOf("colorHex", "number", "time")))
                put("properties", JSONObject().apply {
                    put("colorHex", JSONObject().put("type", "string").put("pattern", "#[0-9A-Fa-f]{6}"))
                    put("number", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 99))
                    put("time", JSONObject().put("type", "string"))
                })
            })
            put("message", JSONObject().put("type", "string"))
            put("sections", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray(listOf("overall", "love", "study", "work", "money", "lotto")))
                fun sec() = JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray(listOf("score", "text", "advice")))
                    put("properties", JSONObject().apply {
                        put("score", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100))
                        put("text", JSONObject().put("type", "string"))
                        put("advice", JSONObject().put("type", "string"))
                    })
                }
                put("overall", sec()); put("love", sec()); put("study", sec())
                put("work", sec()); put("money", sec()); put("lotto", sec())
            })
            put("keywords", JSONObject().apply {
                put("type", "array")
                put("items", JSONObject().put("type", "string"))
                put("minItems", 1); put("maxItems", 4)
            })
            put("emotions", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray(listOf("positive", "neutral", "negative")))
                put("properties", JSONObject().apply {
                    put("positive", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100))
                    put("neutral", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100))
                    put("negative", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 100))
                })
            })
            // optional lotto
            put("lottoNumbers", JSONObject().apply {
                put("type", "array")
                put("items", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 45))
                put("minItems", 6); put("maxItems", 6)
            })
            put("checklist", JSONObject().apply {
                put("type", "array")
                put("items", JSONObject().put("type", "string"))
                put("minItems", 3); put("maxItems", 3)
            })
            // tomorrow.long
            put("tomorrow", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray(listOf("long")))
                put("properties", JSONObject().apply {
                    put("long", JSONObject().put("type", "string"))
                })
            })
        })
        return obj
    }

    private fun buildUserPrompt(u: UserInfo, seed: Int): String {
        val today = todayKey()
        val weekday = dayOfWeekKorean()
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
seed: $seed

[가이드]
- 출력은 도구 daily_fortune 인자(JSON)로만.
- 톤: 따뜻하지만 전문적인 컨설턴트. 과장체 금지, 명료한 문장.
- checklist는 시간/수치 포함, 12~18자.
- emotions 분포: positive $pMin~$pMax, neutral $nMin~$nMax, negative $ngMin~$ngMax.
- lucky.colorHex는 palette에서, 최근 사용(avoidColors/avoidTimes) 회피, time은 "오전/오후 X시".
- 프로필/seed에 따라 서술이 달라지게 구체적으로 작성.

[tomorrow.long 요구사항]
- 한국어 600~900자.
- 섹션: "아침(09~12)", "오후(13~17)", "저녁(19~22)" 소제목 포함.
- 각 섹션별 2~4개의 실행 지시를 시간·분량·횟수 단위로 제시(예: "25분×2 세션", "메시지 5줄", "예산 1만 원").
- lucky.time/number/color를 적절히 활용(예: 해당 시간에 연락·미팅 배치).
- 오늘 점수가 낮은 영역 보완 액션을 필수로 포함.
- 불필요한 형용사/서론 금지. 바로 실행계획으로.

[힌트]
palette: $palette
avoidColors: $avoidColors
avoidTimes: $avoidTimes
        """.trimIndent()
    }

    // ====== Emotions helper ======
    private fun emotionRange(seed: Int): Sextuple {
        val r = Random(seed)
        val pMin = 35 + r.nextInt(10)
        val pMax = pMin + 20 + r.nextInt(16)
        val nMin = 15 + r.nextInt(10)
        val nMax = nMin + 20 + r.nextInt(6)
        val ngMin = 5 + r.nextInt(10)
        val ngMax = ngMin + 10 + r.nextInt(8)
        return Sextuple(pMin, pMax, nMin, nMax, ngMin, ngMax)
    }

    data class Sextuple(val a: Int, val b: Int, val c: Int, val d: Int, val e: Int, val f: Int)

    private fun seededEmotions(seed: Int): Triple<Int, Int, Int> {
        val r = Random(seed)
        val pos = 40 + r.nextInt(40)
        val neg = 5 + r.nextInt(25)
        val neu = 100 - (pos + neg).coerceAtMost(95)
        return Triple(pos, neu.coerceIn(10, 50), neg)
    }

    // ====== Bind ======
    private fun setKeywords(list: List<String>) {
        chips.removeAllViews()
        list.take(4).forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = false
                isClickable = false
                setTextColor(Color.WHITE)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorSecondaryContainer,
                        0
                    )
                )
            }
            chips.addView(chip)
        }
    }

    private fun setLucky(colorHex: String, number: Int, time: String) {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.parseColor("#FFD54F")))
        }
        viewLuckyColor.background = shape
        tvLuckyNumber.text = number.toString()
        tvLuckyTime.text = time
    }

    private fun setEmotionBars(pos: Int, neu: Int, neg: Int) {
        fun v(x: Int) = when {
            x <= 0 -> 0
            x in 1..7 -> 8
            else -> x
        }.coerceIn(0, 100)
        barPos.setProgressCompat(v(pos), true)
        barNeu.setProgressCompat(v(neu), true)
        barNeg.setProgressCompat(v(neg), true)
        tvPos.text = "${pos.coerceIn(0, 100)}%"
        tvNeu.text = "${neu.coerceIn(0, 100)}%"
        tvNeg.text = "${neg.coerceIn(0, 100)}%"
    }

    private fun bindFromPayload(obj: JSONObject, view: View) {
        val kwArr = obj.optJSONArray("keywords") ?: JSONArray()
        val kws = (0 until kwArr.length()).mapNotNull { kwArr.optString(it).takeIf { it.isNotBlank() } }
        if (kws.isNotEmpty()) setKeywords(kws)

        val lucky = obj.optJSONObject("lucky")
        setLucky(
            lucky?.optString("colorHex").orEmpty().ifBlank { pickLuckyColorFallback() },
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

        resultText.text = formatSections(obj)
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

    private fun formatSections(obj: JSONObject) = buildSpannedString {
        val sections = obj.optJSONObject("sections") ?: JSONObject()
        val lottoNums = obj.optJSONArray("lottoNumbers")
        fun line(label: String, key: String) {
            val s = sections.optJSONObject(key) ?: JSONObject()
            val score = s.optInt("score", -1)
            val text = s.optString("text").ifBlank { s.optString("advice") }
            if (text.isNotBlank() || score >= 0) {
                color(sectionTitleColor) { bold { append(label) } }
                if (score >= 0) append(" (${score}점)")
                if (key == "lotto" && lottoNums != null && lottoNums.length() == 6) {
                    val arr = (0 until 6).map { lottoNums.optInt(it) }.sorted()
                    append("  번호: ${arr.joinToString(", ")}")
                }
                append(" - ")
                append(text.trim())
                append("\n")
            }
        }
        line("총운", "overall"); line("연애운", "love"); line("학업운", "study")
        line("직장운", "work"); line("재물운", "money"); line("로또운", "lotto")
    }

    private fun expandFortuneCard(view: View?) {
        if (view == null || isExpanded) return
        isExpanded = true
        val root = view.findViewById<ConstraintLayout>(R.id.root_fortune_layout)
        val scroll = view.findViewById<ScrollView>(R.id.resultScrollView)
        val set = TransitionSet().apply {
            addTransition(Slide(Gravity.TOP))
            addTransition(Fade(Fade.IN))
            duration = 450
            ordering = TransitionSet.ORDERING_TOGETHER
        }
        TransitionManager.beginDelayedTransition(root as ViewGroup, set)
        fortuneCard.visibility = View.VISIBLE
        val newH = (resources.displayMetrics.heightPixels * 0.66f).toInt()
        val curH = max(scroll.height, 160)
        ValueAnimator.ofInt(curH, newH).apply {
            duration = 700
            startDelay = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val h = a.animatedValue as Int
                scroll.layoutParams.height = h
                scroll.requestLayout()
            }
            start()
        }
    }

    // ====== Parsing / Fallback ======
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
        var depth = 0
        var start = -1
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

        // lucky
        val lucky = (obj.optJSONObject("lucky") ?: JSONObject()).apply {
            if (!has("colorHex")) put("colorHex", pickLuckyColorFallback())
            if (!has("number")) put("number", pickLuckyNumberFallback(seed))
            if (!has("time")) put("time", pickLuckyTimeFallback())
        }
        obj.put("lucky", lucky)

        // emotions
        val (p, n, ng) = seededEmotions(seed)
        val emo = (obj.optJSONObject("emotions") ?: JSONObject()).apply {
            put("positive", optInt("positive", p).coerceIn(0, 100))
            put("neutral", optInt("neutral", n).coerceIn(0, 100))
            put("negative", optInt("negative", ng).coerceIn(0, 100))
        }
        obj.put("emotions", emo)

        // checklist
        val cl = (obj.optJSONArray("checklist") ?: JSONArray())
        val list = mutableListOf<String>()
        for (i in 0 until cl.length()) cl.optString(i)?.let { if (it.isNotBlank()) list += it }
        val fb = listOf("오전 10시 산책 15분", "연락 1건 먼저 보내기", "불필요 구독 1개 점검")
        while (list.size < 3) list += fb[list.size]
        obj.put("checklist", JSONArray(list.take(3)))

        // sections fallback
        obj.put("sections", obj.optJSONObject("sections") ?: JSONObject().apply {
            fun s(sc: Int, t: String, adv: String = "") =
                JSONObject().put("score", sc).put("text", t).put("advice", adv)
            put("overall", s(80, "오늘은 새 출발에 좋은 흐름입니다.", "오전 20분 정리 후 핵심 1건 먼저 처리."))
            put("love", s(75, "상대와의 대화에서 진솔함이 통합니다.", "메시지는 5줄 이내, 공감 2회."))
            put("study", s(82, "짧게 집중 루틴이 효율적입니다.", "25분×2 세트 후 10분 복습."))
            put("work", s(78, "협업에서 의견을 먼저 제시해 보세요.", "메신저 확인은 2회로 묶기."))
            put("money", s(70, "지출 관리가 핵심. 자동이체 점검.", "소액 저축 1건 즉시 설정."))
            put("lotto", s(55, "소액·가벼운 재미로만 즐기기.", "지출 한도 내에서만!"))
        })

        // lotto numbers (1..45, unique 6)
        val lotto = obj.optJSONArray("lottoNumbers")
        if (lotto == null || lotto.length() != 6) {
            obj.put("lottoNumbers", JSONArray(genLottoNumbers(seed)))
        }

        // tomorrow.long — normalize with lucky values (even when API provided)
        val luckyObj = obj.getJSONObject("lucky")
        val lTime = luckyObj.optString("time")
        val lNum = luckyObj.optInt("number")
        val lColor = luckyObj.optString("colorHex")

        val tomorrowObj = (obj.optJSONObject("tomorrow") ?: JSONObject())
        val longRaw = tomorrowObj.optString("long")
        val longFixed = normalizePlan(
            if (longRaw.isBlank()) makeTomorrowPlan(obj) else longRaw,
            lTime, lNum, lColor
        )
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
            put("checklist", JSONArray(listOf("오전 10시 산책 15분", "연락 1건 먼저 보내기", "불필요 구독 1개 점검")))
            put("sections", JSONObject().apply {
                fun s(sc: Int, t: String, adv: String = "") =
                    JSONObject().put("score", sc).put("text", t).put("advice", adv)
                put("overall", s(80, "오늘은 새 출발에 좋은 흐름입니다.", "오전 20분 정리 후 핵심 1건 먼저 처리."))
                put("love", s(75, "상대와의 대화에서 진솔함이 통합니다.", "메시지는 5줄 이내, 공감 2회."))
                put("study", s(82, "짧게 집중 루틴이 효율적입니다.", "25분×2 세트 후 10분 복습."))
                put("work", s(78, "협업에서 의견을 먼저 제시해 보세요.", "메신저 확인은 2회로 묶기."))
                put("money", s(70, "지출 관리가 핵심. 자동이체 점검.", "소액 저축 1건 즉시 설정."))
                put("lotto", s(55, "소액·가벼운 재미로만 즐기기.", "지출 한도 내에서만!"))
            })
            put("lottoNumbers", JSONArray(genLottoNumbers(seed)))
        }
        base.put("tomorrow", JSONObject().put("long", makeTomorrowPlan(base)))
        return base
    }

    // ====== Lotto / Lucky History ======
    private fun genLottoNumbers(seed: Int): List<Int> {
        val r = Random(seed xor 0x9E3779B9u.toInt())
        val set = LinkedHashSet<Int>()
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
            val c = it.optString("colorHex")
            val t = it.optString("time")
            if (c.isNotBlank() && t.isNotBlank()) updateLuckyHistory(c, t)
        }
        return avoidRepeats(payload)
    }

    private fun avoidRepeats(payload: JSONObject): JSONObject {
        val lucky = payload.optJSONObject("lucky") ?: JSONObject()
        var color = lucky.optString("colorHex")
        var time = lucky.optString("time")
        val usedColors = getRecentLuckyColors()
        val usedTimes = getRecentLuckyTimes()

        if (color.isBlank() || usedColors.contains(color)) {
            color = luckyPalette.firstOrNull { it !in usedColors } ?: luckyPalette.random()
            lucky.put("colorHex", color)
        }
        if (time.isBlank() || usedTimes.contains(time)) {
            val hours = (6..22).map {
                if (it < 12) "오전 ${it}시" else "오후 ${if (it == 12) 12 else it - 12}시"
            }
            lucky.put("time", hours.firstOrNull { it !in usedTimes } ?: "오후 3시")
        }
        payload.put("lucky", lucky)
        return payload
    }

    private fun pickLuckyColorFallback(): String = luckyPalette.random()
    private fun pickLuckyTimeFallback(): String {
        val hours = (6..22).map {
            if (it < 12) "오전 ${it}시" else "오후 ${if (it == 12) 12 else it - 12}시"
        }
        return hours.random()
    }

    private fun pickLuckyNumberFallback(seed: Int): Int =
        (Random(seed).nextInt(95) + 3)

    // ====== Tomorrow Plan (local fallback) ======
    private fun makeTomorrowPlan(obj: JSONObject): String {
        val sec = obj.optJSONObject("sections") ?: JSONObject()
        val lucky = obj.optJSONObject("lucky")
        val luckyTime = lucky?.optString("time").orEmpty()
        val luckyNum = lucky?.optInt("number", 0) ?: 0
        val luckyCol = lucky?.optString("colorHex").orEmpty()
        val worst = listOf("overall", "love", "study", "work", "money")
            .mapNotNull { k -> sec.optJSONObject(k)?.let { k to it.optInt("score", 0) } }
            .minByOrNull { it.second }?.first ?: "overall"

        val cl = obj.optJSONArray("checklist") ?: JSONArray()
        fun ck(i: Int, d: String) = cl.optString(i, d)

        return buildString {
            append("아침(09~12)\n")
            append("· 20분 정리 후 우선순위 1건 처리, 완료 알림 보내기\n")
            append("· ").append(ck(0, "중요 연락 1건")).append(" — 메시지는 5줄 이하, 공감 2회\n")
            append("· ").append(luckyTime).append(" 전까지 25분×2 세트 집중, 사이 5분 스트레칭\n\n")
            append("오후(13~17)\n")
            append("· 행운 숫자 ").append(luckyNum).append("를 미팅/연락 타이밍에 활용 (").append(luckyTime).append(")\n")
            append("· 문서/보고서는 3문단 구조, 불필요한 수식어 삭제 → 가독성 ↑\n")
            append("· 재무·학습·업무 중 낮은 영역 1건 보완(25분×2)\n\n")
            append("저녁(19~22)\n")
            append("· ").append(
                when (worst) {
                    "love" -> "대화 리캡: 상대 말 요약 2줄 + 감사 한 줄"
                    "study" -> "10분 복습 + 내일 학습 키워드 3개 메모"
                    "work" -> "오늘 배운 점 3개 기록, 업무 로그 정리"
                    "money" -> "지출 3분 점검, 자동이체 1건 재확인"
                    else -> "10분 회고, 내일 체크 3가지 작성"
                }
            ).append("\n")
            append("· 행운색 ").append(luckyCol).append(" 악세서리/배경 사용, 집중 앵커로 활용")
        }
    }

    // ====== Normalize: Deep plan must match lucky values ======
    private fun normalizePlan(
        plan: String,
        luckyTime: String,
        luckyNumber: Int,
        luckyColor: String
    ): String {
        if (plan.isBlank()) return plan
        var s = plan

        // 행운 시간 라인
        s = if (Regex("행운\\s*시간").containsMatchIn(s))
            s.replace(Regex("행운\\s*시간[^\\n]*"), "행운 시간: $luckyTime")
        else "행운 시간: $luckyTime\n$s"

        // 행운 숫자
        s = if (Regex("행운\\s*숫자\\s*\\d+").containsMatchIn(s))
            s.replace(Regex("행운\\s*숫자\\s*\\d+"), "행운 숫자 $luckyNumber")
        else s.replace(
            "오후(13~17)",
            "오후(13~17)\n· 행운 숫자 ${luckyNumber}를 미팅/연락 타이밍에 활용 ($luckyTime)"
        )

        // 행운색(헥스)
        s = s.replace(Regex("(행운 ?색[^#\\n]*)(#[0-9A-Fa-f]{6})")) { m ->
            m.groupValues[1] + luckyColor
        }
        if (!Regex("행운 ?색").containsMatchIn(s)) {
            s += "\n\n행운색 $luckyColor 악세서리/배경 사용, 집중 앵커로 활용"
        }

        return s
    }

    // ====== Deep dialog ======
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

        val top3 = pickTop("overall", "love", "study", "work", "money", "lotto")
            .joinToString("\n• ") { (k, v) -> "${labelOf(k)}: ${v.optInt("score", 0)} — ${v.optString("text")}" }
            .let { if (it.isBlank()) "데이터가 충분하지 않아요." else "• $it" }

        val lucky = obj.optJSONObject("lucky")
        val luckyTime = lucky?.optString("time").orEmpty()
        theval@ run {
            // just a label to avoid IDE warnings if needed
        }
        val luckyNum = lucky?.optInt("number", 0) ?: 0
        val luckyCol = lucky?.optString("colorHex").orEmpty()

        val apiPlan = obj.optJSONObject("tomorrow")?.optString("long").orEmpty()
        val plan = normalizePlan(
            if (apiPlan.isNotBlank()) apiPlan else makeTomorrowPlan(obj),
            luckyTime, luckyNum, luckyCol
        )

        val dv = layoutInflater.inflate(R.layout.dialog_fortune_deep, null)
        dv.findViewById<TextView>(R.id.highlights).text = top3
        dv.findViewById<TextView>(R.id.tomorrowTip).text = plan

        val dlg = MaterialAlertDialogBuilder(requireContext()).setView(dv).create()
        dv.findViewById<Button>(R.id.btnClose).setOnClickListener { dlg.dismiss() }
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dlg.show()
    }
}
