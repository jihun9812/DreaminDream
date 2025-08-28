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

    private val EMOTION_DICT: Map<String, List<String>> = mapOf(
        "긍정" to listOf("기쁨","행복","감사","희망","설렘","사랑","만족","즐거움","뿌듯","성취감","안도","자신감"),
        "평온" to listOf("평온","안정","차분","편안","휴식","고요","느긋","여유"),
        "활력" to listOf("활력","에너지","집중력","동기","의욕","생기","힘","추진력","열정"),
        "몰입" to listOf("몰입","집중","심층","깊이","플로우","열중","빠져듦"),
        "중립" to listOf("보통","무난","일상","평범","중립","그냥"),
        "혼란" to listOf("혼란","갈등","애매","망설","주저","복잡","뒤섞","헷갈"),
        "불안" to listOf("불안","초조","걱정","긴장","압박","스트레스","공포","두려움","촉박"),
        "우울/피로" to listOf("우울","슬픔","피곤","지침","무기력","권태","허무","외로움","좌절")
    )
    private val THEME_DICT: Map<String, List<String>> = mapOf(
        "관계" to listOf("가족","부모","형제","친구","연인","동료","상사","팀","모임","대화","신뢰","갈등","협력"),
        "성취" to listOf("시험","성공","승진","성과","목표","연습","도전","해내","완료","발표","면접","준비","달성"),
        "변화" to listOf("이사","이직","전환","여행","새로운","시작","변화","정리","마감","계획","결정","이동"),
        "불안요인" to listOf("마감","금전","돈","건강","병","실수","평가","압박","실패","늦음","분실","추락","추격","사고")
    )

    private val JOSA = setOf("은","는","이","가","을","를","에","에서","에게","께","한테","으로","로","과","와","도","만","처럼","같이","까지","부터","마다","뿐","조차","이나","나","라서","보다","만큼","마저","밖에","및","등","에게서","하고","랑","이랑")
    private val STOPWORDS = setOf(
        "그리고","그래서","그러나","하지만","및","또는","혹은",
        "하다","했다","되는","되어","됐다","되다","하는","하며","하고","했다가","한다",
        "것","수","좀","매우","너무","정말","모든","전체","내용","경향","의미","상황","이야기","부분","측면","전반",
        "오늘","오늘의","이번","저번","주간","기록","분석","요약","핵심","포인트","중요","체크","체크리스트",
        "문제","기회","풍요","요소","상태","관점","가이드","조언","유형","예측","현실","사건","해결","정화","안정","추구",
        "꿈","해몽","텍스트","문장","표현","감정","테마","키워드"
    )

    fun analyzeWeek(dreams: List<String>, interps: List<String>): WeekAnalysis {
        val emoBase = normalize((dreams + interps).joinToString("\n"))
        val emoTokens = tokenize(emoBase)

        val interpBase = normalize(interps.joinToString("\n"))
        val interpTokens = tokenize(interpBase)

        val emoLabels = EMOTION_DICT.keys.toList()
        val themeLabels = THEME_DICT.keys.toList()

        val emoDist = normalizePercent(score(emoTokens, EMOTION_DICT))
        val themeDist = normalizePercent(score(emoTokens, THEME_DICT))
        val feeling = topFeeling(emoLabels, emoDist)

        val dreamsJoined = dreams.joinToString("\n")
        val kw = topKeywordsFromInterps(interpTokens, 6)
            .filter { dreamsJoined.contains(it.replace(" ", "")) }
            .take(3)
            .ifEmpty {
                topKeywordsFromInterps(tokenize(normalize(dreamsJoined)), 5).take(3)
            }

        val analysis = buildNarrative(
            dreams = dreams, interps = interps, feeling = feeling,
            themeLabels = themeLabels, themeDist = themeDist
        )

        return WeekAnalysis(feeling, kw, analysis, emoLabels, emoDist, themeLabels, themeDist)
    }

    fun analyzeWeek(texts: List<String>): WeekAnalysis =
        analyzeWeek(dreams = texts, interps = texts)

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
        val tails = listOf("하다","되다","스럽다","답다","하니","하며","하는","했다","했고","하게","적인","적으로","스러움","스러워","같다","같이","처럼","뿐","들","들의","적","적임","성")
        val tail = tails.firstOrNull { t2 -> x.length > t2.length && x.endsWith(t2) }
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

    private fun topFeeling(labels: List<String>, dist: List<Float>): String {
        val idx = maxIndex(dist); val label = labels[idx]
        return when (label) {
            "불안", "혼란", "우울/피로" -> "$label ↑"
            "활력", "몰입", "긍정" -> "$label ↑"
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

        val ban = setOf("관계","성취","변화","불안요인")
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

    private fun buildNarrative(
        dreams: List<String>,
        interps: List<String>,
        feeling: String,
        themeLabels: List<String>,
        themeDist: List<Float>
    ): String {
        val raw = (dreams + interps).joinToString("\n")
        val hasToilet = raw.contains("화장실") || raw.contains("변기")
        val hasPoop = raw.contains("똥") || raw.contains("대변")
        val hasPig = raw.contains("돼지")
        val hasHouse = raw.contains("집")
        val hasYard = raw.contains("마당") || raw.contains("대문") || raw.contains("현관")
        val hasHand = raw.contains("손") || raw.contains("손으로") || raw.contains("닦")

        val topThemes = themeLabels.zip(themeDist).sortedByDescending { it.second }.map { it.first }.take(2)

        val sb = StringBuilder()
        sb.append("<p><b>요약</b> — ")
        val parts = mutableListOf<String>()
        if (hasToilet || hasPoop) parts += "막혀 있던 것을 비우고 정리하는 장면"
        if (hasPig) parts += "복과 재물의 상징이 생활권 안으로 들어오는 장면"
        if (hasHouse) parts += "가정/내면 기반을 확인하는 장면"
        if (hasYard) parts += "내 영역과 바깥의 경계가 열리는 장면"
        if (parts.isEmpty()) parts += "두 꿈이 ‘정리 후 유입’의 흐름을 잇는 장면"
        sb.append(parts.joinToString(" → ")).append("으로 이어집니다.</p>")

        sb.append("<p><b>상징 연결</b> — ")
        val link = when {
            (hasToilet || hasPoop) && hasPig -> "불필요한 것을 비운 뒤(정화) 복이 들어오니, ‘정리 후 유입’의 전형적인 서사입니다."
            (hasPig && hasHouse) -> "복(돼지)이 집/가정과 결합해 실생활 재정·가족 이슈에 긍정 흐름을 암시합니다."
            (hasToilet || hasPoop) && hasHand -> "스스로 손을 써 해결하는 장면은 주도권 회복을 뜻합니다."
            else -> "두 꿈이 ‘닫힘→열림’의 전환을 만들어 내고 있습니다."
        }
        sb.append(link).append("</p>")

        if (topThemes.isNotEmpty()) {
            sb.append("<p><b>감정/테마</b> — 현재 정서는 ‘").append(feeling).append("’ 쪽으로 기울며, 테마는 ")
            sb.append(topThemes.joinToString("·")).append(" 중심으로 전개됩니다.</p>")
        } else {
            sb.append("<p><b>감정/테마</b> — 현재 정서는 ‘").append(feeling).append("’ 성향이 도드라집니다.</p>")
        }

        sb.append("<p><b>미래 전망(1~2주)</b> — ")
        val outlook = when {
            (hasPoop || hasPig) && hasYard -> "작은 수입/선물/제안이 문턱까지 들어옵니다. 생활권 가까운 곳에서 기회가 포착됩니다."
            hasPig -> "재정·물질적 이득의 신호가 약하게나마 잡힙니다. 주변의 ‘작은 호의’가 단서가 됩니다."
            hasToilet || hasPoop -> "막혀 있던 일 하나가 깔끔히 정리되면서 다음 단계로 넘어갑니다."
            else -> "닫혀 있던 흐름이 서서히 풀리며, 실제 변화의 단서가 보이기 시작합니다."
        }
        sb.append(outlook).append("</p>")

        sb.append("<ul>")
        sb.append("<li>작은 제안·선물·환급 등 ‘현금성’ 신호를 놓치지 마세요.</li>")
        sb.append("<li>생활/가정 영역에서 정리할 항목을 하나만 골라 마무리하세요.</li>")
        sb.append("<li>직접 손대면(서류 정리, 연락, 청구 등) 속도가 붙습니다.</li>")
        sb.append("</ul>")
        sb.append("<p><b>결론</b> — ‘정리 후 유입’의 서사: 비운 만큼 채워지는 주간입니다.</p>")

        return sb.toString()
    }
}
