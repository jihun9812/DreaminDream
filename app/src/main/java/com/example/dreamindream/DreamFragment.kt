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

        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        prefs = requireContext().getSharedPreferences("dream_history_$uid", Context.MODE_PRIVATE)

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

        // 결과 클릭 시 전체보기
        resultTextView.setOnClickListener {
            showResultDialog(requireContext(), resultTextView.text.toString())
        }
    }

    private fun initUi(root: View) {
        updateUsageLabel()

        interpretButton.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))

            // ✅ 키보드 자동 내림 + 결과 영역으로 스크롤 준비
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
                    // ✅ 광고 보기/취소 — 시청 완료(보상)되어야만 진행
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
            // ▶ 실제 보상형 광고 표시. 보상 Earned 시에만 진행.
            AdManager.showRewarded(
                activity = requireActivity(),
                onRewardEarned = {
                    bs.dismiss()
                    onRewardEarnedProceed()
                    AdManager.loadRewarded(requireContext()) // 다음을 위해 다시 로드
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
        usageTextView?.text = "오늘 남은 해몽 기회: ${remain}회"
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

    // 해몽 요청
    private fun startInterpret(prompt: String) {
        showLoading()

        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", """
                너는 '예지몽 분석 컨설턴트'야.
                아래 꿈 내용을 바탕으로 현실적이고 신뢰감 있게 해석해.
                구조:
                - 💭 꿈이 전하는 메시지
                - 🧠 핵심 상징 해석
                - 📌 예지 포인트
                - ☀️ 오늘의 활용 팁
                - 🎯 오늘의 행동 3가지(시간·수치 포함)
                [꿈 내용] "$prompt"
            """.trimIndent())
        )

        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.7)
            put("messages", messages)
            put("max_tokens", 900)
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        http.newCall(req).enqueue(object : Callback {
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
        val dayKey = todayKey()
        val arr = JSONArray(prefs.getString(dayKey, "[]") ?: "[]")
        if (arr.length() >= 10) arr.remove(0)
        arr.put(JSONObject().put("dream", dream).put("result", result))
        prefs.edit().putString(dayKey, arr.toString()).apply()
    }

    // ---- 결과 수신 후 처리 (즉시 표시 + 제목 색 입히기) ----
    private fun onResultArrived(text: String) {
        hideLoading()
        resultTextView.setTextColor(Color.parseColor("#FFFFFF"))
        resultTextView.text = styleResult(text.ifBlank { "해몽 결과가 비어있습니다." })
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
        resultTextView.text = "해석 중입니다…"
        resultTextView.setTextColor(Color.parseColor("#BFD0DC"))
    }
    private fun hideLoading() {
        interpretButton.isEnabled = true
        lottieLoading?.apply { cancelAnimation(); visibility = View.GONE }
    }

    // ---- 텍스트 스타일링 (헤더에 색/볼드/사이즈) ----
    private fun styleResult(raw: String): CharSequence {
        // ‘### ’ 같은 마크다운 헤더 토큰 제거
        val clean = raw.replace(Regex("(?m)^\\s*#{1,4}\\s*"), "")
        val sb = SpannableStringBuilder(clean)

        // 헤더 라인 매칭 (이모지 시작)
        val headerRegex = Regex("(?m)^(💭\\s*꿈이 전하는 메시지|🧠\\s*핵심 상징 해석|📌\\s*예지 포인트|☀️\\s*오늘의 활용 팁|🎯\\s*오늘의 행동\\s*3가지.*?)$")
        val matches = headerRegex.findAll(clean)

        // 색 팔레트
        fun colorFor(h: String) = when {
            h.startsWith("💭") -> Color.parseColor("#9BE7FF") // 하늘
            h.startsWith("🧠") -> Color.parseColor("#FFB3C1") // 핑크
            h.startsWith("📌") -> Color.parseColor("#FFD166") // 노랑
            h.startsWith("☀️") -> Color.parseColor("#FFE082") // 앰버
            else               -> Color.parseColor("#A5D6A7") // 초록 (🎯)
        }

        matches.forEach { m ->
            val start = m.range.first
            val end   = m.range.last + 1
            sb.setSpan(ForegroundColorSpan(colorFor(m.value)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(1.06f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return sb
    }

    // ---- 키보드 내리고 결과 영역 보이게 스크롤 ----
    private fun hideKeyboardAndScrollToResult(root: View) {
        // 키보드 내리기
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        dreamEditText.clearFocus()
        imm.hideSoftInputFromWindow(root.windowToken, 0)

        // 결과 영역으로 스크롤 (가장 가까운 ScrollView 찾아서 이동)
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
        //  캘린더/리스트 다이얼로그 — 아래 잘리지 않게 높이 80% 제한
        fun showResultDialog(context: Context, result: String) {
            val v = View.inflate(context, R.layout.dream_result_dialog, null)
            val tv = v.findViewById<TextView>(R.id.resultTextView)

            // 헤더 스타일 동일 적용
            val clean = result.ifBlank { "해몽 결과가 비어있습니다." }.replace(Regex("(?m)^\\s*#{1,4}\\s*"), "")
            val sb = SpannableStringBuilder(clean)
            val headerRegex = Regex("(?m)^(💭\\s*꿈이 전하는 메시지|🧠\\s*핵심 상징 해석|📌\\s*예지 포인트|☀️\\s*오늘의 활용 팁|🎯\\s*오늘의 행동\\s*3가지.*?)$")
            fun colorFor(h: String) = when {
                h.startsWith("💭") -> Color.parseColor("#9BE7FF")
                h.startsWith("🧠") -> Color.parseColor("#FFB3C1")
                h.startsWith("📌") -> Color.parseColor("#FFD166")
                h.startsWith("☀️") -> Color.parseColor("#FFE082")
                else               -> Color.parseColor("#A5D6A7")
            }
            headerRegex.findAll(clean).forEach { m ->
                val s = m.range.first; val e = m.range.last + 1
                sb.setSpan(ForegroundColorSpan(colorFor(m.value)), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(1.06f), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            tv.text = sb

            // 높이 제한 + 스크롤 설정
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
