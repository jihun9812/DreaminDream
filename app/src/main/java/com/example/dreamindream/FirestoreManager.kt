// file: app/src/main/java/com/example/dreamindream/FirestoreManager.kt
package com.example.dreamindream

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

object FirestoreManager {
    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "FirestoreManager"

    // ───────────────────────────────
    // 꿈 저장/불러오기 (기존 유지)
    // ───────────────────────────────
    fun saveDream(userId: String, dream: String, result: String, onComplete: (() -> Unit)? = null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val dateKey = sdf.format(Date())
        val entry = mapOf(
            "dream" to dream,
            "result" to result,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("users").document(userId)
            .collection("dreams").document(dateKey)
            .collection("entries")
            .add(entry)
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }

    fun getAllDreamDates(context: Context, userId: String, onResult: (Set<LocalDate>) -> Unit) {
        val prefs = context.getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        db.collection("users").document(userId).collection("dreams")
            .get()
            .addOnSuccessListener { snapshot ->
                val dateSet = mutableSetOf<LocalDate>()
                val documents = snapshot.documents
                var completed = 0
                if (documents.isEmpty()) { onResult(emptySet()); return@addOnSuccessListener }

                for (doc in documents) {
                    val dateStr = doc.id
                    val parsedDate = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                    if (parsedDate != null) dateSet.add(parsedDate)

                    doc.reference.collection("entries").get()
                        .addOnSuccessListener { entries ->
                            val jsonArray = JSONArray()
                            for (entry in entries) {
                                val dream = entry.getString("dream") ?: ""
                                val result = entry.getString("result") ?: ""
                                jsonArray.put(JSONObject().apply {
                                    put("dream", dream); put("result", result)
                                })
                            }
                            editor.putString(dateStr, jsonArray.toString())
                            editor.apply()
                            if (++completed == documents.size) onResult(dateSet)
                        }
                        .addOnFailureListener {
                            if (++completed == documents.size) onResult(dateSet)
                        }
                }
            }
            .addOnFailureListener { onResult(emptySet()) }
    }

    // ───────────────────────────────
    // 주간 리포트 (기존 유지 + 확장)
    // ───────────────────────────────
    fun saveWeeklyReport(userId: String, feeling: String, keywords: List<String>, analysis: String) {
        val weekKey = WeekUtils.weekKey()
        val report = mapOf(
            "feeling" to feeling,
            "keywords" to keywords,
            "analysis" to analysis,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("users").document(userId)
            .collection("weekly_reports").document(weekKey)
            .set(report, SetOptions.merge())
    }

    /** 기존 시그니처(레거시 호환) */
    fun loadWeeklyReport(userId: String, onResult: (String, List<String>, String) -> Unit) {
        val weekKey = WeekUtils.weekKey()
        loadWeeklyReport(userId, weekKey, onResult)
    }

    /** 기존 시그니처(weekKey 지정, 레거시 호환) */
    fun loadWeeklyReport(userId: String, weekKey: String, onResult: (String, List<String>, String) -> Unit) {
        db.collection("users").document(userId)
            .collection("weekly_reports").document(weekKey)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val feeling = doc.getString("feeling") ?: ""
                    val keywords = doc.get("keywords") as? List<String> ?: emptyList()
                    val analysis = doc.getString("analysis") ?: ""
                    onResult(feeling, keywords, analysis)
                } else onResult("", emptyList(), "")
            }
            .addOnFailureListener { onResult("", emptyList(), "") }
    }

