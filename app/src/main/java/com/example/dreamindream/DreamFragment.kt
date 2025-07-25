package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdView
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

class DreamFragment : Fragment() {

    private val apiKey = BuildConfig.OPENAI_API_KEY
    private lateinit var prefs: SharedPreferences
    private lateinit var resultTextView: TextView
    private lateinit var dreamEditText: EditText
    private lateinit var lottieLoading: LottieAnimationView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dream, container, false)

        // 광고 초기화
        MobileAds.initialize(requireContext())
        view.findViewById<AdView>(R.id.adView).loadAd(AdRequest.Builder().build())

        prefs = requireContext().getSharedPreferences("dream_history", Context.MODE_PRIVATE)

        dreamEditText = view.findViewById(R.id.dreamEditText)
        resultTextView = view.findViewById(R.id.resultTextView)
        lottieLoading = view.findViewById(R.id.lottieLoading)
        lottieLoading.visibility = View.GONE

        fun View.applyScaleClick(action: () -> Unit) {
            this.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
                action()
            }
        }

        // 뒤로가기
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

        // 해몽 버튼
        view.findViewById<Button>(R.id.interpretButton).applyScaleClick {
            val dreamText = dreamEditText.text.toString().trim()
            if (validateInputSmart(dreamText)) {
                fetchInterpretation(dreamText)
            }
        }

        return view
    }

    private fun showLoading() {
        lottieLoading.visibility = View.VISIBLE
        lottieLoading.playAnimation()
    }

    private fun hideLoading(result: String) {
        lottieLoading.cancelAnimation()
        lottieLoading.visibility = View.GONE
        resultTextView.text = result
    }

    private fun validateInputSmart(input: String): Boolean {
        val bannedStarters = listOf(
            "안녕", "gpt", "hello", "how are you", "what is", "tell me", "chatgpt",
            "who are you", "날씨 알려줘", "시간 알려줘", "몇시", "몇 시"
        )
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
                JSONObject().put("role", "user").put("content", """
너는 지금부터 꿈을 예지몽처럼 분석하는 전문가다. 아래 꿈은 예지몽이라고 가정하고 다음 조건에 따라 분석하라.

[분석 기준]
- 꿈 내용을 바로 분석하라. 서론이나 설명 없이 곧바로 해석 시작
-설명은 존댓말로 성심껏.
- 인물, 배경, 행동, 감정 요소를 분석해 현실에서 발생할 수 있는 구체적인 사건으로 연결
- 심리적 조언이나 모호한 말 금지. 반드시 실제로 일어날 수 있는 사건으로 예측
- 마지막에 예고된 사건을 **한 줄로 요약**
- 그 뒤에 이 꿈이 길몽인지 흉몽인지 **짧은 한줄로 판단**
- 마지막에는 **"조심할 점"과 "해야 할 점"**을 각각 1~2줄로 정리

[꿈 내용]
\"\"\"$prompt\"\"\"
""".trimIndent())
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
        // 1) 로컬 저장
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val savedArray = JSONArray(prefs.getString(dateKey, "[]") ?: "[]")
        savedArray.put(JSONObject().apply {
            put("dream", dream)
            put("result", result)
        })
        prefs.edit {
            putString(dateKey, savedArray.toString())
        }

        // 2) Firestore 저장
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
