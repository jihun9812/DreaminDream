package com.example.dreamindream

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.example.dreamindream.ads.AdManager
import com.google.android.gms.ads.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class DreamFragment : Fragment() {

    private val TAG = "DreamFragment"
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }
    private val dateFmt by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    private val FREE_LIMIT = 1
    private val AD_LIMIT = 2
    private val PREF_KEY_DATE = "dream_last_date"
    private val PREF_KEY_COUNT = "dream_count"

    private val BANNED_STARTERS = listOf(
        "안녕","gpt","hello","how are you","what is","tell me","chatgpt",
        "who are you","날씨 알려줘","시간 알려줘","몇시","몇 시"
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var dreamEditText: EditText
    private lateinit var lottieLoading: LottieAnimationView
    private lateinit var usageTextView: TextView
    private lateinit var interpretButton: Button

    // ✅ 배너는 멤버로 보관(수명주기 안전)
    private var bannerAdView: AdView? = null

    private val http = OkHttpClient()
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_dream, container, false)

        // ⚠️ MobileAds.initialize()는 앱에서 한 번만
        bannerAdView = view.findViewById<AdView>(R.id.adView).apply {
            visibility = View.GONE
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("Ads", "✅ Banner loaded")
                    this@apply.visibility = View.VISIBLE
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.e("Ads", "❌ Banner fail code=${e.code}, ${e.message}")
                    this@apply.visibility = View.GONE
                }
            }
            // ❗ XML에 app:adUnitId / app:adSize 지정되어 있어야 함
            // ❗ 여기서는 adUnitId 설정 금지(중복 설정 크래시 방지)
            loadAd(AdRequest.Builder().build())
        }

        // 보상형 프리로드
        AdManager.initialize(requireContext())

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        prefs = requireContext().getSharedPreferences("dream_history_$uid", Context.MODE_PRIVATE)

        bindViews(view)
        initUi()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.slide_in_left, R.anim.slide_out_right,
                            R.anim.slide_in_right, R.anim.slide_out_left
                        )
                        .replace(R.id.fragment_container, HomeFragment())
                        .disallowAddToBackStack()
                        .commit()
                }
            })
    }

    // ── AdView 수명주기
    override fun onResume() {
        super.onResume()
        bannerAdView?.resume()
    }
    override fun onPause() {
        bannerAdView?.pause()
        super.onPause()
    }
    override fun onDestroyView() {
        bannerAdView?.destroy()
        bannerAdView = null
        super.onDestroyView()
    }

    private fun bindViews(root: View) {
        dreamEditText   = root.findViewById(R.id.dreamEditText)
        lottieLoading   = root.findViewById(R.id.lottieLoading)
        usageTextView   = root.findViewById(R.id.usageTextView)
        interpretButton = root.findViewById(R.id.interpretButton)
    }

    private fun initUi() {
        lottieLoading.visibility = View.GONE
        updateUsageText()

        interpretButton.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            val input = dreamEditText.text.toString().trim()
            if (!validateInput(input)) return@setOnClickListener

            val count = getTodayCount() // ✅ 경고 제거: 디폴트 파라미터 사용
            when {
                count < FREE_LIMIT -> startInterpretation(input, count)
                count < FREE_LIMIT + AD_LIMIT -> showAdPrompt {
                    val latest = dreamEditText.text.toString().trim()
                    if (validateInput(latest)) startInterpretation(latest, count)
                }
                else -> showLimitDialog()
            }
        }
    }

    // ── 공통 유틸 (여기서만 한 번 정의)
    private fun todayKey(): String = dateFmt.format(Date())

    // ── Daily counter
    private fun getTodayCount(resetIfNewDay: Boolean = true): Int {
        val today = todayKey()
        val savedDate = prefs.getString(PREF_KEY_DATE, "")
        var count = prefs.getInt(PREF_KEY_COUNT, 0)
        if (resetIfNewDay && savedDate != today) {
            prefs.edit().putString(PREF_KEY_DATE, today).putInt(PREF_KEY_COUNT, 0).apply()
            count = 0
        }
        return count
    }

    private fun increaseTodayCount(current: Int) {
        prefs.edit().putInt(PREF_KEY_COUNT, current + 1).apply()
        updateUsageText()
    }

    private fun updateUsageText() {
        val today = todayKey()
        val savedDate = prefs.getString(PREF_KEY_DATE, "")
        val count = if (savedDate == today) prefs.getInt(PREF_KEY_COUNT, 0) else 0
        val remaining = (FREE_LIMIT + AD_LIMIT - count).coerceAtLeast(0)
        usageTextView.text = getString(R.string.dream_usage_count, remaining)
    }

    // ── Ad BottomSheet
    private fun showAdPrompt(onAccept: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.window?.setDimAmount(0.5f)

        ViewCompat.setOnApplyWindowInsetsListener(dialogView) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom + sys.bottom)
            insets
        }

        val btnWatch = dialogView.findViewById<Button>(R.id.btnWatchAd)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val progress = dialogView.findViewById<ProgressBar>(R.id.progressAd)
        val status = dialogView.findViewById<TextView>(R.id.textStatus)

        fun setReady(ready: Boolean) {
            btnWatch.isEnabled = ready
            progress.visibility = if (ready) View.GONE else View.VISIBLE
            btnWatch.text = if (ready) "광고 보기" else "광고 준비 중…"
            status.text = if (ready) "광고가 준비되었습니다" else "광고 준비 중…"
        }
        fun setFailed(msg: String) {
            btnWatch.isEnabled = true
            progress.visibility = View.GONE
            btnWatch.text = "다시 시도"
            status.text = msg.ifBlank { "광고를 불러오지 못했습니다. 다시 시도해주세요." }
        }

        setReady(AdManager.isReady())
        AdManager.addOnLoadedListener { if (dialog.isShowing) setReady(true) }
        AdManager.addOnFailedListener { err ->
            if (dialog.isShowing) {
                val msg = if (err != null) "로드 실패(code=${err.code}) 재시도 중…" else "네트워크 불안정으로 재시도 중…"
                setFailed(msg)
            }
        }
        if (!AdManager.isReady()) AdManager.loadAd(requireContext())

        btnWatch.setOnClickListener {
            if (btnWatch.text == "다시 시도") {
                setReady(false)
                AdManager.loadAd(requireContext())
                return@setOnClickListener
            }
            dialog.dismiss()
            AdManager.showAd(
                requireActivity(),
                onRewardEarned = onAccept,
                onFailed = {
                    view?.let { v ->
                        Snackbar.make(v, "광고를 완료해야 해몽이 가능합니다.", Snackbar.LENGTH_SHORT).show()
                    }
                }
            )
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ── Interpretation
    private fun startInterpretation(text: String, currentCount: Int) {
        interpretButton.isEnabled = false
        fetchInterpretation(text)
        increaseTodayCount(currentCount)
    }

    private fun validateInput(input: String): Boolean {
        val lower = input.lowercase().trim()
        val smallTalk = BANNED_STARTERS.any { lower.startsWith(it) }
        val isQuestion = lower.endsWith("?") || lower.startsWith("왜") || lower.startsWith("뭐야")
        val isMath = Regex("^\\s*\\d+\\s*[-+*/]\\s*\\d+\\s*$").containsMatchIn(input)
        val isGibberish = input.count { it.isLetterOrDigit() } < (input.length / 2)

        return when {
            input.isBlank() -> { toast("꿈 내용을 입력해주세요."); false }
            input.length < 10 || smallTalk || isQuestion || isMath || isGibberish -> {
                toast("의미 있는 꿈 내용을 구체적으로 입력해주세요."); false
            }
            else -> true
        }
    }

    private fun fetchInterpretation(prompt: String) {
        showLoading()

        val body = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("temperature", 0.7)
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", """
                        너는 지금부터 '예지몽 분석 전문가'야. 사용자가 제공한 꿈은 단순한 상상이 아닌 미래를 암시하는 **예지몽**이라고 가정하고 분석해.
                        반드시 아래 5가지 항목을 구분해서 자세하고 현실적으로 작성해줘.
                        각 항목 앞의 이모지는 그대로 사용하고, 실제로 일어날 수 있는 사건에 기반하여 **현실성 있는 해몽**을 제공해야 해.
                        표현은 신뢰감 있고 조리 있게, 마치 전문 상담사처럼 작성해.

                        ---
                        💭 꿈이 전하는 메시지
                        🧠 꿈속 상징의 의미
                        📌 예지 포인트 요약
                        ☀️ 운세 활용 팁
                        🎯 오늘의 행동 포인트
                        ---
                        [꿈 내용] "$prompt"
                    """.trimIndent())
                }
            ))
        }.toString().toRequestBody("application/json".toMediaType())

        http.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "GPT 요청 실패", e)
                if (call.isCanceled()) return
                val act = activity ?: return
                act.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    hideLoading("해몽 결과를 받아올 수 없습니다.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) { response.close(); return }
                val text = if (response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    if (raw.length > 10000) "결과가 너무 커서 해몽을 표시할 수 없습니다."
                    else try {
                        JSONObject(raw)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } catch (e: Exception) {
                        Log.e(TAG, "파싱 실패", e); "결과 파싱 오류"
                    }
                } else "해몽 요청 실패 (${response.code})"
                response.close()

                val act = activity ?: return
                act.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    hideLoading(text)
                }
                io.execute { saveDream(prompt, text) }
            }
        })
    }

    private fun saveDream(dream: String, result: String) {
        val dateKey = todayKey()
        val list = JSONArray(prefs.getString(dateKey, "[]") ?: "[]")
        if (list.length() >= 10) list.remove(0)
        list.put(JSONObject().apply { put("dream", dream); put("result", result) })
        prefs.edit().putString(dateKey, list.toString()).apply()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("dreams").document(dateKey)
            .collection("entries")
            .add(hashMapOf("dream" to dream, "result" to result, "timestamp" to System.currentTimeMillis()))
    }

    // ── Loading / Dialog / Utils
    private fun showLimitDialog() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("오늘 해몽 제한 도달")
            .setMessage("오늘은 최대 3회까지만 해몽할 수 있어요.\n더 많은 해몽을 원하시면 구독을 고려해보세요.")
            .setPositiveButton("확인", null)
            .setNeutralButton("구독 안내") { _, _ ->
                view?.let { Snackbar.make(it, "🛍️ 곧 구독 기능이 출시될 예정입니다!", Snackbar.LENGTH_SHORT).show() }
            }
            .show()
    }

    private fun showLoading() {
        if (!isAdded) return
        if (lottieLoading.visibility == View.VISIBLE) return
        lottieLoading.visibility = View.VISIBLE
        lottieLoading.playAnimation()
    }

    private fun hideLoading(result: String) {
        if (!isAdded) return
        lottieLoading.cancelAnimation()
        lottieLoading.visibility = View.GONE
        interpretButton.isEnabled = true
        val ctx = context ?: return
        showResultDialog(ctx, result)
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    companion object {
        fun showResultDialog(context: Context, result: String) = showResult(context, result)
        fun showDreamResultDialog(context: Context, result: String) = showResultDialog(context, result)

        private fun showResult(context: Context, result: String) {
            val styled: CharSequence = Html.fromHtml(
                result.replace("💭 꿈이 전하는 메시지", "<font color='#4B0082'><b>💭 꿈이 전하는 메시지</b></font>")
                    .replace("🧠 꿈속 상징의 의미", "<br><font color='#006400'><b>🧠 꿈속 상징의 의미</b></font>")
                    .replace("📌 예지 포인트 요약", "<br><font color='#8B0000'><b>📌 예지 포인트 요약</b></font>")
                    .replace("☀️ 운세 활용 팁", "<br><font color='#DAA520'><b>☀️ 운세 활용 팁</b></font>")
                    .replace("🎯 오늘의 행동 포인트", "<br><font color='#4682B4'><b>🎯 오늘의 행동 포인트</b></font>"),
                Html.FROM_HTML_MODE_LEGACY
            )

            val dialog = Dialog(context).apply {
                setContentView(R.layout.dream_result_dialog)
                setCancelable(false)
                setOnKeyListener { _, keyCode, _ -> keyCode == android.view.KeyEvent.KEYCODE_BACK }
                window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                findViewById<TextView>(R.id.resultTextView).text = styled
                findViewById<View>(R.id.btn_close).setOnClickListener { dismiss() }
            }
            dialog.show()
        }
    }
}
