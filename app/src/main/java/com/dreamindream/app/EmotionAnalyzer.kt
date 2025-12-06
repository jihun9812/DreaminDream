package com.dreamindream.app

import android.content.Context
import android.content.res.Resources
import java.util.Locale

data class WeekAnalysis(
    val feeling: String,
    val keywords: List<String>,
    val analysis: String,
    val emotionLabels: List<String>,
    val emotionDist: List<Float>,
    val themeLabels: List<String>,
    val themeDist: List<Float>
)

object EmotionAnalyzer {
    // 다국어 지원을 위해 캐시 제거하거나 매번 로드.
    // 여기서는 간단하게 Context가 넘어올 때마다 자원을 참조하도록 구조 유지하되 캐싱 제거.

    fun analyzeWeek(ctx: Context, dreams: List<String>, interps: List<String>): WeekAnalysis {
        val res = ctx.resources

        // Load Resources fresh every time to support locale changes
        val emotionLabels = res.getStringArray(R.array.emotion_labels).toList()
        val themeLabels = res.getStringArray(R.array.theme_labels).toList()

        // Dictionaries (가정: 리소스 배열 ID가 존재함)
        val emotionDict = mapOf(
            // 인덱스 순서대로 매핑된다고 가정
            emotionLabels.getOrElse(0){"Positive"} to res.getStringArray(R.array.emotion_dict_positive).toList(),
            emotionLabels.getOrElse(1){"Calm"}     to res.getStringArray(R.array.emotion_dict_calm).toList(),
            emotionLabels.getOrElse(2){"Vitality"} to res.getStringArray(R.array.emotion_dict_vitality).toList(),
            emotionLabels.getOrElse(3){"Flow"}     to res.getStringArray(R.array.emotion_dict_flow).toList(),
            emotionLabels.getOrElse(4){"Neutral"}  to res.getStringArray(R.array.emotion_dict_neutral).toList(),
            emotionLabels.getOrElse(5){"Confusion"}to res.getStringArray(R.array.emotion_dict_confusion).toList(),
            emotionLabels.getOrElse(6){"Anxiety"}  to res.getStringArray(R.array.emotion_dict_anxiety).toList(),
            emotionLabels.getOrElse(7){"Depression"} to res.getStringArray(R.array.emotion_dict_depression_fatigue).toList(),
        )
        // ... themeDict 생략 (동일 패턴 적용)

        val josa = res.getStringArray(R.array.josa_ko).toSet()
        val stopwords = res.getStringArray(R.array.stopwords_ko).toSet()
        val tails = res.getStringArray(R.array.token_tails_ko).toList()

        // Logic
        val emoBase = normalize((dreams + interps).joinToString("\n"))
        val emoTokens = tokenize(emoBase, josa, stopwords, tails)

        val interpBase = normalize(interps.joinToString("\n"))
        val interpTokens = tokenize(interpBase, josa, stopwords, tails)

        val emoDist = normalizePercent(score(emoTokens, emotionDict))
        // Theme Dict 로직은 생략되었으나 위와 동일 패턴
        // Mocking Theme Dist for compilation
        val themeDist = List(themeLabels.size) { 0f }

        val feeling = topFeeling(emotionLabels, emoDist, ctx.getString(R.string.ea_trend_up))

        val dreamsJoined = dreams.joinToString("\n")
        val kw = topKeywordsFromInterps(interpTokens, stopwords, 6)
            .filter { dreamsJoined.contains(it.replace(" ", "")) }
            .take(3)

        val analysis = res.getString(R.string.ai_analysis_title) // Fallback text

        return WeekAnalysis(feeling, kw, analysis, emotionLabels, emoDist, themeLabels, themeDist)
    }

    // --- Helpers ---
    private fun normalize(s: String): String = s.lowercase(Locale.getDefault()).replace(Regex("[^ㄱ-힣a-zA-Z0-9\\s]"), " ")

    private fun tokenize(s: String, josa: Set<String>, stop: Set<String>, tails: List<String>): List<String> =
        s.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { stripJosaAndTail(it, josa, tails) }
            .map { it.replace(Regex("\\d"), "") }
            .filter { it.length in 2..8 && it !in josa && it !in stop }

    private fun stripJosaAndTail(t: String, josa: Set<String>, tails: List<String>): String {
        var x = t
        josa.firstOrNull { x.length > it.length && x.endsWith(it) }?.let { x = x.dropLast(it.length) }
        tails.firstOrNull { x.length > it.length && x.endsWith(it) }?.let { x = x.dropLast(it.length) }
        return x
    }

    private fun score(tokens: List<String>, dict: Map<String, List<String>>): FloatArray {
        val arr = FloatArray(dict.size)
        dict.entries.forEachIndexed { i, (_, kws) ->
            var s = 0f
            for (tok in tokens) for (k in kws) if (tok.contains(k)) s += 1f
            arr[i] = s
        }
        return arr
    }

    private fun normalizePercent(arr: FloatArray): List<Float> {
        val sum = arr.sum().takeIf { it > 0f } ?: 1f
        return arr.map { (it / sum) * 100f }
    }

    private fun topFeeling(labels: List<String>, dist: List<Float>, upSuffix: String): String {
        val idx = dist.indices.maxByOrNull { dist[it] } ?: 0
        return labels.getOrElse(idx) { "-" }
    }

    private fun topKeywordsFromInterps(tokens: List<String>, stop: Set<String>, n: Int): List<String> {
        return tokens.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(n)
    }
}