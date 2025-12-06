package com.dreamindream.app

import android.content.Context
import android.graphics.Color
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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    fun fetchDaily(u: UserInfo, seed: Int, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        // ★ 수정됨: "lottoNumbers"를 최상단 필드로 요청함
        val prompt = """
            Role: Expert Fortune Teller.
            User: ${u.nickname}, ${u.birth}, ${u.gender}, MBTI: ${u.mbti}.
            Date: ${storage.todayKey()}. Seed: $seed.
            
            Generate JSON.
            1. "radar": Scores (0-100) for "love", "money", "work", "health", "social".
            2. "summary": One mystical sentence summary.
            3. "lucky": { "colorHex": "#RRGGBB", "number": (1-99), "time": "ex) 2 PM", "direction": "ex) East" }
            4. "checklist": 2 specific actionable tasks.
            5. "sections": 
               - "love", "money", "work", "health", "social": Each needs { "score": int, "text": "Specific advice (1-2 sentences)" }.
            6. "lottoNumbers": [Generate 6 unique integers (1-45)].
            
            Output JSON only. Language: ${context.getString(R.string.api_lang_code)}.
        """.trimIndent()

        postRequest(prompt, onSuccess, onError)
    }

    fun fetchDeep(u: UserInfo, daily: JSONObject, seed: Int, onSuccess: (JSONObject?) -> Unit) {
        val prompt = """
            Based on daily fortune: $daily
            Perform DEEP Analysis.
            
            Required JSON:
            1. "flow_curve": Array of 7 integers (0-100) for [6AM, 9AM, 12PM, 3PM, 6PM, 9PM, 12AM].
            2. "highlights": 3 bullet points of hidden insights.
            3. "risk_opp": "Risk vs Opportunity analysis".
            4. "solution": "Concrete behavioral solution".
            5. "tomorrow_preview": "Hint for tomorrow".
            
            Output JSON only. Language: ${context.getString(R.string.api_lang_code)}. Tone: Premium.
        """.trimIndent()

        postRequest(prompt, { onSuccess(it) }, { onSuccess(null) })
    }

    private fun postRequest(prompt: String, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("response_format", JSONObject().put("type", "json_object"))
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

    fun scoreColor(score: Int): Int = when {
        score >= 80 -> Color.parseColor("#FFD700")
        score >= 60 -> Color.parseColor("#4FC3F7")
        else -> Color.parseColor("#EF5350")
    }
}