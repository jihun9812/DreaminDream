package com.example.dreamindream

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object DailyMessageManager {

    private const val TAG = "DailyMessageManager"
    private val db by lazy { FirebaseFirestore.getInstance() }

    // 단일 OkHttpClient (타임아웃 지정)
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private fun todayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("daily_msg_cache", Context.MODE_PRIVATE)
    private fun localKey(date: String) = "dm_$date"

    // 완전 실패 시 마지막 안전망 (문구는 strings.xml)
    private fun fallbackBank(ctx: Context) = listOf(
        ctx.getString(R.string.daily_fallback_1),
        ctx.getString(R.string.daily_fallback_2),
        ctx.getString(R.string.daily_fallback_3),
        ctx.getString(R.string.daily_fallback_4),
        ctx.getString(R.string.daily_fallback_5),
    )

    fun getMessage(context: Context, onResult: (String) -> Unit) {
        val today = todayKey()
        Log.d(TAG, "todayKey=$today tz=${TimeZone.getDefault().id}")

        val p = prefs(context)

        // 1) 로컬 즉시
        p.getString(localKey(today), null)?.let { onResult(it) }

        // 2) Firestore 조회
        db.collection("daily_messages").document(today).get()
            .addOnSuccessListener { doc ->
                val fromFs = doc.getString("message")
                if (!fromFs.isNullOrBlank()) {
                    Log.d(TAG, "FS hit")
                    p.edit().putString(localKey(today), fromFs).putString("dm_last", fromFs).apply()
                    onResult(fromFs)
                } else {
                    // 3) GPT 생성
                    fetchFromGPT(context) { gpt ->
                        if (!gpt.isNullOrBlank()) {
                            Log.d(TAG, "GPT ok (no FS doc)")
                            db.collection("daily_messages").document(today)
                                .set(mapOf("message" to gpt), SetOptions.merge())
                                .addOnSuccessListener { Log.d(TAG, "FS save ok: $today") }
                                .addOnFailureListener { e -> Log.e(TAG, "FS save fail: ${e.message}") }

                            p.edit().putString(localKey(today), gpt).putString("dm_last", gpt).apply()
                            onResult(gpt)
                        } else {
                            Log.w(TAG, "GPT failed, fallback")
                            val last = p.getString("dm_last", null)
                            onResult(last ?: fallbackBank(context).random())
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "FS fail: ${e.message}")
                // FS 실패 → GPT 시도
                fetchFromGPT(context) { gpt ->
                    if (!gpt.isNullOrBlank()) {
                        Log.d(TAG, "GPT ok (FS fail)")
                        db.collection("daily_messages").document(today)
                            .set(mapOf("message" to gpt), SetOptions.merge())
                            .addOnSuccessListener { Log.d(TAG, "FS save ok: $today") }
                            .addOnFailureListener { ex -> Log.e(TAG, "FS save fail: ${ex.message}") }

                        p.edit().putString(localKey(today), gpt).putString("dm_last", gpt).apply()
                        onResult(gpt)
                    } else {
                        Log.w(TAG, "GPT failed, fallback (FS fail)")
                        val last = p.getString("dm_last", null)
                        onResult(last ?: fallbackBank(context).random())
                    }
                }
            }
    }

    // ▼ 문자열 리소스 기반 프롬프트 (컨텍스트 필요)
    private fun fetchFromGPT(context: Context, onResult: (String?) -> Unit) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        Log.d(TAG, "OPENAI len=${apiKey.length}")

        if (apiKey.isBlank()) { onResult(null); return }

        // 날짜 포맷 템플릿도 strings.xml 로 이동
        val datePattern = context.getString(R.string.daily_date_format) // 예: "yyyy년 M월 d일"
        val dateStr = SimpleDateFormat(datePattern, Locale.getDefault()).format(Date())

        // 프롬프트 템플릿(strings.xml) 적용
        val prompt = context.getString(R.string.daily_prompt_template, dateStr)

        val body = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("max_tokens", 100)
            put("temperature", 0.4)
            put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            ))
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "GPT onFailure: ${e.message}")
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "GPT HTTP ${resp.code}")
                        onResult(null); return
                    }
                    val text = try {
                        val raw = resp.body?.string().orEmpty()
                        val content = JSONObject(raw)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                        // 따옴표/공백 제거
                        content.replace(Regex("""^[`"'“”\s]+|[`"'“”\s]+$"""), "")
                    } catch (ex: Exception) {
                        Log.e(TAG, "GPT parse err: ${ex.message}")
                        null
                    }
                    onResult(text)
                }
            }
        })
    }
}
