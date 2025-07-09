package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdView
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
    private lateinit var loadingView: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dream, container, false)

        // 광고 초기화
        MobileAds.initialize(requireContext())
        view.findViewById<AdView>(R.id.adView).loadAd(AdRequest.Builder().build())

        prefs = requireContext().getSharedPreferences("dream_history", Context.MODE_PRIVATE)

        // UI 초기화
        dreamEditText = view.findViewById(R.id.dreamEditText)
        resultTextView = view.findViewById(R.id.resultTextView)

        // ✅ 공통 애니메이션 함수
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
            if (validateInput(dreamText)) {
                fetchInterpretation(dreamText)
            }
        }

        return view
    }

    private fun showLoading() {
        resultTextView.text = "🔮 해몽 중입니다..."
    }

    private fun hideLoading(result: String) {
        resultTextView.text = result
    }

    private fun validateInput(input: String): Boolean {
        val bannedStarters = listOf("안녕", "gpt", "hello", "how are you", "what is", "tell me", "chatgpt", "who are you", "날씨", "시간")
        val isSmallTalk = bannedStarters.any { input.lowercase().startsWith(it) }
        val isMathOnly = Regex("^\\s*\\d+\\s*[-+*/]\\s*\\d+\\s*$").containsMatchIn(input)
        val isGibberish = input.count { it.isLetterOrDigit() } < (input.length / 2)

        return when {
            input.isBlank() -> {
                resultTextView.text = "꿈 내용을 입력해주세요."
                false
            }
            input.length < 10 || isSmallTalk || isMathOnly || isGibberish -> {
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
너는 지금부터 꿈을 예지몽처럼 분석하는 전문가 역할을 수행해야 해. 다른 종류의 해몽은 절대 하지 마. 반드시 예지몽이라는 전제 하에 다음 꿈 내용을 분석해.

[분석 기준]
- 반드시 이 꿈이 **미래에 실제로 일어날 수 있는 사건**을 암시하는 것으로 가정해.
- 꿈 속 **인물, 배경, 행동, 감정**을 세부적으로 해석해서, 그 요소가 어떤 미래 사건을 예고하는지 구체적으로 설명해.
- 현실 세계에서 발생할 수 있는 **구체적인 일이나 변화**를 반드시 예시로 들어줘.
- 막연한 해석이나 조언은 금지하고, 반드시 예지몽처럼 직결된 결과만 말해.
- 마지막에는 이 꿈이 예고하는 미래 사건을 한 줄로 요약해.

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
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
        val savedArray = JSONArray(prefs.getString(dateKey, "[]") ?: "[]")
        savedArray.put(JSONObject().apply {
            put("dream", dream)
            put("result", result)
        })
        prefs.edit {
            putString(dateKey, savedArray.toString())
        }
    }
}
