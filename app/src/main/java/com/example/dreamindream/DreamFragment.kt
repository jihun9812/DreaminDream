package com.example.dreamindream

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
import com.example.dreamindream.ads.AdManager
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
import com.example.dreamindream.ads.Ads

class DreamFragment : Fragment() {

    private val logTag = "DreamFragment"
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }

    // 1회 무료 + 2회 광고 = 3회
    private val freeLimit = 1
    private val adLimit = 2
    private val prefKeyDate = "dream_last_date"
    private val prefKeyCount = "dream_count"
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val bannedStarters = listOf("안녕","gpt","hello","how are you","what is","tell me","chatgpt","who are you","날씨","시간")

    // Views
    private lateinit var prefs: SharedPreferences
    private lateinit var dreamEditText: EditText
    private lateinit var interpretButton: Button
    private lateinit var resultTextView: TextView
    private var usageTextView: TextView? = null
    private var bannerAdView: AdView? = null
    private var lottieLoading: LottieAnimationView? = null

    private val http = OkHttpClient()

    // ✅ Firestore 트리거용 사용자 UID
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

        // ✅ uid 보관 (로그인/익명 모두 값 존재하도록 앱 전체에서 Anonymous sign-in 보장 필요)
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

        // ▼ 안내 문구
        resultTextView.text = getString(R.string.dream_result_placeholder)
        resultTextView.setTextColor(Color.parseColor("#BFD0DC"))
    }

    private fun initUi(root: View) {
        updateUsageLabel()

        // ✅ 입력칸 내부 스크롤 + 부모 ScrollView와의 제스처 충돌 방지
        dreamEditText.isVerticalScrollBarEnabled = true
        dreamEditText.movementMethod = ScrollingMovementMethod.getInstance()
        dreamEditText.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        // ⛔ 결과칸은 내부 스크롤 제거 → 화면 전체(바깥 ScrollView)로 스크롤
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
                used < freeLimit -> {
                    startInterpret(input)
                    increaseTodayCount(used)
                }
                used < freeLimit + adLimit -> {
                    showAdPrompt {
                        val latest = dreamEditText.text.toString().trim()
                        if (validateInput(latest)) {
                            startInterpret(latest)
                            increaseTodayCount(used)
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
        prefs.edit().putString(prefKeyDate, todayKey()).putInt(prefKeyCount, (current + 1).coerceAtMost(freeLimit + adLimit)).apply()
        updateUsageLabel()
    }
    private fun updateUsageLabel() {
        val remain = (freeLimit + adLimit - getTodayCount()).coerceAtLeast(0)
        usageTextView?.text = getString(R.string.dream_today_left, remain)
    }

    // 입력 검증
    private fun validateInput(input: String): Boolean {
        val lower = input.lowercase()
        val isMath = Regex("^\\s*\\d+\\s*[-+*/]\\s*\\d+\\s*$").containsMatchIn(input)
        val smallTalk = bannedStarters.any { lower.startsWith(it) }
        return when {
            input.isBlank() -> { toast(getString(R.string.dream_input_empty)); false }
            input.length < 10 || isMath || smallTalk -> { toast(getString(R.string.dream_input_not_meaningful)); false }
            else -> true
        }
    }

    private fun startInterpret(prompt: String) {
        showLoading()

        // 프롬프트를 strings.xml에서 가져와 삽입
        val content = getString(
            R.string.dream_prompt_template,
            prompt,
            getString(R.string.dream_section_message),
            getString(R.string.dream_section_symbols),
            getString(R.string.dream_section_premonition),
            getString(R.string.dream_section_tips_today),
            getString(R.string.dream_section_actions_three)
        )

        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", content)
        )

        val body = JSONObject().apply {
            put("model", "gpt-4.1-mini")
            put("temperature", 0.86)
            put("messages", messages)
            put("max_tokens", 1100)
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        OkHttpClient().newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(logTag, "GPT 요청 실패", e)
                ui { onResultArrived(getString(R.string.dream_network_error)) }
            }
            override fun onResponse(call: Call, response: Response) {
                val text = if (response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    try {
                        JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    } catch (_: Exception) { getString(R.string.dream_parse_error) }
                } else getString(R.string.dream_request_failed, response.code)
                response.close()
                ui { onResultArrived(text) }
                saveDream(prompt, text)
            }
        })
    }

    private fun saveDream(dream: String, result: String) {
        // 로컬(프리뷰/캘린더 표시용)
        val dayKey = todayKey()
        val arr = JSONArray(prefs.getString(dayKey, "[]") ?: "[]")
        if (arr.length() >= 10) arr.remove(0)
        arr.put(JSONObject().put("dream", dream).put("result", result))
        prefs.edit().putString(dayKey, arr.toString()).apply()

        //  Firestore 저장 → Cloud Function(sendDreamResult) 트리거 → 이메일 발송
        if (userId.isNotBlank()) {
            FirestoreManager.saveDream(userId, dream, result) // ← 오늘 날짜 자동
        }
    }

    // ---- 결과 수신 후 처리
    private fun onResultArrived(text: String) {
        hideLoading()
        resultTextView.setTextColor(Color.parseColor("#FFFFFF"))
        resultTextView.text = styleResult(text.ifBlank { getString(R.string.dream_result_empty) })
    }

    // ─────────────────────────────────────────────
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
        var text = raw.ifBlank { getString(R.string.dream_result_empty) }
            .replace(Regex("(?m)^\\s*#{1,4}\\s*"), "")      // # 헤더 제거
            .replace("**", "")                              // 볼드 마크 제거
            .replace(Regex("`{1,3}"), "")                   // 코드 마크 제거
            .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")      // 리스트 → 불릿
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
            idx = en + 1 // '\n'
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
            .setMessage(getString(R.string.dream_quota_message)) // 인자 제거
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    companion object {
        fun showResultDialog(context: Context, result: String) {
            val v = View.inflate(context, R.layout.dream_result_dialog, null)
            val tv = v.findViewById<TextView>(R.id.resultTextView)

            var text = result.ifBlank { context.getString(R.string.dream_result_empty) }
                .replace(Regex("(?m)^\\s*#{1,4}\\s*"), "")
                .replace("**", "")
                .replace(Regex("`{1,3}"), "")
                .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
                .trimEnd()

            data class Sec(val emoji: String, val label: String, val headerColor: Int, val bodyColor: Int)
            val secs = listOf(
                Sec("💭", context.getString(R.string.dream_section_message),   Color.parseColor("#9BE7FF"), Color.parseColor("#E6F7FF")),
                Sec("🧠", context.getString(R.string.dream_section_symbols),   Color.parseColor("#FFB3C1"), Color.parseColor("#FFE6EC")),
                Sec("📌", context.getString(R.string.dream_section_premonition),Color.parseColor("#FFD166"), Color.parseColor("#FFF1CC")),
                Sec("☀️", context.getString(R.string.dream_section_tips_today),Color.parseColor("#FFE082"), Color.parseColor("#FFF4D6")),
                Sec("🎯", context.getString(R.string.dream_section_actions_three), Color.parseColor("#A5D6A7"), Color.parseColor("#E9F8ED"))
            )

            fun matchHeader(line: String): Sec? {
                val s = line.trim()
                return secs.firstOrNull { sec ->
                    s == sec.label || s == "${sec.emoji} ${sec.label}" ||
                            s == "${sec.label}:" || s == "${sec.emoji} ${sec.label}:"
                }
            }

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
                val start = idx
                val end = start + line.length
                matchHeader(line)?.let { sec -> hits += Hit(start, end, sec) }
                idx = end + 1
            }

            hits.forEach { h ->
                sb.setSpan(ForegroundColorSpan(h.sec.headerColor), h.start, h.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), h.start, h.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(1.08f), h.start, h.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            for (i in hits.indices) {
                val bodyStart = hits[i].end + 1
                val bodyEnd   = if (i + 1 < hits.size) hits[i + 1].start - 1 else finalText.length
                if (bodyStart < bodyEnd) {
                    sb.setSpan(ForegroundColorSpan(hits[i].sec.bodyColor), bodyStart, bodyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            tv.text = sb

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
