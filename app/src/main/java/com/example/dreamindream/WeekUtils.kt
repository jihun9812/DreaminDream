// app/src/main/java/com/example/dreamindream/WeekUtils.kt
package com.example.dreamindream

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object WeekUtils {

    fun weekKey(ts: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply {
            timeInMillis = ts
            firstDayOfWeek = Calendar.MONDAY
        }
        val y = c.get(Calendar.YEAR)
        val w = c.get(Calendar.WEEK_OF_YEAR)
        return String.format("%04d-W%02d", y, w)
    }

    fun previousWeekKey(base: String = weekKey(), delta: Int = 1): String {
        val m = Regex("""(\d{4})-W(\d{2})""").find(base) ?: return base
        val y = m.groupValues[1].toInt()
        val w = m.groupValues[2].toInt()
        val c = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            clear()
            set(Calendar.YEAR, y)
            set(Calendar.WEEK_OF_YEAR, w)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            add(Calendar.WEEK_OF_YEAR, -delta)
        }
        return weekKey(c.timeInMillis)
    }

    fun weekChipLabel(weekKey: String = weekKey()): String {
        val (y, w) = weekKey.split("-W")
        return "WEEK $w · $y"
    }

    private fun calendarForWeekKey(weekKey: String): Calendar? {
        val m = Regex("""(\d{4})-W(\d{2})""").find(weekKey) ?: return null
        val y = m.groupValues[1].toInt()
        val w = m.groupValues[2].toInt()
        return Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            clear()
            set(Calendar.YEAR, y)
            set(Calendar.WEEK_OF_YEAR, w)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) // ISO 주 시작(월)
        }
    }

    fun weekRangeMillis(weekKey: String): Pair<Long, Long> {
        val cal = calendarForWeekKey(weekKey) ?: return 0L to 0L
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        val end = cal.timeInMillis - 1
        return start to end
    }

    /** 해당 주차의 yyyy-MM-dd 7개 날짜키 반환 (시스템/앱 기본 로케일 사용) */
    fun weekDateKeys(weekKey: String): List<String> {
        val cal = calendarForWeekKey(weekKey) ?: return emptyList()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val out = mutableListOf<String>()
        repeat(7) {
            out.add(sdf.format(Date(cal.timeInMillis)))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    /** baseKey로부터 targetKey가 몇 주 차이인지(양수=과거, 0=이번 주, 음수=미래) */
    fun weeksBetween(baseKey: String = weekKey(), targetKey: String): Int {
        val base = calendarForWeekKey(baseKey) ?: return 0
        val target = calendarForWeekKey(targetKey) ?: return 0
        val diff = base.timeInMillis - target.timeInMillis
        return (diff / (7L * 24 * 60 * 60 * 1000)).toInt()
    }

    // ---- 로케일 대응 relative label ----

    /** 기존 시그니처 유지(시스템 기본 로케일 사용) */
    fun relativeLabel(weekKey: String, baseKey: String = weekKey()): String {
        return relativeLabelInternal(null, weekKey, baseKey)
    }

    /** 앱 로케일을 정확히 쓰고 싶으면 Context 버전 사용 */
    fun relativeLabel(ctx: Context, weekKey: String, baseKey: String = weekKey()): String {
        return relativeLabelInternal(ctx, weekKey, baseKey)
    }

    private fun relativeLabelInternal(ctx: Context?, weekKey: String, baseKey: String): String {
        val d = weeksBetween(baseKey, weekKey)
        val loc = ctx?.resources?.configuration?.locales?.get(0) ?: Locale.getDefault()
        val ko = loc.language.startsWith("ko", ignoreCase = true)

        return when {
            d <= 0 -> if (ko) "이번 주" else "This week"
            d == 1 -> if (ko) "지난 주" else "Last week"
            d > 1  -> if (ko) "${d}주 전" else "$d weeks ago"
            else   -> { // d < 0 (미래 주)
                val ahead = -d
                if (ko) "${ahead}주 후" else "In $ahead weeks"
            }
        }
    }
}
