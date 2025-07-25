package com.example.dreamindream

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object HolidayStorage {
    private const val PREF_KEY = "holidays_json"
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun saveHolidays(context: Context, holidays: List<Holiday>) {
        val arr = JSONArray()
        holidays.forEach {
            val obj = JSONObject()
            obj.put("date", it.date.format(formatter))
            obj.put("name", it.name)
            arr.put(obj)
        }
        context.getSharedPreferences("holiday", Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, arr.toString()).apply()
    }

    fun loadHolidays(context: Context): List<Holiday> {
        val json = context.getSharedPreferences("holiday", Context.MODE_PRIVATE)
            .getString(PREF_KEY, null) ?: return emptyList()
        val arr = JSONArray(json)
        val result = mutableListOf<Holiday>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val date = LocalDate.parse(obj.getString("date"), formatter)
            val name = obj.getString("name")
            result.add(Holiday(date, name))
        }
        return result
    }
}
