package com.dreamindream.app.ui.calendar

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.R
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.dreamindream.app.DreamEntry   // ✅ 추가

class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val locale: Locale get() = ctx.resources.configuration.locales[0]

    private val _ui = MutableStateFlow(CalendarUiState())
    val ui: StateFlow<CalendarUiState> = _ui

    private fun prefsForUser(): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        return if (uid != null)
            ctx.getSharedPreferences("dream_history_$uid", Context.MODE_PRIVATE)
        else
            ctx.getSharedPreferences("dream_history", Context.MODE_PRIVATE)
    }

    init {
        val m = YearMonth.now()
        _ui.value = _ui.value.copy(month = m, selected = LocalDate.now())
        loadMonth(m)
        select(LocalDate.now())
    }

    fun prevMonth() {
        val m = _ui.value.month.minusMonths(1)
        _ui.value = _ui.value.copy(month = m)
        loadMonth(m)
        val day = _ui.value.selected.dayOfMonth.coerceAtMost(m.lengthOfMonth())
        select(LocalDate.of(m.year, m.month, day))
    }

    fun nextMonth() {
        val m = _ui.value.month.plusMonths(1)
        _ui.value = _ui.value.copy(month = m)
        loadMonth(m)
        val day = _ui.value.selected.dayOfMonth.coerceAtMost(m.lengthOfMonth())
        select(LocalDate.of(m.year, m.month, day))
    }

    fun select(date: LocalDate) {
        _ui.value = _ui.value.copy(selected = date)
        refreshInlineListFor(date)
    }

    private fun loadMonth(month: YearMonth) {
        val map = mutableMapOf<LocalDate, Int>()
        val prefs = prefsForUser()
        for (d in 1..month.lengthOfMonth()) {
            val date = LocalDate.of(month.year, month.month, d)
            val count = JSONArray(prefs.getString(date.toString(), "[]") ?: "[]").length()
            if (count > 0) map[date] = count
        }
        _ui.value = _ui.value.copy(counts = map)
    }

    private fun getDreamArray(date: LocalDate): JSONArray {
        val prefs = prefsForUser()
        return JSONArray(prefs.getString(date.toString(), "[]") ?: "[]")
    }

    private fun saveDreamArray(date: LocalDate, arr: JSONArray) {
        prefsForUser().edit().putString(date.toString(), arr.toString()).apply()
    }

    private fun refreshInlineListFor(date: LocalDate) {
        val arr = getDreamArray(date)
        val list = mutableListOf<DreamEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val preview = obj.optString("dream").replace("\n", " ").trim()
            list += DreamEntry(
                dream = if (preview.length > 60) preview.substring(0, 60) + "…" else preview,
                result = obj.optString("result")
            )
        }
        _ui.value = _ui.value.copy(dreams = list.ifEmpty { emptyList() })
    }

    fun openResult(entry: DreamEntry) {
        _ui.value = _ui.value.copy(showResult = entry.result)
    }

    fun closeResult() { _ui.value = _ui.value.copy(showResult = null) }

    fun askDelete(index: Int) { _ui.value = _ui.value.copy(askDeleteAt = index) }

    fun confirmDelete() {
        val idx = _ui.value.askDeleteAt ?: return
        val date = _ui.value.selected
        val arr = getDreamArray(date)
        if (idx !in 0 until arr.length()) { _ui.value = _ui.value.copy(askDeleteAt = null); return }

        val newArr = JSONArray()
        for (i in 0 until arr.length()) if (i != idx) newArr.put(arr.getJSONObject(i))
        saveDreamArray(date, newArr)

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirestoreManager.updateDreamsForDate(
                uid = uid,
                dateKey = date.toString(),
                itemsJson = newArr.toString(),
                onComplete = { /* no-op */ }
            )
        }

        refreshInlineListFor(date)
        loadMonth(_ui.value.month)
        _ui.value = _ui.value.copy(askDeleteAt = null)
    }

    fun cancelDelete() { _ui.value = _ui.value.copy(askDeleteAt = null) }

    fun weekdayShortName(dow: DayOfWeek): String =
        dow.getDisplayName(TextStyle.SHORT, locale)
}
