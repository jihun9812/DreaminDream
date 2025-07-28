package com.example.dreamindream
import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.example.dreamindream.ads.AdManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdView
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
import android.app.AlertDialog
import android.text.Html

class DreamFragment : Fragment() {

    private val apiKey = BuildConfig.OPENAI_API_KEY
    private lateinit var prefs: SharedPreferences
    private lateinit var resultTextView: TextView
    private lateinit var dreamEditText: EditText
    private lateinit var lottieLoading: LottieAnimationView
    private lateinit var usageTextView: TextView

    private val MAX_FREE_CALLS = 1
    private val MAX_AD_CALLS = 2
    private val PREF_KEY_DATE = "dream_last_date"
    private val PREF_KEY_COUNT = "dream_count"

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
        resultTextView = view.findViewById(R.id.resultTextView)
        lottieLoading = view.findViewById(R.id.lottieLoading)
        usageTextView = view.findViewById(R.id.usageTextView)
        lottieLoading.visibility = View.GONE

        updateUsageText()

        fun View.applyScaleClick(action: () -> Unit) {
            this.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
                action()
            }
        }

        view.findViewById<ImageButton>(R.id.backButton).applyScaleClick {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        view.findViewById<Button>(R.id.interpretButton).applyScaleClick {
            val dreamText = dreamEditText.text.toString().trim()
            if (!validateInputSmart(dreamText)) return@applyScaleClick

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val savedDate = prefs.getString(PREF_KEY_DATE, "")
            var count = prefs.getInt(PREF_KEY_COUNT, 0)

            if (savedDate != today) {
                prefs.edit {
                    putString(PREF_KEY_DATE, today)
                    putInt(PREF_KEY_COUNT, 0)
                }
                count = 0
            }


            when {
                count < MAX_FREE_CALLS -> {
                    fetchInterpretation(dreamText)
                    prefs.edit {
                        putInt(PREF_KEY_COUNT, count + 1)
                    }
                    updateUsageText()

                }
                count < MAX_FREE_CALLS + MAX_AD_CALLS -> {
                    showAdPrompt {
                        val updatedDreamText = dreamEditText.text.toString().trim()
                        if (validateInputSmart(updatedDreamText)) {
                            fetchInterpretation(updatedDreamText)
                            prefs.edit {
                                putInt(PREF_KEY_COUNT, count + 1)
                            }
                            updateUsageText()

                        }
                    }
                }
                else -> {
                    showLimitDialog()
                }
            }
        }

        return view
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
                onAccept()  // 광고 시청 완료 후 실행
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
        lottieLoading.visibility = View.VISIBLE
        lottieLoading.playAnimation()
    }

    private fun hideLoading(result: String) {
        lottieLoading.cancelAnimation()
        lottieLoading.visibility = View.GONE
        val styled = Html.fromHtml(
            result.replace("💭 꿈이 전하는 메시지", "<font color='#4B0082'><b>💭 꿈이 전하는 메시지</b></font>")
                .replace("🧠 꿈속 상징의 의미", "<br><font color='#006400'><b>🧠 꿈속 상징의 의미</b></font>")
                .replace("📌 예지 포인트 요약", "<br><font color='#8B0000'><b>📌 예지 포인트 요약</b></font>")
                .replace("☀️ 운세 활용 팁", "<br><font color='#DAA520'><b>☀️ 운세 활용 팁</b></font>")
                .replace("🎯 오늘의 행동 포인트", "<br><font color='#4682B4'><b>🎯 오늘의 행동 포인트</b></font>"),
            Html.FROM_HTML_MODE_LEGACY
        )
        resultTextView.text = styled
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
                resultTextView.text = "꿈 내용을 입력해주세요."
                false
            }
            input.length < 10 || isSmallTalk || isShortSingleWord || isQuestion || isMathOnly || isGibberish -> {
                resultTextView.text = "의미 있는 꿈 내용을 구체적으로 입력해주세요."
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
                너는 지금부터 꿈을 예지몽처럼 분석하는 전문가야. 아래 꿈은 예지몽이라고 가정했고 다음과 같이 분석해.

                [분석 항목]
                💭 꿈이 전하는 메시지  
                🧠 꿈속 상징의 의미  
                📌 예지 포인트 요약  
                ☀️ 운세 활용 팁  
                🎯 오늘의 행동 포인트  

                각 항목은 정확하고 간결하게 작성하고, 이모티콘은 그대로 사용해줘.  
                꼭 현실적으로 일어날 수 있는 사건을 기반으로 분석해.

                [꿈 내용]
                \"$prompt\"
            """.trimIndent())
                }
            ))
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())

        OkHttpClient().newCall(
            Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    hideLoading("해몽 결과를 받아올 수 없습니다.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = if (response.isSuccessful) {
                    try {
                        JSONObject(response.body?.string() ?: "")
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } catch (_: Exception) {
                        "결과 파싱 오류"
                    }
                } else {
                    "해몽 요청 실패 (${response.code})"
                }

                requireActivity().runOnUiThread {
                    hideLoading(responseText)
                    saveDream(prompt, responseText)
                }
            }
        })
    }

    private fun saveDream(dream: String, result: String) {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val savedArray = JSONArray(prefs.getString(dateKey, "[]") ?: "[]")
        savedArray.put(JSONObject().apply {
            put("dream", dream)
            put("result", result)
        })
        prefs.edit {
            putString(dateKey, savedArray.toString())
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
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
}
