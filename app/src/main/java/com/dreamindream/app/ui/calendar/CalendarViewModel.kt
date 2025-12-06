package com.dreamindream.app.ui.calendar

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

class CalendarViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()
    private val resources get() = app.resources

    private val appLocale: Locale
        get() = resources.configuration.locales[0]

    private val dateFormatter by lazy {
        DateTimeFormatter.ofPattern(
            app.getString(R.string.fmt_month_year),
            appLocale
        )
    }

    private val CAL_START_YEAR = 2024
    private val CAL_END_YEAR = 2030

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState

    private val prefs: SharedPreferences by lazy { prefsForUser() }

    init {
        val today = LocalDate.now()
        val currentMonth = YearMonth.from(today)
        updateMonthTitle(currentMonth)
        refreshInlineListFor(today)
        loadRemoteDates()
    }

    // --- Public API -----------------------------------------------------------

    fun onMonthScroll(yearMonth: YearMonth) {
        updateMonthTitle(yearMonth)
    }

    fun onDateClicked(date: LocalDate): LocalDate {
        val old = _uiState.value.selectedDate
        _uiState.update { it.copy(selectedDate = date) }
        updateMonthTitle(YearMonth.from(date))
        refreshInlineListFor(date)
        return old
    }

    fun onDeleteDream(index: Int) {
        val date = _uiState.value.selectedDate
        deleteEntryAt(date, index)
    }

    // --- 내부 로직 ------------------------------------------------------------

    private fun updateMonthTitle(month: YearMonth) {
        _uiState.update {
            it.copy(
                monthTitle = dateFormatter.format(month)
            )
        }
    }

    private fun refreshInlineListFor(date: LocalDate) {
        val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale)
        val title = app.getString(R.string.dream_list_title, date.toString(), dow)

        val arr = getDreamArray(date)
        val list = mutableListOf<InlineDream>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)

            // 원본 꿈 내용 가져오기
            val fullDream = obj.optString("dream")

            // 미리보기용 텍스트 (줄바꿈 제거 및 길이 제한)
            val previewRaw = fullDream.replace("\n", " ").trim()
            val preview = if (previewRaw.length > 60)
                previewRaw.substring(0, 60) + "…"
            else
                previewRaw

            list += InlineDream(
                index = i,
                preview = preview,
                result = obj.optString("result"),
                originalDream = fullDream // ✅ 추가된 필드 매핑
            )
        }

        _uiState.update {
            it.copy(
                selectedDate = date,
                dreamListTitle = title,
                dreamsForSelectedDay = list,
                isDreamListEmpty = list.isEmpty()
            )
        }
    }

    private fun deleteEntryAt(date: LocalDate, pos: Int) {
        val arr = getDreamArray(date)
        if (pos !in 0 until arr.length()) return

        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            if (i != pos) newArr.put(arr.getJSONObject(i))
        }
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
    }

    // --- SharedPreferences 기반 로컬 저장 ------------------------------------

    private fun prefsForUser(): SharedPreferences {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        return if (userId != null) {
            app.getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)
        } else {
            app.getSharedPreferences("dream_history", Context.MODE_PRIVATE)
        }
    }

    private fun getDreamArray(date: LocalDate): JSONArray {
        val prefs = prefs
        val key = date.toString()
        return JSONArray(prefs.getString(key, "[]") ?: "[]")
    }

    private fun saveDreamArray(date: LocalDate, arr: JSONArray) {
        prefs.edit().putString(date.toString(), arr.toString()).apply()
    }

    fun getDreamCount(date: LocalDate): Int = getDreamArray(date).length()

    // --- Firestore Sync -----------------

    private fun loadRemoteDates() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            FirestoreManager.getAllDreamDates(app, uid) {
                _uiState.update { state ->
                    state.copy(calendarRefreshToken = state.calendarRefreshToken + 1)
                }
            }
        }
    }

    fun calendarStartMonth(): YearMonth = YearMonth.of(CAL_START_YEAR, 1)
    fun calendarEndMonth(): YearMonth = YearMonth.of(CAL_END_YEAR, 12)
    fun firstDayOfWeek(): DayOfWeek = DayOfWeek.SUNDAY
}