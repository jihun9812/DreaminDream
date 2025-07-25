package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.constraintlayout.widget.ConstraintLayout


class FortuneFragment : Fragment() {

    private val apiKey = BuildConfig.OPENAI_API_KEY
    private lateinit var prefs: SharedPreferences
    private lateinit var resultText: TextView
    private lateinit var loadingView: LottieAnimationView
    private lateinit var button: Button
    private lateinit var fortuneCard: CardView
    private var isExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_fortune, container, false)

        resultText = view.findViewById(R.id.fortune_result)
        loadingView = view.findViewById(R.id.fortune_loading)
        button = view.findViewById(R.id.fortuneButton)
        fortuneCard = view.findViewById(R.id.fortuneCard)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        prefs = requireContext().getSharedPreferences("user_info_$userId", Context.MODE_PRIVATE)
        view.findViewById<AdView>(R.id.adView_fortune).loadAd(AdRequest.Builder().build())

        // ✅ 테스트용: 항상 새로 보게 (운영 시 제거)
        prefs.edit().remove("fortune_${getToday()}").apply()

        val today = getToday()
        val cachedResult = prefs.getString("fortune_$today", null)
        if (cachedResult != null) {
            resultText.text = cachedResult
            expandFortuneCard(view)
            button.isEnabled = false
        }

        fun View.applyScaleClick(action: () -> Unit) {
            this.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
                action()
            }
        }

        button.applyScaleClick {
            val userInfo = checkUserInfo()
            if (userInfo != null) {
                fetchFortune(userInfo, today, view)
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

        return view
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

    private fun fetchFortune(userInfo: Triple<String, String, String>, today: String, view: View) {
        val (nickname, mbti, birth) = userInfo
        val prompt = buildPrompt(nickname, mbti, birth)

        // 로딩 시작 - 위에서 뚝 떨어지는 애니메이션 (더 통통 튀게)
        loadingView.alpha = 0f
        loadingView.translationY = -300f
        loadingView.scaleX = 0.3f
        loadingView.scaleY = 0.3f
        loadingView.visibility = View.VISIBLE
        loadingView.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setInterpolator(android.view.animation.BounceInterpolator())
            .start()
        loadingView.playAnimation()

        fortuneCard.visibility = View.GONE
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
                    expandFortuneCard(view)
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
                    expandFortuneCard(view)
                    prefs.edit().putString("fortune_$today", fortuneResult).apply()
                    button.isEnabled = false
                }
            }
        })
    }

    private fun expandFortuneCard(view: View?) {
        if (isExpanded) return
        isExpanded = true

        val rootLayout = view?.findViewById<ConstraintLayout>(R.id.root_fortune_layout)
        val scrollView = view?.findViewById<ScrollView>(R.id.resultScrollView)
        val resultText = view?.findViewById<TextView>(R.id.fortune_result)

        // 카드뷰 보이기 (위에서 아래로)
        fortuneCard.alpha = 0f
        fortuneCard.scaleX = 0.8f
        fortuneCard.scaleY = 0.8f
        fortuneCard.translationY = -150f
        fortuneCard.visibility = View.VISIBLE

        // 카드뷰 슬라이드다운 애니메이션 - 부드럽게
        fortuneCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(900)
            .setStartDelay(100)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()

        // 높이 확장 애니메이션
        val newHeight = (resources.displayMetrics.heightPixels * 0.66).toInt()
        val currentHeight = scrollView?.height ?: 80

        android.animation.ValueAnimator.ofInt(currentHeight, newHeight).apply {
            duration = 700
            startDelay = 300
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animator ->
                val animatedValue = animator.animatedValue as Int
                scrollView?.layoutParams?.height = animatedValue
                scrollView?.requestLayout()
            }
            start()
        }

        resultText?.maxLines = Integer.MAX_VALUE
        resultText?.ellipsize = null
    }

    private fun buildPrompt(nickname: String, mbti: String, birth: String): String {
        return """
        오늘의 운세 제공 AI입니다. 아래 정보로 운세를 작성하세요.

        [사용자 정보]
        - 닉네임: $nickname
        - MBTI: $mbti  
        - 생일: $birth

        [필수 작성 규칙]
        1. 무조건 "오늘의 운세를 보여드릴게요 $nickname 님!"으로 시작
        2. 각 항목마다 점수(0-100점) 필수 표시
        3. 존댓말 사용
        4. 간결하고 명확하게 작성

        [운세 항목 - 순서대로]
        1. 총운 (점수/100)
        2. 재물운 (점수/100)  
        3. 연애운 (점수/100)
        4. 학업/직장운 (점수/100)
        5. 로또운 + 추천번호 6자리 (점수/100)

        [점수별 가이드]
        - 60점↓: 주의사항, 피할 행동
        - 61점↑: 추천 행동, 기회

        [마무리]
        - 오늘 하루 한정 운세임을 명시
        - 재미있는 한줄 추가

        즉시 운세 결과만 출력하세요. 설명 없이 바로 시작하세요.
    """.trimIndent()
    }
}