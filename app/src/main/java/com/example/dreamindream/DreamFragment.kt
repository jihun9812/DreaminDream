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
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.example.dreamindream.ads.AdManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
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
import android.view.KeyEvent

class DreamFragment : Fragment() {

    private val apiKey = BuildConfig.OPENAI_API_KEY
    private lateinit var prefs: SharedPreferences
    private lateinit var dreamEditText: EditText
    private lateinit var lottieLoading: LottieAnimationView
    private lateinit var usageTextView: TextView
    private lateinit var interpretButton: Button

    private val MAX_FREE_CALLS = 1
    private val MAX_AD_CALLS = 2
    private val PREF_KEY_DATE = "dream_last_date"
    private val PREF_KEY_COUNT = "dream_count"

    private val client = OkHttpClient()
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dream, container, false)

        MobileAds.initialize(requireContext())
        view.findViewById<AdView>(R.id.adView).loadAd(AdRequest.Builder().build())
        AdManager.loadAd(requireContext())

        prefs = requireContext().getSharedPreferences("dream_history", Context.MODE_PRIVATE)

        dreamEditText = view.findViewById(R.id.dreamEditText)
        lottieLoading = view.findViewById(R.id.lottieLoading)
        usageTextView = view.findViewById(R.id.usageTextView)
        interpretButton = view.findViewById(R.id.interpretButton)

        lottieLoading.visibility = View.GONE
        updateUsageText()

        fun View.applyScaleClick(action: () -> Unit) {
            this.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
                action()
            }
        }

        interpretButton.applyScaleClick {
            val dreamText = dreamEditText.text.toString().trim()
            if (!validateInputSmart(dreamText)) return@applyScaleClick

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val savedDate = prefs.getString(PREF_KEY_DATE, "")
            var count = prefs.getInt(PREF_KEY_COUNT, 0)

            if (savedDate != today) {
                prefs.edit().putString(PREF_KEY_DATE, today).putInt(PREF_KEY_COUNT, 0).apply()
                count = 0
            }

            when {
                count < MAX_FREE_CALLS -> {
                    startInterpretation(dreamText, count)
                }
                count < MAX_FREE_CALLS + MAX_AD_CALLS -> {
                    showAdPrompt {
                        val updatedDreamText = dreamEditText.text.toString().trim()
                        if (validateInputSmart(updatedDreamText)) {
                            startInterpretation(updatedDreamText, count)
                        }
                    }
                }
                else -> showLimitDialog()
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.slide_in_left,
                            R.anim.slide_out_right,
                            R.anim.slide_in_right,
                            R.anim.slide_out_left
                        )
                        .replace(R.id.fragment_container, HomeFragment())
                        .disallowAddToBackStack() // 핵심: 중첩 방지
                        .commit()
                }
            })


    }

    private fun startInterpretation(text: String, count: Int) {
        interpretButton.isEnabled = false
        fetchInterpretation(text)
        prefs.edit().putInt(PREF_KEY_COUNT, count + 1).apply()
        updateUsageText()
    }

    private fun updateUsageText() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = prefs.getString(PREF_KEY_DATE, "")
        val count = if (savedDate == today) prefs.getInt(PREF_KEY_COUNT, 0) else 0
        val remaining = (MAX_FREE_CALLS + MAX_AD_CALLS) - count
        usageTextView.text = getString(R.string.dream_usage_count, remaining.coerceAtLeast(0))
    }

    private fun showAdPrompt(onAccept: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ad_prompt, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)

        dialogView.findViewById<Button>(R.id.btnWatchAd).setOnClickListener {
            dialog.dismiss()
            AdManager.showAd(requireActivity(), {
                onAccept()
            }, {
                Snackbar.make(requireView(), "광고를 완료해야 해몽이 가능합니다.", Snackbar.LENGTH_SHORT).show()
            })
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setDimAmount(0.5f)
        dialog.show()
    }

    private fun showLimitDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("오늘 해몽 제한 도달")
            .setMessage("오늘은 최대 3회까지만 해몽할 수 있어요.\n더 많은 해몽을 원하시면 구독을 고려해보세요.")
            .setPositiveButton("확인", null)
            .setNeutralButton("구독 안내") { _, _ ->
                Snackbar.make(requireView(), "🛍️ 곧 구독 기능이 출시될 예정입니다!", Snackbar.LENGTH_SHORT)
                    .setTextColor(android.graphics.Color.WHITE)
                    .show()
            }
            .show()
    }

    private fun showLoading() {
        if (lottieLoading.visibility == View.VISIBLE) return
        lottieLoading.visibility = View.VISIBLE
        lottieLoading.playAnimation()
    }

    private fun hideLoading(result: String) {
        lottieLoading.cancelAnimation()
        lottieLoading.visibility = View.GONE
        interpretButton.isEnabled = true
        showDreamResultDialog(requireContext(), result)
    }

    companion object {
        fun showDreamResultDialog(context: Context, result: String) {
            val styled: CharSequence = Html.fromHtml(
                result.replace("💭 꿈이 전하는 메시지", "<font color='#4B0082'><b>💭 꿈이 전하는 메시지</b></font>")
                    .replace("🧠 꿈속 상징의 의미", "<br><font color='#006400'><b>🧠 꿈속 상징의 의미</b></font>")
                    .replace("📌 예지 포인트 요약", "<br><font color='#8B0000'><b>📌 예지 포인트 요약</b></font>")
                    .replace("☀️ 운세 활용 팁", "<br><font color='#DAA520'><b>☀️ 운세 활용 팁</b></font>")
                    .replace("🎯 오늘의 행동 포인트", "<br><font color='#4682B4'><b>🎯 오늘의 행동 포인트</b></font>"),
                Html.FROM_HTML_MODE_LEGACY
            )

            val dialog = Dialog(context)
            dialog.setContentView(R.layout.dream_result_dialog)
            dialog.setCancelable(false)
            dialog.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

            // ✅ 여기가 핵심: 다이얼로그 폭 꽉 채우고 배경 투명하게
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            dialog.findViewById<TextView>(R.id.resultTextView).text = styled
            dialog.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }

            dialog.show()
        }
    }


    private fun validateInputSmart(input: String): Boolean {
        val bannedStarters = listOf("안녕", "gpt", "hello", "how are you", "what is", "tell me", "chatgpt",
            "who are you", "날씨 알려줘", "시간 알려줘", "몇시", "몇 시")
        val lower = input.lowercase().trim()
        val isSmallTalk = bannedStarters.any { lower.startsWith(it) }
        val isShortSingleWord = lower.length < 8 && (lower in listOf("날씨", "시간"))
        val isQuestion = (lower.endsWith("?") || lower.startsWith("왜") || lower.startsWith("뭐야"))
        val isMathOnly = Regex("^\\s*\\d+\\s*[-+*/]\\s*\\d+\\s*$").containsMatchIn(input)
        val isGibberish = input.count { it.isLetterOrDigit() } < (input.length / 2)

        return when {
            input.isBlank() -> {
                Toast.makeText(requireContext(), "꿈 내용을 입력해주세요.", Toast.LENGTH_SHORT).show()
                false
            }
            input.length < 10 || isSmallTalk || isShortSingleWord || isQuestion || isMathOnly || isGibberish -> {
                Toast.makeText(requireContext(), "의미 있는 꿈 내용을 구체적으로 입력해주세요.", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun fetchInterpretation(prompt: String) {
        showLoading()

        val requestJson = JSONObject().apply {
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
                        (꿈이 전달하는 전반적 메시지를 요약. 심리 상태나 삶의 방향성 등 내면적 의미 중심)

                        🧠 꿈속 상징의 의미  
                        (꿈에 나온 주요 인물, 사물, 상황 등 각각의 상징이 지닌 의미를 설명)

                        📌 예지 포인트 요약  
                        (가장 핵심적인 예지 포인트를 2~3줄로 요약. 미래에 일어날 수 있는 사건을 구체적으로 제시)

                        ☀️ 운세 활용 팁  
                        (이 꿈을 어떻게 활용하면 좋은지 운세 관점에서 조언. 피해야 할 일/추천 행동 등)

                        🎯 오늘의 행동 포인트  
                        (오늘 바로 실천 가능한 조언이나 주의점. 현실적인 액션 위주로 작성)

                        ---
                        [꿈 내용]  
                        "$prompt"
                    """.trimIndent())
                }
            ))
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())

        client.newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DreamFragment", "GPT 요청 실패", e)
                requireActivity().runOnUiThread {
                    hideLoading("해몽 결과를 받아올 수 없습니다.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = if (response.isSuccessful) {
                    val rawBody = response.body?.string() ?: ""
                    if (rawBody.length > 10000) {
                        "결과가 너무 커서 해몽을 표시할 수 없습니다."
                    } else {
                        try {
                            JSONObject(rawBody)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                                .trim()
                        } catch (e: Exception) {
                            Log.e("DreamFragment", "파싱 실패", e)
                            "결과 파싱 오류"
                        }
                    }
                } else {
                    "해몽 요청 실패 (${response.code})"
                }

                requireActivity().runOnUiThread {
                    hideLoading(responseText)
                }

                executor.execute {
                    saveDream(prompt, responseText)
                }
            }
        })
    }

    private fun saveDream(dream: String, result: String) {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val savedArray = JSONArray(prefs.getString(dateKey, "[]") ?: "[]")
        if (savedArray.length() >= 10) savedArray.remove(0)

        savedArray.put(JSONObject().apply {
            put("dream", dream)
            put("result", result)
        })
        prefs.edit().putString(dateKey, savedArray.toString()).apply()

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "dream" to dream,
            "result" to result,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("users").document(userId)
            .collection("dreams").document(dateKey)
            .collection("entries").add(data)
    }
}
