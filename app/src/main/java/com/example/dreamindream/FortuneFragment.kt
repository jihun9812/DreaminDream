package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class FortuneFragment : Fragment() {

    private val apiKey = BuildConfig.OPENAI_API_KEY
    private lateinit var prefs: SharedPreferences
    private lateinit var resultText: TextView
    private lateinit var loadingView: LottieAnimationView
    private lateinit var button: Button
    private lateinit var cardView: CardView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_fortune, container, false)

        resultText = view.findViewById(R.id.fortune_result)
        loadingView = view.findViewById(R.id.fortune_loading)
        button = view.findViewById(R.id.fortuneButton)
        cardView = view.findViewById(R.id.fortuneCard)

        prefs = requireContext().getSharedPreferences("user_info", Context.MODE_PRIVATE)
        view.findViewById<AdView>(R.id.adView_fortune).loadAd(AdRequest.Builder().build())

        // CardView는 처음에 작게(wrap_content) 시작
        cardView.post { setCardMin() }

        // 버튼 클릭 애니메이션 + 로직
        fun View.applyScaleClick(action: () -> Unit) {
            this.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
                action()
            }
        }

        button.applyScaleClick {
            setCardMin() // 결과 전엔 항상 작게
            val userInfo = checkUserInfo()
            if (userInfo != null) {
                fetchFortune(userInfo)
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

        return view
    }

    // CardView 작게 (wrap_content + minHeight)
    private fun setCardMin() {
        val params = cardView.layoutParams
        params.height = LinearLayout.LayoutParams.WRAP_CONTENT
        cardView.layoutParams = params
    }

    // CardView 크게 (400dp)
    private fun setCardLarge() {
        val params = cardView.layoutParams
        params.height = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 400f, resources.displayMetrics
        ).toInt()
        cardView.layoutParams = params
    }

    private fun getToday(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun checkUserInfo(): Triple<String, String, String>? {
        val nickname = prefs.getString("nickname", "") ?: ""
        val mbti = prefs.getString("mbti", "") ?: ""
        val birth = prefs.getString("birthdate", "") ?: ""

        return if (nickname.isNotBlank() && mbti.isNotBlank() && birth.isNotBlank()) {
            Triple(nickname, mbti, birth)
        } else {
            Toast.makeText(requireContext(), getString(R.string.error_missing_info), Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun fetchFortune(userInfo: Triple<String, String, String>) {
        val (nickname, mbti, birth) = userInfo
        val prompt = buildPrompt(nickname, mbti, birth)

        // --- Lottie 로딩 애니메이션 시작 ---
        loadingView.visibility = View.VISIBLE
        loadingView.playAnimation()
        resultText.text = ""

        val requestBody = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("temperature", 0.7)
            put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            ))
        }

        val body = requestBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    loadingView.cancelAnimation()
                    loadingView.visibility = View.GONE
                    resultText.text = getString(R.string.error_fetch_failed)
                    setCardMin()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                val fortuneResult = if (response.isSuccessful) {
                    try {
                        JSONObject(responseBody).getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    } catch (_: Exception) {
                        "응답 파싱 오류"
                    }
                } else {
                    "운세 요청 실패 (${response.code})"
                }

                requireActivity().runOnUiThread {
                    loadingView.cancelAnimation()
                    loadingView.visibility = View.GONE
                    resultText.text = fortuneResult
                    setCardLarge()
                }
            }
        })
    }

    private fun buildPrompt(nickname: String, mbti: String, birth: String): String {
        return """
        너는 오늘의 운세를 제공하는 AI야. 아래 사용자 정보를 기반으로 재미있고 직관적인 오늘의 운세를 작성해줘.

        [사용자 정보]
        - 닉네임: $nickname
        - MBTI: $mbti
        - 생일: $birth

        [작성 형식]
        각 항목에 대해 0~100점 사이의 점수를 제공하고, 점수에 따라 아래와 같은 설명을 덧붙여줘:
        - 60점 이하: 주의할 점이나 피해야 할 행동
        - 61점 이상: 추천하는 행동이나 기회

        항목:
        1. 총운
        2. 재물운
        3. 연애운
        4. 학업/직장운
        5. 로또 운세 및 추천 번호 (6자리)

        [추가 요청]
        - 문장은 자연스럽고 사용자에게 말을 거는 듯한 말투로 써줘.
        - 마지막에 오늘의 재미 요소나 유머 한 줄도 추가해줘.
        - 모든 결과는 오늘 하루 동안만 유효하다는 느낌을 줘.

        결과는 사용자에게 바로 보여질 형식이야. 너무 길거나 어렵지 않게 구성해줘.
    """.trimIndent()
    }
}
