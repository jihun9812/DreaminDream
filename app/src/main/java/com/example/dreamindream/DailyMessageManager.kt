package com.example.dreamindream

import android.content.Context
import androidx.core.content.edit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object DailyMessageManager {
    private const val PREF_NAME = "daily_message"
    private const val KEY_DATE = "cached_date"
    private const val KEY_TEXT = "cached_text"

    fun getMessage(context: Context, onResult: (String) -> Unit) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_DATE, null)
        val cached = prefs.getString(KEY_TEXT, null)

        if (today == lastDate && !cached.isNullOrEmpty()) {
            onResult(cached)
            return
        }

        val prompt = "꿈 해몽 전문가 AI가 하루에 하나씩 말하는 조언 멘트. 1문장. 신비롭고 친근한 말투."

        val json = """
            {
              "model": "gpt-3.5-turbo",
              "messages": [{"role": "user", "content": "$prompt"}],
              "max_tokens": 50
            }
        """.trimIndent()

        val body = json.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("오늘의 메시지를 불러올 수 없어요 🥲")
            }

            override fun onResponse(call: Call, response: Response) {
                val res = JSONObject(response.body?.string() ?: "")
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                prefs.edit {
                    putString(KEY_DATE, today)
                    putString(KEY_TEXT, res)
                }

                onResult(res)
            }
        })
    }
}
