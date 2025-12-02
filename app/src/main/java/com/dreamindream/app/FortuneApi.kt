package com.dreamindream.app

import android.content.Context
import android.util.Log
import com.dreamindream.app.FortuneStorage.UserInfo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FortuneApi(
    private val context: Context,
    private val storage: FortuneStorage
) {
    private val client = OkHttpClient()

    companion object {
        private const val TAG = "FortuneApi"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    // 오늘 날짜 키 (예: 2025-12-01)
    private fun todayKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    // =========================
    //  Daily Fortune
    // =========================
    fun fetchDaily(
        u: UserInfo,
        seed: Int,
        onSuccess: (JSONObject) -> Unit,
        onError: (String, Triple<Int, Int, Int>) -> Unit
    ) {
        val body = buildDailyRequest(u, seed)
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "fetchDaily onFailure", e)
                val msg = mapErrorToUserMessage(e.message ?: "network")
                // 기본 감정 프리셋 (긍정/중립/부정)
                onError(msg, Triple(60, 25, 15))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val msg = mapHttpError(response.code)
                        onError(msg, Triple(60, 25, 15))
                        return
                    }
                    val bodyStr = response.body?.string() ?: run {
                        onError(
                            context.getString(R.string.err_network_generic, "empty"),
                            Triple(60, 25, 15)
                        )
                        return
                    }
                    try {
                        val root = JSONObject(bodyStr)
                        val choices = root.optJSONArray("choices")
                        if (choices == null || choices.length() == 0) {
                            onError(
                                context.getString(R.string.err_network_generic, "choices"),
                                Triple(60, 25, 15)
                            )
                            return
                        }
                        val msgObj = choices.getJSONObject(0)
                            .getJSONObject("message")
                        val content = msgObj.optString("content")
                        val json = JSONObject(content)
                        onSuccess(json)
                    } catch (t: Throwable) {
                        Log.e(TAG, "fetchDaily parse error", t)
                        onError(
                            context.getString(R.string.err_network_generic, "parse"),
                            Triple(60, 25, 15)
                        )
                    }
                }
            }
        })
    }

    private fun buildDailyRequest(u: UserInfo, seed: Int): JSONObject {
        val sys = context.getString(R.string.fortune_system_daily)
        val prompt = context.getString(
            R.string.fortune_daily_prompt_template,
            u.nickname ?: "",
            u.mbti ?: "",
            u.birth ?: "",
            u.gender ?: "",
            u.birthTime ?: "",
            todayKey(),
            seed.toString()
        )

        return JSONObject().apply {
            put("model", "gpt-4.1-mini")
            put("temperature", 0.7)
            put("max_tokens", 2000)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sys))
                put(JSONObject().put("role", "user").put("content", prompt))
            })
        }
    }

    // =========================
    //  Deep Fortune (심화 분석)
    // =========================
    fun fetchDeep(
        u: UserInfo,
        daily: JSONObject,
        seed: Int,
        cb: (JSONObject?) -> Unit
    ) {
        val sys = context.getString(R.string.fortune_system_deep)
        val prompt = buildDeepPrompt(u, daily, seed)

        val body = JSONObject().apply {
            put("model", "gpt-4.1-mini")
            put("temperature", 0.5)
            put("max_tokens", 2000)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sys))
                put(JSONObject().put("role", "user").put("content", prompt))
            })
        }

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "fetchDeep onFailure", e)
                cb(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "fetchDeep http ${response.code}")
                        cb(null)
                        return
                    }
                    val bodyStr = response.body?.string() ?: run {
                        cb(null)
                        return
                    }
                    cb(
                        try {
                            val root = JSONObject(bodyStr)
                            val choices = root.optJSONArray("choices")
                            if (choices == null || choices.length() == 0) null
                            else {
                                val msgObj = choices.getJSONObject(0)
                                    .getJSONObject("message")
                                val content = msgObj.optString("content")
                                JSONObject(content)
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "fetchDeep parse error", t)
                            null
                        }
                    )
                }
            }
        })
    }

    private fun buildDeepPrompt(
        u: UserInfo,
        daily: JSONObject,
        seed: Int
    ): String {

        val tone = "calm" // 또는 너희 프로젝트의 tone 생성 방식 사용(원하면 styleTokens 넣어줄게)

        return context.getString(
            R.string.fortune_deep_prompt_template,
            u.nickname ?: "",     // %1$s
            u.mbti ?: "",         // %2$s
            u.birth ?: "",        // %3$s
            u.gender ?: "",       // %4$s
            u.birthTime ?: "",    // %5$s
            todayKey(),           // %6$s
            tone,                 // %7$s  ← ★ 반드시 추가!
            seed.toString(),      // %8$s
            daily.toString()      // %9$s
        )
    }


    // =========================
    //  Lucky time helpers
    // =========================

    fun humanizeLuckyTime(raw: String): String {
        // 일단은 원래 문자열 그대로 쓰고,
        // 필요하면 나중에 "09:00~12:00" -> "오전 9시 ~ 12시" 같은 변환 추가하면 됨.
        return raw.trim()
    }

    fun pickLuckyTimeFallback(): String {
        // strings.xml 에 적당한 기본 문구가 있다면 그걸 쓰고,
        // 없으면 그냥 "오전 11시" 고정
        return try {
            context.getString(R.string.fortune_lucky_time_fallback)
        } catch (_: Throwable) {
            "오전 11시"
        }
    }

    // =========================
    //  Error helpers
    // =========================

    private fun mapErrorToUserMessage(reason: String): String = when {
        reason.contains("401") -> context.getString(R.string.err_auth, reason)
        reason.contains("403") -> context.getString(R.string.err_forbidden, reason)
        reason.contains("404") -> context.getString(R.string.err_not_found, reason)
        reason.contains("429") -> context.getString(R.string.err_rate_limit, reason)
        reason.contains("timeout", ignoreCase = true) ->
            context.getString(R.string.err_timeout, reason)

        else -> context.getString(R.string.err_network_generic, reason)
    }

    private fun mapHttpError(code: Int): String = mapErrorToUserMessage("http $code")
}
