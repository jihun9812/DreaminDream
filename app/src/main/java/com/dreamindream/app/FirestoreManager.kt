// app/src/main/java/com/example/dreamindream/FirestoreManager.kt
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object FirestoreManager {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val TAG = "FirestoreManager"
    private fun now() = System.currentTimeMillis()

    private val http by lazy { OkHttpClient() }
    private val apiKey by lazy { BuildConfig.OPENAI_API_KEY }

    private fun emoLabels(res: android.content.res.Resources) = listOf(
        res.getString(R.string.emo_positive),
        res.getString(R.string.emo_calm),
        res.getString(R.string.emo_vitality),
        res.getString(R.string.emo_flow),
        res.getString(R.string.emo_neutral),
        res.getString(R.string.emo_confusion),
        res.getString(R.string.emo_anxiety),
        res.getString(R.string.emo_depression_fatigue)
    )
    private fun themeLabels(res: android.content.res.Resources) = listOf(
        res.getString(R.string.theme_rel),
        res.getString(R.string.theme_achieve),
        res.getString(R.string.theme_change),
        res.getString(R.string.theme_risk)
    )

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
    fun thisWeekKey(): String {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        val y = cal.get(Calendar.YEAR)
        val w = cal.get(Calendar.WEEK_OF_YEAR)
        return String.format(Locale.US, "%04d-W%02d", y, w)
    }
    fun weekDateKeys(weekKey: String): List<String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        return runCatching {
            val parts = weekKey.split("-W")
            val y = parts[0].toInt()
            val w = parts[1].toInt()
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                minimalDaysInFirstWeek = 4
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.WEEK_OF_YEAR, w)
                set(Calendar.YEAR, y)
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

    private fun <T : Number> scaleToPercent(raw: List<T>, target: Int): List<Float> {
        val v = raw.map { it.toFloat().coerceAtLeast(0f) }
        val list = when {
            v.isEmpty() -> List(target) { 0f }
            v.size == target -> v
            v.size > target -> v.take(target)
            else -> v + List(target - v.size) { 0f }
        }
        val maxVal = list.maxOrNull() ?: 0f
        val sum = list.sum()

        if (maxVal >= 10f || sum >= 100f) {
            return if (sum > 0f && sum != 100f) list.map { it * (100f / sum) } else list
        }
        if (maxVal <= 1.01f) {
            val s = list.sum().takeIf { it > 0f } ?: 1f
            return list.map { (it / s) * 100f }
        }
        val s2 = list.sum().takeIf { it > 0f } ?: 1f
        return list.map { (it / s2) * 100f }
    }

    private fun topIndexOrNull(dist: List<Float>): Int? {
        if (dist.isEmpty()) return null
        var maxI = 0
        var maxV = dist[0]
        for (i in 1 until dist.size) {
            val v = dist[i]
            if (v > maxV) { maxV = v; maxI = i }
        }
        return if (maxV > 0f) maxI else null
    }
    private fun positivityScore(dist: List<Float>): Int {
        val sum = dist.sum().takeIf { it > 0f } ?: return 0
        val pos = (0..3).sumOf { dist[it].toDouble() }.toFloat()
        return ((pos / sum) * 100f).roundToInt().coerceIn(0, 100)
    }

    data class WeekEntry(val ts: Long, val dream: String, val interp: String)

    private fun localAnalyze(
        entries: List<WeekEntry>,
        res: android.content.res.Resources
    ): Pair<List<Float>, List<Float>> {
        val emo = FloatArray(8)
        val th  = FloatArray(4)

        val emoDict = mapOf(
            0 to listOf(
                "희망","기대","기쁨","행복","좋","즐겁","감사","사랑","뿌듯",
                "hope","joy","happi","delight","gratitud","love","proud","optimis","excite","cheer","good"
            ),
            1 to listOf(
                "평온","차분","안정","편안","여유","느긋",
                "calm","peace","seren","relax","comfor","chill","stable","steady","easygoing","laidback"
            ),
            2 to listOf(
                "활력","에너지","의욕","상쾌","기운","파워","생기",
                "ener","energy","motiv","fresh","vigor","vigour","power","livel","spirited","drive"
            ),
            3 to listOf(
                "몰입","집중","열중","열심","빠져",
                "flow","immers","focus","concentrat","absorb","engag","inthezone","zone"
            ),
            5 to listOf(
                "혼란","헷갈","갈등","모르겠","혼돈",
                "confus","puzzl","conflict","unsure","uncertain","chaos","mess"
            ),
            6 to listOf(
                "불안","걱정","긴장","초조","두려움","공포","염려","근심",
                "anx","worr","tens","nerv","fear","scare","terr","concern","uneas","stress","panic"
            ),
            7 to listOf(
                "우울","피곤","지침","무기력","슬픔","허탈","고단","피로",
                "depress","tired","exhaust","fatig","powerless","sad","blue","letharg","drain","burnout","weary"
            )
        )

        val themeDict = mapOf(
            0 to listOf(
                "가족","친구","연인","사람","관계","대화","만남","팀","동료","부모","형제","연락",
                "family","friend","partner","relationship","people","talk","conversation","meet","team","colleague",
                "parent","mother","father","sibling","girlfriend","boyfriend","wife","husband"
            ),
            1 to listOf(
                "성공","성취","시험","공부","일","프로젝트","승진","합격","목표","성과","점수","우승",
                "success","achiev","exam","test","study","work","project","promotion","pass","goal","result","score","win","grade"
            ),
            2 to listOf(
                "변화","이사","이직","전학","시작","종료","이동","전환","새로","끝","옮기","교체",
                "change","move","relocat","transfer","start","begin","end","finish","shift","transition","switch","new","quit","leave"
            ),
            3 to listOf(
                "위험","사고","문제","실패","손해","리스크","불안요소","위협","불상사","사건",
                "risk","danger","accident","problem","fail","failure","loss","threat","hazard","crisis","trouble","incident"
            )
        )

        val texts = entries.joinToString("\n") { (it.dream + " " + it.interp).lowercase(Locale.getDefault()) }
        val tokenRegex = Regex("""\p{L}+""")
        val tokens = tokenRegex.findAll(texts).map { it.value }.toList()

        fun countByPrefixes(prefixes: List<String>): Int {
            var hits = 0
            outer@ for (p in prefixes) {
                val pref = p.lowercase(Locale.getDefault())
                for (t in tokens) {
                    if (t.startsWith(pref)) { hits++; continue@outer }
                }
            }
            return hits
        }

        for ((idx, kws) in emoDict) emo[idx] = countByPrefixes(kws).toFloat()
        val nonNeutral = emo[0] + emo[1] + emo[2] + emo[3] + emo[5] + emo[6] + emo[7]
        emo[4] = max(0f, entries.size * 0.5f - nonNeutral * 0.2f)

        for ((idx, kws) in themeDict) th[idx] = countByPrefixes(kws).toFloat()

        val emoP = scaleToPercent(emo.toList(), 8)
        val thP  = scaleToPercent(th.toList(), 4)
        return emoP to thP
    }

    private fun fullKeywords(list: List<String>): List<String> =
        list.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().toList()
    private fun top1(list: List<String>): List<String> =
        list.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3).toList()

    fun saveDream(
        uid: String,
        dream: String,
        result: String,
        dateKey: String = todayKey(),
        onComplete: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val dayRef = db.collection("users").document(uid)
            .collection("dreams").document(dateKey)
        val entry = hashMapOf("dream" to dream, "result" to result, "timestamp" to now())
        dayRef.collection("entries").add(entry)
            .addOnSuccessListener {
                dayRef.set(
                    mapOf("count" to FieldValue.increment(1.0), "last_updated" to now()),
                    SetOptions.merge()
                ).addOnCompleteListener { onComplete?.invoke() }
                db.collection("users").document(uid)
                    .collection("weekly_reports").document(thisWeekKey())
                    .set(mapOf("stale" to true), SetOptions.merge())
            }
            .addOnFailureListener { e -> Log.e(TAG, "saveDream failed", e); onError?.invoke(e) }
    }

    fun loadDayEntries(uid: String, dateKey: String, onResult: (List<Map<String, Any>>) -> Unit) {
        db.collection("users").document(uid)
            .collection("dreams").document(dateKey)
            .collection("entries")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { d ->
                    mapOf(
                        "dream" to (d.getString("dream") ?: ""),
                        "result" to (d.getString("result") ?: ""),
                        "timestamp" to (d.getLong("timestamp") ?: 0L)
                    )
                }
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun loadWeeklySummary(
        uid: String,
        weekKey: String = thisWeekKey(),
        onResult: (feeling: String, keywords: List<String>, analysis: String, score: Int?) -> Unit
    ) {
        db.collection("users").document(uid)
            .collection("weekly_reports").document(weekKey)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { onResult("", emptyList(), "", null); return@addOnSuccessListener }

                val labels = (doc.get("emotionLabels") as? List<*>)?.map { it.toString() }
                val dist   = (doc.get("emotionDist")   as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() }
                val distP  = if (dist != null) scaleToPercent(dist, 8) else null

                val feeling = run {
                    val saved = doc.getString("feeling").orEmpty().trim()
                    val top = if (labels != null && distP != null && labels.size == distP.size) {
                        topIndexOrNull(distP)?.let { labels[it] }
                    } else null
                    when {
                        top != null -> top
                        saved.contains(",") -> saved.substringBefore(",").trim()
                        else -> saved
                    }
                }

                val keywordsHome = (doc.get("keywords_home") as? List<*>)?.mapNotNull { it?.toString() }
                    ?: ((doc.get("keywords") as? List<*>)?.mapNotNull { it?.toString() }?.let { top1(it) } ?: emptyList())

                val analysis = doc.getString("analysis").orEmpty()
                val scoreSaved = (doc.getLong("score") ?: doc.getDoubleOrNull("score"))?.toInt()
                val score = scoreSaved ?: (distP?.let { positivityScore(it) })
                onResult(feeling, keywordsHome, analysis, score)
            }
            .addOnFailureListener { onResult("", emptyList(), "", null) }
    }
    fun loadWeeklyReport(
        uid: String,
        weekKey: String = thisWeekKey(),
        onResult: (feeling: String, keywords: List<String>, analysis: String, score: Int?) -> Unit
    ) = loadWeeklySummary(uid, weekKey, onResult)

    fun loadWeeklyReportFull(
        ctx: Context,
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
        val emoDefault = emoLabels(ctx.resources)
        val themeDefault = themeLabels(ctx.resources)
        db.collection("users").document(uid)
            .collection("weekly_reports").document(weekKey)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult("", emptyList(), "", emoDefault, List(8) { 0f },
                        themeDefault, List(4) { 0f }, 0, 0L, "base", 0L, true)
                    return@addOnSuccessListener
                }

                val keywords = ((doc.get("keywords") as? List<*>)?.map { it.toString() } ?: emptyList()).let { fullKeywords(it) }

                val emoLabelsDoc  = (doc.get("emotionLabels") as? List<*>)?.map { it.toString() } ?: emoDefault
                val emoDistDocRaw = (doc.get("emotionDist")   as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: List(8){0f}
                val themeLabelsDoc  = (doc.get("themeLabels") as? List<*>)?.map { it.toString() } ?: themeDefault
                val themeDistDocRaw = (doc.get("themeDist")   as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: List(4){0f}

                val emoDist = scaleToPercent(emoDistDocRaw, 8)
                val themeDist = scaleToPercent(themeDistDocRaw, 4)

                val feeling = topIndexOrNull(emoDist)?.let { emoLabelsDoc[it] }
                    ?: doc.getString("feeling").orEmpty().let { s -> if (s.contains(",")) s.substringBefore(",").trim() else s }

                val analysis = doc.getString("analysis") ?: ""
                val sourceCount = (doc.getLong("sourceCount") ?: 0L).toInt()
                val lastRebuiltAt = doc.getLong("lastRebuiltAt") ?: 0L
                val tier = doc.getString("tier") ?: "base"
                val proAt = doc.getLong("proAt") ?: 0L
                val stale = doc.getBoolean("stale") ?: true

                onResult(
                    feeling, keywords, analysis,
                    emoLabelsDoc, emoDist, themeLabelsDoc, themeDist,
                    sourceCount, lastRebuiltAt, tier, proAt, stale
                )
            }
            .addOnFailureListener {
                onResult("", emptyList(), "",
                    emoDefault, List(8) { 0f },
                    themeDefault, List(4) { 0f },
                    0, 0L, "base", 0L, true)
            }
    }

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
                .addOnFailureListener { if (++done == dates.size) onResult(total) }
        }
    }
    fun countDreamEntriesToday(uid: String, onResult: (Int) -> Unit) {
        val dateKey = todayKey()
        db.collection("users").document(uid)
            .collection("dreams").document(dateKey)
            .collection("entries")
            .get()
            .addOnSuccessListener { onResult(it.size()) }
            .addOnFailureListener { onResult(0) }
    }

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
                        if (dream.isNotBlank() || interp.isNotBlank()) all += WeekEntry(ts, dream, interp)
                    }
                    if (++fetched == dates.size) {
                        val sorted = all.sortedBy { it.ts }
                        onResult(sorted.takeLast(limit), sorted.size)
                    }
                }
                .addOnFailureListener {
                    if (++fetched == dates.size) {
                        val sorted = all.sortedBy { it.ts }
                        onResult(sorted.takeLast(limit), sorted.size)
                    }
                }
        }
    }

    fun aggregateDreamsForWeek(uid: String, weekKey: String, ctx: Context, callback: (Boolean) -> Unit) {
        collectWeekEntriesLimited(uid, weekKey, limit = 4) { entries, totalCount ->
            val ref = db.collection("users").document(uid).collection("weekly_reports").document(weekKey)
            val dreams = entries.map { it.dream }.filter { it.isNotBlank() }
            val interps = entries.map { it.interp }.filter { it.isNotBlank() }
            val meta = mapOf(
                "snapshotDreams" to dreams,
                "snapshotInterps" to interps,
                "sourceCount" to totalCount,
                "rebuildPolicy" to "ADD_ONLY",
                "lastRebuiltAt" to now(),
                "timestamp" to now()
            )

            if (totalCount < 2) {
                ref.set(meta + mapOf("stale" to true), SetOptions.merge())
                    .addOnSuccessListener { callback(false) }
                    .addOnFailureListener { callback(false) }
                return@collectWeekEntriesLimited
            }

            val header = ctx.getString(R.string.week_prompt_intro) + "\n" +
                    ctx.getString(R.string.week_prompt_header_note)
            val rules = ctx.getString(R.string.week_prompt_rules)
            val prompt = buildString {
                appendLine(header)
                dreams.forEachIndexed { idx, d ->
                    appendLine(ctx.getString(R.string.tag_dream_original, idx + 1) + " " + d)
                }
                if (interps.any { it.isNotBlank() }) {
                    interps.forEachIndexed { idx, t ->
                        appendLine(ctx.getString(R.string.tag_interpret_text, idx + 1) + " " + t)
                    }
                }
                append(rules)
            }

            var emoDist: List<Float> = List(8) { 0f }
            var themeDist: List<Float> = List(4) { 0f }
            var feelingTop = ""
            var keywordsFull: List<String> = emptyList()
            var analysis = ""

            val reqBody = JSONObject().apply {
                put("model", "gpt-4.1-mini")
                put("temperature", 0.6)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                put("response_format", JSONObject().put("type", "json_schema").put("json_schema",
                    JSONObject()
                        .put("name", "weekly_report")
                        .put("schema", JSONObject()
                            .put("type", "object")
                            .put("properties", JSONObject()
                                .put("feeling", JSONObject().put("type", "string"))
                                .put("keywords", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")))
                                .put("analysis", JSONObject().put("type", "string"))
                                .put("score", JSONObject().put("type", "number"))
                                .put("emotionLabels", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")))
                                .put("emotionDist", JSONObject().put("type", "array").put("items", JSONObject().put("type", "number")))
                                .put("themeLabels", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")))
                                .put("themeDist", JSONObject().put("type", "array").put("items", JSONObject().put("type", "number")))
                            )
                            .put("required", JSONArray(listOf("feeling", "keywords", "analysis")))
                            .put("additionalProperties", false)
                        )
                ))
            }
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(reqBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            http.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.w(TAG, "GPT call failed, fallback to local: ${e.message}")
                    val (emoF, themeF) = localAnalyze(entries, ctx.resources)
                    emoDist = emoF; themeDist = themeF
                    feelingTop = topIndexOrNull(emoDist)?.let { emoLabels(ctx.resources)[it] } ?: ""
                    keywordsFull = fullKeywords(interps + dreams)
                    analysis = ""
                    saveWeekly(ref, meta, ctx, feelingTop, keywordsFull, analysis, emoDist, themeDist) { callback(false) }
                }

                override fun onResponse(call: Call, response: Response) {
                    val bodyStr = response.body?.string().orEmpty()
                    response.close()
                    try {
                        val js = JSONObject(bodyStr)
                        val content = js.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content")
                        val payload = JSONObject(content)

                        val emoRaw = payload.optJSONArray("emotionDist")?.let { a ->
                            (0 until a.length()).map { a.optDouble(it, 0.0).toFloat() }
                        } ?: emptyList()
                        val thRaw = payload.optJSONArray("themeDist")?.let { a ->
                            (0 until a.length()).map { a.optDouble(it, 0.0).toFloat() }
                        } ?: emptyList()
                        emoDist = scaleToPercent(emoRaw, 8)
                        themeDist = scaleToPercent(thRaw, 4)

                        feelingTop = topIndexOrNull(emoDist)?.let { emoLabels(ctx.resources)[it] }
                            ?: payload.optString("feeling", "")

                        val kwList = payload.optJSONArray("keywords")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()
                        keywordsFull = fullKeywords(kwList)

                        analysis = payload.optString("analysis", "")

                        if (emoDist.all { it == 0f } && themeDist.all { it == 0f }) {
                            val (emoF, themeF) = localAnalyze(entries, ctx.resources)
                            emoDist = emoF; themeDist = themeF
                            if (feelingTop.isBlank()) {
                                feelingTop = topIndexOrNull(emoDist)?.let { emoLabels(ctx.resources)[it] } ?: ""
                            }
                        }

                        saveWeekly(ref, meta, ctx, feelingTop, keywordsFull, analysis, emoDist, themeDist) { callback(true) }
                    } catch (e: Exception) {
                        Log.w(TAG, "GPT parse fail, fallback to local: ${e.message}")
                        val (emoF, themeF) = localAnalyze(entries, ctx.resources)
                        emoDist = emoF; themeDist = themeF
                        feelingTop = topIndexOrNull(emoDist)?.let { emoLabels(ctx.resources)[it] } ?: ""
                        keywordsFull = fullKeywords(interps + dreams)
                        analysis = ""
                        saveWeekly(ref, meta, ctx, feelingTop, keywordsFull, analysis, emoDist, themeDist) { callback(false) }
                    }
                }
            })
        }
    }

    private fun saveWeekly(
        ref: com.google.firebase.firestore.DocumentReference,
        meta: Map<String, Any>,
        ctx: Context,
        feelingTop: String,
        keywordsFull: List<String>,
        analysis: String,
        emoDist: List<Float>,
        themeDist: List<Float>,
        done: () -> Unit
    ) {
        val emoL = emoLabels(ctx.resources)
        val themeL = themeLabels(ctx.resources)
        val score = positivityScore(emoDist)

        val save = mapOf(
            "feeling" to feelingTop,
            "keywords" to keywordsFull,
            "keywords_home" to top1(keywordsFull),
            "analysis" to analysis,
            "score" to score,
            "emotionLabels" to emoL,
            "emotionDist" to emoDist,
            "themeLabels" to themeL,
            "themeDist" to themeDist,
            "stale" to false,
            "lastRebuiltAt" to now(),
            "timestamp" to now()
        )
        ref.set(meta + save, SetOptions.merge())
            .addOnCompleteListener { done() }
    }

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
                    "keywords" to fullKeywords(keywords),
                    "keywords_home" to top1(keywords),
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

    fun getAllDreamDates(ctx: Context, onResult: (Set<String>) -> Unit) {
        val uid = currentUid()
        if (uid == null) { onResult(emptySet()); return }
        getAllDreamDates(uid, null, onResult)
    }
    fun getAllDreamDates(ctx: Context, uid: String, monthPrefix: String? = null, onResult: (Set<String>) -> Unit) {
        getAllDreamDates(uid, monthPrefix, onResult)
    }
    fun getAllDreamDates(uid: String, monthPrefix: String? = null, onResult: (Set<String>) -> Unit) {
        db.collection("users").document(uid).collection("dreams").get()
            .addOnSuccessListener { snap ->
                val all = snap.documents.map { it.id }
                val filtered = monthPrefix?.let { pfx -> all.filter { it.startsWith(pfx) } } ?: all
                onResult(filtered.toSet())
            }
            .addOnFailureListener { onResult(emptySet()) }
    }

    fun updateDreamsForDate(
        uid: String = currentUid().orEmpty(),
        dateKey: String = todayKey(),
        itemsJson: String,
        onComplete: (() -> Unit)? = null
    ) {
        if (uid.isBlank()) { onComplete?.invoke(); return }
        val arr: JSONArray = runCatching {
            val raw = itemsJson.trim()
            if (raw.startsWith("[")) JSONArray(raw) else JSONArray().put(JSONObject(raw))
        }.getOrElse { JSONArray() }
        if (arr.length() == 0) { onComplete?.invoke(); return }

        val perDateAdded = mutableMapOf<String, Int>()
        var done = 0
        val total = arr.length()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val dk = obj.optString("dateKey", dateKey)
            val dream = obj.optString("dream", "")
            val result = obj.optString("result", "")
            val ts = if (obj.has("timestamp")) obj.optLong("timestamp") else now()
            val dayRef = db.collection("users").document(uid).collection("dreams").document(dk)
            val entry = mapOf("dream" to dream, "result" to result, "timestamp" to ts)

            dayRef.collection("entries").add(entry)
                .addOnSuccessListener {
                    perDateAdded[dk] = (perDateAdded[dk] ?: 0) + 1
                    if (++done == total) {
                        perDateAdded.forEach { (dkey, c) ->
                            db.collection("users").document(uid).collection("dreams").document(dkey)
                                .set(mapOf("count" to FieldValue.increment(c.toDouble()), "last_updated" to now()), SetOptions.merge())
                        }
                        db.collection("users").document(uid)
                            .collection("weekly_reports").document(thisWeekKey())
                            .set(mapOf("stale" to true), SetOptions.merge())
                            .addOnCompleteListener { onComplete?.invoke() }
                    }
                }
                .addOnFailureListener {
                    if (++done == total) onComplete?.invoke()
                }
        }
    }
    fun updateDreamsForDate(itemsJson: String, onComplete: (() -> Unit)? = null) {
        val uid = currentUid()
        if (uid == null) { onComplete?.invoke(); return }
        updateDreamsForDate(uid = uid, dateKey = todayKey(), itemsJson = itemsJson, onComplete = onComplete)
    }
    fun updateDreamsForDate(uid: String, dateKey: String, onCount: ((Int) -> Unit)? = null) {
        val dayRef = db.collection("users").document(uid).collection("dreams").document(dateKey)
        dayRef.collection("entries").get()
            .addOnSuccessListener { snap ->
                val c = snap.size()
                dayRef.set(mapOf("count" to c, "last_updated" to now()), SetOptions.merge())
                    .addOnCompleteListener { onCount?.invoke(c) }
            }
            .addOnFailureListener { onCount?.invoke(0) }
    }

    fun deleteWeeklyReport(uid: String, weekKey: String, onComplete: ((Boolean) -> Unit)? = null) {
        db.collection("users").document(uid)
            .collection("weekly_reports").document(weekKey)
            .delete()
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }

    fun saveDailyFortune(uid: String, dateKey: String = todayKey(),
                         payload: Map<String, Any>, onComplete: (() -> Unit)? = null) {
        db.collection("users").document(uid)
            .collection("daily_fortunes").document(dateKey)
            .set(payload + mapOf("timestamp" to now()), SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }
    fun saveDailyFortune(uid: String, dateKey: String = todayKey(),
                         payloadJson: JSONObject, onComplete: (() -> Unit)? = null) {
        saveDailyFortune(uid, dateKey, jsonObjectToMap(payloadJson), onComplete)
    }

    fun listWeeklyReportKeys(uid: String, limit: Int = 30, onResult: (List<String>) -> Unit) {
        db.collection("users").document(uid)
            .collection("weekly_reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .addOnSuccessListener { snap -> onResult(snap.documents.map { it.id }) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun resetWeeklyReport(uid: String, weekKey: String = thisWeekKey(), ctx: Context, onComplete: (() -> Unit)? = null) {
        val payload = mapOf(
            "feeling" to "",
            "keywords" to emptyList<String>(),
            "keywords_home" to emptyList<String>(),
            "analysis" to "",
            "emotionLabels" to emoLabels(ctx.resources),
            "emotionDist" to List(8) { 0f },
            "themeLabels" to themeLabels(ctx.resources),
            "themeDist" to List(4) { 0f },
            "sourceCount" to 0,
            "tier" to "base",
            "proAt" to 0L,
            "stale" to true,
            "lastRebuiltAt" to now(),
            "timestamp" to now()
        )
        db.collection("users").document(uid)
            .collection("weekly_reports").document(weekKey)
            .set(payload, SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }

    fun getUserProfile(uid: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc -> onResult(if (doc.exists()) doc.data else null) }
            .addOnFailureListener { onResult(null) }
    }
    fun saveUserProfile(uid: String, userProfile: Map<String, Any>, onComplete: (() -> Unit)? = null) {
        db.collection("users").document(uid).set(userProfile, SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { onComplete?.invoke() }
    }

    private fun jsonObjectToMap(js: JSONObject): Map<String, Any> {
        val out = mutableMapOf<String, Any>()
        val it = js.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = js.get(k)
            out[k] = when (v) {
                is JSONObject -> jsonObjectToMap(v)
                is JSONArray -> (0 until v.length()).map { idx ->
                    val item = v.get(idx)
                    when (item) {
                        is JSONObject -> jsonObjectToMap(item)
                        is JSONArray -> (0 until item.length()).map { j -> item.get(j) }
                        else -> item
                    }
                }
                else -> v
            }
        }
        return out
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.getDoubleOrNull(key: String): Double? {
        val v = get(key) ?: return null
        return if (v is Number) v.toDouble() else null
    }
}