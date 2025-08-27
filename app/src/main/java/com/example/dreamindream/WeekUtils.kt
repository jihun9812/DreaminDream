// file: app/src/main/java/com/example/dreamindream/WeekUtils.kt
package com.example.dreamindream

import java.text.SimpleDateFormat
import java.util.*

object WeekUtils {
    fun weekKey(ts: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts; firstDayOfWeek = Calendar.MONDAY }
        val y = c.get(Calendar.YEAR); val w = c.get(Calendar.WEEK_OF_YEAR)
        return String.format("%04d-W%02d", y, w)
    }

    fun previousWeekKey(base: String = weekKey(), delta: Int = 1): String {
        val m = Regex("""(\d{4})-W(\d{2})""").find(base) ?: return base
        val y = m.groupValues[1].toInt(); val w = m.groupValues[2].toInt()
        val c = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY; clear()
            set(Calendar.YEAR, y); set(Calendar.WEEK_OF_YEAR, w); set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            add(Calendar.WEEK_OF_YEAR, -delta)
        }
        return weekKey(c.timeInMillis)
    }

    fun weekChipLabel(weekKey: String = weekKey()): String {
        val (y, w) = weekKey.split("-W"); return "WEEK ${w} · ${y}"
    }

    private fun calendarForWeekKey(weekKey: String): Calendar? {
        val m = Regex("""(\d{4})-W(\d{2})""").find(weekKey) ?: return null
        val y = m.groupValues[1].toInt(); val w = m.groupValues[2].toInt()
        return Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY; clear()
            set(Calendar.YEAR, y); set(Calendar.WEEK_OF_YEAR, w); set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
    }

    fun weekRangeMillis(weekKey: String): Pair<Long, Long> {
        val cal = calendarForWeekKey(weekKey) ?: return 0L to 0L
        val start = cal.timeInMillis; cal.add(Calendar.DAY_OF_YEAR, 7); val end = cal.timeInMillis - 1
        return start to end
    }

    /** 해당 주차의 yyyy-MM-dd 7개 날짜키 반환 */
    fun weekDateKeys(weekKey: String): List<String> {
        val cal = calendarForWeekKey(weekKey) ?: return emptyList()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val out = mutableListOf<String>()
        repeat(7) {
            out.add(sdf.format(Date(cal.timeInMillis)))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    fun weeksBetween(baseKey: String = weekKey(), targetKey: String): Int {
        val base = calendarForWeekKey(baseKey) ?: return 0
        val target = calendarForWeekKey(targetKey) ?: return 0
        val diff = base.timeInMillis - target.timeInMillis
        return (diff / (7L * 24 * 60 * 60 * 1000)).toInt()
    }

    fun relativeLabel(weekKey: String, baseKey: String = weekKey()): String {
        val d = weeksBetween(baseKey, weekKey)
        return when { d <= 0 -> "이번 주"; d == 1 -> "저번 주"; else -> "${d}주 전" }
    }
}