    /** ✅ 신규 시그니처: 세분화 결과까지 로드 */
    fun loadWeeklyReport(
        userId: String,
        weekKey: String,
        onResult: (String, List<String>, String, List<String>, List<Float>, List<String>, List<Float>) -> Unit
    ) {
        db.collection("users").document(userId)
            .collection("weekly_reports").document(weekKey)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val feeling = doc.getString("feeling") ?: ""
                    val keywords = doc.get("keywords") as? List<String> ?: emptyList()
                    val analysis = doc.getString("analysis") ?: ""
                    val emoLabels = (doc.get("emotionLabels") as? List<*>)?.map { it.toString() }
                        ?: listOf("긍정","평온","활력","몰입","중립","혼란","불안","우울/피로")
                    val emoDist = (doc.get("emotionDist") as? List<*>)?.map { (it as Number).toFloat() }
                        ?: List(emoLabels.size) { 0f }
                    val themeLabels = (doc.get("themeLabels") as? List<*>)?.map { it.toString() }
                        ?: listOf("관계","성취","변화","불안요인")
                    val themeDist = (doc.get("themeDist") as? List<*>)?.map { (it as Number).toFloat() }
                        ?: List(themeLabels.size) { 0f }

                    onResult(feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
                } else {
                    onResult("", emptyList(), "", emptyList(), emptyList(), emptyList(), emptyList())
                }
            }
            .addOnFailureListener {
                onResult("", emptyList(), "", emptyList(), emptyList(), emptyList(), emptyList())
            }
    }

    fun deleteWeeklyReport(userId: String, weekKey: String, onComplete: (() -> Unit)? = null) {
        db.collection("users").document(userId)
            .collection("weekly_reports").document(weekKey)
            .delete()
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }

    /** 최신순 주 키 목록 */
    fun listWeeklyReportKeys(userId: String, limit: Int = 26, onResult: (List<String>) -> Unit) {
        db.collection("users").document(userId)
            .collection("weekly_reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.map { it.id }) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    /** 가장 최근 리포트 키 */
    fun getLatestWeeklyReportKey(userId: String, onResult: (String?) -> Unit) {
        db.collection("users").document(userId)
            .collection("weekly_reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.firstOrNull()?.id) }
            .addOnFailureListener { onResult(null) }
    }

    // ───────────────────────────────
    // ✅ 주간 정밀 집계: 7일 모든 꿈 텍스트 모아서 저장
    // ───────────────────────────────
    fun aggregateDreamsForWeek(
        userId: String,
        weekKey: String,
        callback: (Boolean) -> Unit
    ) {
        val dates = WeekUtils.weekDateKeys(weekKey) // yyyy-MM-dd 7개
        if (dates.isEmpty()) { callback(false); return }

        val collected = mutableListOf<String>()
        var fetched = 0

        dates.forEach { dateKey ->
            db.collection("users").document(userId)
                .collection("dreams").document(dateKey)
                .collection("entries")
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { d ->
                        d.getString("dream")?.let { if (it.isNotBlank()) collected.add(it) }
                        d.getString("result")?.let { if (it.isNotBlank()) collected.add(it) } // 보조 텍스트도 반영
                    }
                    if (++fetched == dates.size) finishAggregate(userId, weekKey, collected, callback)
                }
                .addOnFailureListener {
                    if (++fetched == dates.size) finishAggregate(userId, weekKey, collected, callback)
                }
        }
    }

    private fun finishAggregate(userId: String, weekKey: String, texts: List<String>, callback: (Boolean) -> Unit) {
        if (texts.size < 2) { callback(false); return } // 최소 2개 이상일 때만 생성

        val result = EmotionAnalyzer.analyzeWeek(texts)

        val payload = hashMapOf(
            "feeling" to result.feeling,
            "keywords" to result.keywords,
            "analysis" to result.analysis,
            "emotionLabels" to result.emotionLabels,
            "emotionDist" to result.emotionDist,
            "themeLabels" to result.themeLabels,
            "themeDist" to result.themeDist,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("users").document(userId)
            .collection("weekly_reports").document(weekKey)
            .set(payload, SetOptions.merge())
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { e ->
                Log.w(TAG, "aggregate failed: ${e.message}", e)
                callback(false)
            }
    }

    // ───────────────────────────────
    // 프로필 (기존 유지)
    // ───────────────────────────────
    fun saveUserProfile(userId: String, userProfile: Map<String, Any>, onComplete: (() -> Unit)? = null) {
        db.collection("users").document(userId)
            .set(userProfile, SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }

    fun getUserProfile(userId: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc -> onResult(if (doc.exists()) doc.data else null) }
            .addOnFailureListener { onResult(null) }
    }

    // ───────────────────────────────
    // 일일 운세 저장/조회 (기존 유지)
    // ───────────────────────────────
    fun saveDailyFortune(userId: String, dateKey: String, payload: JSONObject, onComplete: (() -> Unit)? = null) {
        try {
            val lucky = payload.optJSONObject("lucky") ?: JSONObject()
            val emotions = payload.optJSONObject("emotions") ?: JSONObject()
            val sections = payload.optJSONObject("sections") ?: JSONObject()
            val keywords = (0 until (payload.optJSONArray("keywords")?.length() ?: 0))
                .mapNotNull { payload.optJSONArray("keywords")?.optString(it) }

            val scores = hashMapOf<String, Int>().apply {
                put("overall", sections.optJSONObject("overall")?.optInt("score") ?: -1)
                put("love",    sections.optJSONObject("love")?.optInt("score") ?: -1)
                put("study",   sections.optJSONObject("study")?.optInt("score") ?: -1)
                put("work",    sections.optJSONObject("work")?.optInt("score") ?: -1)
                put("money",   sections.optJSONObject("money")?.optInt("score") ?: -1)
                put("lotto",   sections.optJSONObject("lotto")?.optInt("score") ?: -1)
            }

            val doc = hashMapOf(
                "date" to dateKey,
                "timestamp" to System.currentTimeMillis(),
                "payload" to payload.toString(),
                "lucky_color" to lucky.optString("colorHex"),
                "lucky_number" to lucky.optInt("number", -1),
                "lucky_time" to lucky.optString("time"),
                "emotions" to mapOf(
                    "positive" to emotions.optInt("positive", -1),
                    "neutral"  to emotions.optInt("neutral", -1),
                    "negative" to emotions.optInt("negative", -1)
                ),
                "scores" to scores,
                "keywords" to keywords
            )

            db.collection("users").document(userId)
                .collection("daily_fortunes").document(dateKey)
                .set(doc, SetOptions.merge())
                .addOnSuccessListener { onComplete?.invoke() }
                .addOnFailureListener { onComplete?.invoke() }
        } catch (_: Exception) { onComplete?.invoke() }
    }

    fun loadDailyFortune(userId: String, dateKey: String, onResult: (JSONObject?) -> Unit) {
        db.collection("users").document(userId)
            .collection("daily_fortunes").document(dateKey)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val raw = doc.getString("payload").orEmpty()
                    val obj = runCatching { JSONObject(raw) }.getOrNull()
                    onResult(obj)
                } else onResult(null)
            }
            .addOnFailureListener { onResult(null) }
    }

    fun getLatestDailyFortuneKey(userId: String, onResult: (String?) -> Unit) {
        db.collection("users").document(userId)
            .collection("daily_fortunes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.firstOrNull()?.id) }
            .addOnFailureListener { onResult(null) }
    }

    fun listDailyFortuneKeys(userId: String, limit: Int = 30, onResult: (List<String>) -> Unit) {
        db.collection("users").document(userId)
            .collection("daily_fortunes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.map { it.id }) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun deleteDailyFortune(userId: String, dateKey: String, onComplete: (() -> Unit)? = null) {
        db.collection("users").document(userId)
            .collection("daily_fortunes").document(dateKey)
            .delete()
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }

    // ───────────────────────────────
    // 레거시 마이그레이션 (기존 유지)
    // ───────────────────────────────
    fun migrateLocalDreams(context: Context, userId: String, alsoUploadToFirestore: Boolean = true, onComplete: ((Int) -> Unit)? = null) {
        val legacyPrefs = context.getSharedPreferences("dream_history", Context.MODE_PRIVATE)
        val newPrefs = context.getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)
        if (newPrefs.getBoolean("migrated_from_legacy", false)) { onComplete?.invoke(0); return }
        val all = legacyPrefs.all
        var migrated = 0
        val editor = newPrefs.edit()

        all.forEach { (key, value) ->
            val isDateKey = Regex("""\d{4}-\d{2}-\d{2}""").matches(key)
            if (isDateKey && value is String && !newPrefs.contains(key)) {
                editor.putString(key, value); migrated++
                if (alsoUploadToFirestore) {
                    runCatching {
                        val jsonArr = JSONArray(value)
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.optJSONObject(i) ?: continue
                            val dream = obj.optString("dream", "")
                            val result = obj.optString("result", "")
                            if (dream.isNotBlank() || result.isNotBlank()) {
                                db.collection("users").document(userId)
                                    .collection("dreams").document(key)
                                    .collection("entries")
                                    .add(mapOf("dream" to dream, "result" to result, "timestamp" to System.currentTimeMillis()))
                            }
                        }
                    }
                }
            }
        }
        editor.putBoolean("migrated_from_legacy", true).apply()
        onComplete?.invoke(migrated)
    }
}
