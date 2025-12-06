package com.dreamindream.app

import android.content.Context
import com.dreamindream.app.FortuneStorage.UserInfo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FortuneApi(
    private val context: Context,
    private val storage: FortuneStorage
) {
    // 긴 응답을 기다리기 위해 타임아웃을 120초로 늘립니다.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    // --- 1. 기본 운세 요청 (Basic Fortune) ---
    fun fetchDaily(u: UserInfo, seed: Int, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val langCode = context.getString(R.string.api_lang_code) // e.g., "Korean"

        val prompt = """
            Act as a mystical fortune teller. Target: TODAY's fortune.
            User: ${u.nickname}, Age: ${storage.ageOf(u.birth)}, Gender: ${u.gender}. Seed: $seed.
            
            Generate JSON:
            {
              "radar": { "love": 0-100, "money": 0-100, "work": 0-100, "health": 0-100, "social": 0-100 },
              "basic_info": {
                "overall": "Summarize the day in 2 sentences.",
                "money_text": "Short financial advice.",
                "love_text": "Short relationship advice.",
                "health_text": "Short health tip.",
                "action_tip": "One concrete action."
              },
              "missions": ["Mission 1 (Easy)", "Mission 2 (Fun)"]
            }
            Language: $langCode.
        """.trimIndent()

        postRequest(prompt, onSuccess, onError)
    }

    // --- 2. 심화 분석 요청 (Deep Analysis) ---
    fun fetchDeep(
        u: UserInfo,
        daily: JSONObject,
        seed: Int,
        onSuccess: (JSONObject?) -> Unit
    ) {
        val langCode = context.getString(R.string.api_lang_code)
        val radar = daily.optJSONObject("radar")?.toString() ?: "{}"

        // [핵심 수정] 그래프 현실화, 글자수 제한, 색상 이름 강제
        val prompt = """
        You are a 'Grand Master Destiny Counselor'. Provide a premium, deeply personal analysis based on radar scores: $radar.
        Target: Adults. Tone: Warm, Professional, Insightful, Mystical but Practical.
        
        CRITICAL INSTRUCTIONS:
        1. **LENGTH & DEPTH**: Do NOT be brief. Each analysis section (money, love, etc.) MUST be at least 300 characters long.
        2. **STRUCTURE**: For each analysis field, use a 3-part structure: 
           - Part 1: Current Energy Reading (Analysis of the present)
           - Part 2: Specific Advice/Solution (Actionable steps)
           - Part 3: Predicted Outcome (Future outlook if advice is followed)
        3. **FLOW CURVE (IMPORTANT)**: Provide exactly 7 integer values (0-100) representing user's energy at [6AM, 9AM, 12PM, 3PM, 6PM, 9PM, 11PM]. 
           - **DO NOT make it a simple straight line.** - Reflect realistic daily energy fluctuations (e.g., lower after lunch, higher in evening, etc).
        4. **FORMATTING**: Use double line breaks (\n\n) to separate paragraphs clearly.
        5. **LUCKY COLOR**: Output the COLOR NAME in $langCode (e.g., 'Dark Navy', 'Golden Yellow'). **DO NOT output Hex codes.**
        6. **EMOTIONAL ANALYSIS**: This is MANDATORY. Analyze the user's subconscious stress and mindset in detail.

        Output JSON Structure:
        {
          "flow_curve": [int, int, int, int, int, int, int], // 6AM, 9AM, 12PM, 3PM, 6PM, 9PM, 11PM
          
          "summary_data": {
             "keywords": "Two distinct keywords related to today's destiny",
             "lucky_color": "Color Name in $langCode (NO HEX CODE)",
             "lucky_item": "Specific object",
             "lucky_time": "Best time",
             "lucky_direction": "Cardinal direction"
          },
          
          "pros_cons": {
             "positive": "One key strength/opportunity today.",
             "negative": "One key weakness/risk to watch."
          },

          "overall_verdict": "Comprehensive summary (Min 4 sentences). Synthesize all luck factors.",
          
          "money_analysis": "Detailed Financial Insight. (Min 300 chars, 3 paragraphs)",
          "love_analysis": "Detailed Relationship Insight. (Min 300 chars, 3 paragraphs)",
          "health_analysis": "Detailed Physical Condition. (Min 300 chars, 3 paragraphs)",
          "career_analysis": "Detailed Work/Study Insight. (Min 300 chars, 3 paragraphs)",
          
          "emotional_analysis": "Detailed Psychological/Mindset Analysis. (Min 300 chars, 3 paragraphs)",
          
          "risk_warning": "Specific risks and how to avoid them.",
          "action_guide": "Morning, Afternoon, Evening specific actions timeline."
        }

        Language: $langCode.
        """.trimIndent()

        postRequest(prompt, { onSuccess(it) }, { onSuccess(null) })
    }

    private fun postRequest(prompt: String, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("response_format", JSONObject().put("type", "json_object"))
            put("temperature", 0.7)
        }

        val req = Request.Builder()
            .url(API_URL)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                onError(context.getString(R.string.network_error))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val respBody = response.body?.string() ?: throw Exception("Empty Body")
                    val content = JSONObject(respBody).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content")
                    onSuccess(JSONObject(content))
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError(context.getString(R.string.parse_error))
                }
            }
        })
    }
}