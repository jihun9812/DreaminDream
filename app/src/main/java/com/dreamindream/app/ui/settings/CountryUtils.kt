package com.dreamindream.app.ui.settings

import java.util.Locale

data class Country(
    val code: String,
    val name: String,
    val flag: String
)

object CountryUtils {

    // 전 세계 국가 리스트 자동 생성
    fun getAllCountries(): List<Country> {
        val isoCountryCodes = Locale.getISOCountries()
        val countryList = mutableListOf<Country>()

        for (code in isoCountryCodes) {
            val locale = Locale("", code)
            val name = locale.displayCountry
            val flag = countryCodeToEmoji(code)

            if (name.isNotBlank()) {
                countryList.add(Country(code, name, flag))
            }
        }
        // 이름 순 정렬
        return countryList.sortedBy { it.name }
    }

    // ISO 국가 코드(KR, US 등)를 국기 이모지(🇰🇷, 🇺🇸)로 변환하는 마법의 함수
    private fun countryCodeToEmoji(countryCode: String): String {
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}