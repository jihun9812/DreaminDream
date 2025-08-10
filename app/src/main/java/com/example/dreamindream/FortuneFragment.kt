package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.view.HapticFeedbackConstants
import android.view.animation.AnimationUtils
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class FortuneFragment : Fragment() {

    private val apiKey = com.example.dreamindream.BuildConfig.OPENAI_API_KEY

    private lateinit var prefs: SharedPreferences
    private lateinit var resultText: TextView
    private lateinit var loadingView: LottieAnimationView
    private lateinit var button: MaterialButton
    private lateinit var fortuneCard: MaterialCardView

    // 모듈
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
    private lateinit var cb1: CheckBox
    private lateinit var cb2: CheckBox
    private lateinit var cb3: CheckBox
    private lateinit var btnCopy: TextView
    private lateinit var btnShare: TextView

    private var isExpanded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_fortune, container, false)

        resultText   = view.findViewById(R.id.fortune_result)
        loadingView  = view.findViewById(R.id.fortune_loading)
        button       = view.findViewById(R.id.fortuneButton)
        fortuneCard  = view.findViewById(R.id.fortuneCard)

        chips        = view.findViewById(R.id.chipsFortune)
        viewLuckyColor = view.findViewById(R.id.viewLuckyColor)
        tvLuckyNumber  = view.findViewById(R.id.tvLuckyNumber)
        tvLuckyTime    = view.findViewById(R.id.tvLuckyTime)
        barPos      = view.findViewById(R.id.barPos)
        barNeu      = view.findViewById(R.id.barNeu)
        barNeg      = view.findViewById(R.id.barNeg)
        tvPos       = view.findViewById(R.id.tvPos)
        tvNeu       = view.findViewById(R.id.tvNeu)
        tvNeg       = view.findViewById(R.id.tvNeg)
        cb1         = view.findViewById(R.id.cbAction1)
        cb2         = view.findViewById(R.id.cbAction2)
        cb3         = view.findViewById(R.id.cbAction3)
        btnCopy     = view.findViewById(R.id.btnCopy)
        btnShare    = view.findViewById(R.id.btnShare)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        prefs = requireContext().getSharedPreferences("user_info_$userId", Context.MODE_PRIVATE)

        view.findViewById<AdView>(R.id.adView_fortune).loadAd(AdRequest.Builder().build())

        // 캐시 로드
        val today = getToday()
        val cachedPayload = prefs.getString("fortune_payload_$today", null)
        val cachedText = prefs.getString("fortune_$today", null)

        if (cachedPayload != null) {
            try {
                bindFromPayload(JSONObject(cachedPayload), view)
                expandFortuneCard(view)
                disableButtonForToday()
            } catch (_: Exception) {
                if (cachedText != null) {
                    resultText.text = cachedText
                    setEmotionBars(60, 30, 10)
                    expandFortuneCard(view)
                    disableButtonForToday()
                }
            }
        } else if (cachedText != null) {
            resultText.text = cachedText
            setEmotionBars(60, 30, 10)
            expandFortuneCard(view)
            disableButtonForToday()
        }

        fun View.scaleClick(action: () -> Unit) {
            setOnClickListener {
                startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                action()
            }
        }

        btnCopy.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("fortune", resultText.text))
            Toast.makeText(requireContext(), "복사됨", Toast.LENGTH_SHORT).show()
        }
        btnShare.setOnClickListener {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, resultText.text.toString())
            }
            startActivity(android.content.Intent.createChooser(send, "공유"))
        }

        button.scaleClick {
            val u = loadUserInfo() ?: return@scaleClick
            fetchFortune(u, today, view)
        }
        // 테스트 편의: 롱클릭 시 오늘 캐시 삭제
        button.setOnLongClickListener {
            prefs.edit().remove("fortune_payload_$today").remove("fortune_$today").apply()
            Toast.makeText(requireContext(), "오늘 캐시 삭제됨", Toast.LENGTH_SHORT).show()
            button.isEnabled = true; button.alpha = 1f
            true
        }

        // 체크리스트 로컬 상태
        loadChecklist(today)
        bindChecklist(today)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                    .replace(R.id.fragment_container, HomeFragment())
                    .disallowAddToBackStack()
                    .commit()
            }
        })
    }

    // --- 모델용 사용자 정보 ---
    data class UserInfo(
        val nickname: String,
        val mbti: String,
        val birth: String,
        val gender: String,
        val birthTime: String
    )

    private fun loadUserInfo(): UserInfo? {
        val nickname  = prefs.getString("nickname", "") ?: ""
        val mbti      = prefs.getString("mbti", "") ?: ""
        val birth     = prefs.getString("birthdate", "") ?: ""
        val gender    = prefs.getString("gender", "") ?: ""
        val birthTime = prefs.getString("birth_time", "선택안함") ?: "선택안함"

        return if (nickname.isNotBlank() && mbti.isNotBlank() && birth.isNotBlank() && gender.isNotBlank() && birthTime != "선택안함") {
            UserInfo(nickname, mbti, birth, gender, birthTime)
        } else {
            Toast.makeText(requireContext(), getString(R.string.error_missing_info), Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun getToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // --- 네트워크 호출 ---
    private fun fetchFortune(u: UserInfo, today: String, view: View) {
        val prompt = buildJsonPrompt(u)

        // 로딩
        button.isEnabled = false; button.alpha = 0.6f
        loadingView.apply {
            alpha = 0f; translationY = -300f; scaleX = 0.3f; scaleY = 0.3f; visibility = View.VISIBLE
            animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(700)
                .setInterpolator(android.view.animation.BounceInterpolator()).start()
            playAnimation()
        }
        fortuneCard.visibility = View.GONE
        resultText.text = ""

        // JSON 모드 강제 + 토큰 여유 확보 + 간결 지시
        val requestBody = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("temperature", 0.5)              // 더 간결/일관되게
            put("max_tokens", 950)               // <-- 520 → 950로 상향
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        OkHttpClient().newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    loadingView.cancelAnimation(); loadingView.visibility = View.GONE
                    resultText.text = getString(R.string.error_fetch_failed)
                    setEmotionBars(60, 30, 10)
                    expandFortuneCard(view)
                    enableButton()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val content = if (response.isSuccessful) {
                    try {
                        JSONObject(body).getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message").getString("content").trim()
                    } catch (_: Exception) { "응답 파싱 오류" }
                } else { "운세 요청 실패 (${response.code})" }

                requireActivity().runOnUiThread {
                    loadingView.cancelAnimation(); loadingView.visibility = View.GONE

                    val payload = parsePayloadAlways(content)   // 항상 JSON 결과
                    bindFromPayload(payload, view)
                    prefs.edit().putString("fortune_payload_$today", payload.toString()).apply()

                    // 스크롤뷰 꼭대기에서 시작 + 레이아웃 갱신
                    view.findViewById<ScrollView>(R.id.resultScrollView)?.let { scrollView ->
                        scrollView.post {
                            scrollView.requestLayout()
                            scrollView.fullScroll(View.FOCUS_UP)
                        }
                    }

                    expandFortuneCard(view)
                    disableButtonForToday()
                }
            }
        })
    }

    // --- 바인딩 ---
    private fun setKeywords(list: List<String>) {
        chips.removeAllViews()
        list.take(4).forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = false
                isClickable = false
                setTextColor(android.graphics.Color.WHITE)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSecondaryContainer, 0)
                )
            }
            chips.addView(chip)
        }
    }

    private fun setLucky(colorHex: String, number: Int, time: String) {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            try { setColor(android.graphics.Color.parseColor(colorHex)) }
            catch (_: Exception) { setColor(android.graphics.Color.parseColor("#FFD54F")) }
        }
        viewLuckyColor.background = shape
        tvLuckyNumber.text = number.toString()
        tvLuckyTime.text = time
    }

    // 굵은 막대 + 최소 표시폭 보정(1~7 → 8)
    private fun setEmotionBars(pos: Int, neu: Int, neg: Int) {
        fun v(v: Int) = when { v <= 0 -> 0; v in 1..7 -> 8; else -> v }.coerceIn(0, 100)
        barPos.setProgressCompat(v(pos), true)
        barNeu.setProgressCompat(v(neu), true)
        barNeg.setProgressCompat(v(neg), true)
        tvPos.text = "${pos.coerceIn(0,100)}%"
        tvNeu.text = "${neu.coerceIn(0,100)}%"
        tvNeg.text = "${neg.coerceIn(0,100)}%"
    }

    private fun setChecklistSuggestions(a1: String?, a2: String?, a3: String?) {
        if (!a1.isNullOrBlank()) cb1.text = a1
        if (!a2.isNullOrBlank()) cb2.text = a2
        if (!a3.isNullOrBlank()) cb3.text = a3
    }

    private fun loadChecklist(dayKey: String) {
        val c = requireContext().getSharedPreferences("fortune_checklist", Context.MODE_PRIVATE)
        cb1.isChecked = c.getBoolean("${dayKey}_1", false)
        cb2.isChecked = c.getBoolean("${dayKey}_2", false)
        cb3.isChecked = c.getBoolean("${dayKey}_3", false)
    }

    private fun bindChecklist(dayKey: String) {
        val c = requireContext().getSharedPreferences("fortune_checklist", Context.MODE_PRIVATE)
        val l = { v: CompoundButton, k: String -> c.edit().putBoolean(k, v.isChecked).apply() }
        cb1.setOnCheckedChangeListener { v, _ -> l(v, "${dayKey}_1") }
        cb2.setOnCheckedChangeListener { v, _ -> l(v, "${dayKey}_2") }
        cb3.setOnCheckedChangeListener { v, _ -> l(v, "${dayKey}_3") }
    }

    private fun bindFromPayload(obj: JSONObject, view: View) {
        resultText.text = obj.optString("message").ifBlank { obj.optString("summary", "") }

        // 키워드
        val kwArr = obj.optJSONArray("keywords") ?: JSONArray()
        val kws = (0 until kwArr.length()).mapNotNull { kwArr.optString(it).takeIf { it.isNotBlank() } }
        if (kws.isNotEmpty()) setKeywords(kws)

        // 행운
        val lucky = obj.optJSONObject("lucky")
        setLucky(
            lucky?.optString("colorHex").orEmpty().ifBlank { "#FFD54F" },
            lucky?.optInt("number", 7) ?: 7,
            lucky?.optString("time").orEmpty().ifBlank { "오후 3시" }
        )

        // 감정
        val emo = obj.optJSONObject("emotions")
        setEmotionBars(
            emo?.optInt("positive", 60) ?: 60,
            emo?.optInt("neutral", 30) ?: 30,
            emo?.optInt("negative", 10) ?: 10
        )

        // 체크리스트
        val act = obj.optJSONArray("checklist") ?: JSONArray()
        setChecklistSuggestions(
            act.optString(0).takeIf { it.isNotBlank() },
            act.optString(1).takeIf { it.isNotBlank() },
            act.optString(2).takeIf { it.isNotBlank() }
        )
    }

    // --- 버튼 상태 ---
    private fun disableButtonForToday() { button.isEnabled = false; button.alpha = 0.6f }
    private fun enableButton() { button.isEnabled = true; button.alpha = 1f }

    // --- 카드 등장 + 스크롤 높이 애니메이션 ---
    private fun expandFortuneCard(view: View?) {
        if (view == null || isExpanded) return
        isExpanded = true

        val root = view.findViewById<ConstraintLayout>(R.id.root_fortune_layout)
        val scroll = view.findViewById<ScrollView>(R.id.resultScrollView)

        val set = TransitionSet().apply {
            addTransition(Slide(Gravity.TOP)); addTransition(Fade(Fade.IN))
            duration = 450; setOrdering(TransitionSet.ORDERING_TOGETHER)
        }
        TransitionManager.beginDelayedTransition(root as ViewGroup, set)
        fortuneCard.visibility = View.VISIBLE

        val newH = (resources.displayMetrics.heightPixels * 0.66f).toInt()
        val curH = scroll.height.coerceAtLeast(160)
        android.animation.ValueAnimator.ofInt(curH, newH).apply {
            duration = 700; startDelay = 150
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { a ->
                val h = a.animatedValue as Int
                scroll.layoutParams.height = h
                scroll.requestLayout()
            }
            start()
        }
    }

    // --- 프롬프트(매일 다르게 + 프로필 반영, 간결 지시 추가) ---
    private fun buildJsonPrompt(u: UserInfo): String {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val weekday = java.text.SimpleDateFormat("EEEE", java.util.Locale.KOREAN).format(java.util.Date())

        return """
다음 정보를 기반으로 "오늘($today, $weekday)"의 운세를 한국어로 생성하되, 반드시 아래 JSON 스키마로만 응답하세요.
코드블록/설명/문장 금지. **순수 JSON만 출력**.

[사용자]
nickname: "${u.nickname}"
mbti: "${u.mbti}"
birthdate: "${u.birth}"
birth_time: "${u.birthTime}"
gender: "${u.gender}"
date: "$today"

[출력 스키마 — JSON 객체]
{
  "lucky": { 
    "colorHex":"#RRGGBB",       // '#' 뒤 6자리 16진수
    "number": 1-99,             // 1~99 정수
    "time": "자연스러운 한국어 시간대"  // 예: "오전 9시", "오후 3시"
  },
  "message": "오늘의 운세를 보여드릴게요, ${u.nickname} 님!\\n[총운] 점수 + 평가 + 조언\\n[연애운] ... (총 7줄)",
  "sections": {
    "overall": { "score": 0-100, "text": "짧은 평가", "advice": "조언" },
    "love":    { "score": 0-100, "text": "짧은 평가", "advice": "조언" },
    "study":   { "score": 0-100, "text": "짧은 평가", "advice": "조언" },
    "work":    { "score": 0-100, "text": "짧은 평가", "advice": "조언" },
    "money":   { "score": 0-100, "text": "짧은 평가", "advice": "조언" },
    "lotto":   { "score": 0-100, "text": "짧은 평가", "advice": "조언" }
  },
  "keywords": ["오늘 흐름과 연관된 단어 최대 4개"],
  "emotions": { "positive": 0-100, "neutral": 0-100, "negative": 0-100 },
  "checklist": ["구체적인 행동 1", "구체적인 행동 2", "구체적인 행동 3"]
}

[제약]
- **모든 필드를 반드시 채움**, 누락 금지.
- lucky.colorHex는 반드시 '#' 뒤 6자리 16진수, number는 1~99 정수, time은 '오전/오후 X시' 형태.
- message는 7줄(인사 1줄 + 운세 6줄).
- sections의 score/text/advice는 message 내용과 일치.
- keywords는 1~4개.
- emotions는 균형 있게(합 100일 필요 없음).
- checklist는 오늘 운세에서 파생된 구체적이고 실행 가능한 행동 3개.
- 예시/샘플/고정값 금지.
""".trimIndent()
    }





    // --- 파서: 항상 JSON 리턴 ---
    private fun parsePayloadAlways(content: String): JSONObject {
        val txt = content.trim()
        try { return validateAndFill(JSONObject(txt)) } catch (_: Exception) {}
        Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```").find(txt)?.let {
            try { return validateAndFill(JSONObject(it.groupValues[1])) } catch (_: Exception) {}
        }
        extractJsonObject(txt)?.let { raw ->
            try { return validateAndFill(JSONObject(raw)) } catch (_: Exception) {}
        }
        return buildFallbackFromText(txt)
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

    private fun validateAndFill(obj: JSONObject): JSONObject {
        if (!obj.has("message")) obj.put("message", "")
        if (obj.optJSONArray("keywords") == null) obj.put("keywords", JSONArray())

        val lucky = (obj.optJSONObject("lucky") ?: JSONObject()).apply {
            if (!has("colorHex")) put("colorHex", "#FFD54F")
            if (!has("number"))  put("number", 7)
            if (!has("time"))    put("time", "오후 3시")
        }
        obj.put("lucky", lucky)

        val emo = (obj.optJSONObject("emotions") ?: JSONObject()).apply {
            put("positive", optInt("positive", 60).coerceIn(0,100))
            put("neutral",  optInt("neutral", 30).coerceIn(0,100))
            put("negative", optInt("negative",10).coerceIn(0,100))
        }
        obj.put("emotions", emo)

        val cl = (obj.optJSONArray("checklist") ?: JSONArray())
        val list = mutableListOf<String>()
        for (i in 0 until cl.length()) cl.optString(i)?.let { if (it.isNotBlank()) list += it }
        val fallback = listOf("중요 연락 하나 보내기","15분 정리/정돈","소액 투자/저축 점검")
        while (list.size < 3) list += fallback[list.size]
        obj.put("checklist", JSONArray(list.take(3)))

        return obj
    }

    private fun buildFallbackFromText(txt: String): JSONObject {
        val obj = JSONObject()
        obj.put("message", txt)

        val kw = Regex("키워드[:：]\\s*([^\n]+)", RegexOption.IGNORE_CASE)
            .find(txt)?.groupValues?.getOrNull(1)
            ?.split(Regex("[,·•]"))?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(4)
            ?: emptyList()
        obj.put("keywords", JSONArray(kw))

        val color = Regex("#[0-9A-Fa-f]{6}").find(txt)?.value ?: "#FFD54F"
        val num = Regex("(?:행운\\s*숫자|lucky\\s*number)\\D*(\\d{1,2})", RegexOption.IGNORE_CASE)
            .find(txt)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 7
        val time = Regex("(\\d{1,2}:\\d{2}\\s*~\\s*\\d{1,2}:\\d{2})").find(txt)?.value ?: "오후 3시"
        obj.put("lucky", JSONObject().apply { put("colorHex", color); put("number", num); put("time", time) })

        val pos = Regex("긍정\\D*(\\d{1,3})").find(txt)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 60
        val neu = Regex("중립\\D*(\\d{1,3})").find(txt)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 30
        val neg = Regex("부정\\D*(\\d{1,3})").find(txt)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10
        obj.put("emotions", JSONObject().apply {
            put("positive", pos.coerceIn(0,100)); put("neutral", neu.coerceIn(0,100)); put("negative", neg.coerceIn(0,100))
        })

        val items = txt.lines().map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("• ") || it.startsWith("□") || it.startsWith("▫") }
            .map { it.replace(Regex("^[-•□▫]\\s*"), "").trim() }
            .take(3)
        val checklist = if (items.size == 3) items else listOf("중요 연락 하나 보내기","15분 정리/정돈","소액 투자/저축 점검")
        obj.put("checklist", JSONArray(checklist))

        return validateAndFill(obj)
    }
}
