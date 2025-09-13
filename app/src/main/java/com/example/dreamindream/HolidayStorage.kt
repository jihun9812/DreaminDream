// file: app/src/main/java/com/example/dreamindream/HolidayStorage.kt
package com.example.dreamindream

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object HolidayStorage {

    private fun keyFor(year: Int) = "holidays_json_$year"

    // --- 새 API: 연도별 저장/로드 ---
    fun saveHolidays(context: Context, year: Int, holidays: List<Holiday>) {
        val arr = JSONArray()
        holidays.forEach {
            arr.put(JSONObject().apply {
                put("date", it.date.toString()) // yyyy-MM-dd
                put("name", it.name)
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

    fun loadHolidaysRange(context: Context, startYear: Int, endYear: Int): List<Holiday> {
        val all = mutableListOf<Holiday>()
        for (y in startYear..endYear) {
            all += loadHolidays(context, y)
        }
        return all
    }

    // --- 구버전 호환(기존 단일 키를 쓰던 코드가 있어도 안전) ---
    @Deprecated("Use year-based APIs")
    fun saveHolidays(context: Context, holidays: List<Holiday>) {
        val year = holidays.firstOrNull()?.date?.year ?: return
        saveHolidays(context, year, holidays)
    }

    @Deprecated("Use year-based APIs")
    fun loadHolidays(context: Context): List<Holiday> {
        // 과거 저장분을 읽되, 없으면 현재 연도 기준으로 연도별 키 조회
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
}
