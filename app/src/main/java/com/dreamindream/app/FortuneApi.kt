// app/src/main/java/com/example/dreamindream/FortuneApi.kt
package com.dreamindream.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale


class FortuneApi(
    private val context: Context,
    private val storage: FortuneStorage
) {

    private val http by lazy { OkHttpClient() }
    private val TAG = "FortuneApi"

    private val BANNED_LOTTO = setOf(5, 12, 19, 23, 34, 41)

    private fun langCode(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (!appLocales.isEmpty) {
            appLocales[0]!!.language
        } else {
            context.resources.configuration.locales[0].language
        }
    }
    private fun isKo(): Boolean = langCode().startsWith("ko", ignoreCase = true)

    fun fetchDaily(
        u: FortuneStorage.UserInfo,
        seed: Int,
        onSuccess: (JSONObject) -> Unit,
        onError: (String, Triple<Int, Int, Int>) -> Unit
    ) {
        val body = buildDailyRequest(u, seed)

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()

        val t0 = System.currentTimeMillis()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                val dt = System.currentTimeMillis() - t0
                Log.e(TAG, "❌ Daily onFailure (${dt}ms): ${e.message}", e)
                val seedPreset = seededEmotions(seed)
                mainThread { onError(mapErrorToUserMessage(e.message ?: "io"), seedPreset) }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string().orEmpty()
                val dt = System.currentTimeMillis() - t0
                Log.d(TAG, "📩 Daily onResponse (${dt}ms) code=${response.code}, len=${raw.length}")
                if (!response.isSuccessful) {
                    val msg = mapHttpError(response.code)
                    val seedPreset = seededEmotions(seed)
                    Log.w(TAG, "⚠️ HTTP ${response.code} body=\n${raw.take(400)}")
                    mainThread { onError(msg, seedPreset) }
                    return
                }
                try {
                    val payload = parseDailyResponse(raw, seed)
                    val adjusted = finalizePayload(payload, seed).apply {
                        val cleaned = sanitizeChecklist((0 until (optJSONArray("checklist")?.length() ?: 0))
                            .mapNotNull { optJSONArray("checklist")?.optString(it) })
                        put("checklist", JSONArray().apply { cleaned.forEach { put(it) } })
                    }
                    mainThread { onSuccess(adjusted) }
                } catch (e: Exception) {
                    Log.w(TAG, "🟨 Daily parse fallback: ${e.message}")
                    val fallback = buildFallbackFromText(raw, seed)
                    mainThread { onSuccess(fallback) }
                }
            }
        })
    }

    fun fetchDeep(
        u: FortuneStorage.UserInfo,
        daily: JSONObject,
        seed: Int,
        cb: (JSONObject?) -> Unit
    ) {
        val body = JSONObject().apply {
            put("model", "gpt-4.1-mini")
            put("temperature", 0.7)
            put("max_tokens", 2200)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", context.getString(R.string.fortune_system_deep)))
                put(JSONObject().put("role", "user").put("content", buildDeepPrompt(u, daily, seed)))
            })
            put("tools", JSONArray().put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "deep_fortune_analysis")
                    put("description", "오늘 운세 기반 심화 분석(전문가 톤)")
                    put("parameters", deepSchema())
                })
            }))
            put("tool_choice", JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().put("name", "deep_fortune_analysis"))
            })
        }

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "❌ Deep onFailure: ${e.message}", e)
                mainThread { cb(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "⚠️ Deep http=${response.code} body=${raw.take(400)}")
                    mainThread { cb(null) }
                    return
                }
                try {
                    val root = JSONObject(raw)
                    val msg = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    val tc = msg.optJSONArray("tool_calls")
                    val args = if (tc != null && tc.length() > 0) {
                        tc.getJSONObject(0).getJSONObject("function").getString("arguments")
                    } else {
                        msg.getJSONObject("function_call").getString("arguments")
                    }
                    mainThread { cb(JSONObject(args)) }
                } catch (_: Exception) {
                    mainThread { cb(null) }
                }
            }
        })
    }

    fun scoreColor(score: Int): Int = when {
        score >= 70 -> Color.parseColor("#17D7A0")
        score >= 40 -> Color.parseColor("#FFC107")
        else -> Color.parseColor("#FF5252")
    }

    fun formatSections(obj: JSONObject): CharSequence {
        val s = context
        val sb = StringBuilder()
        val sections = obj.optJSONObject("sections") ?: JSONObject()
        val lottoNums = obj.optJSONArray("lottoNumbers")

        val labels = mapOf(
            "overall" to s.getString(R.string.section_overall),
            "love"    to s.getString(R.string.section_love),
            "study"   to s.getString(R.string.section_study),
            "work"    to s.getString(R.string.section_work),
            "money"   to s.getString(R.string.section_money),
            "lotto"   to s.getString(R.string.section_lotto)
        )

        fun line(key: String) {
            val label = labels[key] ?: key
            val sec = sections.optJSONObject(key) ?: JSONObject()
            val score = sec.optInt("score", -1)
            val text  = cleanse(sec.optString("text").ifBlank { sec.optString("advice") })

            val header = if (score >= 0)
                s.getString(R.string.section_header_with_score, label, score)
            else label
            sb.append(header)

            if (key == "lotto") {
                if (lottoNums != null && lottoNums.length() == 6) {
                    val arr = (0 until 6).map { lottoNums.optInt(it) }.sorted().joinToString(", ")
                    sb.append("  ").append(s.getString(R.string.label_lotto_numbers, arr)).append('\n')
                } else {
                    sb.append("  ").append(s.getString(R.string.label_lotto_numbers_dash)).append('\n')
                }
                return
            }
            if (text.isNotBlank()) sb.append(" - ").append(text.trim())
            sb.append('\n')
        }

        listOf("overall","love","study","work","money","lotto").forEach { line(it) }
        return sb.toString()
    }

    fun buildSectionDetails(title: String, score: Int, text: String?, advice: String?): String {
        val s = context
        val base = cleanse(text?.trim().orEmpty().ifBlank { s.getString(R.string.daily_summary_base) })
        val tip  = cleanse(advice?.trim().orEmpty().let { if (it.isNotBlank()) s.getString(R.string.bullet_with_text, it) else "" })

        fun moodByScore(): String = when {
            score >= 85 -> s.getString(R.string.mood_high)
            score >= 70 -> s.getString(R.string.mood_good)
            score >= 55 -> s.getString(R.string.mood_fair)
            else        -> s.getString(R.string.mood_low)
        }

        fun textBy(title: String): Pair<String,String> {
            val m = moodByScore()
            return when (title) {
                s.getString(R.string.section_love)    -> s.getString(R.string.sec_love_text, m)    to s.getString(R.string.sec_love_advice)
                s.getString(R.string.section_study)   -> s.getString(R.string.sec_study_text, m)   to s.getString(R.string.sec_study_advice)
                s.getString(R.string.section_work)    -> s.getString(R.string.sec_work_text, m)    to s.getString(R.string.sec_work_advice)
                s.getString(R.string.section_money)   -> s.getString(R.string.sec_money_text, m)   to s.getString(R.string.sec_money_advice)
                s.getString(R.string.section_overall) -> s.getString(R.string.sec_overall_text, m) to s.getString(R.string.sec_overall_advice)
                s.getString(R.string.section_lotto)   -> "" to s.getString(R.string.sec_lotto_tip)
                else -> "" to s.getString(R.string.default_generic_advice)
            }
        }

        val (autoText, autoAdvice) = textBy(title)
        val outText   = base.ifBlank { autoText }
        val outAdvice = tip.ifBlank { s.getString(R.string.bullet_with_text, autoAdvice) }
        return listOf(outText, outAdvice).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun sanitizeChecklist(items: List<String>): List<String> {
        val out = items.map { neutralizeChecklistText(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        return if (out.size == 3) out else out + buildEssentialChecklist().drop(out.size).take(3 - out.size)
    }

    private fun neutralizeChecklistText(src: String): String {
        val s = context
        var t = src.trim()
        t = neutralizeCorporateTerms(t)
            .replace(Regex("숙제|과제|수업|강의|시험|퀴즈|레포트|제출"), s.getString(R.string.replace_report))
        if (Regex("연락|전화|메시지|문자|DM|카톡|카카오").containsMatchIn(t)) t = s.getString(R.string.contact_sanitized)
        t = stripTimePhrases(t)
            .replace(Regex("^•\\s*"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (t.length > 30) t = t.take(30)
        if (t.length < 3) t = s.getString(R.string.essential_check_1)
        t = t.replace(Regex("할 ?일.*(마무리|끝내기)"), s.getString(R.string.essential_check_1))
        return cleanse(t)
    }

    private fun buildEssentialChecklist(): List<String> =
        listOf(
            context.getString(R.string.essential_check_1),
            context.getString(R.string.essential_check_2),
            context.getString(R.string.essential_check_3)
        )

    // 새 함수 추가: 길이 제한 없는 불릿 정리
    private fun cleanBulletLineLong(src: String): String =
        cleanse(
            neutralizeCorporateTerms(
                stripTimePhrases(src)
                    .replace(Regex("^•\\s*"), "")
                    .replace(Regex("\\s{2,}"), " ")
                    .trim()
            )
        )

    fun humanizeLuckyTime(raw: String?): String {
        val s = context
        if (raw.isNullOrBlank()) return ""
        var t = raw.trim()
        val am = s.getString(R.string.label_am)   // "AM" (en) / "오전" (ko)
        val pm = s.getString(R.string.label_pm)   // "PM" / "오후"
        val h  = s.getString(R.string.label_hour_suffix) // "h" / "시"

        // (A) 한글 포맷: "오전/오후 01시(~02시)"
        Regex("(오전|오후)\\s*(\\d{1,2})시(?:\\s*~\\s*(오전|오후)?\\s*(\\d{1,2})시)?")
            .find(t)?.let { m ->
                fun seg(koAmpm: String?, hourStr: String): String {
                    val ampm = when (koAmpm) { "오전" -> am; "오후" -> pm; else -> "" }
                    val hour = hourStr.toInt().let { if (it == 0 || it == 12) 12 else it % 12 }
                    return (if (ampm.isNotBlank()) "$ampm " else "") + "$hour$h"
                }
                val first = seg(m.groupValues[1], m.groupValues[2])
                val second = m.groupValues.getOrNull(4)?.takeIf { it.isNotEmpty() }?.let {
                    seg(m.groupValues.getOrNull(3).takeIf { !it.isNullOrEmpty() }, it)
                }
                return if (second != null) "$first~$second" else first
            }

        // (B) 24h 포맷: "13:00~15:00"
        t = t.replace(Regex("\\(\\s*~\\s*"), "~").replace(")", "")
        Regex("(\\d{1,2}):(\\d{2})~(\\d{1,2}):(\\d{2})").find(t)?.let { m ->
            fun h12(hh: String): String {
                val H = hh.toInt()
                val ampm = if (H in 0..11) am else pm
                val hour = when { H == 0 -> 12; H <= 12 -> H; else -> H - 12 }
                return "$ampm $hour$h"
            }
            val st = h12(m.groupValues[1]); val en = h12(m.groupValues[3])
            return if (st == en) st else "$st~$en"
        }

        // 이미 AM/PM 들어있으면 그대로
        if (Regex("${am}|${pm}").containsMatchIn(t)) return t
        return t
    }


    fun pickLuckyTimeFallback(): String {
        val am = context.getString(R.string.label_am)
        val pm = context.getString(R.string.label_pm)
        val h  = context.getString(R.string.label_hour_suffix)
        val hours = (6..22).map { if (it < 12) "$am ${it}$h" else "$pm ${if (it == 12) 12 else it - 12}$h" }
        return hours.random()
    }

    private fun buildDailyRequest(u: FortuneStorage.UserInfo, seed: Int): JSONObject {
        return JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.7)
            put("max_tokens", 2200)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", context.getString(R.string.fortune_system_daily)))
                put(JSONObject().put("role", "user").put("content", buildUserPrompt(u, seed)))
            })
            put("tools", JSONArray().put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", "daily_fortune")
                    put("description", "사용자 맞춤 하루 운세 JSON 반환")
                    put("parameters", fortuneSchema())
                })
            }))
            put("tool_choice", JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().put("name", "daily_fortune"))
            })
        }
    }

    private fun parseDailyResponse(raw: String, seed: Int): JSONObject {
        val root = JSONObject(raw)
        val msg = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val tc = msg.optJSONArray("tool_calls")
        return if (tc != null && tc.length() > 0) {
            val args = tc.getJSONObject(0).getJSONObject("function").getString("arguments")
            validateAndFill(JSONObject(args), seed)
        } else {
            val fc = msg.optJSONObject("function_call")
            if (fc != null) {
                val args = fc.optString("arguments", "{}")
                validateAndFill(JSONObject(args), seed)
            } else {
                parsePayloadAlways(msg.optString("content"), seed)
            }
        }
    }

    private fun parsePayloadAlways(content: String, seed: Int): JSONObject {
        val txt = content.trim()
        runCatching { return validateAndFill(JSONObject(txt), seed) }
        Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```").find(txt)?.let {
            runCatching { return validateAndFill(JSONObject(it.groupValues[1]), seed) }
        }
        extractJsonObject(txt)?.let { raw -> runCatching { return validateAndFill(JSONObject(raw), seed) } }
        return buildFallbackFromText(txt, seed)
    }

    private fun extractJsonObject(text: String): String? {
        var d = 0; var s = -1
        for (i in text.indices) when (text[i]) {
            '{' -> { if (d == 0) s = i; d++ }
            '}' -> { d--; if (d == 0 && s >= 0) return text.substring(s, i + 1) }
        }
        return null
    }

    private fun finalizePayload(payload: JSONObject, seed: Int): JSONObject {
        payload.optJSONObject("lucky")?.let {
            val c = it.optString("colorHex")
            val t = it.optString("time")
            val n = it.optInt("number", -1)
            if (c.isNotBlank()) storage.pushHistory("lucky_history_colors", c)
            if (t.isNotBlank()) storage.pushHistory("lucky_history_times", t)
            if (n in 10..99) storage.pushHistory("lucky_history_numbers", n.toString())
        }
        return payload
    }

    private fun buildFallbackFromText(txt: String, seed: Int): JSONObject {
        val (p, n, ng) = seededEmotions(seed)
        val base = JSONObject().apply {
            put("keywords", JSONArray())
            put("lucky", JSONObject().apply {
                put("colorHex", pickLuckyColorDeterministic(seed, storage.getRecentLuckyColors(5)))
                put("number", pickLuckyNumberDiversified(seed, storage.getRecentLuckyNumbers(10)))
                put("time", pickLuckyTimeFallback())
            })
            put("emotions", JSONObject().apply { put("positive", p); put("neutral", n); put("negative", ng) })
            put("sections", JSONObject())
            put("checklist", JSONArray(buildEssentialChecklist()))
            put("lottoNumbers", sanitizeLotto(null, seed))
        }
        base.put("tomorrow", JSONObject().put("long", makeTomorrowPlan(base)))
        return validateAndFill(base, seed)
    }

    private fun fortuneSchema(): JSONObject {
        val obj = JSONObject()
        obj.put("type", "object")
        obj.put("required", JSONArray().apply { put("lucky"); put("sections"); put("keywords"); put("emotions"); put("checklist"); put("tomorrow") })
        obj.put("properties", JSONObject().apply {
            put("lucky", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().apply { put("colorHex"); put("number"); put("time") })
                put("properties", JSONObject().apply {
                    put("colorHex", JSONObject().put("type", "string").put("pattern", "#[0-9A-Fa-f]{6}"))
                    put("number", JSONObject().put("type", "integer").put("minimum", 10).put("maximum", 99))
                    put("time", JSONObject().put("type", "string"))
                })
            })
            put("sections", JSONObject().apply {
                put("type", "object")
                put("required", JSONArray().apply { put("overall"); put("love"); put("study"); put("work"); put("money"); put("lotto") })
                fun sec() = JSONObject().apply {
                    put("type", "object")
                    put("required", JSONArray().apply { put("score"); put("text"); put("advice") })
                    put("properties", JSONObject().apply {
                        put("score", JSONObject().put("type", "integer").put("minimum", 40).put("maximum", 100))
                        put("text", JSONObject().put("type", "string"))
                        put("advice", JSONObject().put("type", "string"))
                    })
                }
                put("overall", sec()); put("love", sec()); put("study", sec()); put("work", sec()); put("money", sec()); put("lotto", sec())
            })
            put("keywords", JSONObject().apply { put("type", "array"); put("items", JSONObject().put("type", "string")); put("minItems", 1); put("maxItems", 4) })
            put("emotions", JSONObject().apply {
                put("type", "object"); put("description", "Sum to 100 (positive+neutral+negative=100). Keep daily variety; avoid extremes."); put("required", JSONArray().apply { put("positive"); put("neutral"); put("negative") })
                put("properties", JSONObject().apply {
                    put("positive", JSONObject().put("type", "integer").put("minimum", 20).put("maximum", 90))
                    put("neutral", JSONObject().put("type", "integer").put("minimum", 10).put("maximum", 50))
                    put("negative", JSONObject().put("type", "integer").put("minimum", 5).put("maximum", 35))
                })
            })
            put("lottoNumbers", JSONObject().apply {
                put("type", "array"); put("items", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 45)); put("minItems", 6); put("maxItems", 6)
            })
            put("checklist", JSONObject().apply { put("type", "array"); put("items", JSONObject().put("type", "string")); put("minItems", 3); put("maxItems", 3) })
            put("tomorrow", JSONObject().apply {
                put("type", "object"); put("required", JSONArray().apply { put("long") })
                put("properties", JSONObject().apply { put("long", JSONObject().put("type", "string")) })
            })
        })
        return obj
    }

    private fun buildUserPrompt(u: FortuneStorage.UserInfo, seed: Int): String {
        val today = storage.todayKey()
        val weekday = java.text.SimpleDateFormat("EEEE", java.util.Locale.KOREAN).format(java.util.Date())
        val userAge = storage.ageOf(u.birth)
        val tag = ageTag(userAge)
        val avoidColors = org.json.JSONArray(storage.getRecentLuckyColors())
        val avoidTimes = org.json.JSONArray(storage.getRecentLuckyTimes())
        val avoidNumbers = org.json.JSONArray(storage.getRecentLuckyNumbers())
        val palette = org.json.JSONArray(luckyPalette)
        val tone = styleTokens(seed)

        // ⬇️ 추가
        val answerLang = if (context.resources.configuration.locales[0].language.startsWith("ko")) "Korean" else "English"

        return """
[사용자]
nickname:"${u.nickname}", mbti:"${u.mbti}", birthdate:"${u.birth}", birth_time:"${u.birthTime}", gender:"${u.gender}"
date:"$today ($weekday)", age:$userAge, age_tag:$tag, seed:$seed, tone:"$tone"

[출력 가이드(엄격)]
- 금지어: ‘리듬’ 금지.
- 전문어 금지.
- 학생/학교 어휘 금지(숙제/과제/수업/강의/시험/퀴즈/레포트/제출).
- 연락 지시 금지(전화/메시지/DM/카톡/연락 등).
- checklist: 개인지칭·시간/마감 표현 금지, 오늘 바로 가능한 3개(12~18자), 명령조 지양(제안/권장 톤).
- 섹션 score 40~100. 각 섹션 2~3문장(80~120자), 실용 팁 1개. tone="$tone".
- lucky.colorHex는 palette에서, 최근 5일 중복 회피(avoidColors/avoidTimes/avoidNumbers).
- lucky.number 10~99, lucky.time은 현지 언어의 AM/PM 시간 표현(예: en → "AM 9h", ko → "오전 9시").
- emotions 현실적 분포, lottoNumbers 6개(1~45).
- “리뷰”, “25분 집중 2회”와 유사 문구 사용 금지(대체: ‘짧은 몰입’).
- Answer ONLY in $answerLang.
...
""".trimIndent()
    }


    private fun deepSchema(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("required", JSONArray().apply {
            put("highlights"); put("plan"); put("tips"); put("luckyColorName"); put("luckyTime"); put("luckyNumber"); put("tomorrowPrep")
        })
        put("properties", JSONObject().apply {
            put("highlights", JSONObject().apply { put("type", "array"); put("items", JSONObject().put("type", "string")); put("minItems", 3); put("maxItems", 6) })
            put("plan", JSONObject().apply {
                put("type", "object"); put("required", JSONArray().apply { put("morning"); put("afternoon"); put("evening") })
                put("properties", JSONObject().apply {
                    put("morning", JSONObject().put("type", "string"))
                    put("afternoon", JSONObject().put("type", "string"))
                    put("evening", JSONObject().put("type", "string"))
                })
            })
            put("tips", JSONObject().apply { put("type", "array"); put("items", JSONObject().put("type", "string")); put("minItems", 3); put("maxItems", 6) })
            put("checklistAdjusted", JSONObject().apply { put("type", "array"); put("items", JSONObject().put("type", "string")) })
            put("tomorrowPrep", JSONObject().put("type", "string"))
            put("luckyColorName", JSONObject().put("type", "string"))
            put("luckyTime", JSONObject().put("type", "string"))
            put("luckyNumber", JSONObject().put("type", "integer"))
        })
    }

    fun showDeepDialog(ctx: Context, deep: JSONObject, lastDaily: JSONObject?) {
        val dialogView = View.inflate(ctx, R.layout.dialog_fortune_deep, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDeepTitle)
        val chipTime = dialogView.findViewById<Chip>(R.id.chipLuckyTime)
        val chipNum = dialogView.findViewById<Chip>(R.id.chipLuckyNumber)
        val chipCol = dialogView.findViewById<Chip>(R.id.chipLuckyColor)

        val tvHigh = dialogView.findViewById<TextView>(R.id.tvDeepHighlights)
        val tvMorn = dialogView.findViewById<TextView>(R.id.tvPlanMorning)
        val tvAft = dialogView.findViewById<TextView>(R.id.tvPlanAfternoon)
        val tvEve = dialogView.findViewById<TextView>(R.id.tvPlanEvening)
        val tvTmr = dialogView.findViewById<TextView>(R.id.tvDeepTomorrow)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnDeepClose)

        val lucky = lastDaily?.optJSONObject("lucky") ?: JSONObject()
        val colorHex = lucky.optString("colorHex")
        val colName = sanitizeColorName(deep.optString("luckyColorName"), colorHex)
        val rawTime = deep.optString("luckyTime", lucky.optString("time"))
        val time = humanizeLuckyTime(rawTime.replace("(~", "~").replace(")", "").trim())
        val num = deep.optInt("luckyNumber", lucky.optInt("number"))

        tvTitle.text = context.getString(R.string.deep_dialog_title)
        chipTime.text = context.getString(R.string.chip_lucky_time, time)
        chipNum.text  = context.getString(R.string.chip_lucky_number, num)
        chipCol.text  = context.getString(R.string.chip_lucky_color, colName)
        runCatching { Color.parseColor(colorHex) }.onSuccess { c ->
            val bg = Color.argb(48, Color.red(c), Color.green(c), Color.blue(c))
            chipCol.chipBackgroundColor = ColorStateList.valueOf(bg)
        }

        val hl = (0 until (deep.optJSONArray("highlights")?.length() ?: 0))
            .mapNotNull { deep.optJSONArray("highlights")?.optString(it)?.trim() }
            .map { context.getString(R.string.bullet_with_text, cleanse(it)) }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { context.getString(R.string.fallback_highlights) }
        tvHigh.text = hl

        val plan = deep.optJSONObject("plan") ?: JSONObject()
        tvMorn.text = cleanse(neutralizeCorporateTerms(plan.optString("morning")))
        tvAft.text  = cleanse(neutralizeCorporateTerms(plan.optString("afternoon")))
        tvEve.text  = cleanse(neutralizeCorporateTerms(plan.optString("evening")))

        val tmr = cleanse(deep.optString("tomorrowPrep", ""))
        val extra = buildTomorrowExtraTips(deep, lastDaily)
        tvTmr.text = if (tmr.isNotBlank()) "${tmr}\n\n$extra" else extra

        val dialog = MaterialAlertDialogBuilder(ctx).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            val dm = ctx.resources.displayMetrics
            dialog.window?.setLayout((dm.widthPixels * 0.94f).toInt(), (dm.heightPixels * 0.80f).toInt())
        }
        btnClose.setTextColor(Color.WHITE)
        btnClose.background = GradientDrawable().apply {
            cornerRadius = 22f
            colors = intArrayOf(Color.parseColor("#9B8CFF"), Color.parseColor("#6F86FF"))
            orientation = GradientDrawable.Orientation.TL_BR
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mapErrorToUserMessage(reason: String): String = when {
        reason.contains("401") -> context.getString(R.string.err_auth, reason)
        reason.contains("403") -> context.getString(R.string.err_forbidden, reason)
        reason.contains("404") -> context.getString(R.string.err_not_found, reason)
        reason.contains("429") -> context.getString(R.string.err_rate_limit, reason)
        reason.contains("timeout", ignoreCase = true) -> context.getString(R.string.err_timeout, reason)
        else -> context.getString(R.string.err_network_generic, reason)
    }

    private fun mapHttpError(code: Int): String = mapErrorToUserMessage("http $code")

    private fun validateAndFill(obj: JSONObject, seed: Int): JSONObject {
        val recentCols = storage.getRecentLuckyColors(5).map { it.uppercase(Locale.ROOT) }
        val recentNums = storage.getRecentLuckyNumbers(10)

        val lucky = (obj.optJSONObject("lucky") ?: JSONObject()).apply {
            val raw = optString("colorHex").uppercase(Locale.ROOT)
            val base = if (raw.matches(Regex("#[0-9A-F]{6}")) && luckyPalette.contains(raw)) raw else null
            var chosenHex = base ?: pickLuckyColorDeterministic(seed, recentCols)
            if (recentCols.contains(chosenHex)) chosenHex = pickLuckyColorDeterministic(seed + 101, recentCols)

            var num = optInt("number", -1)
            if (num !in 10..99) num = pickLuckyNumberDiversified(seed, recentNums)
            if (num == 27 || recentNums.contains(num)) {
                num = pickLuckyNumberDiversified(seed + 1337, recentNums)
            }

            val t = humanizeLuckyTime(optString("time").ifBlank { pickLuckyTimeFallback() })
            put("colorHex", chosenHex); put("number", num); put("time", t)
        }
        obj.put("lucky", lucky)

        val (p, n, ng) = seededEmotions(seed)
        val emo = (obj.optJSONObject("emotions") ?: JSONObject()).apply {
            put("positive", optInt("positive", p).coerceIn(20, 90))
            put("neutral", optInt("neutral", n).coerceIn(10, 50))
            put("negative", optInt("negative", ng).coerceIn(5, 35))
        }
        obj.put("emotions", emo)
        // --- Emotion normalization & balancing to avoid sticky 70% positives ---
        run {
            val e = obj.getJSONObject("emotions")
            val p0 = e.optInt("positive")
            val n0 = e.optInt("neutral")
            val g0 = e.optInt("negative")

            // 1) Normalize to sum = 100
            var sum = (p0 + n0 + g0).coerceAtLeast(1)
            var pf = p0 * 100f / sum
            var nf = n0 * 100f / sum
            var gf = g0 * 100f / sum

            // 2) Light "pull-to-center" to keep daily variety while avoiding extremes
            // Target bands chosen to look natural in UI and avoid constant 70+ green.
            fun clamp(x: Float, lo: Int, hi: Int) = x.coerceIn(lo.toFloat(), hi.toFloat())
            val pCentered = clamp(pf, 35, 55)
            val nCentered = clamp(nf, 20, 40)
            val gCentered = clamp(gf, 10, 25)

            // Blend original with centered (alpha=0.35 keeps API trend but softens bias)
            val alpha = 0.35f
            pf = pf * (1f - alpha) + pCentered * alpha
            nf = nf * (1f - alpha) + nCentered * alpha
            gf = gf * (1f - alpha) + gCentered * alpha

            // 3) Round to ints and fix rounding drift to make sum exactly 100
            var pi = pf.toInt()
            var ni = nf.toInt()
            var gi = gf.toInt()
            var drift = 100 - (pi + ni + gi)
            // Assign remainder to the largest component by absolute fractional part
            if (drift != 0) {
                val fracs = listOf(
                    Pair("p", pf - pi),
                    Pair("n", nf - ni),
                    Pair("g", gf - gi)
                ).sortedByDescending { it.second }
                when (fracs.first().first) {
                    "p" -> pi += drift
                    "n" -> ni += drift
                    else -> gi += drift
                }
            }

            // Final safety clamp and write back
            pi = pi.coerceIn(20, 90)
            ni = ni.coerceIn(10, 50)
            gi = gi.coerceIn(5, 35)

            // If clamping broke the sum, nudge neutral to compensate
            val fix = 100 - (pi + ni + gi)
            if (fix != 0) ni = (ni + fix).coerceIn(10, 50)

            e.put("positive", pi)
            e.put("neutral", ni)
            e.put("negative", gi)
            obj.put("emotions", e)
        }
        // --- /Emotion normalization ---


        val secIn = obj.optJSONObject("sections")
        val sec = secIn ?: JSONObject()
        val keys = listOf("overall", "love", "study", "work", "money", "lotto")
        if (secIn == null) {
            seededSectionScores(seed).forEach { (k, v) ->
                sec.put(k, JSONObject().put("score", v).put("text", "").put("advice", ""))
            }
        }
        val lt = lucky.optString("time")
        val ln = lucky.optInt("number", pickLuckyNumberDiversified(seed, recentNums))
        keys.forEach { k ->
            val sObj = sec.optJSONObject(k) ?: JSONObject().also { sec.put(k, it) }
            val sc = sObj.optInt("score", 70).coerceIn(40, 100)
            sObj.put("score", sc)
            var curText = sObj.optString("text").trim()
            var curAdv = sObj.optString("advice").trim()
            if (k != "lotto" && (curText.isBlank() || curAdv.isBlank())) {
                val (t, a) = defaultSectionCopy(k, sc, lt, ln)
                if (curText.isBlank()) curText = t
                if (curAdv.isBlank()) curAdv = a
            }
            if (k != "lotto") {
                sObj.put("text", cleanse(curText))
                sObj.put("advice", cleanse(curAdv))
            } else {
                sObj.put("text", ""); sObj.put("advice", "")
            }
        }
        val baseKeys = listOf("love", "study", "work", "money")
        val baseScores = baseKeys.mapNotNull { sec.optJSONObject(it)?.optInt("score", 70) }.ifEmpty { listOf(70, 70, 70, 70) }
        val overallScore = calcTotalScore(baseScores)
        sec.optJSONObject("overall")?.put("score", overallScore)
        obj.put("sections", sec)

        obj.put("lottoNumbers", sanitizeLotto(obj.optJSONArray("lottoNumbers"), seed))

        val cl = obj.optJSONArray("checklist")
        val itemsRaw = (0 until (cl?.length() ?: 0)).mapNotNull { cl?.optString(it) }
        val itemsClean = sanitizeChecklist(itemsRaw)
        obj.put("checklist", JSONArray().apply { itemsClean.forEach { put(it) } })

        val tObj = (obj.optJSONObject("tomorrow") ?: JSONObject())
        val longFixed = cleanse(
            normalizePlan(tObj.optString("long"), lt, ln, lucky.optString("colorHex"))
                .ifBlank { makeTomorrowPlan(obj) }
        )
        tObj.put("long", longFixed); obj.put("tomorrow", tObj)

        return obj
    }

    private fun defaultSectionCopy(key: String, score: Int, luckyTime: String, luckyNumber: Int): Pair<String, String> {
        val s = context
        val mood = when {
            score >= 85 -> s.getString(R.string.mood_high)
            score >= 70 -> s.getString(R.string.mood_good)
            score >= 55 -> s.getString(R.string.mood_fair)
            else -> s.getString(R.string.mood_low)
        }
        val text = when (key) {
            "love"    -> s.getString(R.string.sec_love_text, mood)
            "study"   -> s.getString(R.string.sec_study_text, mood)
            "work"    -> s.getString(R.string.sec_work_text, mood)
            "money"   -> s.getString(R.string.sec_money_text, mood)
            "overall" -> s.getString(R.string.sec_overall_text, mood)
            else      -> mood
        }
        val advice = when (key) {
            "love"    -> s.getString(R.string.sec_love_advice)
            "study"   -> s.getString(R.string.sec_study_advice)
            "work"    -> s.getString(R.string.sec_work_advice)
            "money"   -> s.getString(R.string.sec_money_advice)
            "overall" -> s.getString(R.string.sec_overall_advice)
            else      -> s.getString(R.string.default_generic_advice)
        }
        return text to advice
    }

    private fun calcTotalScore(sectionScores: List<Int>): Int {
        if (sectionScores.isEmpty()) return 0
        val avg = sectionScores.average()
        val lowCnt = sectionScores.count { it < 50 }
        val raw = avg.coerceAtMost(if (lowCnt >= 2) 75.0 else 95.0)
        val minBound = (sectionScores.minOrNull() ?: 0) - 5
        val maxBound = (sectionScores.maxOrNull() ?: 100) + 8
        return raw.roundToInt().coerceIn(minBound, maxBound).coerceIn(0, 100)
    }

    private fun normalizePlan(raw: String?, luckyTime: String, luckyNumber: Int, colorHex: String): String {
        val s = context
        var t = raw?.trim().orEmpty()
        t = t.replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("^#{1,6}\\s*"), "")
            .trim()
        t = neutralizeCorporateTerms(t)
            .replace(Regex("숙제|과제|수업|강의|시험|퀴즈|레포트|제출"), s.getString(R.string.replace_report))
            .replace(Regex("연락|전화|메시지|문자|카톡|DM|카카오"), s.getString(R.string.contact_sanitized))
        t = stripTimePhrases(t).trim()

        val hasMorning = t.contains("아침")
        val hasAfternoon = t.contains("오후")
        val hasEvening = t.contains("저녁")

        if (!(hasMorning && hasAfternoon && hasEvening) || t.length < 80) {
            return makeTomorrowPlan(JSONObject().apply {
                put("lucky", JSONObject().put("time", luckyTime).put("number", luckyNumber).put("colorHex", colorHex))
            })
        }
        if (t.length > 900) t = t.take(900) + "…"
        return cleanse(t)
    }

    private fun makeTomorrowPlan(base: JSONObject): String =
        context.getString(R.string.tomorrow_plan_template)


    private fun buildTomorrowExtraTips(deep: JSONObject, daily: JSONObject?): String {
        val tips = (0 until (deep.optJSONArray("tips")?.length() ?: 0))
            .mapNotNull { deep.optJSONArray("tips")?.optString(it) }
            .map { context.getString(R.string.bullet_with_text, cleanBulletLineLong(it)) }

        val adj = (0 until (deep.optJSONArray("checklistAdjusted")?.length() ?: 0))
            .mapNotNull { deep.optJSONArray("checklistAdjusted")?.optString(it) }
            .map { context.getString(R.string.bullet_with_text, cleanBulletLineLong(it)) }

        // fallback도 더 이상 18자 자르지 않음
        val fallback = daily?.optJSONArray("checklist")?.let { arr ->
            (0 until arr.length()).map {
                context.getString(R.string.bullet_with_text, cleanBulletLineLong(arr.optString(it)))
            }
        } ?: emptyList()

        val lines = (tips + adj).ifEmpty { fallback }
        return if (lines.isNotEmpty()) lines.joinToString("\n") else context.getString(R.string.tomorrow_extra_fallback)
    }


    private fun cleanse(text: String): String {
        var s = text
        s = s.replace("리뷰", "돌아보기")
        s = s.replace(Regex("25\\s*분\\s*집중\\s*2\\s*회"), "짧은 몰입 한 번")
        s = s.replace(Regex("\\s{2,}"), " ").trim()
        return s
    }

    private fun styleTokens(seed: Int): String {
        val isKo = context.resources.configuration.locales[0].language.startsWith("ko")
        val bankKo = listOf("차분한","단단한","선명한","기민한","유연한","담백한","리더십",
            "분석적","균형감","민첩함","집중","꾸준함","정갈함","실용","낙관","침착","절제","명료","차분집중")
        val bankEn = listOf("calm","solid","clear","nimble","flexible","plain","leadership",
            "analytical","balance","agile","focus","steady","neat","practical","optimistic","composed","discipline","lucid","calm focus")
        val bank = if (isKo) bankKo else bankEn
        val r = kotlin.random.Random(seed)
        val picks = LinkedHashSet<String>()
        while (picks.size < 3 && picks.size < bank.size) picks += bank[r.nextInt(bank.size)]
        return picks.joinToString(",")
    }


    private val luckyPalette = listOf("#1E88E5", "#3949AB", "#43A047", "#FB8C00", "#E53935", "#8E24AA", "#546E7A", "#00897B", "#FDD835", "#6D4C41")

    private fun pickLuckyColorDeterministic(seed: Int, recent: List<String>): String {
        val recentSet = recent.map { it.uppercase(Locale.ROOT) }.toSet()
        val candidates = luckyPalette.filter { it !in recentSet }
        val pool = if (candidates.isNotEmpty()) candidates else luckyPalette
        val idx = (abs(seed) % pool.size)
        return pool[idx]
    }

    private fun pickLuckyNumberDiversified(seed: Int, recent: List<Int>): Int {
        val bad = recent.toMutableSet().apply { add(27) }
        var num = (abs(seed) % 90) + 10
        var tries = 0
        while ((num in bad) && tries < 8) {
            num = ((num + (abs(seed shr (tries + 1)) % 17) + 11) % 90) + 10
            tries++
        }
        return num.coerceIn(10, 99)
    }

    private fun seededEmotions(seed: Int): Triple<Int, Int, Int> {
        val r = Random(seed)
        val pos = 40 + r.nextInt(46)
        val neg = 5 + r.nextInt(26)
        val neu = (100 - pos - neg).coerceIn(10, 50)
        return Triple(pos, neu, neg)
    }

    private fun seededSectionScores(seed: Int): Map<String, Int> {
        val r = Random(seed); val base = 60 + r.nextInt(21)
        val map = mutableMapOf(
            "overall" to (base + r.nextInt(15) - 7).coerceIn(40, 100),
            "love" to (base + r.nextInt(20) - 10).coerceIn(40, 100),
            "study" to (base + r.nextInt(20) - 10).coerceIn(40, 100),
            "work" to (base + r.nextInt(20) - 10).coerceIn(40, 100),
            "money" to (base + r.nextInt(20) - 10).coerceIn(40, 100),
            "lotto" to (50 + r.nextInt(16)).coerceIn(40, 100)
        )
        val low = map.keys.random(r); val high = (map.keys - low).random(r)
        map[low] = 40 + r.nextInt(16); map[high] = 85 + r.nextInt(16)
        return map
    }

    private fun stripTimePhrases(src: String): String {
        var s = src
        s = s.replace(Regex("(오전|오후)\\s*\\d{1,2}시(\\s*~\\s*(오전|오후)?\\s*\\d{1,2}시)?"), "")
        s = s.replace(Regex("\\d{1,2}시\\s*(까지|전)?"), "")
        s = s.replace(Regex("(오늘|내일)?\\s*(아침|오전|점심|오후|저녁|밤)"), "")
        s = s.replace(Regex("\\s{2,}"), " ")
        return s.trim()
    }

    private fun neutralizeCorporateTerms(text: String): String {
        val s = context
        var t = text
        t = t.replace(Regex("회의|미팅|면담"), s.getString(R.string.replace_meeting))
        t = t.replace(Regex("이메일"), s.getString(R.string.replace_email))
        t = t.replace(Regex("보고서"), s.getString(R.string.replace_report))
        t = t.replace(Regex("결재"), s.getString(R.string.replace_approval))
        t = t.replace(Regex("메신저"), s.getString(R.string.replace_messenger))
        return t
    }

    // 기존 sanitizeColorName(...) 전부 교체
    private fun sanitizeColorName(nameRaw: String, hex: String): String {
        val lang = context.resources.configuration.locales[0].language.lowercase(Locale.ROOT)
        val isKo = lang.startsWith("ko")
        val m = nameRaw.trim().lowercase(Locale.ROOT)
        val hexU = hex.uppercase(Locale.ROOT)

        val mapEn = mapOf(
            "blue" to "Blue", "navy" to "Indigo", "indigo" to "Indigo", "green" to "Green",
            "orange" to "Orange", "red" to "Red", "purple" to "Purple", "violet" to "Purple",
            "slate" to "Slate", "teal" to "Teal", "cyan" to "Teal", "yellow" to "Yellow", "brown" to "Brown", "amber" to "Yellow"
        )
        val mapKo = mapOf(
            "blue" to "블루", "navy" to "인디고", "indigo" to "인디고", "green" to "그린",
            "orange" to "오렌지", "red" to "레드", "purple" to "퍼플", "violet" to "퍼플",
            "slate" to "슬레이트", "teal" to "틸", "cyan" to "틸", "yellow" to "옐로", "brown" to "브라운", "amber" to "옐로"
        )
        fun fbEn() = when (hexU) {
            "#1E88E5" -> "Blue"; "#3949AB" -> "Indigo"; "#43A047" -> "Green"; "#FB8C00" -> "Orange"; "#E53935" -> "Red"
            "#8E24AA" -> "Purple"; "#546E7A" -> "Slate"; "#00897B" -> "Teal"; "#FDD835" -> "Yellow"; "#6D4C41" -> "Brown"
            else -> "Lucky color"
        }
        fun fbKo() = when (hexU) {
            "#1E88E5" -> "블루"; "#3949AB" -> "인디고"; "#43A047" -> "그린"; "#FB8C00" -> "오렌지"; "#E53935" -> "레드"
            "#8E24AA" -> "퍼플"; "#546E7A" -> "슬레이트"; "#00897B" -> "틸"; "#FDD835" -> "옐로"; "#6D4C41" -> "브라운"
            else -> context.getString(R.string.color_name_fallback)
        }

        return if (isKo) {
            mapKo[m] ?: fbKo()
        } else {
            mapEn[m] ?: fbEn()
        }
    }


    private fun sanitizeLotto(arr: JSONArray?, seed: Int): JSONArray {
        val set = LinkedHashSet<Int>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val v = arr.optInt(i, -1)
                if (v in 1..45 && v !in BANNED_LOTTO) set += v
            }
        }
        val r = Random(seed xor 0x9E3779B9u.toInt())
        while (set.size < 6) {
            val n = 1 + r.nextInt(45)
            if (n !in set && n !in BANNED_LOTTO) set += n
        }
        return JSONArray().apply { set.toList().sorted().forEach { put(it) } }
    }

    @Suppress("unused")
    private fun genLottoNumbers(seed: Int): List<Int> {
        val r = Random(seed xor 0x9E3779B9u.toInt()); val set = LinkedHashSet<Int>()
        while (set.size < 6) {
            val n = 1 + r.nextInt(45)
            if (n !in BANNED_LOTTO) set += n
        }
        return set.toList().sorted()
    }

    private fun mainThread(block: () -> Unit) =
        (context as? android.app.Activity)?.runOnUiThread { block() }

    private fun ageTag(age: Int): String = when {
        age < 13 -> "아동"
        age < 20 -> "10대"
        age < 23 -> "20대초"
        age < 30 -> "20대"
        age < 35 -> "30대초"
        age < 40 -> "30대"
        age < 45 -> "40대초"
        age < 50 -> "40대"
        age < 60 -> "50대"
        else -> "60대+"
    }

    private fun buildDeepPrompt(
        u: FortuneStorage.UserInfo,
        daily: JSONObject,
        seed: Int
    ): String {
        val todayKey = storage.todayKey()
        val tone = styleTokens(seed)
        return context.getString(
            R.string.fortune_deep_prompt_template,
            u.nickname ?: "",
            u.mbti ?: "",
            u.birth ?: "",
            u.gender ?: "",
            u.birthTime ?: "",
            todayKey,
            tone,
            seed.toString(),
            daily.toString()
        )
    }
}
