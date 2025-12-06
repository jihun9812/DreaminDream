package com.dreamindream.app

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.random.Random

data class WeeklyReportData(
    val feeling: String, val keywords: List<String>, val analysis: String,
    val analysisJson: String,
    val analysisImageUrl: String,
    val emotionLabels: List<String>, val emotionDist: List<Float>,
    val themeLabels: List<String>, val themeDist: List<Float>,
    val sourceCount: Int, val lastRebuiltAt: Long, val tier: String,
    val proAt: Long, val stale: Boolean, val score: Int, val grade: String
)

data class WeekEntry(val ts: Long, val dream: String, val interp: String)

data class DreamScoreResult(val score: Int, val grade: String)

object FirestoreManager {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val TAG = "FirestoreManager"
    private fun now() = System.currentTimeMillis()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }

    private fun getResList(ctx: Context, resId: Int): List<String> {
        return try { ctx.resources.getStringArray(resId).toList() } catch (e: Exception) { emptyList() }
    }

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())


    fun saveDeepFortune(uid: String, dateKey: String, deepPayload: JSONObject, onComplete: (() -> Unit)? = null) {
        val map = jsonToMap(deepPayload)
        // daily_fortunes 컬렉션 내의 해당 날짜 문서에 'deep_analysis' 필드로 병합 저장
        db.collection("users").document(uid).collection("daily_fortunes").document(dateKey)
            .set(mapOf("deep_analysis" to map, "has_deep" to true), SetOptions.merge())
            .addOnCompleteListener { onComplete?.invoke() }
    }

    // 심화 운세 불러오기
    fun getDeepFortune(uid: String, dateKey: String, onResult: (JSONObject?) -> Unit) {
        db.collection("users").document(uid).collection("daily_fortunes").document(dateKey).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.contains("deep_analysis")) {
                    val deepMap = doc.get("deep_analysis") as? Map<*, *>
                    if (deepMap != null) {
                        onResult(JSONObject(deepMap))
                    } else {
                        onResult(null)
                    }
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    fun thisWeekKey(): String {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        return String.format(Locale.US, "%04d-W%02d", cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR))
    }

    fun weekDateKeys(weekKey: String): List<String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return runCatching {
            val parts = weekKey.split("-W")
            val year = parts[0].toInt()
            val week = parts[1].toInt()
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                minimalDaysInFirstWeek = 4
                set(Calendar.YEAR, year)
                set(Calendar.WEEK_OF_YEAR, week)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            (0..6).map {
                val s = fmt.format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 1)
                s
            }
        }.getOrElse { emptyList() }
    }

    private fun <T : Number> scaleToPercent(raw: List<T>, target: Int): List<Float> {
        val v = raw.map { it.toFloat().coerceAtLeast(0f) }
        val list = if (v.size >= target) v.take(target) else v + List(target - v.size) { 0f }
        val sum = list.sum()
        return if (sum > 0f) list.map { (it / sum) * 100f } else list
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            map[key] = convertJsonValue(value)
        }
        return map
    }

    private fun jsonToList(array: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until array.length()) {
            list.add(convertJsonValue(array.get(i)))
        }
        return list
    }

    private fun convertJsonValue(value: Any): Any? {
        return when (value) {
            is JSONObject -> jsonToMap(value)
            is JSONArray -> jsonToList(value)
            else -> value
        }
    }

    // --- Core Functions (Calendar & Profile Support) ---

    // [수정됨] 꿈 해몽 저장 시 전체 누적 카운트 증가 로직 추가
    fun saveDream(uid: String, dream: String, result: String) {
        val dateKey = todayKey()
        val userRef = db.collection("users").document(uid)
        val dayRef = userRef.collection("dreams").document(dateKey)
        val entry = hashMapOf("dream" to dream, "result" to result, "timestamp" to now())

        dayRef.collection("entries").add(entry).addOnSuccessListener {
            // 1. 해당 날짜의 카운트 증가
            dayRef.set(mapOf("count" to FieldValue.increment(1.0), "last_updated" to now()), SetOptions.merge())

            // 2. 주간 리포트 갱신 필요 표시
            userRef.collection("weekly_reports").document(thisWeekKey())
                .set(mapOf("stale" to true), SetOptions.merge())

            // 3. [추가] 유저 전체 꿈 해몽 횟수(total_dream_count) 증가 -> Settings 화면 표시용
            userRef.set(mapOf("total_dream_count" to FieldValue.increment(1.0)), SetOptions.merge())
        }
    }

    // [추가] Settings 화면을 위한 통계 데이터 (전체 누적, 오늘) 조회
    fun getDreamStats(uid: String, onResult: (total: Int, today: Int) -> Unit) {
        // 1. 유저 정보에서 전체 카운트 조회
        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val total = userDoc.getLong("total_dream_count")?.toInt() ?: 0

            // 2. 오늘 날짜 서브컬렉션에서 금일 횟수 조회
            db.collection("users").document(uid).collection("dreams").document(todayKey())
                .collection("entries").get()
                .addOnSuccessListener { todaySnap ->
                    onResult(total, todaySnap.size())
                }
                .addOnFailureListener {
                    onResult(total, 0)
                }
        }.addOnFailureListener {
            onResult(0, 0)
        }
    }

    fun countDreamEntriesToday(uid: String, onResult: (Int) -> Unit) {
        db.collection("users").document(uid).collection("dreams").document(todayKey()).collection("entries").get()
            .addOnSuccessListener { onResult(it.size()) }.addOnFailureListener { onResult(0) }
    }

    fun getAllDreamDates(context: Context, uid: String, onResult: (Set<String>) -> Unit) {
        db.collection("users").document(uid).collection("dreams").get()
            .addOnSuccessListener { snap ->
                val dates = snap.documents.map { it.id }.toSet()
                onResult(dates)
            }
            .addOnFailureListener { onResult(emptySet()) }
    }

    fun updateDreamsForDate(uid: String, dateKey: String, itemsJson: String, onComplete: (() -> Unit)? = null) {
        val dayRef = db.collection("users").document(uid).collection("dreams").document(dateKey)
        dayRef.collection("entries").get().addOnSuccessListener { snapshot ->
            val batch = db.batch()
            for (doc in snapshot.documents) batch.delete(doc.reference)
            batch.commit().addOnSuccessListener {
                val arr = try { JSONArray(itemsJson) } catch (e: Exception) { JSONArray() }
                if (arr.length() == 0) {
                    dayRef.set(mapOf("count" to 0, "last_updated" to now()), SetOptions.merge())
                    onComplete?.invoke()
                } else {
                    var addedCount = 0
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        dayRef.collection("entries").add(mapOf(
                            "dream" to obj.optString("dream"),
                            "result" to obj.optString("result"),
                            "timestamp" to now()
                        )).addOnCompleteListener {
                            addedCount++
                            if (addedCount == arr.length()) {
                                dayRef.set(mapOf("count" to arr.length(), "last_updated" to now()), SetOptions.merge())
                                db.collection("users").document(uid).collection("weekly_reports").document(thisWeekKey())
                                    .set(mapOf("stale" to true), SetOptions.merge())
                                onComplete?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }

    fun getUserProfile(uid: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { onResult(it.data) }.addOnFailureListener { onResult(null) }
    }

    fun updateUserProfile(uid: String, data: Map<String, Any>, onComplete: () -> Unit) {
        db.collection("users").document(uid).set(data, SetOptions.merge()).addOnCompleteListener { onComplete() }
    }

    fun saveDailyFortune(uid: String, dateKey: String, payload: JSONObject, onComplete: (() -> Unit)? = null) {
        val map = jsonToMap(payload)
        db.collection("users").document(uid).collection("daily_fortunes").document(dateKey)
            .set(map + mapOf("timestamp" to now()), SetOptions.merge())
            .addOnCompleteListener { onComplete?.invoke() }
    }

    // --- Report Logic ---

    fun countDreamEntriesForWeek(uid: String, weekKey: String, onResult: (Int) -> Unit) {
        val dates = weekDateKeys(weekKey)
        if (dates.isEmpty()) { onResult(0); return }
        val counter = AtomicInteger(0)
        val totalDreams = AtomicInteger(0)

        dates.forEach { dateKey ->
            db.collection("users").document(uid).collection("dreams").document(dateKey).collection("entries").get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) totalDreams.addAndGet(task.result?.size() ?: 0)
                    if (counter.incrementAndGet() == dates.size) onResult(totalDreams.get())
                }
        }
    }

    fun loadWeeklyReportFull(ctx: Context, uid: String, weekKey: String, onResult: (WeeklyReportData) -> Unit) {
        val emoLabels = getResList(ctx, R.array.emotion_labels)
        val themeLabels = getResList(ctx, R.array.theme_labels)
        val empty = WeeklyReportData("", emptyList(), "", "", "", emoLabels, List(8){0f}, themeLabels, List(4){0f}, 0, 0L, "base", 0L, true, 0, "F")

        db.collection("users").document(uid).collection("weekly_reports").document(weekKey).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onResult(empty); return@addOnSuccessListener }
                try {
                    val emD = scaleToPercent((doc.get("emotionDist") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: List(8){0f}, 8)
                    val thD = scaleToPercent((doc.get("themeDist") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: List(4){0f}, 4)

                    onResult(WeeklyReportData(
                        feeling = doc.getString("feeling") ?: "",
                        keywords = (doc.get("keywords") as? List<*>)?.map { it.toString() } ?: emptyList(),
                        analysis = doc.getString("analysis") ?: "",
                        analysisJson = doc.getString("analysis_json") ?: "",
                        analysisImageUrl = doc.getString("analysis_image_url") ?: "",
                        emotionLabels = emoLabels,
                        emotionDist = emD,
                        themeLabels = themeLabels,
                        themeDist = thD,
                        sourceCount = (doc.getLong("sourceCount") ?: 0L).toInt(),
                        lastRebuiltAt = doc.getLong("lastRebuiltAt") ?: 0L,
                        tier = doc.getString("tier") ?: "base",
                        proAt = doc.getLong("proAt") ?: 0L,
                        stale = doc.getBoolean("stale") ?: true,
                        score = (doc.getLong("score") ?: 0L).toInt(),
                        grade = doc.getString("grade") ?: "F"
                    ))
                } catch (e: Exception) { onResult(empty) }
            }
            .addOnFailureListener { onResult(empty) }
    }

    fun generateProReport(uid: String, weekKey: String, ctx: Context, callback: (Boolean) -> Unit) {
        collectWeekEntriesLimited(uid, weekKey, limit = 10) { entries, count ->
            if (count < 1) { callback(false); return@collectWeekEntriesLimited }

            // 1. 통계 및 로컬 분석 실행 (그래프/점수 생성 필수)
            val (emoD, thD) = localAnalyze(ctx, entries)
            val scoreData = calculateDreamScore(emoD, thD)
            val allText = entries.joinToString(" ") { it.dream }
            val dummyKws = extractKeywords(allText)
            val userLang = Locale.getDefault().displayLanguage

            val prompt = """
    You are an experienced clinical psychologist specializing in Jungian and psychodynamic dream work.
    Your goal is to give grounded, realistic insights that help the user reflect on their week.

    Dreams (from the same person over one week):
    ${entries.map { it.dream }.joinToString(" | ")}

    STRICT INSTRUCTIONS:
    - Write in $userLang only.
    - No fortune-telling, magic, spirits, astrology, past lives, or supernatural predictions.
    - Keep the tone calm, warm, and professional. Focus on emotions, inner conflicts, needs, and coping strategies.
    - Treat all dreams as parts of ONE connected story about the same person.
      Do NOT analyze each dream separately or enumerate them (no "first dream, second dream" style).
    - Use simple, conversational language that a non-expert can easily read.
    - Avoid very long sentences. Prefer clear, short sentences.

    Field-by-field rules:

    - "title":
      * A short, poetic title (max 25 characters) that captures the overall mood of the week.
    - "summary":
      * 1–2 sentences that briefly describe the main emotional theme of these dreams.
    - "core_themes":
      * 3–5 simple keywords (single words or very short phrases).
    - "deep_analysis":
      * 3–5 short paragraphs, each 2–4 sentences.
      * Clearly weave the dreams into ONE integrated narrative about the dreamer’s current inner world.
      * Use blank lines between paragraphs so it is easy to read on a phone.
      * Focus on realistic psychological interpretation (emotions, patterns, relationships, stress, desires).
    - "subconscious_message":
      * Exactly 1 sentence that gently summarizes what the inner self wants to say to the dreamer.
      * No magic, no prophecy, no guaranteed outcomes.
    - "actionable_advice":
      * 3–5 concrete, realistic suggestions the user can actually do in everyday life
        (e.g. journaling, setting boundaries, resting, talking to someone, planning small changes).
      * Each item should be short and practical.
    - "lucky_action":
      * ONE small symbolic action that feels a bit special but is still realistic and healthy
        (for example: writing a kind letter to yourself, taking a quiet evening walk, tidying your room
        as a fresh start, lighting a candle while reflecting on your week).
      * It MUST NOT mention miracles, the universe granting wishes, magical luck, or any guaranteed success.
      * It should sound like a gentle self-care ritual, not fortune-telling.

    Output JSON only, with this exact structure and keys (no extra text before or after):

    {
      "title": "A poetic title",
      "summary": "Concise summary.",
      "core_themes": ["Keyword1", "Keyword2", "Keyword3"],
      "deep_analysis": "Integrated multi-paragraph analysis...",
      "subconscious_message": "One powerful sentence.",
      "actionable_advice": ["Advice 1", "Advice 2", "Advice 3"],
      "lucky_action": "A realistic, symbolic action."
    }
""".trimIndent()


            val reqBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                put("response_format", JSONObject().put("type", "json_object"))
                put("temperature", 0.7)
            }

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(reqBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { callback(false) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: ""
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

                        val textContent = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        val jsonParsed = JSONObject(textContent)
                        val aiThemes = jsonParsed.optJSONArray("core_themes")
                        val finalKeywords = if (aiThemes != null && aiThemes.length() > 0) {
                            (0 until aiThemes.length()).map { aiThemes.getString(it).replace("#", "") }
                        } else {
                            dummyKws
                        }
                        val feeling = jsonParsed.optString("title")

                        saveWeekly(
                            db.collection("users").document(uid).collection("weekly_reports").document(weekKey),
                            feeling, finalKeywords, "", textContent, "",
                            emoD, thD, scoreData, count, "pro"
                        ) { callback(true) }

                    } catch (e: Exception) { e.printStackTrace(); callback(false) }
                }
            })
        }
    }

    fun aggregateDreamsForWeek(uid: String, weekKey: String, ctx: Context, callback: (Boolean) -> Unit) {
        collectWeekEntriesLimited(uid, weekKey, limit = 10) { entries, totalCount ->
            val ref = db.collection("users").document(uid).collection("weekly_reports").document(weekKey)
            if (totalCount < 2) {
                ref.set(mapOf("stale" to true, "sourceCount" to totalCount), SetOptions.merge())
                callback(false)
                return@collectWeekEntriesLimited
            }
            fallbackToLocal(entries, ref, ctx, callback)
        }
    }

    private fun fallbackToLocal(entries: List<WeekEntry>, ref: com.google.firebase.firestore.DocumentReference, ctx: Context, callback: (Boolean) -> Unit) {
        val (emoD, thD) = localAnalyze(ctx, entries)
        val scoreData = calculateDreamScore(emoD, thD)
        val allText = entries.joinToString(" ") { it.dream }
        val keywords = extractKeywords(allText)
        val emoLabels = getResList(ctx, R.array.emotion_labels)
        val themeLabels = getResList(ctx, R.array.theme_labels)
        val emoIdx = emoD.indices.maxByOrNull { emoD[it] } ?: 0
        val emoLbl = emoLabels.getOrElse(emoIdx) { "Neutral" }
        val thIdx = thD.indices.maxByOrNull { thD[it] } ?: 0
        val themeLbl = themeLabels.getOrElse(thIdx) { "Life" }
        val mainKeyword = keywords.firstOrNull() ?: "Dream"

        val analysisText = try {
            ctx.getString(R.string.analysis_template_basic, emoLbl, themeLbl, mainKeyword)
        } catch (e: Exception) {
            "Analysis complete. Emotion: $emoLbl, Theme: $themeLbl."
        }

        saveWeekly(ref, emoLbl, keywords, analysisText, "", "", emoD, thD, scoreData, entries.size, "base") {
            callback(true)
        }
    }

    private fun extractKeywords(text: String): List<String> {
        return text.split(" ", "\n", ".")
            .filter { it.length > 1 }
            .groupingBy { it }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .take(4)
            .map { it.key.replace("#", "").replace(Regex("[^a-zA-Z0-9가-힣]"), "") }
    }

    private fun calculateDreamScore(emoDist: List<Float>, themeDist: List<Float>): DreamScoreResult {
        val posScore = (emoDist.getOrElse(0){0f} + emoDist.getOrElse(1){0f} + emoDist.getOrElse(2){0f} + emoDist.getOrElse(3){0f})
        val negScore = (emoDist.getOrElse(5){0f} + emoDist.getOrElse(6){0f} + emoDist.getOrElse(7){0f})
        var rawScore = 50f + (posScore * 0.8f) - (negScore * 1.2f)
        val thSum = themeDist.sum().coerceAtLeast(1f)
        if (thSum > 0) {
            rawScore += (themeDist.getOrElse(0){0f}/thSum * 5f) + (themeDist.getOrElse(1){0f}/thSum * 5f) - (themeDist.getOrElse(3){0f}/thSum * 8f)
        }
        val finalScore = rawScore.roundToInt().coerceIn(0, 100)
        val grade = when { finalScore >= 85 -> "A"; finalScore >= 65 -> "B"; finalScore >= 35 -> "C"; finalScore >= 15 -> "D"; else -> "F" }
        return DreamScoreResult(finalScore, grade)
    }

    private fun localAnalyze(ctx: Context, entries: List<WeekEntry>): Pair<List<Float>, List<Float>> {
        val emo = FloatArray(8)
        val th = FloatArray(4)
        val texts = entries.joinToString(" ") { (it.dream + " " + it.interp).lowercase() }
        val res = ctx.resources

        fun checkMultiLang(vararg arrayIds: Int): Boolean {
            return arrayIds.any { id ->
                try { res.getStringArray(id).any { keyword -> texts.contains(keyword.lowercase()) } }
                catch (e: Exception) { false }
            }
        }

        if (checkMultiLang(R.array.kw_positive_en, R.array.kw_positive_ko, R.array.kw_positive_zh, R.array.kw_positive_hi, R.array.kw_positive_ar)) emo[0] += 1f
        if (checkMultiLang(R.array.kw_calm_en, R.array.kw_calm_ko, R.array.kw_calm_zh, R.array.kw_calm_hi, R.array.kw_calm_ar)) emo[1] += 1f
        if (checkMultiLang(R.array.kw_vitality_en, R.array.kw_vitality_ko, R.array.kw_vitality_zh, R.array.kw_vitality_hi, R.array.kw_vitality_ar)) emo[2] += 1f
        if (checkMultiLang(R.array.kw_focus_en, R.array.kw_focus_ko, R.array.kw_focus_zh, R.array.kw_focus_hi, R.array.kw_focus_ar)) emo[3] += 1f
        if (checkMultiLang(R.array.kw_confusion_en, R.array.kw_confusion_ko, R.array.kw_confusion_zh, R.array.kw_confusion_hi, R.array.kw_confusion_ar)) emo[5] += 1f
        if (checkMultiLang(R.array.kw_anxiety_en, R.array.kw_anxiety_ko, R.array.kw_anxiety_zh, R.array.kw_anxiety_hi, R.array.kw_anxiety_ar)) emo[6] += 1f
        if (checkMultiLang(R.array.kw_depression_en, R.array.kw_depression_ko, R.array.kw_depression_zh, R.array.kw_depression_hi, R.array.kw_depression_ar)) emo[7] += 1f

        if (emo.sum() == 0f) emo[4] = 5f

        if (checkMultiLang(R.array.kw_theme_rel_en, R.array.kw_theme_rel_ko, R.array.kw_theme_rel_zh, R.array.kw_theme_rel_hi, R.array.kw_theme_rel_ar)) th[0] += 1f
        if (checkMultiLang(R.array.kw_theme_success_en, R.array.kw_theme_success_ko, R.array.kw_theme_success_zh, R.array.kw_theme_success_hi, R.array.kw_theme_success_ar)) th[1] += 1f
        if (checkMultiLang(R.array.kw_theme_change_en, R.array.kw_theme_change_ko, R.array.kw_theme_change_zh, R.array.kw_theme_change_hi, R.array.kw_theme_change_ar)) th[2] += 1f
        if (checkMultiLang(R.array.kw_theme_risk_en, R.array.kw_theme_risk_ko, R.array.kw_theme_risk_zh, R.array.kw_theme_risk_hi, R.array.kw_theme_risk_ar)) th[3] += 1f

        if (th.sum() == 0f) th[0] = 1f

        return Pair(scaleToPercent(emo.toList(), 8), scaleToPercent(th.toList(), 4))
    }

    private fun saveWeekly(
        ref: com.google.firebase.firestore.DocumentReference,
        feeling: String, kws: List<String>, analysis: String, analysisJson: String, imgUrl: String,
        emoDist: List<Float>, themeDist: List<Float>, score: DreamScoreResult, count: Int, tier: String,
        done: () -> Unit
    ) {
        val data = mapOf(
            "feeling" to feeling, "keywords" to kws, "analysis" to analysis,
            "analysis_json" to analysisJson, "analysis_image_url" to imgUrl,
            "emotionDist" to emoDist, "themeDist" to themeDist,
            "score" to score.score, "grade" to score.grade, "sourceCount" to count,
            "stale" to false, "lastRebuiltAt" to now(), "timestamp" to now(), "tier" to tier
        )
        ref.set(data, SetOptions.merge()).addOnCompleteListener { done() }
    }

    fun listWeeklyReportKeys(uid: String, limit: Int, onResult: (List<String>) -> Unit) {
        db.collection("users").document(uid).collection("weekly_reports").orderBy("timestamp", Query.Direction.DESCENDING).limit(limit.toLong()).get()
            .addOnSuccessListener { onResult(it.documents.map { d -> d.id }) }.addOnFailureListener { onResult(emptyList()) }
    }

    fun collectWeekEntriesLimited(uid: String, weekKey: String, limit: Int, onResult: (List<WeekEntry>, Int) -> Unit) {
        val dates = weekDateKeys(weekKey)
        if (dates.isEmpty()) { onResult(emptyList(), 0); return }
        val allEntries = Collections.synchronizedList(mutableListOf<WeekEntry>())
        val counter = AtomicInteger(0)
        dates.forEach { dateKey ->
            db.collection("users").document(uid).collection("dreams").document(dateKey).collection("entries").get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.documents?.forEach { doc ->
                        allEntries.add(WeekEntry(doc.getLong("timestamp")?:0L, doc.getString("dream")?:"", doc.getString("result")?:""))
                    }
                }
                if (counter.incrementAndGet() == dates.size) {
                    onResult(allEntries.filter { it.dream.isNotBlank() }.sortedBy { it.ts }.takeLast(limit), allEntries.size)
                }
            }
        }
    }
}