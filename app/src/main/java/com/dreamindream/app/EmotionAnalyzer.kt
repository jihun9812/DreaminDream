package com.dreamindream.app

import android.content.Context
import android.content.res.Resources
import java.util.Locale
import kotlin.math.max

data class WeekAnalysis(
    val feeling: String,
    val keywords: List<String>,
    val analysis: String,
    val emotionLabels: List<String>,
    val emotionDist: List<Float>,
    val themeLabels: List<String>,
    val themeDist: List<Float>
)

/**
 * 기본(베이직) 주간 분석기.
 * - Pro(심화) 결과가 없을 때만 사용되는 fallback 경로.
 */
object EmotionAnalyzer {

    // ── 리소스에서 채워지는 캐시 ───────────────────────────────────
    private var inited = false
    private lateinit var EMOTION_LABELS: List<String>
    private lateinit var THEME_LABELS: List<String>
    private lateinit var EMOTION_DICT: Map<String, List<String>>
    private lateinit var THEME_DICT: Map<String, List<String>>
    private lateinit var JOSA: Set<String>
    private lateinit var STOPWORDS: Set<String>
    private lateinit var TOKEN_TAILS: List<String>

    private fun ensureInit(res: Resources) {
        if (inited) return

        // 라벨
        EMOTION_LABELS = listOf(
            res.getString(R.string.emo_positive),
            res.getString(R.string.emo_calm),
            res.getString(R.string.emo_vitality),
            res.getString(R.string.emo_flow),
            res.getString(R.string.emo_neutral),
            res.getString(R.string.emo_confusion),
            res.getString(R.string.emo_anxiety),
            res.getString(R.string.emo_depression_fatigue),
        )
        THEME_LABELS = listOf(
            res.getString(R.string.theme_rel),
            res.getString(R.string.theme_achieve),
            res.getString(R.string.theme_change),
            res.getString(R.string.theme_risk),
        )

        // 감정/테마 사전
        EMOTION_DICT = mapOf(
            res.getString(R.string.emo_positive) to res.getStringArray(R.array.emotion_dict_positive).toList(),
            res.getString(R.string.emo_calm)     to res.getStringArray(R.array.emotion_dict_calm).toList(),
            res.getString(R.string.emo_vitality) to res.getStringArray(R.array.emotion_dict_vitality).toList(),
            res.getString(R.string.emo_flow)     to res.getStringArray(R.array.emotion_dict_flow).toList(),
            res.getString(R.string.emo_neutral)  to res.getStringArray(R.array.emotion_dict_neutral).toList(),
            res.getString(R.string.emo_confusion)to res.getStringArray(R.array.emotion_dict_confusion).toList(),
            res.getString(R.string.emo_anxiety)  to res.getStringArray(R.array.emotion_dict_anxiety).toList(),
            res.getString(R.string.emo_depression_fatigue) to res.getStringArray(R.array.emotion_dict_depression_fatigue).toList(),
        )
        THEME_DICT = mapOf(
            res.getString(R.string.theme_rel)     to res.getStringArray(R.array.theme_dict_rel).toList(),
            res.getString(R.string.theme_achieve) to res.getStringArray(R.array.theme_dict_achieve).toList(),
            res.getString(R.string.theme_change)  to res.getStringArray(R.array.theme_dict_change).toList(),
            res.getString(R.string.theme_risk)    to res.getStringArray(R.array.theme_dict_risk).toList(),
        )

        // 형태소/불용어
        JOSA        = res.getStringArray(R.array.josa_ko).toSet()
        STOPWORDS   = res.getStringArray(R.array.stopwords_ko).toSet()
        TOKEN_TAILS = res.getStringArray(R.array.token_tails_ko).toList()

        inited = true
    }

    // ── public API ────────────────────────────────────────────────
    fun analyzeWeek(ctx: Context, dreams: List<String>, interps: List<String>): WeekAnalysis {
        ensureInit(ctx.resources)

        val emoBase   = normalize((dreams + interps).joinToString("\n"))
        val emoTokens = tokenize(emoBase)

        val interpBase   = normalize(interps.joinToString("\n"))
        val interpTokens = tokenize(interpBase)

        val emoDist   = normalizePercent(score(emoTokens, EMOTION_DICT))
        val themeDist = normalizePercent(score(emoTokens, THEME_DICT))
        val feeling   = topFeeling(EMOTION_LABELS, emoDist, ctx.getString(R.string.ea_trend_up))

        val dreamsJoined = dreams.joinToString("\n")
        val kw = topKeywordsFromInterps(interpTokens, 6)
            .filter { dreamsJoined.contains(it.replace(" ", "")) }
            .take(3)
            .ifEmpty {
                topKeywordsFromInterps(tokenize(normalize(dreamsJoined)), 5).take(3)
            }

        val analysis = buildNarrative(
            res = ctx.resources,
            dreams = dreams,
            interps = interps,
            feeling = feeling,
            themeLabels = THEME_LABELS,
            themeDist = themeDist
        )

        return WeekAnalysis(feeling, kw, analysis, EMOTION_LABELS, emoDist, THEME_LABELS, themeDist)
    }

