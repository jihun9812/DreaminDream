package com.dreamindream.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.LoadAdError
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan

class DreamFragment : Fragment() {

    private val logTag = "DreamFragment"
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }

    // 1회 무료 + 2회 광고 = 3회
    private val freeLimit = 1
    private val adLimit = 2
    private val prefKeyDate = "dream_last_date"
    private val prefKeyCount = "dream_count"
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val bannedStarters = listOf(
        "안녕","gpt","hello","how are you","what is","tell me","chatgpt","who are you","날씨","시간",
        "씨발","개새끼","병신","니애미","좆밥","씨발롬","애미","창녀"
    )

    // Views
    private lateinit var prefs: SharedPreferences
    private lateinit var dreamEditText: EditText
    private lateinit var interpretButton: Button
    private lateinit var resultTextView: TextView
    private var usageTextView: TextView? = null
    private var bannerAdView: AdView? = null
    private var lottieLoading: LottieAnimationView? = null

    // 공용 OkHttpClient (타임아웃 상향 + 재시도)
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(65, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Firestore 트리거용 사용자 UID
    private var userId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_dream, container, false)

        // 배너
        bannerAdView = v.findViewById<AdView?>(R.id.adView)?.apply {
            visibility = View.GONE
            adListener = object : AdListener() {
                override fun onAdLoaded() { visibility = View.VISIBLE }
                override fun onAdFailedToLoad(error: LoadAdError) { visibility = View.GONE; Log.e(logTag, "Banner fail: ${error.code}") }
            }
            loadAd(AdRequest.Builder().build())
        }

        // 보상형 초기화 & 프리로드
        AdManager.initialize(requireContext())
        AdManager.loadRewarded(requireContext())

        // uid (익명 포함)
        userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        prefs = requireContext().getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)

        bindViews(v)
        initUi(v)
        return v
    }

    override fun onResume() { super.onResume(); bannerAdView?.resume() }
    override fun onPause() { bannerAdView?.pause(); super.onPause() }
    override fun onDestroyView() { bannerAdView?.destroy(); bannerAdView = null; super.onDestroyView() }

    // 필수 뷰
    private fun <T: View> req(root: View, id: Int, name: String): T {
        @Suppress("UNCHECKED_CAST")
        return root.findViewById<T?>(id)
            ?: throw IllegalStateException("fragment_dream.xml에 <$name> 뷰(id=$id)가 필요합니다.")
    }

    private fun bindViews(root: View) {
        dreamEditText   = req(root, R.id.dreamEditText, "EditText@dreamEditText")
        interpretButton = req(root, R.id.interpretButton, "Button@interpretButton")
        resultTextView  = req(root, R.id.resultTextView, "TextView@resultTextView")
        usageTextView   = root.findViewById(R.id.usageTextView)
        lottieLoading   = root.findViewById(R.id.lottieLoading)

        // 안내 문구
        resultTextView.text = getString(R.string.dream_result_placeholder)
        resultTextView.setTextColor(Color.parseColor("#BFD0DC"))
    }

    private fun initUi(root: View) {
        updateUsageLabel()

        // 입력칸 내부 스크롤 + 부모 ScrollView 제스처 충돌 방지
        dreamEditText.isVerticalScrollBarEnabled = true
        dreamEditText.movementMethod = ScrollingMovementMethod.getInstance()
        dreamEditText.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        // 결과칸은 바깥 스크롤만
        resultTextView.isVerticalScrollBarEnabled = false
        resultTextView.setOnTouchListener(null)
        resultTextView.movementMethod = null

        interpretButton.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            hideKeyboardAndScrollToResult(root)

            val input = dreamEditText.text.toString().trim()
            if (!validateInput(input)) return@setOnClickListener

            val used = getTodayCount()
            when {
                // ✅ 성공 시에만 카운트 증가
                used < freeLimit -> {
                    startInterpret(
                        prompt = input,
                        onSuccess = { increaseTodayCount(used) },
                        onFailure = { /* 차감 없음 */ }
                    )
                }
                used < freeLimit + adLimit -> {
                    showAdPrompt {
                        val latest = dreamEditText.text.toString().trim()
                        if (validateInput(latest)) {
                            startInterpret(
                                prompt = latest,
                                onSuccess = { increaseTodayCount(used) },
                                onFailure = { /* 차감 없음 */ }
                            )
                        }
                    }
                }
                else -> showLimitDialog()
            }
        }
    }

    // --- 바텀시트 (광고 보기 / 취소) ---
    private fun showAdPrompt(onRewardEarnedProceed: () -> Unit) {
        val bs = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnWatch  = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWatchAd)
        val textStatus= view.findViewById<TextView>(R.id.textStatus)
        val progress  = view.findViewById<ProgressBar>(R.id.progressAd)

        btnCancel.setOnClickListener { bs.dismiss() }
        btnWatch.setOnClickListener {
            btnWatch.isEnabled = false
            progress.visibility = View.VISIBLE
            textStatus.text = getString(R.string.ad_preparing)
            AdManager.showRewarded(
                activity = requireActivity(),
                onRewardEarned = {
                    bs.dismiss()
                    onRewardEarnedProceed()
                    AdManager.loadRewarded(requireContext())
                },
                onClosed = {
                    btnWatch.isEnabled = true
                    progress.visibility = View.GONE
                    textStatus.text = getString(R.string.ad_closed_try_again)
                    AdManager.loadRewarded(requireContext())
                },
                onFailed = { reason ->
                    btnWatch.isEnabled = true
                    progress.visibility = View.GONE
                    textStatus.text = getString(R.string.ad_load_failed_fmt, reason)
                    AdManager.loadRewarded(requireContext())
                }
            )
        }
        bs.setContentView(view)
        bs.show()
    }

    // 카운터/라벨
    private fun todayKey(): String = dateFmt.format(Date())
    private fun getTodayCount(): Int {
        val today = todayKey()
        val savedDate = prefs.getString(prefKeyDate, "")
        val count = prefs.getInt(prefKeyCount, 0)
        return if (savedDate == today) count else 0
    }
    private fun increaseTodayCount(current: Int) {
        prefs.edit()
            .putString(prefKeyDate, todayKey())
            .putInt(prefKeyCount, (current + 1).coerceAtMost(freeLimit + adLimit))
            .apply()
        updateUsageLabel()
    }
    private fun updateUsageLabel() {
        val remain = (freeLimit + adLimit - getTodayCount()).coerceAtLeast(0)
        usageTextView?.text = getString(R.string.dream_today_left, remain)
    }

    // 입력 검증
    private fun validateInput(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT)
        val isMath = Regex("^\\s*\\d+\\s*[-+*/]\\s*\\d+\\s*$").containsMatchIn(input)
        val smallTalk = bannedStarters.any { lower.startsWith(it) }
        return when {
            input.isBlank() -> { toast(getString(R.string.dream_input_empty)); false }
            input.length < 10 || isMath || smallTalk -> { toast(getString(R.string.dream_input_not_meaningful)); false }
            else -> true
        }
    }

    // --- GPT 호출 (성공 시에만 저장/차감) + 1회 자동 재시도
    private fun startInterpret(
        prompt: String,
        onSuccess: (String) -> Unit = {},
        onFailure: (String) -> Unit = {},
        attempt: Int = 1
    ) {
        showLoading()

        val content = getString(
            R.string.dream_prompt_template,
            prompt,
            getString(R.string.dream_section_message),
            getString(R.string.dream_section_symbols),
            getString(R.string.dream_section_premonition),
            getString(R.string.dream_section_tips_today),
            getString(R.string.dream_section_actions_three)
        )

        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))
        val body = JSONObject().apply {
            put("model", "gpt-4.1-mini")
            put("temperature", 0.7)
            put("messages", messages)
            put("max_tokens", 1100)
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        fun retryOrFail(reason: String, e: Throwable? = null) {
            val transient = (e is java.net.SocketTimeoutException) || reason == "408" || reason == "429" || reason.startsWith("5")
            if (attempt == 1 && transient) {
                resultTextView.postDelayed({
                    startInterpret(prompt, onSuccess, onFailure, attempt = 2)
                }, 800)
            } else {
                ui { onResultArrived(getString(R.string.dream_network_error)) }
                onFailure(getString(R.string.dream_network_error))
            }
        }

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(logTag, "GPT 요청 실패", e)
                retryOrFail("fail", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(logTag, "GPT 응답 코드: ${resp.code}")
                        retryOrFail(resp.code.toString(), null)
                        return
                    }

                    val raw = resp.body?.string().orEmpty()
                    val parsed = try {
                        JSONObject(raw)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } catch (ex: Exception) {
                        Log.e(logTag, "GPT 파싱 실패", ex)
                        null
                    }

                    if (parsed.isNullOrBlank()) {
                        retryOrFail("parse", null)
                        return
                    }

                    // ✅ 성공 시에만 결과 표시/저장/차감
                    ui { onResultArrived(parsed) }
                    saveDream(prompt, parsed)
                    onSuccess(parsed)
                }
            }
        })
    }

    // 저장(성공 결과만)
    private fun saveDream(dream: String, result: String) {
        // 로컬 저장 (오늘 날짜 키에 누적, 최대 10개 유지)
        val dayKey = todayKey()
        val prev = prefs.getString(dayKey, "[]") ?: "[]"
        val arr = try { JSONArray(prev) } catch (_: Exception) { JSONArray() }
        if (arr.length() >= 10) { try { arr.remove(0) } catch (_: Exception) {} }
        arr.put(JSONObject().put("dream", dream).put("result", result))
        prefs.edit().putString(dayKey, arr.toString()).apply()

        // Firestore 저장 (uid 있을 때만)
        if (userId.isNotBlank()) {
            try {
                FirestoreManager.saveDream(userId, dream, result)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    ReportWarmup.warmUpThisWeek(requireContext().applicationContext, userId)
                }, 800)
            } catch (e: Exception) {
                Log.e(logTag, "Firestore save failed: ${e.localizedMessage}")
            }
        }
    }

    // ---- 결과 수신 후 처리
    private fun onResultArrived(text: String) {
        hideLoading()
        resultTextView.setTextColor(Color.parseColor("#FFFFFF"))
        resultTextView.text = styleResult(text.ifBlank { getString(R.string.dream_result_empty) })
    }

    // 로딩 표시/해제
    private fun showLoading() {
        interpretButton.isEnabled = false
        lottieLoading?.apply {
            alpha = 0f; translationY = -200f; scaleX = 0.7f; scaleY = 0.7f
            visibility = View.VISIBLE
            animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(400).start()
            playAnimation()
        }
        resultTextView.text = getString(R.string.dream_loading)
        resultTextView.setTextColor(Color.parseColor("#BFD0DC"))
    }
    private fun hideLoading() {
        interpretButton.isEnabled = true
        lottieLoading?.apply { cancelAnimation(); visibility = View.GONE }
    }

    // 섹션별 색 적용 + 섹션 사이 한 줄 공백
    private fun styleResult(raw: String): CharSequence {
        val text = raw.ifBlank { getString(R.string.dream_result_empty) }
            .replace(Regex("(?m)^\\s*#{1,4}\\s*"), "")
            .replace("**", "")
            .replace(Regex("`{1,3}"), "")
            .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
            .trimEnd()

        data class Sec(val key: Regex, val headerColor: Int, val bodyColor: Int)

        val secs = listOf(
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(getString(R.string.dream_section_message))})\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color.parseColor("#9BE7FF"), Color.parseColor("#E6F7FF")),
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(getString(R.string.dream_section_symbols))}|핵심\s*포인트)\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color.parseColor("#FFB3C1"), Color.parseColor("#FFE6EC")),
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(getString(R.string.dream_section_premonition))})\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color.parseColor("#FFD166"), Color.parseColor("#FFF1CC")),
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(getString(R.string.dream_section_tips_today))})\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color.parseColor("#FFE082"), Color.parseColor("#FFF4D6")),
            Sec(Regex("""^(?:\P{L}*)?\s*(${Regex.escape(getString(R.string.dream_section_actions_three))})\s*:?\s*$""", RegexOption.IGNORE_CASE),
                Color.parseColor("#A5D6A7"), Color.parseColor("#E9F8ED"))
        )

        fun matchHeader(line: String): Sec? = secs.firstOrNull { it.key.matches(line.trim()) }

        val lines = text.split('\n')
        val rebuilt = StringBuilder(text.length + 64)
        var metFirst = false
        lines.forEach { line ->
            val sec = matchHeader(line)
            if (sec != null && metFirst) rebuilt.append('\n')
            rebuilt.append(line.trimEnd()).append('\n')
            if (sec != null) metFirst = true
        }
        val finalText = rebuilt.toString().trimEnd()

        val sb = SpannableStringBuilder(finalText)

        data class Hit(val start: Int, val end: Int, val sec: Sec)
        val hits = mutableListOf<Hit>()
        var idx = 0
        finalText.split('\n').forEach { line ->
            val st = idx
            val en = st + line.length
            matchHeader(line)?.let { sec -> hits += Hit(st, en, sec) }
            idx = en + 1
        }
        if (hits.isEmpty()) return sb

        hits.forEach { h ->
            sb.setSpan(ForegroundColorSpan(h.sec.headerColor), h.start, h.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD),               h.start, h.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(1.08f),                h.start, h.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        for (i in hits.indices) {
            val bodyStart = hits[i].end + 1
            val bodyEnd   = if (i + 1 < hits.size) hits[i + 1].start - 1 else finalText.length
            if (bodyStart < bodyEnd) {
                sb.setSpan(ForegroundColorSpan(hits[i].sec.bodyColor), bodyStart, bodyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }

    private fun hideKeyboardAndScrollToResult(root: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        dreamEditText.clearFocus()
        imm.hideSoftInputFromWindow(root.windowToken, 0)
        resultTextView.post {
            var parentView: View? = resultTextView
            var scroll: ScrollView? = null
            while (parentView?.parent is View) {
                parentView = parentView.parent as View
                if (parentView is ScrollView) { scroll = parentView; break }
            }
            scroll?.smoothScrollTo(0, resultTextView.top)
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    private fun ui(block: () -> Unit) { activity?.runOnUiThread { if (isAdded) block() } }

    private fun showLimitDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dream_quota_title))
            .setMessage(getString(R.string.dream_quota_message))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    companion object {
        // DreamFragment.kt - companion object 안
        fun showResultDialog(context: Context, raw: String) {
            val v = View.inflate(context, R.layout.dream_result_dialog, null)

            // 1) 섹션 컨테이너를 잡는다 (XML에서 바꿔놓은 id)
            val container = v.findViewById<LinearLayout>(R.id.sectionsContainer)

            // 2) 섹션 파서
            data class Section(val title: String, val body: String)

            fun isHeader(line: String): Boolean {
                val t = line.trim()
                val starters = listOf("•","·","-","—","⭐","✨","🧠","🌙","⚠","📌","🔑","💡","📍","🚨","🍀")
                val keys = listOf(
                    context.getString(R.string.dream_section_message),
                    context.getString(R.string.dream_section_symbols),
                    context.getString(R.string.dream_section_premonition),
                    context.getString(R.string.dream_section_tips_today),
                    context.getString(R.string.dream_section_actions_three),
                    "요약","핵심","포인트","추천","행동","주의","팁","키워드"
                )
                return t.isNotEmpty() && t.length <= 30 &&
                        (starters.any { t.startsWith(it) } || keys.any { t.contains(it) })
            }

            fun parseSections(text: String): List<Section> {
                val cleaned = text
                    .replace(Regex("(?m)^\\s*#{1,4}\\s*"), "") // 헤딩 마크다운 제거
                    .replace("**", "")
                    .replace(Regex("`{1,3}"), "")
                    .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
                    .trimEnd()

                val lines = cleaned.replace("\r\n","\n").split('\n')
                val out = mutableListOf<Section>()
                var curHeader: String? = null
                val buf = StringBuilder()
                fun flush() {
                    val body = buf.toString().trim()
                    if (body.isNotEmpty()) out += Section(curHeader ?: "내용", body)
                    curHeader = null; buf.setLength(0)
                }
                for (ln in lines) {
                    if (isHeader(ln)) { if (buf.isNotEmpty()) flush(); curHeader = ln.trim() }
                    else { if (buf.isNotEmpty()) buf.append('\n'); buf.append(ln) }
                }
                flush()
                return out
            }

            // 3) 본문 가독성 보정 (줄간격/번호 강조)
            fun prettify(text: String): CharSequence {
                val s = text
                    .replace("\n{3,}".toRegex(), "\n\n")
                    .replace("^[\\s•·-]+".toRegex(RegexOption.MULTILINE), "• ")
                val ssb = android.text.SpannableStringBuilder(s)
                val rgx = "^(\\d{1,2}\\.)\\s".toRegex(RegexOption.MULTILINE)
                rgx.findAll(s).forEach { m ->
                    ssb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        m.range.first, m.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                return ssb
            }

            // 4) 섹션 카드 inflate
            val inflater = LayoutInflater.from(context)
            parseSections(raw.ifBlank { context.getString(R.string.dream_result_empty) }).forEach { sec ->
                val card = inflater.inflate(R.layout.item_result_section, container, false)
                card.findViewById<TextView>(R.id.tvTitle).text = sec.title.trim()
                card.findViewById<TextView>(R.id.tvBody).text  = prettify(sec.body.trim())
                container.addView(card)
            }

            // 다이얼로그 공통 세팅 (기존과 동일)
            val dm = context.resources.displayMetrics
            val maxH = (dm.heightPixels * 0.80f).toInt()
            val scroll = v.findViewById<ScrollView>(R.id.scrollDialog)
            scroll.layoutParams = scroll.layoutParams.apply { height = maxH }
            scroll.isFillViewport = true
            scroll.clipToPadding = false

            val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
                .setView(v)
                .create()

            v.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
        }

    }
}
