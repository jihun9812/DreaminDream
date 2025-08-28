// app/src/main/java/com/example/dreamindream/DreamFragment.kt
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

        // ✅ uid 보관 (로그인/익명 모두 값 존재)
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
        resultTextView.text = "여기에 해몽 결과가 표시됩니다."
        resultTextView.setTextColor(Color.parseColor("#BFD0DC"))
    }

    private fun initUi(root: View) {
        updateUsageLabel()

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
            textStatus.text = "광고 준비 중…"
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
                    textStatus.text = "광고가 닫혔어요. 다시 시도해 주세요."
                    AdManager.loadRewarded(requireContext())
                },
                onFailed = { reason ->
                    btnWatch.isEnabled = true
                    progress.visibility = View.GONE
                    textStatus.text = "광고 로드 실패 ($reason). 잠시 후 다시 시도해 주세요."
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
        usageTextView?.text = "오늘 남은 횟수 : ${remain}회"
    }

    // 입력 검증
    private fun validateInput(input: String): Boolean {
        val lower = input.lowercase()
        val isMath = Regex("^\\s*\\d+\\s*[-+*/]\\s*\\d+\\s*$").containsMatchIn(input)
        val smallTalk = bannedStarters.any { lower.startsWith(it) }
        return when {
            input.isBlank() -> { toast("꿈 내용을 입력해주세요."); false }
            input.length < 10 || isMath || smallTalk -> { toast("의미 있는 꿈 내용을 구체적으로 입력해주세요."); false }
            else -> true
        }
    }

    private fun startInterpret(prompt: String) {
        showLoading()

        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", """
                너는 '예지몽 분석 해몽가'야.
                아래 꿈 내용을 바탕으로 현실적이고 신뢰감 있게 해석해.
                구조:
                - 💭 꿈이 전하는 메시지
                - 🧠 핵심 상징 해석
                - 📌 예지 포인트
                - ☀️ 오늘의 활용 팁
                - 🎯 오늘의 행동 3가지
                [꿈 내용] "$prompt"
            """.trimIndent())
        )

        val body = JSONObject().apply {
            put("model", "gpt-4.1-mini")
            put("temperature", 0.6)
            put("messages", messages)
            put("max_tokens", 900)
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
                ui { onResultArrived("해몽 결과를 받아올 수 없습니다. 네트워크를 확인하고 다시 시도해 주세요.") }
            }
            override fun onResponse(call: Call, response: Response) {
                val text = if (response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    try {
                        JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    } catch (_: Exception) { "결과 파싱 오류가 발생했어요." }
                } else "해몽 요청 실패 (${response.code})"
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
            // ❗️dateKey에 null 전달 금지 (Non-null)
            FirestoreManager.saveDream(userId, dream, result) // ← 오늘 날짜 자동
        }
    }

    // ---- 결과 수신 후 처리
    private fun onResultArrived(text: String) {
        hideLoading()
        resultTextView.setTextColor(Color.parseColor("#FFFFFF"))
        resultTextView.text = styleResult(text.ifBlank { "해몽 결과가 비어있습니다." })
    }

    // (스타일링/로딩/유틸 메서드는 기존 그대로 …)
    // ─────────────────────────────────────────────
    private fun showLoading() {
        interpretButton.isEnabled = false
        lottieLoading?.apply {
            alpha = 0f; translationY = -200f; scaleX = 0.7f; scaleY = 0.7f
            visibility = View.VISIBLE
            animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(400).start()
            playAnimation()
        }
        resultTextView.text = "해석 중입니다…"
        resultTextView.setTextColor(Color.parseColor("#BFD0DC"))
    }
    private fun hideLoading() {
        interpretButton.isEnabled = true
        lottieLoading?.apply { cancelAnimation(); visibility = View.GONE }
    }

    private fun styleResult(raw: String): CharSequence {
        var clean = raw.replace(Regex("(?m)^\\s*#{1,4}\\s*"), "")
        clean = clean.replace("**", "")
        clean = clean.replace(Regex("`{1,3}"), "")
        clean = clean.replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")

        val sb = SpannableStringBuilder(clean)

        data class H(val emoji: String, val label: String, val color: Int)
        val headers = listOf(
            H("💭", "꿈이 전하는 메시지", Color.parseColor("#9BE7FF")),
            H("🧠", "핵심 상징 해석",   Color.parseColor("#FFB3C1")),
            H("📌", "예지 포인트",     Color.parseColor("#FFD166")),
            H("☀️", "오늘의 활용 팁",  Color.parseColor("#FFE082")),
            H("🎯", "오늘의 행동 3가지",Color.parseColor("#A5D6A7"))
        )

        headers.forEach { h ->
            val pattern = Regex("(?m)^(?:${Regex.escape(h.emoji)}\\s*)?${Regex.escape(h.label)}.*$")
            pattern.findAll(clean).forEach { m ->
                val s = m.range.first
                val e = m.range.last + 1
                sb.setSpan(ForegroundColorSpan(h.color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(1.06f), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
            .setTitle("오늘 기회 소진")
            .setMessage("오늘은 해몽 가능 횟수(무료 1회 + 광고 2회)를 모두 사용했어요. 내일 다시 시도해 주세요.")
            .setPositiveButton("확인", null)
            .show()
    }

    companion object {
        fun showResultDialog(context: Context, result: String) {
            val v = View.inflate(context, R.layout.dream_result_dialog, null)
            val tv = v.findViewById<TextView>(R.id.resultTextView)

            var clean = result.ifBlank { "해몽 결과가 비어있습니다." }
                .replace(Regex("(?m)^\\s*#{1,4}\\s*"), "")
                .replace("**", "")
                .replace(Regex("`{1,3}"), "")
                .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")

            val sb = SpannableStringBuilder(clean)

            data class H(val emoji: String, val label: String, val color: Int)
            val headers = listOf(
                H("💭", "꿈이 전하는 메시지", Color.parseColor("#9BE7FF")),
                H("🧠", "핵심 상징 해석",   Color.parseColor("#FFB3C1")),
                H("📌", "예지 포인트",     Color.parseColor("#FFD166")),
                H("☀️", "오늘의 활용 팁",  Color.parseColor("#FFE082")),
                H("🎯", "오늘의 행동 3가지",Color.parseColor("#A5D6A7"))
            )
            headers.forEach { h ->
                val pattern = Regex("(?m)^(?:${Regex.escape(h.emoji)}\\s*)?${Regex.escape(h.label)}.*$")
                pattern.findAll(clean).forEach { m ->
                    val s = m.range.first; val e = m.range.last + 1
                    sb.setSpan(ForegroundColorSpan(h.color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(1.06f), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