    // ── text utils ────────────────────────────────────────────────
    private fun normalize(s: String): String =
        s.lowercase(Locale.KOREA).replace(Regex("[^ㄱ-힣a-zA-Z0-9\\s]"), " ")

    private fun tokenize(s: String): List<String> =
        s.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { stripJosaAndTail(it) }
            .map { it.replace(Regex("\\d"), "") }
            .filter { it.length in 2..8 && it !in JOSA && it !in STOPWORDS }

    private fun stripJosaAndTail(t: String): String {
        var x = t
        val hit = JOSA.firstOrNull { j -> x.length > j.length && x.endsWith(j) }
        if (hit != null) x = x.dropLast(hit.length)
        val tail = TOKEN_TAILS.firstOrNull { t2 -> x.length > t2.length && x.endsWith(t2) }
        if (tail != null) x = x.dropLast(tail.length)
        return x
    }

    private fun score(tokens: List<String>, dict: Map<String, List<String>>): FloatArray {
        val arr = FloatArray(dict.size)
        dict.entries.forEachIndexed { i, (_, kws) ->
            var s = 0f
            for (tok in tokens) for (k in kws)
                if (tok == k || tok.contains(k.lowercase(Locale.KOREA)))
                    s += if (tok == k) 1.2f else 1.0f
            arr[i] = s
        }
        return arr
    }

    private fun normalizePercent(arr: FloatArray): List<Float> {
        val sum = arr.sum().takeIf { it > 0f } ?: 1f
        return arr.map { (it / sum) * 100f }
    }

    private fun topFeeling(labels: List<String>, dist: List<Float>, upSuffix: String): String {
        val idx = maxIndex(dist); val label = labels[idx]
        return when (label) {
            labels[5], labels[6], labels[7] -> label + upSuffix // 혼란/불안/우울·피로
            labels[0], labels[2], labels[3] -> label + upSuffix // 긍정/활력/몰입
            else -> label
        }
    }

    private fun maxIndex(xs: List<Float>): Int {
        var i = 0; var m = Float.NEGATIVE_INFINITY; var mi = 0
        for (v in xs) { if (v > m) { m = v; mi = i } ; i++ }
        return mi
    }

    /**
     * 해석문 토큰에서 우선 추출(유니/바이그램), 없으면 꿈 원문에서 추출.
     * 간단한 빈도·길이 제약과 중복 스테밍 제거.
     */
    private fun topKeywordsFromInterps(tokens: List<String>, n: Int): List<String> {
        if (tokens.isEmpty()) return emptyList()
        val unigrams = tokens.filter { it !in STOPWORDS && it.length in 2..8 }
            .groupingBy { it }.eachCount().toMutableMap()

        val bigramCounts = mutableMapOf<String, Int>()
        for (i in 0 until tokens.size - 1) {
            val a = tokens[i]; val b = tokens[i + 1]
            if (a in STOPWORDS || b in STOPWORDS) continue
            if (a.length < 2 || b.length < 2) continue
            val bg = "$a $b"
            bigramCounts[bg] = (bigramCounts[bg] ?: 0) + 1
        }

        val scored = mutableMapOf<String, Float>()
        unigrams.forEach { (k, v) -> scored[k] = (scored[k] ?: 0f) + v }
        bigramCounts.forEach { (k, v) -> scored[k] = (scored[k] ?: 0f) + v * 1.7f }

        fun stem(x: String) = stripJosaAndTail(x.replace(" ", ""))
        val picked = mutableListOf<String>()
        for (kw in scored.entries.sortedByDescending { it.value }.map { it.key }) {
            if (picked.size >= n) break
            val st = stem(kw)
            if (picked.any { stem(it) == st }) continue
            picked += kw
        }
        val bigramFirst = picked.filter { it.contains(' ') } + picked.filterNot { it.contains(' ') }
        return bigramFirst.take(3)
    }

    // 더미: 내러티브는 리소스/분포를 이용해 간단히 구성(기존 프로젝트 함수 유지)
    private fun buildNarrative(
        res: Resources,
        dreams: List<String>,
        interps: List<String>,
        feeling: String,
        themeLabels: List<String>,
        themeDist: List<Float>
    ): String {
        val topThemeIdx = maxIndex(themeDist)
        val theme = themeLabels[topThemeIdx]
        return res.getString(R.string.ai_analysis_title) + " · " + theme
    }
}
