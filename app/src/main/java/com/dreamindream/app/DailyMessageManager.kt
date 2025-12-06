package com.dreamindream.app

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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

    // [수정 1] 날짜 + 언어 코드를 조합한 키 생성 (예: 20240520_ko, 20240520_en)
    private fun todayLangKey(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val langCode = Locale.getDefault().language // ko, en, hi, ar, zh 등
        return "${dateStr}_${langCode}"
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("daily_msg_cache", Context.MODE_PRIVATE)

    // [수정 2] 로컬 캐시 키도 언어별로 분리
    private fun localKey(keyWithLang: String) = "dm_$keyWithLang"

    // 완전 실패 시 마지막 안전망 (문구는 strings.xml)
    private fun fallbackBank(ctx: Context) = listOf(
        ctx.getString(R.string.daily_fallback_1),
        ctx.getString(R.string.daily_fallback_2),
        ctx.getString(R.string.daily_fallback_3),
        ctx.getString(R.string.daily_fallback_4),
        ctx.getString(R.string.daily_fallback_5),
    )

    fun getMessage(context: Context, onResult: (String) -> Unit) {
        val keyWithLang = todayLangKey() // 오늘날짜_언어코드
        Log.d(TAG, "Key: $keyWithLang / TZ: ${TimeZone.getDefault().id}")

        val p = prefs(context)

        // 1) 로컬 캐시 즉시 확인 (언어별 키로 조회)
        p.getString(localKey(keyWithLang), null)?.let {
            onResult(it)
        }

        // 2) Firestore 조회
        db.collection("daily_messages").document(keyWithLang).get()
            .addOnSuccessListener { doc ->
                val fromFs = doc.getString("message")
                if (!fromFs.isNullOrBlank()) {
                    Log.d(TAG, "FS hit for $keyWithLang")
                    // 로컬에 저장 및 결과 반환
                    p.edit()
                        .putString(localKey(keyWithLang), fromFs)
                        .putString("dm_last", fromFs) // 최후의 수단용(언어 무관하게 가장 최근 것)
                        .apply()
                    onResult(fromFs)
                } else {
                    // 3) GPT 생성 (문서가 없으므로)
                    fetchFromGPT(context) { gpt ->
                        if (!gpt.isNullOrBlank()) {
                            Log.d(TAG, "GPT ok (no FS doc) for $keyWithLang")

                            // Firestore에 저장 (언어별 문서 ID 사용)
                            db.collection("daily_messages").document(keyWithLang)
                                .set(mapOf("message" to gpt, "lang" to Locale.getDefault().language), SetOptions.merge())
                                .addOnSuccessListener { Log.d(TAG, "FS save ok: $keyWithLang") }
                                .addOnFailureListener { e -> Log.e(TAG, "FS save fail: ${e.message}") }

                            p.edit()
                                .putString(localKey(keyWithLang), gpt)
                                .putString("dm_last", gpt)
                                .apply()
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
                        Log.d(TAG, "GPT ok (FS fail) for $keyWithLang")
                        // 저장은 시도하되 실패해도 진행
                        db.collection("daily_messages").document(keyWithLang)
                            .set(mapOf("message" to gpt, "lang" to Locale.getDefault().language), SetOptions.merge())

                        p.edit()
                            .putString(localKey(keyWithLang), gpt)
                            .putString("dm_last", gpt)
                            .apply()
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
        if (apiKey.isBlank()) { onResult(null); return }

        // [중요] 날짜 포맷도 현재 언어 설정(Locale)을 따릅니다.
        val datePattern = context.getString(R.string.daily_date_format) // 예: "yyyy년 M월 d일" or "MMM d, yyyy"
        val dateStr = SimpleDateFormat(datePattern, Locale.getDefault()).format(Date())

        // [중요] strings.xml에 있는 템플릿을 사용하므로,
        // 폰 설정이 한국어면 values-ko/strings.xml, 힌디어면 values-hi/strings.xml의 프롬프트를 가져옴
        // 따라서 GPT는 해당 언어로 응답하게 됩니다.
        val prompt = context.getString(R.string.daily_prompt_template, dateStr)

        val body = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("max_tokens", 120) // 언어별 길이 차이를 고려해 약간 여유
            put("temperature", 0.6) // 약간의 창의성 부여
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
                        // 따옴표/공백 제거 및 클린업
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