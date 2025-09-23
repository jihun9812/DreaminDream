// FortuneApi.kt
package com.example.dreamindream

import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class FortuneApi(
    private val context: Context,
    private val storage: FortuneStorage
) {
    private val http by lazy { OkHttpClient() }
    private val TAG = "FortuneFragment"

    // 금지 로또 숫자(개별 숫자 차단)
    private val BANNED_LOTTO = setOf(5, 12, 19, 23, 34, 41)

    fun fetchDaily(
        u: FortuneStorage.UserInfo,
        seed: Int,
        onSuccess: (JSONObject) -> Unit,
        onError: (String, Triple<Int,Int,Int>) -> Unit
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
                Log.e(TAG, "❌ onFailure ($dt ms) | error=${e.message}", e)
                val seedPreset = seededEmotions(seed)
                val msg = mapErrorToUserMessage(e.message ?: "io")
                mainThread { onError(msg, seedPreset) }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string().orEmpty()
                val dt = System.currentTimeMillis() - t0
                Log.d(TAG, "📩 onResponse ($dt ms) | code=${response.code} | len=${raw.length}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "⚠️ HTTP error ${response.code} | bodyPreview=${raw.take(400)}")
                    val msg = mapHttpError(response.code)
                    val seedPreset = seededEmotions(seed)
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
                    Log.w(TAG, "🟨 parse fallback: ${e.message}")
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
            put("model","gpt-4.1-mini"); put("temperature",0.7)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role","system").put("content","당신은 프리미엄 라이프 코치이자 운세 분석가입니다. 도구만 호출해 JSON을 반환하세요."))
                put(JSONObject().put("role","user").put("content", buildDeepPrompt(u, daily, seed)))
            })
            put("tools", JSONArray().put(JSONObject().apply {
                put("type","function")
                put("function", JSONObject().apply {
                    put("name","deep_fortune_analysis")
                    put("description","오늘 운세 기반의 심화 분석(전문가 톤) 반환")
                    put("parameters", deepSchema())
                })
            }))
            put("tool_choice", JSONObject().apply { put("type","function"); put("function", JSONObject().put("name","deep_fortune_analysis")) })
            put("max_tokens", 2200)
        }
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization","Bearer ${BuildConfig.OPENAI_API_KEY}")
            .addHeader("Content-Type","application/json").build()

        http.newCall(req).enqueue(object: Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { mainThread { cb(null) } }
            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) { mainThread { cb(null) }; return }
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
                } catch (_: Exception) { mainThread { cb(null) } }
            }
        })
    }

    // ───────────────────────── UI helpers ─────────────────────────

    fun scoreColor(score: Int): Int = when {
        score >= 70 -> Color.parseColor("#17D7A0")
        score >= 40 -> Color.parseColor("#FFC107")
        else        -> Color.parseColor("#FF5252")
    }

    fun formatSections(obj: JSONObject): CharSequence {
        val sb = StringBuilder()
        val sections = obj.optJSONObject("sections") ?: JSONObject()
        val lottoNums = obj.optJSONArray("lottoNumbers")
        fun line(label: String, key: String) {
            val s = sections.optJSONObject(key) ?: JSONObject()
            val score = s.optInt("score", -1)
            val text = cleanse(s.optString("text").ifBlank { s.optString("advice") })
            sb.append(label); if (score >= 0) sb.append(" (${score}점)")
            if (key == "lotto") {
                if (lottoNums != null && lottoNums.length() == 6) {
                    val arr = (0 until 6).map { lottoNums.optInt(it) }.sorted()
                    sb.append("  번호: ${arr.joinToString(", ")}\n")
                } else sb.append("\n"); return
            }
            if (text.isNotBlank()) { sb.append(" - ").append(text.trim()) }
            sb.append("\n")
        }
        line("총운","overall"); line("연애운","love"); line("학업운","study")
        line("직장운","work"); line("재물운","money"); line("로또운","lotto")
        return sb.toString()
    }

    fun buildSectionDetails(title: String, score: Int, text: String?, advice: String?): String {
        val base = cleanse(text?.trim().orEmpty().ifBlank { "오늘의 흐름을 간결히 정리했어요." })
        val tip  = cleanse(advice?.trim().orEmpty().let { if (it.isNotBlank()) "• $it" else "" })

        fun extraBy(title: String, score: Int): String = when (title) {
            "총운" -> when {
                score >= 85 -> "• 기회: 자신 있는 첫 걸음을 크게 끊으면 하루 전체가 따라옵니다."
                score >= 70 -> "• 유지: 목표를 1개로 고정하고, 과감히 나머지는 내일로 미루세요."
                score >= 55 -> "• 주의: 선택을 줄여 피로를 낮추고, 가벼운 완료 1개로 전진감 만들기."
                else        -> "• 복구: 쉬운 일 10분만, 감점 요소 만들지 않기가 최우선."
            }
            "연애운" -> when {
                score >= 85 -> "• 기회: 가벼운 칭찬·공감으로 분위기가 빠르게 따뜻해집니다."
                score >= 70 -> "• 유지: 민감한 주제는 피하고 편안한 대화."
                score >= 55 -> "• 주의: 과한 해석 금지. 짧고 간결하게."
                else        -> "• 복구: 기대치 낮추고 감사 한 줄 남기기."
            }
            "학업운" -> when {
                score >= 85 -> "• 기회: 자신 있는 파트로 짧게 몰입해 한 덩어리를 끝내기."
                score >= 70 -> "• 유지: 분량을 줄이고 핵심 1개만 잡기."
                score >= 55 -> "• 주의: 노트 5줄 요약만 남기기."
                else        -> "• 복구: 예열용 문제 소량으로 감 되찾기."
            }
            "직장운" -> when {
                score >= 85 -> "• 기회: 임팩트 높은 태스크 1건 먼저."
                score >= 70 -> "• 유지: 서브태스크 2개 이내로 범위 좁히기."
                score >= 55 -> "• 주의: 동시에 여러 일 금지."
                else        -> "• 복구: 난도 낮은 정리·정돈 1건으로 복귀 동력."
            }
            "재물운" -> when {
                score >= 85 -> "• 기회: 소액이라도 확정체크."
                score >= 70 -> "• 유지: 지출 카테고리 1개만 정리."
                score >= 55 -> "• 주의: 충동지출 경계."
                else        -> "• 복구: 지출 1건 확인·정리부터."
            }
            "로또운" -> "• 참고: 오락 범위를 넘기지 않도록 상한선 설정."
            else -> "• 유지: 범위를 좁혀 꾸준함 확보."
        }
        val extra = cleanse(extraBy(title, score))
        return listOf(base, tip, extra).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    // ─────────────── Normalizers / Rules ───────────────

    fun sanitizeChecklist(items: List<String>): List<String> {
        val out = items.map { neutralizeChecklistText(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        return if (out.size == 3) out else out + buildEssentialChecklist().drop(out.size).take(3 - out.size)
    }

    private fun neutralizeChecklistText(src: String): String {
        var t = src.trim()
        t = neutralizeCorporateTerms(t)
            .replace(Regex("숙제|과제|수업|강의|시험|퀴즈|레포트|제출"), "정리")
        if (Regex("연락|전화|메시지|문자|DM|카톡|카카오").containsMatchIn(t)) t = "알림 1건 정리"
        t = stripTimePhrases(t)
            .replace(Regex("^•\\s*"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (t.length > 18) t = t.take(18)
        if (t.length < 4) t = "핵심 할 일 1개 완료"
        t = t.replace(Regex("할 ?일.*(마무리|끝내기)"), "핵심 할 일 1개 완료")
        return cleanse(t)
    }

    private fun buildEssentialChecklist(): List<String> =
        listOf("핵심 작업 1개 완료", "알림·메모 3분 정리", "가벼운 스트레칭 5분")

    /** ()를 제거하고 ‘오전/오후 HH시(~HH시)’를 한 줄로 정리 */
    fun humanizeLuckyTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var t = raw.trim()
        // ( ~12시 ) → ~12시
        t = t.replace(Regex("\\(\\s*~\\s*"), "~").replace(")", "")
        // HH:MM~HH:MM → 오전/오후 HH시(~HH시)
        Regex("(\\d{1,2}):(\\d{2})~(\\d{1,2}):(\\d{2})").find(t)?.let { m ->
            fun h(hh:String): String {
                val H=hh.toInt(); val ampm=if (H in 0..11) "오전" else "오후"
                val h12=when{H==0->12; H<=12->H; else->H-12}; return "$ampm ${h12}시"
            }
            val s = h(m.groupValues[1]); val e = h(m.groupValues[3]); return if (s==e) s else "$s~$e"
        }
        val map = mapOf(
            "자시" to "오전 12시~오전 1시","축시" to "오전 1시~오전 3시","인시" to "오전 3시~오전 5시","묘시" to "오전 5시~오전 7시",
            "진시" to "오전 7시~오전 9시","사시" to "오전 9시~오전 11시","오시" to "오전 11시~오후 1시","미시" to "오후 1시~오후 3시",
            "신시" to "오후 3시~오후 5시","유시" to "오후 5시~오후 7시","술시" to "오후 7시~오후 9시","해시" to "오후 9시~오후 11시"
        )
        map.entries.firstOrNull { t.contains(it.key) }?.let { return it.value }
        if (Regex("오전|오후").containsMatchIn(t)) return t
        return t
    }

    fun pickLuckyTimeFallback(): String {
        val hours = (6..22).map { if (it < 12) "오전 ${it}시" else "오후 ${if (it == 12) 12 else it - 12}시" }
        return hours.random()
    }

    // ─────────────── Builders / Parsers ───────────────

    private fun buildDailyRequest(u: FortuneStorage.UserInfo, seed: Int): JSONObject {
        return JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.7)
            put("max_tokens", 2200)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    "당신은 프리미엄 라이프 코치이자 운세 분석가입니다. 항상 function 호출만으로 JSON을 반환하세요."))
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
            '{' -> { if (d==0) s=i; d++ }
            '}' -> { d--; if (d==0 && s>=0) return text.substring(s, i+1) }
        }
        return null
    }

    private fun finalizePayload(payload: JSONObject, seed: Int): JSONObject {
        payload.optJSONObject("lucky")?.let {
            val c = it.optString("colorHex"); val t = it.optString("time"); val n = it.optInt("number", -1)
            if (c.isNotBlank()) storage.pushHistory("lucky_history_colors", c)
            if (t.isNotBlank()) storage.pushHistory("lucky_history_times",  t)
            if (n in 10..99)     storage.pushHistory("lucky_history_numbers", n.toString())
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

    // ─────────────── Schema / Prompt ───────────────

    private fun fortuneSchema(): JSONObject {
        val obj = JSONObject()
        obj.put("type","object")
        obj.put("required", JSONArray().apply { put("lucky"); put("sections"); put("keywords"); put("emotions"); put("checklist"); put("tomorrow") })
        obj.put("properties", JSONObject().apply {
            put("lucky", JSONObject().apply {
                put("type","object")
                put("required", JSONArray().apply { put("colorHex"); put("number"); put("time") })
                put("properties", JSONObject().apply {
                    put("colorHex", JSONObject().put("type","string").put("pattern","#[0-9A-Fa-f]{6}"))
                    put("number", JSONObject().put("type","integer").put("minimum",10).put("maximum",99))
                    put("time", JSONObject().put("type","string"))
                })
            })
            put("sections", JSONObject().apply {
                put("type","object")
                put("required", JSONArray().apply { put("overall"); put("love"); put("study"); put("work"); put("money"); put("lotto") })
                fun sec() = JSONObject().apply {
                    put("type","object")
                    put("required", JSONArray().apply { put("score"); put("text"); put("advice") })
                    put("properties", JSONObject().apply {
                        put("score", JSONObject().put("type","integer").put("minimum",40).put("maximum",100))
                        put("text", JSONObject().put("type","string"))
                        put("advice", JSONObject().put("type","string"))
                    })
                }
                put("overall", sec()); put("love", sec()); put("study", sec()); put("work", sec()); put("money", sec()); put("lotto", sec())
            })
            put("keywords", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")); put("minItems",1); put("maxItems",4) })
            put("emotions", JSONObject().apply {
                put("type","object"); put("required", JSONArray().apply { put("positive"); put("neutral"); put("negative") })
                put("properties", JSONObject().apply {
                    put("positive", JSONObject().put("type","integer").put("minimum",20).put("maximum",90))
                    put("neutral",  JSONObject().put("type","integer").put("minimum",10).put("maximum",50))
                    put("negative", JSONObject().put("type","integer").put("minimum",5).put("maximum",35))
                })
            })
            put("lottoNumbers", JSONObject().apply {
                put("type","array"); put("items", JSONObject().put("type","integer").put("minimum",1).put("maximum",45)); put("minItems",6); put("maxItems",6)
            })
            put("checklist", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")); put("minItems",3); put("maxItems",3) })
            put("tomorrow", JSONObject().apply { put("type","object"); put("required", JSONArray().apply { put("long") }); put("properties", JSONObject().apply { put("long", JSONObject().put("type","string")) }) })
        })
        return obj
    }

    private fun buildUserPrompt(u: FortuneStorage.UserInfo, seed: Int): String {
        val today = storage.todayKey(); val weekday = SimpleDateFormat("EEEE", Locale.KOREAN).format(Date())
        val userAge = storage.ageOf(u.birth); val tag = ageTag(userAge)
        val avoidColors = JSONArray(storage.getRecentLuckyColors())
        val avoidTimes = JSONArray(storage.getRecentLuckyTimes())
        val avoidNumbers = JSONArray(storage.getRecentLuckyNumbers())
        val palette = JSONArray(luckyPalette)
        val tone = styleTokens(seed)
        return """
[사용자]
nickname:"${u.nickname}", mbti:"${u.mbti}", birthdate:"${u.birth}", birth_time:"${u.birthTime}", gender:"${u.gender}"
date:"$today ($weekday)", age:$userAge, age_tag:$tag, seed:$seed, tone:"$tone"

[출력 가이드(엄격)]
- 금지어: ‘리듬’ 금지.
- 학생/학교 어휘 금지(숙제/과제/수업/강의/시험/퀴즈/레포트/제출).
- 연락 지시 금지(전화/메시지/DM/카톡/연락 등).
- checklist: 개인지칭·시간/마감 표현 금지, 오늘 바로 가능한 3개(12~18자).
- 섹션 score 40~100. 각 섹션 2~3문장(80~160자), 실용 팁 1개. tone="$tone".
- lucky.colorHex는 palette에서, 최근 5일 중복 회피(avoidColors/avoidTimes/avoidNumbers).
- lucky.number 10~99, lucky.time ‘오전/오후 HH시(~HH시)’.
- emotions 현실적 분포, lottoNumbers 6개(1~45). 
- “리뷰”, “25분 집중 2회”와 유사 문구 사용 금지(대체: ‘돌아보기’, ‘짧게 몰입’).

[심화 유도]
- tomorrow.long(400~700자): ‘아침/오후/저녁’ 소제목 + 최저점 영역 보완 액션(정량).

palette:$palette, avoidColors:$avoidColors, avoidTimes:$avoidTimes, avoidNumbers:$avoidNumbers
        """.trimIndent()
    }

    private fun deepSchema(): JSONObject = JSONObject().apply {
        put("type","object")
        put("required", JSONArray().apply {
            put("highlights"); put("plan"); put("tips"); put("luckyColorName"); put("luckyTime"); put("luckyNumber"); put("tomorrowPrep")
        })
        put("properties", JSONObject().apply {
            put("highlights", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")); put("minItems",3); put("maxItems",6) })
            put("plan", JSONObject().apply {
                put("type","object"); put("required", JSONArray().apply { put("morning"); put("afternoon"); put("evening") })
                put("properties", JSONObject().apply {
                    put("morning", JSONObject().put("type","string"))
                    put("afternoon", JSONObject().put("type","string"))
                    put("evening", JSONObject().put("type","string"))
                })
            })
            put("tips", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")); put("minItems",3); put("maxItems",6) })
            put("checklistAdjusted", JSONObject().apply { put("type","array"); put("items", JSONObject().put("type","string")) })
            put("tomorrowPrep", JSONObject().put("type","string"))
            put("luckyColorName", JSONObject().put("type","string"))
            put("luckyTime", JSONObject().put("type","string"))
            put("luckyNumber", JSONObject().put("type","integer"))
        })
    }

    fun showDeepDialog(ctx: Context, deep: JSONObject, lastDaily: JSONObject?) {
        val dialogView = View.inflate(ctx, R.layout.dialog_fortune_deep, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDeepTitle)
        val chipTime = dialogView.findViewById<Chip>(R.id.chipLuckyTime)
        val chipNum  = dialogView.findViewById<Chip>(R.id.chipLuckyNumber)
        val chipCol  = dialogView.findViewById<Chip>(R.id.chipLuckyColor)

        val tvHigh  = dialogView.findViewById<TextView>(R.id.tvDeepHighlights)
        val tvMorn  = dialogView.findViewById<TextView>(R.id.tvPlanMorning)
        val tvAft   = dialogView.findViewById<TextView>(R.id.tvPlanAfternoon)
        val tvEve   = dialogView.findViewById<TextView>(R.id.tvPlanEvening)
        val tvTmr   = dialogView.findViewById<TextView>(R.id.tvDeepTomorrow)
        val btnClose= dialogView.findViewById<MaterialButton>(R.id.btnDeepClose)

        val lucky = lastDaily?.optJSONObject("lucky") ?: JSONObject()
        val colorHex = lucky.optString("colorHex")
        val colName  = sanitizeColorName(deep.optString("luckyColorName"), colorHex)
        val rawTime  = deep.optString("luckyTime", lucky.optString("time"))
        val time = humanizeLuckyTime(rawTime.replace("(~","~").replace(")","").trim())
        val num  = deep.optInt("luckyNumber", lucky.optInt("number"))

        tvTitle.text = "심화 분석"
        chipTime.text = "시간  $time"
        chipNum.text  = "숫자  $num"
        chipCol.text  = "색상  $colName"
        runCatching { Color.parseColor(colorHex) }.onSuccess { c ->
            val bg = Color.argb(48, Color.red(c), Color.green(c), Color.blue(c))
            chipCol.chipBackgroundColor = android.content.res.ColorStateList.valueOf(bg)
        }

        val hl = (0 until (deep.optJSONArray("highlights")?.length() ?: 0))
            .mapNotNull { deep.optJSONArray("highlights")?.optString(it)?.trim() }
            .map { "• ${cleanse(it)}" }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { "• 오늘 흐름을 간결히 정리했어요." }
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
            dialog.window?.setLayout((dm.widthPixels*0.94f).toInt(), (dm.heightPixels*0.80f).toInt())
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

    // ─────────────── Internals ───────────────

    private fun mapErrorToUserMessage(reason: String): String = when {
        reason.contains("401") -> "인증 오류가 발생했어요. 설정의 API 키를 확인해주세요. ($reason)"
        reason.contains("403") -> "접근이 거부되었어요. 권한/결제 상태를 확인해주세요. ($reason)"
        reason.contains("404") -> "서버 주소를 찾지 못했어요. 잠시 후 다시 시도해주세요. ($reason)"
        reason.contains("429") -> "호출 한도가 초과되었어요. 잠시 기다렸다가 다시 시도해주세요. ($reason)"
        reason.contains("timeout", ignoreCase = true) -> "네트워크가 지연되고 있어요. 연결 상태를 확인한 뒤 다시 시도해주세요. ($reason)"
        else -> "네트워크 오류가 발생했어요. ($reason)"
    }
    private fun mapHttpError(code: Int): String = mapErrorToUserMessage("http $code")

    private fun validateAndFill(obj: JSONObject, seed: Int): JSONObject {
        // ----- lucky(color, number, time) 다변화 -----
        val recentCols = storage.getRecentLuckyColors(5).map { it.uppercase(Locale.ROOT) }
        val recentNums = storage.getRecentLuckyNumbers(10)

        val lucky = (obj.optJSONObject("lucky") ?: JSONObject()).apply {
            // 색상: 모델이 준 값이 팔레트 밖이거나 최근값이면 시드기반 로테이션으로 교체
            val raw = optString("colorHex").uppercase(Locale.ROOT)
            val base = if (raw.matches(Regex("#[0-9A-F]{6}")) && luckyPalette.contains(raw)) raw else null
            var chosenHex = base ?: pickLuckyColorDeterministic(seed, recentCols)
            if (recentCols.contains(chosenHex)) chosenHex = pickLuckyColorDeterministic(seed + 101, recentCols)

            // 숫자: 10~99, 최근 10개·특히 27 회피, 필요시 시드 바꿔 재시도
            var num = optInt("number", -1)
            if (num !in 10..99) num = pickLuckyNumberDiversified(seed, recentNums)
            if (num == 27 || recentNums.contains(num)) {
                num = pickLuckyNumberDiversified(seed + 1337, recentNums)
            }

            // 시간: 괄호 제거 후 사람이 읽기 좋게
            val t = humanizeLuckyTime(optString("time").ifBlank { pickLuckyTimeFallback() })

            put("colorHex", chosenHex); put("number", num); put("time", t)
        }
        obj.put("lucky", lucky)

        // ----- emotions -----
        val (p, n, ng) = seededEmotions(seed)
        val emo = (obj.optJSONObject("emotions") ?: JSONObject()).apply {
            put("positive", optInt("positive", p).coerceIn(20, 90))
            put("neutral",  optInt("neutral",  n).coerceIn(10, 50))
            put("negative", optInt("negative", ng).coerceIn(5, 35))
        }
        obj.put("emotions", emo)

        // ----- sections -----
        val secIn = obj.optJSONObject("sections")
        val sec = secIn ?: JSONObject()
        val keys = listOf("overall","love","study","work","money","lotto")
        if (secIn == null) {
            seededSectionScores(seed).forEach { (k,v) ->
                sec.put(k, JSONObject().put("score", v).put("text","").put("advice",""))
            }
        }
        val lt = lucky.optString("time"); val ln = lucky.optInt("number", pickLuckyNumberDiversified(seed, recentNums))
        keys.forEach { k ->
            val s = sec.optJSONObject(k) ?: JSONObject().also { sec.put(k, it) }
            val sc = s.optInt("score", 70).coerceIn(40, 100)
            s.put("score", sc)
            var curText = s.optString("text").trim()
            var curAdv  = s.optString("advice").trim()
            if (k != "lotto" && (curText.isBlank() || curAdv.isBlank())) {
                val (t, a) = defaultSectionCopy(k, sc, lt, ln)
                if (curText.isBlank()) curText = t
                if (curAdv.isBlank())  curAdv  = a
            }
            if (k != "lotto") {
                s.put("text", cleanse(curText))
                s.put("advice", cleanse(curAdv))
            } else {
                s.put("text",""); s.put("advice","")
            }
        }
        val baseKeys = listOf("love","study","work","money")
        val baseScores = baseKeys.mapNotNull { sec.optJSONObject(it)?.optInt("score", 70) }.ifEmpty { listOf(70,70,70,70) }
        val overallScore = calcTotalScore(baseScores)
        sec.optJSONObject("overall")?.put("score", overallScore)
        obj.put("sections", sec)

        // ----- lotto / checklist / tomorrow -----
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

    private fun defaultSectionCopy(key: String, score: Int, luckyTime: String, luckyNumber: Int): Pair<String,String> {
        val mood = when {
            score >= 85 -> "상승세가 뚜렷합니다."
            score >= 70 -> "흐름이 안정적입니다."
            score >= 55 -> "기복이 있으니 속도를 조절하세요."
            else        -> "기대치보다 낮아 기본을 단단히 하는 날입니다."
        }
        val text = when (key) {
            "love"  -> "관계에서는 $mood 말보다 태도가 신뢰를 만듭니다. 감정선을 과장하지 말고, 편안한 주제로 속도를 맞춰보세요."
            "study" -> "학습 집중도는 $mood 분량을 줄여도 꾸준함을 유지하면 성과가 납니다. 핵심 키워드를 먼저 붙잡아 보세요."
            "work"  -> "업무 흐름은 $mood 목표를 하나로 좁히면 효율이 올라갑니다. 산만함을 줄이고 우선순위를 재정렬하세요."
            "money" -> "재정 운은 $mood 즉흥지출을 삼가고 지출 1건만 점검해도 균형을 지킬 수 있습니다."
            "overall"-> "오늘 전반은 $mood 작은 성취를 차곡차곡 쌓기 좋습니다. 욕심을 덜고 기본 루틴을 지키면 안정감이 커집니다."
            else    -> mood
        }
        val advice = when (key) {
            "love"  -> "자극적인 화제 대신 편안한 대화로 분위기 안정."
            "study" -> "짧게 몰입해 핵심 1개만 끝내기."
            "work"  -> "한 가지에 집중하기."
            "money" -> "필요 지출만 남기고 오늘 1건 점검."
            "overall"-> "확실한 한 가지에 집중하기."
            else    -> "체크리스트 1개를 지금 실행."
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
        var t = raw?.trim().orEmpty()
        t = t.replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("^#{1,6}\\s*"), "")
            .trim()
        t = neutralizeCorporateTerms(t)
            .replace(Regex("숙제|과제|수업|강의|시험|퀴즈|레포트|제출"), "정리")
            .replace(Regex("연락|전화|메시지|문자|카톡|DM|카카오"), "알림 확인")
        t = stripTimePhrases(t).trim()
        val hasMorning = t.contains("아침"); val hasAfternoon = t.contains("오후"); val hasEvening = t.contains("저녁")
        if (!(hasMorning && hasAfternoon && hasEvening) || t.length < 80) {
            return makeTomorrowPlan(JSONObject().apply {
                put("lucky", JSONObject().put("time", luckyTime).put("number", luckyNumber).put("colorHex", colorHex))
            })
        }
        if (t.length > 900) t = t.take(900) + "…"
        return cleanse(t)
    }

    private fun makeTomorrowPlan(base: JSONObject): String = buildString {
        append("• 오늘 흐름을 간결히 정리했어요.\n\n")
        append("아침(09~12)\n - 핵심 작업 1개 완료\n - 알림·메모 3분 정리\n\n")
        append("오후(13~17)\n - 짧게 몰입해 한 가지를 끝내기\n - 가벼운 스트레칭 5분\n\n")
        append("저녁(19~22)\n - 하루 기록 3줄, 내일 첫 작업 1줄 적기\n")
    }

    private fun buildTomorrowExtraTips(deep: JSONObject, daily: JSONObject?): String {
        val tips = (0 until (deep.optJSONArray("tips")?.length() ?: 0))
            .mapNotNull { deep.optJSONArray("tips")?.optString(it) }
            .map { "• " + cleanse(neutralizeCorporateTerms(stripTimePhrases(it))) }
        val adj = (0 until (deep.optJSONArray("checklistAdjusted")?.length() ?: 0))
            .mapNotNull { deep.optJSONArray("checklistAdjusted")?.optString(it) }
            .map { "• " + cleanse(neutralizeChecklistText(it)) }
        val fallback = daily?.optJSONArray("checklist")?.let { arr ->
            (0 until arr.length()).map { "• " + cleanse(neutralizeChecklistText(arr.optString(it))) }
        } ?: emptyList()
        val lines = (tips + adj).ifEmpty { fallback }
        return if (lines.isNotEmpty()) lines.joinToString("\n") else "• 내일 아침 첫 10분은 오늘의 핵심 1개만 이어서 진행하세요."
    }

    // ─────────────── Utility ───────────────

    // 금지 문구 정화기
    private fun cleanse(text: String): String {
        var s = text
        s = s.replace("리뷰", "돌아보기")
        s = s.replace(Regex("25\\s*분\\s*집중\\s*2\\s*회"), "짧게 몰입해 한 가지 끝내기")
        s = s.replace(Regex("\\s{2,}"), " ").trim()
        return s
    }

    private fun styleTokens(seed: Int): String {
        val bank = listOf("차분한","단단한","선명한","기민한","유연한","담백한","리더십","분석적","균형감","민첩함","집중","꾸준함","정갈함","실용","낙관","침착","절제","명료","차분집중")
        val r = Random(seed)
        return (0 until 3).map { bank[r.nextInt(bank.size)] }.distinct().joinToString(",")
    }

    // 단색 10종 팔레트
    private val luckyPalette = listOf("#1E88E5","#3949AB","#43A047","#FB8C00","#E53935","#8E24AA","#546E7A","#00897B","#FDD835","#6D4C41")

    // 최근 회피 + 시드 기반 로테이션
    private fun pickLuckyColorDeterministic(seed: Int, recent: List<String>): String {
        val recentSet = recent.map { it.uppercase(Locale.ROOT) }.toSet()
        val candidates = luckyPalette.filter { it !in recentSet }
        val pool = if (candidates.isNotEmpty()) candidates else luckyPalette
        val idx = (abs(seed) % pool.size)
        return pool[idx]
    }

    private fun pickLuckyNumberDiversified(seed: Int, recent: List<Int>): Int {
        val bad = recent.toMutableSet().apply { add(27) } // 27 과다 대비
        var num = (abs(seed) % 90) + 10
        var tries = 0
        while ((num in bad) && tries < 8) {
            num = ((num + (abs(seed shr (tries + 1)) % 17) + 11) % 90) + 10
            tries++
        }
        return num.coerceIn(10, 99)
    }

    private fun seededEmotions(seed: Int): Triple<Int, Int, Int> {
        val r = Random(seed); val pos = 40 + r.nextInt(46); val neg = 5 + r.nextInt(26); val neu = (100 - pos - neg).coerceIn(10,50)
        return Triple(pos, neu, neg)
    }
    private fun seededSectionScores(seed: Int): Map<String,Int> {
        val r = Random(seed); val base = 60 + r.nextInt(21)
        val map = mutableMapOf(
            "overall" to (base + r.nextInt(15)-7).coerceIn(40,100),
            "love"    to (base + r.nextInt(20)-10).coerceIn(40,100),
            "study"   to (base + r.nextInt(20)-10).coerceIn(40,100),
            "work"    to (base + r.nextInt(20)-10).coerceIn(40,100),
            "money"   to (base + r.nextInt(20)-10).coerceIn(40,100),
            "lotto"   to (50 + r.nextInt(16)).coerceIn(40,100)
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
        var s = text
        s = s.replace(Regex("회의|미팅|면담"),"상담/정리")
        s = s.replace(Regex("이메일"),"알림/메모")
        s = s.replace(Regex("보고서"),"노트 정리")
        s = s.replace(Regex("결재"),"확인")
        s = s.replace(Regex("메신저"),"연락")
        return s
    }
    private fun sanitizeColorName(nameRaw: String, hex: String): String {
        val m = nameRaw.trim().lowercase(Locale.ROOT)
        val map = mapOf(
            "blue" to "블루", "navy" to "인디고", "indigo" to "인디고", "green" to "그린",
            "orange" to "오렌지", "red" to "레드", "purple" to "퍼플", "violet" to "퍼플",
            "slate" to "슬레이트", "teal" to "틸", "cyan" to "틸", "yellow" to "옐로", "brown" to "브라운", "amber" to "옐로"
        )
        val allowed = setOf("블루","인디고","그린","오렌지","레드","퍼플","슬레이트","틸","옐로","브라운")
        val fromMap = map[m]
        return when {
            allowed.contains(nameRaw) -> nameRaw
            fromMap != null -> fromMap
            else -> when (hex.uppercase(Locale.ROOT)) {
                "#1E88E5" -> "블루"; "#3949AB" -> "인디고"; "#43A047" -> "그린"; "#FB8C00" -> "오렌지"; "#E53935" -> "레드"
                "#8E24AA" -> "퍼플"; "#546E7A" -> "슬레이트"; "#00897B" -> "틸"; "#FDD835" -> "옐로"; "#6D4C41" -> "브라운"
                else -> "행운색"
            }
        }
    }

    // 금지 숫자 제외 + 유효성 보정
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

    // ─────────────── 보조: ageTag / buildDeepPrompt ───────────────

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
        else     -> "60대+"
    }

    private fun buildDeepPrompt(
        u: FortuneStorage.UserInfo,
        daily: JSONObject,
        seed: Int
    ): String {
        val todayKey = storage.todayKey()
        val tone = styleTokens(seed)

        val lucky = daily.optJSONObject("lucky") ?: JSONObject()
        val sections = daily.optJSONObject("sections") ?: JSONObject()

        val luckyHex = lucky.optString("colorHex")
        val luckyNum = lucky.optInt("number")
        val luckyTime = lucky.optString("time")

        val worstKey = listOf("love","study","work","money")
            .minByOrNull { sections.optJSONObject(it)?.optInt("score", 70) ?: 70 } ?: "work"

        return """
[컨텍스트]
- 사용자: nickname="${u.nickname}", mbti="${u.mbti}", birthdate="${u.birth}", gender="${u.gender}", birth_time="${u.birthTime}"
- 날짜: $todayKey, tone="$tone", seed=$seed
- 일일 운세(JSON): ${daily.toString()}

[요청]
아래 스키마의 function만 호출하세요. 설명/자유 텍스트를 본문으로 쓰지 말고, function 인자(JSON)만 반환합니다.

[작성 규칙]
- highlights: 오늘 흐름의 핵심 3~6개(짧고 임팩트).
- plan: 아침/오후/저녁 각각 2~3줄, 실천 문장. 학생/학교/연락 지시 어휘 금지. ‘리뷰’ 표현 금지.
- tips: 3~6개, 바로 실행 가능한 액션. ‘25분 집중 2회’류 문구 금지(대체: ‘짧게 몰입’).
- checklistAdjusted: daily.checklist를 현실적으로 다듬거나 대체(최대 3~5개).
- luckyColorName: ${sanitizeColorName("", luckyHex)} 등 한글 색상명.
- luckyTime: "${humanizeLuckyTime(luckyTime)}" 또는 명확한 시간대.
- luckyNumber: 가능하면 ${luckyNum} 유지.
- tomorrowPrep: 최저점 영역(${worstKey}) 보완 중심의 한 문단.

반드시 deep_fortune_analysis를 호출하세요.
        """.trimIndent()
    }
}
