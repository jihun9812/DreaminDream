package com.dreamindream.app

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object HolidayTranslator {

    private const val TAG = "HolidayTranslator"

    // 흔히 쓰이는 공휴일은 깔끔한 관용표기로 우선 매핑 (번역 퀄리티 보정)
    private val direct = mapOf(
        "설날" to "Seollal (Lunar New Year)",
        "설" to "Seollal (Lunar New Year)",
        "설날연휴" to "Seollal Holiday",
        "추석" to "Chuseok (Harvest Festival)",
        "추석연휴" to "Chuseok Holiday",
        "부처님오신날" to "Buddha's Birthday",
        "석가탄신일" to "Buddha's Birthday",
        "어린이날" to "Children's Day",
        "현충일" to "Memorial Day",
        "제헌절" to "Constitution Day",
        "광복절" to "National Liberation Day",
        "개천절" to "National Foundation Day",
        "한글날" to "Hangeul Day",
        "성탄절" to "Christmas Day",
        "기독탄신일" to "Christmas Day",
        "임시공휴일" to "Temporary Holiday",
        "대통령선거" to "Presidential Election Day",
        "국회의원선거" to "National Assembly Election Day",
        "지방선거" to "Local Election Day"
    )

    private val substituteRegex = Regex("""대체공휴일\((.+)\)""")
    private val holidayRegex = Regex("""(.+)\s*연휴$""")

    private val cache = ConcurrentHashMap<String, String>()

    @Volatile
    private var translator: Translator? = null

    // ============== (A) 빠른 매핑 전용: 비동기 없음, ML Kit 불필요 ==============
    /** ML Kit 없이 즉시 동작하는 빠른 Ko→En 변환 (direct 매핑 + 규칙 처리) */
    fun quickKoToEn(text: String): String {
        if (text.isBlank()) return text

        // 1) 관용 표기 우선
        direct[text]?.let { return it }

        // 2) "대체공휴일(XXX)" → Substitute Holiday (XXX의 영문)
        substituteRegex.matchEntire(text)?.let { m ->
            val innerKo = m.groupValues[1]
            val innerEn = quickKoToEn(innerKo)
            return "Substitute Holiday ($innerEn)"
        }

        // 3) "XXX연휴" → XXX Holiday
        holidayRegex.matchEntire(text)?.let { m ->
            val baseKo = m.groupValues[1]
            val baseEn = quickKoToEn(baseKo)
            return "$baseEn Holiday"
        }

        // 4) 매핑 없으면 원문 유지
        return text
    }

    /** 영어권이면 quickKoToEn 적용, 아니면 원문 */
    fun localizeForUiQuick(rawName: String, locale: Locale): String {
        return if (locale.language.startsWith("en", ignoreCase = true)) {
            quickKoToEn(rawName)
        } else {
            rawName
        }
    }
    // ======================================================================

    /**
     * Ko -> En 번역기 준비 + 모델 다운로드 (필요 시)
     * requireWifi=true면 Wi-Fi에서만 모델 받음.
     */
    suspend fun ensureModel(
        requireWifi: Boolean = true,
        source: String = TranslateLanguage.KOREAN,
        target: String = TranslateLanguage.ENGLISH
    ): Translator {
        val current = translator
        if (current != null) return current

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()

        val t = Translation.getClient(options)
        val cond = DownloadConditions.Builder()
            .apply { if (requireWifi) requireWifi() }
            .build()

        t.downloadModelIfNeeded(cond).await()
        translator = t
        return t
    }

    /**
     * (선택) ML Kit 사용한 suspend 번역. 실패 시 원문 반환.
     * 관용 표기가 있으면 그걸 우선 적용.
     */
    suspend fun translateKoToEn(text: String): String {
        if (text.isBlank()) return text

        // 1) 관용 표기
        direct[text]?.let { return it }

        // 2) "대체공휴일(XXX)"
        substituteRegex.matchEntire(text)?.let { m ->
            val innerKo = m.groupValues[1]
            val innerEn = translateKoToEn(innerKo)
            return "Substitute Holiday ($innerEn)"
        }

        // 3) "XXX연휴"
        holidayRegex.matchEntire(text)?.let { m ->
            val baseKo = m.groupValues[1]
            val baseEn = translateKoToEn(baseKo)
            return "$baseEn Holiday"
        }

        // 4) 캐시
        cache[text]?.let { return it }

        // 5) ML Kit
        return try {
            val t = ensureModel(requireWifi = true)
            val result = t.translate(text).await()
            cache[text] = result
            result
        } catch (e: Exception) {
            Log.w(TAG, "translate failed: ${e.message}")
            text // 실패하면 원문 유지
        }
    }

    /** 영어권이면 ML Kit 번역(또는 direct), 아니면 원문 */
    suspend fun localizeForUi(rawName: String, locale: Locale): String {
        return if (locale.language.startsWith("en", ignoreCase = true)) {
            translateKoToEn(rawName)
        } else {
            rawName
        }
    }

    /** 필요 시 호출해서 메모리/리소스 정리 */
    fun close() {
        try {
            translator?.close()
        } catch (_: Exception) {}
        translator = null
        cache.clear()
    }
}

/** UI에서 편하게 쓸 수 있는 확장(suspend) */
suspend fun Holiday.localizedName(context: Context): String {
    val locale = context.resources.configuration.locales[0]
    return HolidayTranslator.localizeForUi(this.name, locale)
}
