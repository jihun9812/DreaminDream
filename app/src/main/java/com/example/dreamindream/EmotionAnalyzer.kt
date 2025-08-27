// file: app/src/main/java/com/example/dreamindream/EmotionAnalyzer.kt
package com.example.dreamindream

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

    // 감정 8축 키워드 사전 (간단 휴리스틱)
    private val EMOTION_DICT: Map<String, List<String>> = mapOf(
        "긍정" to listOf("기쁨","행복","감사","희망","설렘","사랑","만족","즐거움","뿌듯","성취감"),
        "평온" to listOf("평온","안정","차분","편안","휴식","휴식감","고요"),
        "활력" to listOf("활력","에너지","집중력","동기","의욕","생기","힘"),
        "몰입" to listOf("몰입","집중","심층","깊이","흐름","플로우"),
        "중립" to listOf("보통","무난","그냥","일상","평범","중립"),
        "혼란" to listOf("혼란","갈등","애매","망설","주저","불확실","복잡"),
        "불안" to listOf("불안","초조","걱정","공포","긴장","압박","스트레스"),
        "우울/피로" to listOf("우울","슬픔","피곤","지침","무기력","외로움","허무")
    )

    // 테마 4축
    private val THEME_DICT: Map<String, List<String>> = mapOf(
        "관계" to listOf("가족","친구","연인","동료","상사","팀","모임","대화","갈등","협력"),
        "성취" to listOf("시험","성공","승진","성과","목표","연습","도전","해내","완료","집중"),
        "변화" to listOf("이사","이직","전환","여행","새로운","시작","변화","재정비"),
        "불안요인" to listOf("마감","금전","돈","건강","병","실수","평가","압박","실패")
    )

    private val STOPWORDS = setOf("그리고","그래서","하지만","그러나","하다","했다","있는","했던","것","수","좀","매우","너무","정말")

    fun analyzeWeek(texts: List<String>): WeekAnalysis {
        val raw = texts.joinToString("\n").replace(Regex("\\s+"), " ").trim()
        val tokens = tokenizeKo(raw).filter { it.length >= 2 && it !in STOPWORDS }

        val emoCounts = score(tokens, EMOTION_DICT)
        val themeCounts = score(tokens, THEME_DICT)
        val emoLabels = EMOTION_DICT.keys.toList()
        val themeLabels = THEME_DICT.keys.toList()

        val emoDist = normalizePercent(emoCounts)
        val themeDist = normalizePercent(themeCounts)

        val pos = emoDist[idx(emoLabels, "긍정")] + emoDist[idx(emoLabels, "평온")] +
                emoDist[idx(emoLabels, "활력")] + emoDist[idx(emoLabels, "몰입")]
        val neu = emoDist[idx(emoLabels, "중립")]
        val neg = emoDist[idx(emoLabels, "혼란")] + emoDist[idx(emoLabels, "불안")] + emoDist[idx(emoLabels, "우울/피로")]
        val feeling = when {
            pos >= neg && pos >= neu -> "긍정↑"
            neg > pos && neg >= neu -> "부정↓"
            else -> "중립"
        }

        val keywords = topKeywords(tokens, topN = 6)
        val analysis = buildString {
            append("이번 주 꿈의 정서적 경향은 ")
            append(if (feeling.startsWith("긍정")) "대체로 긍정적" else if (feeling.startsWith("부정")) "부정 요인의 영향" else "중립적 균형")
            append("으로 나타났습니다. ")
            append("주요 키워드는 ${keywords.joinToString(", ")} 입니다. ")
            append("감정 분포는 ")
            append(emoLabels.zip(emoDist).joinToString(" · ") { (l, v) -> "$l ${"%.1f".format(v)}%" })
            append("이며, 테마 분포는 ")
            append(themeLabels.zip(themeDist).joinToString(" · ") { (l, v) -> "$l ${"%.1f".format(v)}%" })
            append("로 요약됩니다.")
        }

        return WeekAnalysis(feeling, keywords, analysis, emoLabels, emoDist, themeLabels, themeDist)
    }

    private fun tokenizeKo(text: String): List<String> =
        text.lowercase(Locale.KOREA)
            .replace(Regex("[^ㄱ-힣a-zA-Z0-9 ]"), " ")
            .split(" ")
            .filter { it.isNotBlank() }

    private fun score(tokens: List<String>, dict: Map<String, List<String>>): FloatArray {
        val arr = FloatArray(dict.size)
        dict.entries.forEachIndexed { idx, (label, kws) ->
            val hits = tokens.count { t -> kws.any { k -> t.contains(k.lowercase(Locale.KOREA)) } }
            arr[idx] = hits.toFloat()
        }
        return arr
    }

    private fun normalizePercent(vals: FloatArray): List<Float> {
        val sum = vals.sum().takeIf { it > 0f } ?: 1f
        return vals.map { v -> (v / sum) * 100f }
    }

    private fun topKeywords(tokens: List<String>, topN: Int): List<String> {
        val freq = tokens.groupingBy { it }.eachCount()
        return freq.entries.sortedByDescending { it.value }
            .map { it.key }
            .filter { it !in STOPWORDS }
            .take(topN)
    }

    private fun idx(labels: List<String>, name: String): Int = max(0, labels.indexOf(name))
}
