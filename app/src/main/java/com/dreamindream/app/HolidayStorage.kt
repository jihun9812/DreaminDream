package com.dreamindream.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object HolidayStorage {

    private fun keyFor(year: Int) = "holidays_json_$year"

    // --- 연도별 저장 ---
    fun saveHolidays(context: Context, year: Int, holidays: List<Holiday>) {
        val arr = JSONArray()
        holidays.forEach {
            arr.put(JSONObject().apply {
                put("date", it.date.toString()) // yyyy-MM-dd
                put("name", it.name)            // 원문(ko) 저장
            })
        }
        context.getSharedPreferences("holidays", Context.MODE_PRIVATE)
            .edit().putString(keyFor(year), arr.toString()).apply()
    }

    fun loadHolidays(context: Context, year: Int): List<Holiday> {
        val sp = context.getSharedPreferences("holidays", Context.MODE_PRIVATE)
        val json = sp.getString(keyFor(year), null) ?: return emptyList()
        val arr = JSONArray(json)
        val list = ArrayList<Holiday>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Holiday(
                    date = LocalDate.parse(o.optString("date")),
                    name = o.optString("name")
                )
            )
        }
        return list
    }

    /**
     * 여러 해를 한 번에 로드.
     * ✅ 변경점: 기기/앱 언어가 영어(en)면 **표시용으로 바로 영문 변환**해서 리턴.
     *    (ML Kit 없이 빠른 매핑 사용 — 대표 공휴일은 전부 커버)
     */
    fun loadHolidaysRange(context: Context, startYear: Int, endYear: Int): List<Holiday> {
        val all = mutableListOf<Holiday>()
        val locale = context.resources.configuration.locales[0]
        val useEnglish = locale.language.startsWith("en", ignoreCase = true)

        for (y in startYear..endYear) {
            val yearList = loadHolidays(context, y)
            if (useEnglish) {
                val mapped = yearList.map { h ->
                    val en = HolidayTranslator.localizeForUiQuick(h.name, locale)
                    h.copy(name = en)
                }
                all += mapped
            } else {
                all += yearList
            }
        }
        return all
    }

    // --- 구버전 호환(단일 키) ---
    @Deprecated("Use year-based APIs")
    fun saveHolidays(context: Context, holidays: List<Holiday>) {
        val year = holidays.firstOrNull()?.date?.year ?: return
        saveHolidays(context, year, holidays)
    }

    @Deprecated("Use year-based APIs")
    fun loadHolidays(context: Context): List<Holiday> {
        val sp = context.getSharedPreferences("holidays", Context.MODE_PRIVATE)
        val legacy = sp.getString("holidays_json", null)
        return if (legacy != null) {
            val arr = JSONArray(legacy)
            val list = ArrayList<Holiday>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Holiday(LocalDate.parse(o.optString("date")), o.optString("name")))
            }
            list
        } else {
            val year = LocalDate.now().year
            loadHolidays(context, year)
        }
    }

    // ====== (선택) ML Kit 번역이 필요한 곳에서 쓸 수 있는 suspend 로더 ======
    suspend fun loadHolidaysLocalized(context: Context, year: Int): List<Holiday> {
        val base = loadHolidays(context, year)
        val locale = context.resources.configuration.locales[0]
        if (!locale.language.startsWith("en")) return base
        return base.map { h ->
            val en = HolidayTranslator.localizeForUi(h.name, locale)
            h.copy(name = en)
        }
    }

    suspend fun loadHolidaysRangeLocalized(context: Context, startYear: Int, endYear: Int): List<Holiday> {
        val all = mutableListOf<Holiday>()
        for (y in startYear..endYear) {
            all += loadHolidaysLocalized(context, y)
        }
        return all
    }
}
