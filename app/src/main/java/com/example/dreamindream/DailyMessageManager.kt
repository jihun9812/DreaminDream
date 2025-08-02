package com.example.dreamindream

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object DailyMessageManager {

    private val db = FirebaseFirestore.getInstance()

    fun getMessage(context: Context, onResult: (String) -> Unit) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        // Firestore에서 오늘 날짜 메시지 확인
        db.collection("daily_messages").document(today).get()
            .addOnSuccessListener { doc ->
                val cached = doc.getString("message")
                if (!cached.isNullOrEmpty()) {
                    onResult(cached) // ✅ 있으면 그걸 보여줌 (모든 사용자 공통)
                } else {
                    // ✅ 없으면 GPT 호출 후 Firestore에 저장
                    fetchFromGPT { message ->
                        db.collection("daily_messages").document(today)
                            .set(mapOf("message" to message))
                        onResult(message)
                    }
                }
            }
            .addOnFailureListener {
                onResult("오늘의 메시지를 불러올 수 없어요...")
            }
    }

    private fun fetchFromGPT(onResult: (String) -> Unit) {
        val prompt = "설명과 서사없이 전문 꿈해몽가답게 오늘의 조언멘트를 짧게 적어줘 한문장 존댓말로.."
        val json = """
            {
              "model": "gpt-3.5-turbo",
              "messages": [{"role": "user", "content": "$prompt"}],
              "max_tokens": 85
            }
        """
            .trimIndent()

        val body = json.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("GPT 호출 실패... 메시지를 가져올 수 없어요.")
            }

            override fun onResponse(call: Call, response: Response) {
                val res = JSONObject(response.body?.string() ?: "")
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                onResult(res)
            }
        })
    }
}
