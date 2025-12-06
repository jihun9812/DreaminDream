package com.dreamindream.app

import android.content.Context
import com.dreamindream.app.BuildConfig
import com.dreamindream.app.R
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
import kotlin.random.Random

// --- Data Classes ---
data class WeeklyReportData(
    val feeling: String,
    val keywords: List<String>,
    val analysis: String,
    val analysisJson: String,
    val analysisImageUrl: String,
    val emotionLabels: List<String>, val emotionDist: List<Float>,
    val themeLabels: List<String>, val themeDist: List<Float>,
    val symbolDetails: List<Map<String, String>>,
    val themeAnalysis: Map<String, String>,
    // ★ 추가됨
    val futurePredictions: List<String>,
    val sourceCount: Int, val lastRebuiltAt: Long, val tier: String,
    val proAt: Long, val stale: Boolean, val score: Int, val grade: String
)
data class WeekEntry(val ts: Long, val dream: String, val interp: String, val id: String = "")

data class DreamScoreResult(val score: Int, val grade: String)

object FirestoreManager {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val TAG = "FirestoreManager"
    private fun now() = System.currentTimeMillis()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }

    private fun getResList(ctx: Context, resId: Int): List<String> {
        return try { ctx.resources.getStringArray(resId).toList() } catch (e: Exception) { emptyList() }
    }

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ==========================================
    // 1. User Profile & Fortune Methods
    // ==========================================

    fun getUserProfile(uid: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { onResult(it.data) }
            .addOnFailureListener { onResult(null) }
    }

    fun updateUserProfile(uid: String, data: Map<String, Any>, onComplete: () -> Unit) {
        db.collection("users").document(uid).set(data, SetOptions.merge())
            .addOnCompleteListener { onComplete() }
    }

    fun saveDailyFortune(uid: String, dateKey: String, payload: JSONObject, onComplete: (() -> Unit)? = null) {
        val map = jsonToMap(payload)
        db.collection("users").document(uid).collection("daily_fortunes").document(dateKey)
            .set(map + mapOf("timestamp" to now()), SetOptions.merge())
            .addOnCompleteListener { onComplete?.invoke() }
    }

    fun saveDeepFortune(uid: String, dateKey: String, deepPayload: JSONObject, onComplete: (() -> Unit)? = null) {
        val map = jsonToMap(deepPayload)
        db.collection("users").document(uid).collection("daily_fortunes").document(dateKey)
            .set(mapOf("deep_analysis" to map, "has_deep" to true), SetOptions.merge())
            .addOnCompleteListener { onComplete?.invoke() }
    }

    fun getDeepFortune(uid: String, dateKey: String, onResult: (JSONObject?) -> Unit) {
        db.collection("users").document(uid).collection("daily_fortunes").document(dateKey).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.contains("deep_analysis")) {
                    val deepMap = doc.get("deep_analysis") as? Map<*, *>
                    if (deepMap != null) onResult(JSONObject(deepMap)) else onResult(null)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    // ==========================================
    // 2. Dream Storage & Calendar Methods
    // ==========================================

    fun saveDream(uid: String, dream: String, result: String) {
        val dateKey = todayKey()
        val userRef = db.collection("users").document(uid)
        val dayRef = userRef.collection("dreams").document(dateKey)
        val entry = hashMapOf("dream" to dream, "result" to result, "timestamp" to now())

        dayRef.collection("entries").add(entry).addOnSuccessListener {
            dayRef.set(mapOf("count" to FieldValue.increment(1.0), "last_updated" to now()), SetOptions.merge())
            userRef.collection("weekly_reports").document(thisWeekKey())
                .set(mapOf("stale" to true), SetOptions.merge())
            userRef.set(mapOf("total_dream_count" to FieldValue.increment(1.0)), SetOptions.merge())
        }
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

    fun getAllDreamDates(context: Context, uid: String, onResult: (Set<String>) -> Unit) {
        db.collection("users").document(uid).collection("dreams").get()
            .addOnSuccessListener { snap ->
                val dates = snap.documents.map { it.id }.toSet()
                onResult(dates)
            }
            .addOnFailureListener { onResult(emptySet()) }
    }

    fun getDreamStats(uid: String, onResult: (total: Int, today: Int) -> Unit) {
        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val total = userDoc.getLong("total_dream_count")?.toInt() ?: 0
            db.collection("users").document(uid).collection("dreams").document(todayKey())
                .collection("entries").get()
                .addOnSuccessListener { todaySnap -> onResult(total, todaySnap.size()) }
                .addOnFailureListener { onResult(total, 0) }
        }.addOnFailureListener { onResult(0, 0) }
    }

    fun countDreamEntriesToday(uid: String, onResult: (Int) -> Unit) {
        db.collection("users").document(uid).collection("dreams").document(todayKey()).collection("entries").get()
            .addOnSuccessListener { onResult(it.size()) }.addOnFailureListener { onResult(0) }
    }

    // ==========================================
    // 3. AI Report Methods
    // ==========================================

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

    fun fetchWeeklyDreams(uid: String, weekKey: String, onResult: (List<WeekEntry>) -> Unit) {
        collectWeekEntriesLimited(uid, weekKey, limit = 50) { entries, _ ->
            onResult(entries)
        }
    }

    fun loadWeeklyReportFull(ctx: Context, uid: String, weekKey: String, onResult: (WeeklyReportData) -> Unit) {
        val emoLabels = getResList(ctx, R.array.emotion_labels)
        val themeLabels = getResList(ctx, R.array.theme_labels)
        // empty 객체에도 futurePredictions 빈 리스트 추가
        val empty = WeeklyReportData("", emptyList(), "", "", "", emoLabels, List(8){0f}, themeLabels, List(4){0f}, emptyList(), emptyMap(), emptyList(), 0, 0L, "base", 0L, true, 0, "F")

        db.collection("users").document(uid).collection("weekly_reports").document(weekKey).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onResult(empty); return@addOnSuccessListener }
                try {
                    val emD = scaleToPercent((doc.get("emotionDist") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: List(8){0f}, 8)
                    val thD = scaleToPercent((doc.get("themeDist") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: List(4){0f}, 4)

                    val jsonStr = doc.getString("analysis_json") ?: ""
                    var symbolDetails = listOf<Map<String, String>>()
                    var themeAnalysis = mapOf<String, String>()
                    // ★ 파싱 변수 추가
                    var futurePredictions = listOf<String>()

                    if (jsonStr.isNotBlank()) {
                        try {
                            val jsonObj = JSONObject(jsonStr)

                            // Symbol Details
                            val symArr = jsonObj.optJSONArray("symbol_details")
                            if (symArr != null) {
                                val list = mutableListOf<Map<String, String>>()
                                for(i in 0 until symArr.length()) {
                                    val item = symArr.getJSONObject(i)
                                    list.add(mapOf("keyword" to item.optString("keyword"), "description" to item.optString("description")))
                                }
                                symbolDetails = list
                            }

                            // Theme Analysis
                            val thObj = jsonObj.optJSONObject("theme_analysis_text")
                            if (thObj != null) {
                                val map = mutableMapOf<String, String>()
                                val keys = thObj.keys()
                                while(keys.hasNext()) { val key = keys.next(); map[key] = thObj.getString(key) }
                                themeAnalysis = map
                            }

                            // ★ Future Predictions 파싱
                            val predArr = jsonObj.optJSONArray("future_predictions")
                            if (predArr != null) {
                                val list = mutableListOf<String>()
                                for(i in 0 until predArr.length()) list.add(predArr.getString(i))
                                futurePredictions = list
                            }

                        } catch (e: Exception) { e.printStackTrace() }
                    }

                    onResult(WeeklyReportData(
                        feeling = doc.getString("feeling") ?: "",
                        keywords = (doc.get("keywords") as? List<*>)?.map { it.toString() } ?: emptyList(),
                        analysis = doc.getString("analysis") ?: "",
                        analysisJson = jsonStr,
                        analysisImageUrl = doc.getString("analysis_image_url") ?: "",
                        emotionLabels = emoLabels,
                        emotionDist = emD,
                        themeLabels = themeLabels,
                        themeDist = thD,
                        symbolDetails = symbolDetails,
                        themeAnalysis = themeAnalysis,
                        futurePredictions = futurePredictions, // ★ 값 할당
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

    // ==========================================
    // ★ [Pro Logic] 심층 분석 (프롬프트 언어 강제 수정됨)
    // ==========================================
    fun generateProReport(
        uid: String,
        weekKey: String,
        ctx: Context,
        selectedDreams: List<WeekEntry>? = null,
        callback: (Boolean) -> Unit
    ) {
        val processDreams = { entries: List<WeekEntry> ->
            if (entries.isEmpty()) {
                callback(false)
            } else {
                val userLocale = Locale.getDefault()
                val targetLang = "${userLocale.displayLanguage} (${userLocale.displayCountry})"
                val allDreamText = entries.joinToString(" || ") { "Dream Content: ${it.dream}" }

                // FirestoreManager.kt 내부의 generateProReport 함수 안의 prompt 부분

                val prompt = """
    Role: You are a Senior Psychoanalyst & Futurist specializing in Jungian Psychology.
    Task: Analyze the provided dreams as connected chapters of a single psychological story.
    
    Input Dreams: "$allDreamText"
    Target Language: $targetLang (MUST OUTPUT CONTENT IN THIS LANGUAGE)
    
    CRITICAL INSTRUCTIONS:
    1. [NO LABELS IN TEXT] Do NOT include labels like "Prediction 1:", "First dream:", or "Theme:". Just write the content.
    2. [KEYS vs VALUES] Keep JSON keys in ENGLISH (e.g., "Relationships"). Write ONLY the values in $targetLang.
    3. [COHESIVE NARRATIVE] Synthesize all dreams into ONE deep psychological essay. Do NOT list them sequentially.
    4. [THEME REASONING] For 'theme_analysis_text', explain WHY that theme is relevant based on specific dream symbols.
    
    JSON Structure:
    {
      "title": "A metaphorical title in $targetLang",
      "summary": "Summary in $targetLang",
      "symbol_details": [ { "keyword": "...", "description": "..." } ],
      "deep_analysis": [
        "Paragraph 1 (The Core Conflict): Analyze the hidden tension shared across dreams.",
        "Paragraph 2 (The Transformation): How the symbols evolve and interact.",
        "Paragraph 3 (The Resolution): Practical insight for real life."
      ],
      "future_predictions": [
       "Prediction for Career/Study (No label, just text)",
        "Prediction for Social Relationships (No label, just text)",
        "Prediction for Inner Self (No label, just text)",
        "Prediction for Love & Romance (No label, just text)",
        "Prediction for Wealth & Abundance (No label, just text)",
        "Prediction for Health & Energy (No label, just text)"
      ],
      "subconscious_message": "Inner voice in $targetLang",
      "lucky_action": "Action in $targetLang",
      "actionable_advice": ["Advice 1", "Advice 2", "Advice 3"],
      "theme_analysis_text": { 
         "Relationships": "Reasoning in $targetLang...", 
         "Achievement": "Reasoning in $targetLang...", 
         "Change": "Reasoning in $targetLang...", 
         "Anxiety": "Reasoning in $targetLang..." 
      },
      "emotion_stats": [Int...],
      "theme_stats": [Int...]
    }
""".trimIndent()

                callOpenAI(prompt) { jsonParsed ->
                    processAIResponse(uid, weekKey, jsonParsed, entries.size, "pro", callback)
                }
            }
        }

        if (selectedDreams != null) {
            processDreams(selectedDreams)
        } else {
            collectWeekEntriesLimited(uid, weekKey, limit = 15) { entries, count ->
                processDreams(entries)
            }
        }
    }


    fun aggregateDreamsForWeek(uid: String, weekKey: String, ctx: Context, callback: (Boolean) -> Unit) {
        collectWeekEntriesLimited(uid, weekKey, limit = 15) { entries, totalCount ->
            if (totalCount < 2) {
                db.collection("users").document(uid).collection("weekly_reports").document(weekKey)
                    .set(mapOf("stale" to true, "sourceCount" to totalCount), SetOptions.merge())
                callback(false)
                return@collectWeekEntriesLimited
            }

            val userLocale = Locale.getDefault()
            val userLang = "${userLocale.displayLanguage} (${userLocale.language})"

            val allDreamText = entries.joinToString(" | ") { it.dream }

            val prompt = """
                [Input Dreams]
                "$allDreamText"
                
                [Configuration]
                Target Language: $userLang
                
                [Role & Task]
                You are an expert dream analyst.
                Do NOT write a fictional story or a fairy tale.
                Instead, analyze the list of dreams above to find common psychological themes, recurring symbols, and emotional patterns for the week.
                Synthesize these findings into a cohesive "Weekly Dream Analysis Report".

                [Output Requirements]
                1. Language: All values must be strictly in $userLang.
                2. Tone: Insightful, analytical, yet warm and mystical (Professional Report Style).
                3. Format: Return ONLY valid JSON.

                [JSON Structure]
                {
                  "title": "A short, insight-based title for the week (e.g., 'A Week of Inner Growth')",
                  "summary": "A psychological summary analyzing the overall flow of the dreams. Explain what the user's subconscious is trying to say this week based on the common patterns. (3-4 sentences)",
                  "core_themes": ["Keyword1", "Keyword2"],
                  "emotion_stats": [Positive, Peaceful, Energetic, Immersed, Neutral, Confused, Anxious, Depressed] (Fill with integer values 1-10 representing the intensity of each emotion in the dreams),
                  "theme_stats": [Relationships, Achievement, Change, Anxiety] (Fill with integer values 1-10 representing the relevance of each theme)
                }
            """.trimIndent()

            callOpenAI(prompt) { jsonParsed ->
                processAIResponse(uid, weekKey, jsonParsed, totalCount, "base", callback)
            }
        }
    }

    // --- Common Response Processor ---
    private fun processAIResponse(
        uid: String,
        weekKey: String,
        jsonParsed: JSONObject?,
        count: Int,
        tier: String,
        callback: (Boolean) -> Unit
    ) {
        if (jsonParsed == null) {
            callback(false)
            return
        }

        try {
            val title = jsonParsed.optString("title")

            val analysisText = if (tier == "pro") {
                val deep = jsonParsed.opt("deep_analysis")
                if (deep is JSONArray) {
                    (0 until deep.length()).joinToString("\n\n") { deep.getString(it) }
                } else {
                    jsonParsed.optString("deep_analysis")
                }
            } else {
                jsonParsed.optString("summary")
            }

            val finalTitle = if (title.isNotBlank()) title else "Weekly Insight"

            val emoArray = jsonParsed.optJSONArray("emotion_stats")
            val themeArray = jsonParsed.optJSONArray("theme_stats")

            val aiEmoDist = if (emoArray != null && emoArray.length() == 8) {
                (0 until 8).map { emoArray.optDouble(it).toFloat() }
            } else {
                makeSpikyRandomDistribution(8, System.currentTimeMillis())
            }

            val aiThemeDist = if (themeArray != null && themeArray.length() == 4) {
                (0 until 4).map { themeArray.optDouble(it).toFloat() }
            } else {
                makeSpikyRandomDistribution(4, System.currentTimeMillis() + 100)
            }

            // Keyword Extraction (Compatibility with old and new structure)
            val keywords = mutableListOf<String>()
            val symArr = jsonParsed.optJSONArray("symbol_details")
            if (symArr != null) {
                for(i in 0 until symArr.length()) keywords.add(symArr.getJSONObject(i).optString("keyword"))
            } else {
                val kArr = jsonParsed.optJSONArray("core_themes")
                if(kArr != null) for(i in 0 until kArr.length()) keywords.add(kArr.getString(i))
            }

            val scoreData = calculateDreamScore(aiEmoDist, aiThemeDist)

            saveWeekly(
                db.collection("users").document(uid).collection("weekly_reports").document(weekKey),
                finalTitle,
                keywords,
                analysisText,
                jsonParsed.toString(),
                "",
                aiEmoDist,
                aiThemeDist,
                scoreData,
                count,
                tier
            ) { callback(true) }

        } catch (e: Exception) {
            e.printStackTrace()
            callback(false)
        }
    }

    private fun makeSpikyRandomDistribution(size: Int, seed: Long): List<Float> {
        val rng = Random(seed)
        val list = MutableList(size) { 0f }
        val mainIdx = rng.nextInt(size)
        list[mainIdx] = rng.nextInt(60, 90).toFloat()
        var subIdx = rng.nextInt(size)
        while(subIdx == mainIdx) subIdx = rng.nextInt(size)
        list[subIdx] = rng.nextInt(20, 40).toFloat()
        for(i in 0 until size) {
            if (i != mainIdx && i != subIdx) list[i] = rng.nextInt(0, 10).toFloat()
        }
        return list
    }

    private fun callOpenAI(prompt: String, onResult: (JSONObject?) -> Unit) {
        val reqBody = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(reqBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onResult(null) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val content = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    onResult(JSONObject(content))
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(null)
                }
            }
        })
    }

    private fun calculateDreamScore(emoDist: List<Float>, themeDist: List<Float>): DreamScoreResult {
        val pos = emoDist.getOrElse(0) { 0f }
        val calm = emoDist.getOrElse(1) { 0f }
        val anxious = emoDist.getOrElse(6) { 0f }
        val depressed = emoDist.getOrElse(7) { 0f }

        var rawScore = 70 + (pos * 0.5f) + (calm * 0.3f) - (anxious * 0.8f) - (depressed * 0.8f)
        val finalScore = rawScore.toInt().coerceIn(10, 99)

        val grade = when {
            finalScore >= 90 -> "S"
            finalScore >= 80 -> "A"
            finalScore >= 70 -> "B"
            finalScore >= 50 -> "C"
            else -> "D"
        }
        return DreamScoreResult(finalScore, grade)
    }

    fun listWeeklyReportKeys(uid: String, limit: Int, onResult: (List<String>) -> Unit) {
        db.collection("users").document(uid).collection("weekly_reports")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(limit.toLong()).get()
            .addOnSuccessListener { onResult(it.documents.map { d -> d.id }) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    private fun saveWeekly(
        ref: com.google.firebase.firestore.DocumentReference,
        feeling: String, kws: List<String>, analysis: String, analysisJson: String, imgUrl: String,
        emoDist: List<Float>, themeDist: List<Float>, score: DreamScoreResult, count: Int, tier: String,
        done: () -> Unit
    ) {
        val finalEmo = scaleToPercent(emoDist, 8)
        val finalTheme = scaleToPercent(themeDist, 4)

        val data = mapOf(
            "feeling" to feeling, "keywords" to kws, "analysis" to analysis,
            "analysis_json" to analysisJson, "analysis_image_url" to imgUrl,
            "emotionDist" to finalEmo, "themeDist" to finalTheme,
            "score" to score.score, "grade" to score.grade, "sourceCount" to count,
            "stale" to false, "lastRebuiltAt" to now(), "timestamp" to now(), "tier" to tier
        )
        ref.set(data, SetOptions.merge()).addOnCompleteListener { done() }
    }

    private fun collectWeekEntriesLimited(uid: String, weekKey: String, limit: Int, onResult: (List<WeekEntry>, Int) -> Unit) {
        val dates = weekDateKeys(weekKey)
        if (dates.isEmpty()) { onResult(emptyList(), 0); return }

        val allEntries = Collections.synchronizedList(mutableListOf<WeekEntry>())
        val counter = AtomicInteger(0)

        dates.forEach { dateKey ->
            db.collection("users").document(uid).collection("dreams").document(dateKey).collection("entries").get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.documents?.forEach { doc ->
                        allEntries.add(WeekEntry(doc.getLong("timestamp")?:0L, doc.getString("dream")?:"", doc.getString("result")?:"", doc.id))
                    }
                }
                if (counter.incrementAndGet() == dates.size) {
                    onResult(allEntries.filter { it.dream.isNotBlank() }.sortedBy { it.ts }.takeLast(limit), allEntries.size)
                }
            }
        }
    }

    private fun <T : Number> scaleToPercent(raw: List<T>, target: Int): List<Float> {
        val v = raw.map { it.toFloat().coerceAtLeast(0f) }
        val list = if (v.size >= target) v.take(target) else v + List(target - v.size) { 0f }
        val sum = list.sum()
        return if (sum > 0f) list.map { (it / sum) * 100f } else {
            List(target) { if(it==0) 100f else 0f }
        }
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
}