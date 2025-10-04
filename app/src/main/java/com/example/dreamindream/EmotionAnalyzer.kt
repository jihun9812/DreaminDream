package com.example.dreamindream

import android.content.Context
import android.content.res.Resources
import java.util.*
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

    // 기존 시그니처 유지용
    @Deprecated("Use analyzeWeek(ctx, dreams, interps)")
    fun analyzeWeek(texts: List<String>): WeekAnalysis =
        throw IllegalStateException("Use analyzeWeek(context, dreams, interps) after string externalization")

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
            // 부정/긍정 쪽엔 트렌드 화살표만 붙여 강조
            labels[5], labels[6], labels[7] -> label + upSuffix // 혼란/불안/우울·피로
            labels[0], labels[2], labels[3] -> label + upSuffix // 긍정/활력/몰입
            else -> label
        }
    }

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
        unigrams.forEach { (k, v) -> scored[k] = (scored[k] ?: 0f) + v.toFloat() }
        bigramCounts.forEach { (k, v) -> scored[k] = (scored[k] ?: 0f) + v * 1.6f }

        val picked = mutableListOf<String>()
        for ((kw, _) in scored.entries.sortedByDescending { it.value }) {
            if (picked.size >= n) break
            val base = kw.replace(" ", "")
            if (kw.any { it.code < 0x3131 } || base.length !in 2..10) continue
            if (kw.split(" ").any { it in STOPWORDS || it in JOSA }) continue
            val stem = stripJosaAndTail(base)
            if (picked.any { stripJosaAndTail(it.replace(" ", "")) == stem }) continue
            picked += kw
        }

        val bigramFirst = picked.filter { it.contains(' ') } + picked.filterNot { it.contains(' ') }
        return bigramFirst.take(if (bigramFirst.size >= 3) 3 else max(2, bigramFirst.size))
    }

    private fun maxIndex(list: List<Float>): Int {
        var mi = 0; var mv = Float.NEGATIVE_INFINITY
        list.forEachIndexed { i, v -> if (v > mv) { mv = v; mi = i } }
        return mi
    }

    // ── HTML 내 문구도 전부 strings.xml 에서 조합 ───────────────
    private fun buildNarrative(
        res: Resources,
        dreams: List<String>,
        interps: List<String>,
        feeling: String,
        themeLabels: List<String>,
        themeDist: List<Float>
    ): String {
        val raw = (dreams + interps).joinToString("\n")
        val hasToilet = raw.contains(res.getString(R.string.ea_kw_toilet1)) || raw.contains(res.getString(R.string.ea_kw_toilet2))
        val hasPoop   = raw.contains(res.getString(R.string.ea_kw_poop1))   || raw.contains(res.getString(R.string.ea_kw_poop2))
        val hasPig    = raw.contains(res.getString(R.string.ea_kw_pig))
        val hasHouse  = raw.contains(res.getString(R.string.ea_kw_house))
        val hasYard   = raw.contains(res.getString(R.string.ea_kw_yard1))   || raw.contains(res.getString(R.string.ea_kw_yard2)) || raw.contains(res.getString(R.string.ea_kw_yard3))
        val hasHand   = raw.contains(res.getString(R.string.ea_kw_hand1))   || raw.contains(res.getString(R.string.ea_kw_hand2)) || raw.contains(res.getString(R.string.ea_kw_hand3))

        val topThemes = themeLabels.zip(themeDist).sortedByDescending { it.second }.map { it.first }.take(2)
        val sep = res.getString(R.string.ea_heading_sep)
        val dot = res.getString(R.string.ea_bullet_delim)

        val sb = StringBuilder()

        // 요약
        sb.append("<p><b>").append(res.getString(R.string.ea_heading_summary)).append("</b>").append(sep)
        val parts = mutableListOf<String>()
        if (hasToilet || hasPoop) parts += res.getString(R.string.ea_part_clear_blockage)
        if (hasPig)               parts += res.getString(R.string.ea_part_fortune_enters)
        if (hasHouse)             parts += res.getString(R.string.ea_part_home_foundation)
        if (hasYard)              parts += res.getString(R.string.ea_part_boundary_opening)
        if (parts.isEmpty())      parts += res.getString(R.string.ea_part_default_flow)
        sb.append(parts.joinToString(res.getString(R.string.ea_arrow_delim)))
            .append(res.getString(R.string.ea_summary_suffix))
            .append("</p>")

        // 상징 연결
        sb.append("<p><b>").append(res.getString(R.string.ea_heading_link)).append("</b>").append(sep)
        val link = when {
            (hasToilet || hasPoop) && hasPig -> res.getString(R.string.ea_link_clear_then_fortune)
            (hasPig && hasHouse)             -> res.getString(R.string.ea_link_fortune_with_home)
            (hasToilet || hasPoop) && hasHand-> res.getString(R.string.ea_link_hands_on)
            else                              -> res.getString(R.string.ea_link_default)
        }
        sb.append(link).append("</p>")

        // 감정/테마
        sb.append("<p><b>").append(res.getString(R.string.ea_heading_emotion_theme)).append("</b>").append(sep)
        if (topThemes.isNotEmpty()) {
            sb.append(res.getString(R.string.ea_emotion_theme_with, feeling, topThemes.joinToString(dot)))
        } else {
            sb.append(res.getString(R.string.ea_emotion_theme_only, feeling))
        }
        sb.append("</p>")

        // 미래 전망
        sb.append("<p><b>").append(res.getString(R.string.ea_heading_outlook)).append("</b>").append(sep)
        val outlook = when {
            (hasPoop || hasPig) && hasYard -> res.getString(R.string.ea_outlook_cash_inbound)
            hasPig                         -> res.getString(R.string.ea_outlook_small_gain)
            hasToilet || hasPoop           -> res.getString(R.string.ea_outlook_unblock_one)
            else                           -> res.getString(R.string.ea_outlook_slow_open)
        }
        sb.append(outlook).append("</p>")

        // 체크리스트
        sb.append("<ul>")
        sb.append("<li>").append(res.getString(R.string.ea_tip_cash_signals)).append("</li>")
        sb.append("<li>").append(res.getString(R.string.ea_tip_home_cleanup)).append("</li>")
        sb.append("<li>").append(res.getString(R.string.ea_tip_take_action)).append("</li>")
        sb.append("</ul>")

        // 결론
        sb.append("<p><b>").append(res.getString(R.string.ea_heading_conclusion)).append("</b>").append(sep)
            .append(res.getString(R.string.ea_conclusion)).append("</p>")

        return sb.toString()
    }
}
