package com.example.dreamindream

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FirestoreManager {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val TAG = "FirestoreManager"
    private fun now() = System.currentTimeMillis()

    // OpenAI
    private val http by lazy { OkHttpClient() }
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
    fun thisWeekKey(): String {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }
    fun weekDateKeys(weekKey: String): List<String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return runCatching {
            val (y, w) = weekKey.split("-W").let { it[0].toInt() to it[1].toInt() }
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                minimalDaysInFirstWeek = 4
                set(Calendar.YEAR, y); set(Calendar.WEEK_OF_YEAR, w); set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            (0..6).map { val s = fmt.format(cal.time); cal.add(Calendar.DAY_OF_MONTH, 1); s }
        }.getOrElse {
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                minimalDaysInFirstWeek = 4
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            (0..6).map { val s = fmt.format(cal.time); cal.add(Calendar.DAY_OF_MONTH, 1); s }
        }
    }
    fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    // ───────────── 꿈 저장 ─────────────
    fun saveDream(
        uid: String,
        dream: String,
        result: String,
        dateKey: String = todayKey(),
        onComplete: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val dayRef  = db.collection("users").document(uid)
            .collection("dreams").document(dateKey)
        val entry = hashMapOf("dream" to dream, "result" to result, "timestamp" to now())

        dayRef.collection("entries").add(entry)
            .addOnSuccessListener {
                dayRef.set(
                    mapOf("count" to FieldValue.increment(1.0), "last_updated" to now()),
                    SetOptions.merge()
                ).addOnCompleteListener { onComplete?.invoke() }

                // 새 꿈이 추가되면 이번 주 리포트를 'stale'로 표시 → 홈/리포트에서 자동 재집계
                val weekKey = thisWeekKey()
                db.collection("users").document(uid)
                    .collection("weekly_reports").document(weekKey)
                    .set(mapOf("stale" to true), SetOptions.merge())
            }
            .addOnFailureListener { e -> Log.e(TAG, "saveDream failed: ${e.message}", e); onError?.invoke(e) }
    }

    // ───────────── 일일 운세(daily_fortunes) ─────────────
    fun saveDailyFortune(uid: String, dateKey: String, payload: JSONObject, onComplete: (() -> Unit)? = null) {
        try {
            val lucky = payload.optJSONObject("lucky") ?: JSONObject()
            val emotions = payload.optJSONObject("emotions") ?: JSONObject()
            val sections = payload.optJSONObject("sections") ?: JSONObject()
            val keywords = (0 until (payload.optJSONArray("keywords")?.length() ?: 0))
                .mapNotNull { payload.optJSONArray("keywords")?.optString(it) }

            val scores = hashMapOf(
                "overall" to (sections.optJSONObject("overall")?.optInt("score") ?: -1),
                "love"    to (sections.optJSONObject("love")?.optInt("score") ?: -1),
                "study"   to (sections.optJSONObject("study")?.optInt("score") ?: -1),
                "work"    to (sections.optJSONObject("work")?.optInt("score") ?: -1),
                "money"   to (sections.optJSONObject("money")?.optInt("score") ?: -1),
                "lotto"   to (sections.optJSONObject("lotto")?.optInt("score") ?: -1),
            )

            val doc = hashMapOf(
                "date" to dateKey,
                "timestamp" to now(),
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

            db.collection("users").document(uid)
                .collection("daily_fortunes").document(dateKey)
                .set(doc, SetOptions.merge())
                .addOnSuccessListener { onComplete?.invoke() }
                .addOnFailureListener { onComplete?.invoke() }
        } catch (_: Exception) { onComplete?.invoke() }
    }

    fun loadDailyFortune(uid: String, dateKey: String, onResult: (JSONObject?) -> Unit) {
        db.collection("users").document(uid)
            .collection("daily_fortunes").document(dateKey)
            .get()
            .addOnSuccessListener { doc ->
                val raw = doc.getString("payload").orEmpty()
                val obj = runCatching { JSONObject(raw) }.getOrNull()
                onResult(if (doc.exists()) obj else null)
            }
            .addOnFailureListener { onResult(null) }
    }
    fun getLatestDailyFortuneKey(uid: String, onResult: (String?) -> Unit) {
        db.collection("users").document(uid)
            .collection("daily_fortunes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.firstOrNull()?.id) }
            .addOnFailureListener { onResult(null) }
    }
    fun listDailyFortuneKeys(uid: String, limit: Int = 30, onResult: (List<String>) -> Unit) {
        db.collection("users").document(uid)
            .collection("daily_fortunes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.map { it.id }) }
            .addOnFailureListener { onResult(emptyList()) }
    }
    fun deleteDailyFortune(uid: String, dateKey: String, onComplete: (() -> Unit)? = null) {
        db.collection("users").document(uid)
            .collection("daily_fortunes").document(dateKey)
            .delete()
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }

    // ───────────── 주간 리포트 저장/로드 ─────────────
    fun saveWeeklyReport(
        uid: String,
        feeling: String,
        keywords: List<String>,
        analysis: String,
        score: Int? = null,
        weekKey: String = thisWeekKey(),
        onComplete: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val ref  = db.collection("users").document(uid)
            .collection("weekly_reports").document(weekKey)

        val data = hashMapOf<String, Any>(
            "feeling" to feeling,
            "keywords" to keywords,
            "analysis" to analysis,
            "updatedAt" to now(),
            "timestamp" to now()
        ).apply { score?.let { put("score", it) } }

        ref.set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { e -> Log.e(TAG, "saveWeeklyReport failed: ${e.message}", e); onError?.invoke(e) }
    }

    fun loadWeeklyReport(uid: String, onResult: (feeling: String, keywords: List<String>, analysis: String) -> Unit) =
        loadWeeklyReport(uid, thisWeekKey()) { f, k, a, _ -> onResult(f, k, a) }

    fun loadWeeklyReport(
        uid: String,
        weekKey: String,
        onResult: (feeling: String, keywords: List<String>, analysis: String, score: Int?) -> Unit
    ) {
        db.collection("users").document(uid)
            .collection("weekly_reports").document(weekKey)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) { onResult("", emptyList(), "", null); return@addOnSuccessListener }
                val feeling  = snap.getString("feeling").orEmpty()
                val analysis = snap.getString("analysis").orEmpty()
                val score    = (snap.getLong("score") ?: snap.getDoubleOrNull("score"))?.toInt()
                val keywords = (snap.get("keywords") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                onResult(feeling, keywords, analysis, score)
            }
            .addOnFailureListener { onResult("", emptyList(), "", null) }
    }

    fun loadWeeklyReportFull(
        uid: String,
        weekKey: String,
        onResult: (
            feeling: String,
            keywords: List<String>,
            analysis: String,
            emotionLabels: List<String>,
            emotionDist: List<Float>,
            themeLabels: List<String>,
            themeDist: List<Float>,
            sourceCount: Int,
            lastRebuiltAt: Long,
            tier: String,
            proAt: Long,
            stale: Boolean
        ) -> Unit
    ) {
        db.collection("users").document(uid)
            .collection("weekly_reports").document(weekKey)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult("", emptyList(), "", emptyList(), emptyList(), emptyList(), emptyList(), 0, 0L, "base", 0L, true)
                    return@addOnSuccessListener
                }
                val feeling = doc.getString("feeling") ?: ""
                val keywords = (doc.get("keywords") as? List<*>)?.map { it.toString() } ?: emptyList()
                val analysis = doc.getString("analysis") ?: ""
                val emoLabels = (doc.get("emotionLabels") as? List<*>)?.map { it.toString() }
                    ?: listOf("긍정","평온","활력","몰입","중립","혼란","불안","우울/피로")
                val emoDist = (doc.get("emotionDist") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() }
                    ?: List(emoLabels.size) { 0f }
                val themeLabels = (doc.get("themeLabels") as? List<*>)?.map { it.toString() }
                    ?: listOf("관계","성취","변화","불안요인")
                val themeDist = (doc.get("themeDist") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() }
                    ?: List(themeLabels.size) { 0f }
                val sourceCount = (doc.getLong("sourceCount") ?: 0L).toInt()
                val lastRebuiltAt = doc.getLong("lastRebuiltAt") ?: (doc.getLong("timestamp") ?: 0L)
                val tier = doc.getString("tier") ?: "base"
                val proAt = doc.getLong("proAt") ?: 0L
                val stale = doc.getBoolean("stale") ?: false
                onResult(feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist, sourceCount, lastRebuiltAt, tier, proAt, stale)
            }
            .addOnFailureListener {
                onResult("", emptyList(), "", emptyList(), emptyList(), emptyList(), emptyList(), 0, 0L, "base", 0L, true)
            }
    }

    fun deleteWeeklyReport(uid: String, weekKey: String, onComplete: (() -> Unit)? = null) {
        db.collection("users").document(uid)
            .collection("weekly_reports").document(weekKey)
            .delete()
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }
    fun listWeeklyReportKeys(uid: String, limit: Int = 26, onResult: (List<String>) -> Unit) {
        db.collection("users").document(uid)
            .collection("weekly_reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.map { it.id }) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // ───── 프로필 & 달력 동기화 ─────
    fun getUserProfile(uid: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc -> onResult(if (doc.exists()) doc.data else null) }
            .addOnFailureListener { onResult(null) }
    }
    fun saveUserProfile(uid: String, userProfile: Map<String, Any>, onComplete: (() -> Unit)? = null) {
        db.collection("users").document(uid)
            .set(userProfile, SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }
    fun getAllDreamDates(context: Context, uid: String, onResult: (Set<LocalDate>) -> Unit) {
        val prefs = context.getSharedPreferences("dream_history_$uid", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        db.collection("users").document(uid).collection("dreams")
            .get()
            .addOnSuccessListener { snapshot ->
                val dateSet = mutableSetOf<LocalDate>()
                val documents = snapshot.documents
                if (documents.isEmpty()) { onResult(emptySet()); return@addOnSuccessListener }

                var completed = 0
                for (doc in documents) {
                    val dateStr = doc.id
                    runCatching { LocalDate.parse(dateStr) }.getOrNull()?.let { dateSet += it }

                    doc.reference.collection("entries").get()
                        .addOnSuccessListener { entries ->
                            val jsonArray = JSONArray()
                            entries.forEach { e ->
                                jsonArray.put(JSONObject().apply {
                                    put("dream", e.getString("dream") ?: "")
                                    put("result", e.getString("result") ?: "")
                                })
                            }
                            editor.putString(dateStr, jsonArray.toString())
                            editor.apply()
                            if (++completed == documents.size) onResult(dateSet)
                        }
                        .addOnFailureListener { if (++completed == documents.size) onResult(dateSet) }
                }
            }
            .addOnFailureListener { onResult(emptySet()) }
    }

    /** (구) 일별 count 합산 — 유지만 하고 사용 안 함 */
    fun countDreamsInWeek(uid: String, weekKey: String = thisWeekKey(), onResult: (Int) -> Unit) {
        val dates = weekDateKeys(weekKey)
        db.collection("users").document(uid).collection("dreams")
            .whereIn(FieldPath.documentId(), dates)
            .get()
            .addOnSuccessListener { snap ->
                val total = snap.documents.sumOf { (it.getLong("count") ?: 0L).toInt() }.coerceAtLeast(0)
                onResult(total)
            }
            .addOnFailureListener { Log.w(TAG, "countDreamsInWeek failed: ${it.message}"); onResult(0) }
    }

    /** ✅ 정확 집계(엔트리 개수) — UI/집계 모두 이 기준으로 통일 */
    fun countDreamEntriesForWeek(uid: String, weekKey: String = thisWeekKey(), onResult: (Int) -> Unit) {
        val dates = weekDateKeys(weekKey)
        if (dates.isEmpty()) { onResult(0); return }
        var total = 0
        var done = 0
        dates.forEach { dateKey ->
            db.collection("users").document(uid)
                .collection("dreams").document(dateKey)
                .collection("entries")
                .get()
                .addOnSuccessListener { snap ->
                    total += snap.size()
                    if (++done == dates.size) onResult(total)
                }
                .addOnFailureListener {
                    if (++done == dates.size) onResult(total)
                }
        }
    }

    // 주간 텍스트 수집(분석용)
    fun collectWeekTexts(uid: String, weekKey: String, onResult: (dreams: List<String>, interps: List<String>) -> Unit) {
        val dates = weekDateKeys(weekKey)
        if (dates.isEmpty()) { onResult(emptyList(), emptyList()); return }

        val dreams = mutableListOf<String>()
        val interps = mutableListOf<String>()
        var fetched = 0

        dates.forEach { dateKey ->
            db.collection("users").document(uid)
                .collection("dreams").document(dateKey)
                .collection("entries")
                .get()
                .addOnSuccessListener { snap ->
                    snap.forEach { d ->
                        d.getString("dream")?.takeIf { it.isNotBlank() }?.let { dreams += it }
                        d.getString("result")?.takeIf { it.isNotBlank() }?.let { interps += it }
                    }
                    if (++fetched == dates.size) onResult(dreams, interps)
                }
                .addOnFailureListener { if (++fetched == dates.size) onResult(dreams, interps) }
        }
    }

    // 주간 엔트리 전체 수집 → 타임스탬프 기준 정렬 후 최근 N개만 반환
    data class WeekEntry(val ts: Long, val dream: String, val interp: String)
    fun collectWeekEntriesLimited(
        uid: String,
        weekKey: String,
        limit: Int,
        onResult: (used: List<WeekEntry>, totalCount: Int) -> Unit
    ) {
        val dates = weekDateKeys(weekKey)
        if (dates.isEmpty()) { onResult(emptyList(), 0); return }

        val all = mutableListOf<WeekEntry>()
        var fetched = 0
        dates.forEach { dateKey ->
            db.collection("users").document(uid)
                .collection("dreams").document(dateKey)
                .collection("entries")
                .get()
                .addOnSuccessListener { snap ->
                    snap.forEach { d ->
                        val dream = d.getString("dream").orEmpty()
                        val interp = d.getString("result").orEmpty()
                        val ts = d.getLong("timestamp") ?: 0L
                        if (dream.isNotBlank() || interp.isNotBlank()) {
                            all += WeekEntry(ts, dream, interp)
                        }
                    }
                    if (++fetched == dates.size) {
                        val sorted = all.sortedBy { it.ts }
                        val limited = sorted.takeLast(limit)
                        onResult(limited, sorted.size)
                    }
                }
                .addOnFailureListener {
                    if (++fetched == dates.size) {
                        val sorted = all.sortedBy { it.ts }
                        val limited = sorted.takeLast(limit)
                        onResult(limited, sorted.size)
                    }
                }
        }
    }

    // 🔥 단일 파이프라인: GPT 스토리 요약 + 키워드/감정/점수 + 그래프 분포 저장
    fun aggregateDreamsForWeek(uid: String, weekKey: String, callback: (Boolean) -> Unit) {
        // 최근 4개만 분석에 사용, 전체 개수는 sourceCount로 저장
        collectWeekEntriesLimited(uid, weekKey, limit = 4) { entries, totalCount ->
            val ref = db.collection("users").document(uid)
                .collection("weekly_reports").document(weekKey)

            val dreams = entries.map { it.dream }.filter { it.isNotBlank() }
            val interps = entries.map { it.interp }.filter { it.isNotBlank() }

            val meta = mapOf(
                "snapshotDreams" to dreams,          // 사용된 최근 4개
                "snapshotInterps" to interps,
                "sourceCount" to totalCount,         // ✅ 실제 주간 엔트리 총 개수(정확 기준)
                "rebuildPolicy" to "ADD_ONLY",
                "tier" to "base",
                "lastRebuiltAt" to now(),
                "timestamp" to now()
            )

            if (totalCount < 2) {
                ref.set(meta + mapOf("stale" to true), SetOptions.merge())
                    .addOnSuccessListener { callback(false) }
                    .addOnFailureListener { callback(false) }
                return@collectWeekEntriesLimited
            }

            // ── GPT 프롬프트 ──
            val prompt = buildString {
                appendLine("아래는 사용자의 최근 1주 꿈 기록(최대 4개, 최신순)과 각 꿈에 대한 해몽/분석 텍스트입니다.")
                dreams.forEachIndexed { idx, d -> appendLine("[꿈 원문 ${idx + 1}] $d") }
                if (interps.any { it.isNotBlank() }) {
                    interps.forEachIndexed { idx, t -> appendLine("[해몽 텍스트 ${idx + 1}] $t") }
                }
                appendLine(
                    """
                    당신은 한국어로만 답하는 '주간 꿈 분석가'입니다.
                    아래 **출력 형식**과 **키워드 규칙**을 반드시 지키세요. 형식에서 벗어나는 말은 금지.

                    ★ 출력 형식(한국어, 다른 말 금지):
                    감정: <정서 한 단어 + 필요 시 ↑/↓>
                    키워드: <명사 2~3개, 쉼표로 구분>
                    점수: <0~100 사이 정수 한 개>
                    AI 분석: <2~4문장 요약 분석(여러 꿈의 공통 서사/연결, 1~2주 전망 간단 포함). 수면/위생/일상루틴 언급 금지>

                    ★ 키워드 규칙(매우 중요):
                    - 반드시 ‘해몽 텍스트들’에서만 추출(해몽이 없을 때만 꿈 원문에서 추출)
                    - 일반 명사만 사용(동사/형용사/숫자/영어/이모지/기호/한 글자/7자 초과 금지)
                    - 조사·어미 제거, 메타 단어 금지
                    - 중복 최소화, 최종 2~3개만 남김
                    """.trimIndent()
                )
            }

            val body = JSONObject().apply {
                put("model", "gpt-4.1-mini")
                put("temperature", 0.6)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                put("max_tokens", 360)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            http.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(TAG, "weekly GPT fail", e)
                    val dist = EmotionAnalyzer.analyzeWeek(dreams, interps)
                    val payload = meta + mapOf(
                        "stale" to true,
                        "feeling" to "",
                        "keywords" to emptyList<String>(),
                        "analysis" to "",
                        "emotionLabels" to dist.emotionLabels,
                        "emotionDist" to dist.emotionDist,
                        "themeLabels" to dist.themeLabels,
                        "themeDist" to dist.themeDist
                    )
                    ref.set(payload, SetOptions.merge())
                        .addOnSuccessListener { callback(false) }
                        .addOnFailureListener { callback(false) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val content = try {
                            JSONObject(resp.body?.string() ?: "")
                                .getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content").trim()
                        } catch (_: Exception) { "" }

                        val feeling = Regex("""감정:\s*([^\n]+)""").find(content)?.groupValues?.get(1)?.trim().orEmpty()
                        val keywords = Regex("""키워드:\s*([^\n]+)""").find(content)
                            ?.groupValues?.get(1)?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                        val score = Regex("""점수:\s*(\d{1,3})""").find(content)?.groupValues?.get(1)?.toIntOrNull()
                        val analysis = Regex("""AI ?분석:\s*(.*)""", RegexOption.DOT_MATCHES_ALL)
                            .find(content)?.groupValues?.get(1)?.trim().orEmpty()

                        val dist = EmotionAnalyzer.analyzeWeek(dreams, interps)

                        val payload = meta + mapOf(
                            "stale" to false,          // 신선
                            "tier" to "base",          // 심화는 리셋
                            "feeling" to feeling,
                            "keywords" to keywords.take(3),
                            "analysis" to analysis,
                            "score" to (score ?: 0),
                            "emotionLabels" to dist.emotionLabels,
                            "emotionDist" to dist.emotionDist,
                            "themeLabels" to dist.themeLabels,
                            "themeDist" to dist.themeDist
                        )

                        ref.set(payload, SetOptions.merge())
                            .addOnSuccessListener { callback(true) }
                            .addOnFailureListener { callback(false) }
                    }
                }
            })
        }
    }

    // PRO(심화) 본문만 갱신
    fun saveProUpgrade(
        uid: String,
        weekKey: String,
        feeling: String,
        keywords: List<String>,
        analysis: String,
        model: String,
        onDone: (() -> Unit)? = null
    ) {
        db.collection("users").document(uid)
            .collection("weekly_reports").document(weekKey)
            .set(
                mapOf(
                    "feeling" to feeling,
                    "keywords" to keywords,
                    "analysis" to analysis,
                    "tier" to "pro",
                    "proAt" to now(),
                    "proModel" to model,
                    "timestamp" to now()
                ),
                SetOptions.merge()
            )
            .addOnCompleteListener { onDone?.invoke() }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.getDoubleOrNull(key: String): Double? {
        val v = get(key) ?: return null
        return if (v is Number) v.toDouble() else null
    }
}
