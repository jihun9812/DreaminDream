package com.example.dreamindream

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dreamindream.databinding.FragmentCalendarBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate? = null

    /**
     * 휴일을 스레드-세이프하게 보관하기 위한 맵.
     * key: 날짜, value: 휴일명
     */
    private val holidayMap = ConcurrentHashMap<LocalDate, String>()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월")

    // ✅ 캘린더 표시 범위: 2024.01 ~ 2030.12
    private val CAL_START_YEAR = 2024
    private val CAL_END_YEAR = 2030

    // Palette
    private val colSun = Color.parseColor("#FF6B6B")
    private val colSat = Color.parseColor("#6FA8FF")
    private val colText = Color.parseColor("#E8F1F8")
    private val colDim = Color.parseColor("#A0A0A0")
    private val colAccent = Color.parseColor("#37C2D0")

    // Inline list
    private lateinit var adapter: DreamInlineAdapter

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recycler
        adapter = DreamInlineAdapter(
            mutableListOf(),
            onOpen = { entry ->
                // 폴드 전용 우측 패널이 없다면 기존 다이얼로그 사용
                DreamFragment.showResultDialog(requireContext(), entry.result)
            },
            onDelete = { pos, _ -> confirmDelete { deleteEntryAt(pos) } }
        )
        binding.recyclerDreams.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDreams.adapter = adapter

        // Calendar
        binding.calendarView.monthScrollListener = { month ->
            updateMonthText(month.yearMonth)
            clearHolidayBanner() // 🔴 달 바뀌면 휴일 라벨 숨김
            binding.textViewMonthYear.alpha = 0f
            binding.textViewMonthYear.animate().alpha(1f).setDuration(200).start()
        }
        binding.calendarView.overScrollMode = View.OVER_SCROLL_NEVER

        val currentMonth = YearMonth.now()
        val daysOfWeek = daysOfWeek(DayOfWeek.SUNDAY)
        selectedDate = LocalDate.now()

        setupCalendar(currentMonth, daysOfWeek)
        setupEventListeners()
        loadHolidays2030() // ✅ 2024~2030 캐시+프리패치
        updateMonthText(currentMonth)
        setupAds(view)

        // Firestore->로컬 동기화 후 반영
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirestoreManager.getAllDreamDates(requireContext(), userId) {
                binding.calendarView.notifyCalendarChanged()
                selectedDate?.let { refreshInlineListFor(it) }
            }
        } else {
            selectedDate?.let { refreshInlineListFor(it) }
        }
    }

    private fun setupCalendar(currentMonth: YearMonth, daysOfWeek: List<DayOfWeek>) {
        val start = YearMonth.of(CAL_START_YEAR, 1)
        val end = YearMonth.of(CAL_END_YEAR, 12)
        binding.calendarView.setup(start, end, daysOfWeek.first())
        binding.calendarView.scrollToMonth(currentMonth)

        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                bindDayView(container, day)
            }
        }

        binding.calendarView.monthHeaderBinder =
            object : MonthHeaderFooterBinder<MonthHeaderViewContainer> {
                override fun create(view: View): MonthHeaderViewContainer =
                    MonthHeaderViewContainer(view)
                override fun bind(container: MonthHeaderViewContainer, month: CalendarMonth) {
                    // 요일 헤더 필요 시 여기에서 처리
                }
            }
    }

    private fun bindDayView(container: DayViewContainer, day: CalendarDay) {
        container.textView.text = day.date.dayOfMonth.toString()
        val isSelected = selectedDate == day.date
        val isToday = day.date == LocalDate.now()
        val holidayName = holidayMap[day.date] // ✅ O(1) 조회, 동시 접근 안전

        // 기록 도트(강도)
        val count = getDreamCount(day.date)
        container.dreamIndicator.apply {
            visibility = if (count > 0) View.VISIBLE else View.GONE
            if (count > 0) {
                val size = when {
                    count >= 5 -> 10.dp()
                    count >= 3 -> 8.dp()
                    else -> 6.dp()
                }
                layoutParams = (layoutParams as ViewGroup.LayoutParams).apply {
                    width = size; height = size
                }
                alpha = when {
                    count >= 5 -> 1f
                    count >= 3 -> 0.85f
                    else -> 0.7f
                }
            }
        }

        container.view.setOnClickListener { handleDayClick(day.date, holidayName) }

        when {
            isSelected -> {
                container.textView.setBackgroundResource(R.drawable.day_selected_background)
                container.textView.setTextColor(Color.WHITE)
            }
            day.position != DayPosition.MonthDate -> {
                container.textView.setBackgroundResource(android.R.color.transparent)
                container.textView.setTextColor(colDim)
            }
            isToday -> {
                container.textView.setBackgroundResource(R.drawable.day_today_background)
                container.textView.setTextColor(colAccent)
            }
            else -> {
                container.textView.setBackgroundResource(android.R.color.transparent)
                container.textView.setTextColor(
                    when {
                        holidayName != null -> colSun
                        day.date.dayOfWeek == DayOfWeek.SUNDAY -> colSun
                        day.date.dayOfWeek == DayOfWeek.SATURDAY -> colSat
                        else -> colText
                    }
                )
            }
        }
    }

    private fun handleDayClick(date: LocalDate, holidayName: String?) {
        val oldDate = selectedDate
        selectedDate = date
        oldDate?.let { binding.calendarView.notifyDateChanged(it) }
        binding.calendarView.notifyDateChanged(date)

        updateMonthText(YearMonth.from(date))

        if (holidayName != null) {
            binding.holidayTextView.text = holidayName
            binding.holidayTextView.visibility = View.VISIBLE
        } else {
            clearHolidayBanner()
        }

        refreshInlineListFor(date)
    }

    private fun setupEventListeners() {
        binding.buttonPreviousMonth.setOnClickListener {
            clearHolidayBanner() // 🔴 버튼으로 이전 달 이동 전 숨김
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.minusMonths(1)?.let {
                binding.calendarView.smoothScrollToMonth(it)
                updateMonthText(it)
            }
        }
        binding.buttonNextMonth.setOnClickListener {
            clearHolidayBanner() // 🔴 버튼으로 다음 달 이동 전 숨김
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.plusMonths(1)?.let {
                binding.calendarView.smoothScrollToMonth(it)
                updateMonthText(it)
            }
        }
    }

    /** 🔴 상단 휴일 라벨 즉시 숨김 */
    private fun clearHolidayBanner() {
        binding.holidayTextView.text = ""
        binding.holidayTextView.visibility = View.GONE
    }

    /**
     * ✅ 2024~2030 전체 휴일을 캐시에서 즉시 로드 후,
     *    비어있는 연도만 네트워크로 가져와 저장/반영.
     *    (holidayMap은 ConcurrentHashMap으로 동시 접근 안전)
     */
    private fun loadHolidays2030() {
        try {
            holidayMap.clear()

            // 1) 캐시 우선 로드
            val cached = HolidayStorage.loadHolidaysRange(requireContext(), CAL_START_YEAR, CAL_END_YEAR)
            for (h in cached) holidayMap[h.date] = h.name
            binding.calendarView.notifyCalendarChanged()

            // 2) 빈 연도만 API 호출해서 채우기
            val missingYears = (CAL_START_YEAR..CAL_END_YEAR).filter { year ->
                HolidayStorage.loadHolidays(requireContext(), year).isEmpty()
            }
            if (missingYears.isEmpty()) return

            // 순차 프리패치
            fun fetchNext(idx: Int) {
                if (idx >= missingYears.size) return
                val y = missingYears[idx]
                HolidayApi.fetchHolidays(
                    y,
                    onSuccess = { list ->
                        // 맵에 병합 (원자적 대입)
                        for (h in list) holidayMap[h.date] = h.name
                        binding.calendarView.notifyCalendarChanged()
                        fetchNext(idx + 1)
                    },
                    onError = {
                        it.printStackTrace()
                        fetchNext(idx + 1)
                    }
                )
            }
            fetchNext(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupAds(view: View) {
        try {
            MobileAds.initialize(requireContext())
            val adView = view.findViewById<AdView>(R.id.adViewCalendar)
            adView?.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMonthText(month: YearMonth) {
        binding.textViewMonthYear.text = dateFormatter.format(month)
    }

    // --- Inline list helpers ---

    private fun refreshInlineListFor(date: LocalDate) {
        val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREA)
        binding.dreamListTitle.text = "${date} (${dow})의 꿈들"

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

        if (list.isEmpty()) {
            binding.emptyDreamText.visibility = View.VISIBLE
            binding.recyclerDreams.visibility = View.GONE
        } else {
            binding.emptyDreamText.visibility = View.GONE
            binding.recyclerDreams.visibility = View.VISIBLE
        }
        adapter.replaceAll(list)
    }

    private fun deleteEntryAt(pos: Int) {
        val date = selectedDate ?: return
        val arr = getDreamArray(date)
        if (pos !in 0 until arr.length()) return

        // 1) 로컬 삭제
        val newArr = JSONArray()
        for (i in 0 until arr.length()) if (i != pos) newArr.put(arr.getJSONObject(i))
        saveDreamArray(date, newArr)

        // 2) UI 반영
        adapter.removeAt(pos)
        if (adapter.itemCount == 0) {
            binding.emptyDreamText.visibility = View.VISIBLE
            binding.recyclerDreams.visibility = View.GONE
        }
        // 3) 달력 도트 갱신
        binding.calendarView.notifyDateChanged(date)
    }

    private fun confirmDelete(onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("삭제하시겠습니까?")
            .setMessage("이 꿈 기록을 삭제하면 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ -> onConfirm() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun prefsForUser(): android.content.SharedPreferences {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        return if (userId != null)
            requireContext().getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)
        else
            requireContext().getSharedPreferences("dream_history", Context.MODE_PRIVATE)
    }

    private fun getDreamArray(date: LocalDate): JSONArray {
        val prefs = prefsForUser()
        return JSONArray(prefs.getString(date.toString(), "[]") ?: "[]")
    }

    private fun saveDreamArray(date: LocalDate, arr: JSONArray) {
        val prefs = prefsForUser()
        prefs.edit().putString(date.toString(), arr.toString()).apply()
    }

    private fun getDreamCount(date: LocalDate): Int = getDreamArray(date).length()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DayViewContainer(view: View) : ViewContainer(view) {
    val textView: TextView = view.findViewById(R.id.calendarDayText)
    val dreamIndicator: View = view.findViewById(R.id.dreamIndicator)
}

class MonthHeaderViewContainer(view: View) : ViewContainer(view)
