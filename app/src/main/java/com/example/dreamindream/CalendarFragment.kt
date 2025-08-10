package com.example.dreamindream

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.dreamindream.databinding.FragmentCalendarBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.kizitonwose.calendar.core.yearMonth
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate? = null
    private val holidays = mutableListOf<Holiday>()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월")

    // ---- Palette(대기업 톤) ----
    private val colSun = Color.parseColor("#FF6B6B")
    private val colSat = Color.parseColor("#6FA8FF")
    private val colText = Color.parseColor("#E8F1F8")
    private val colDim = Color.parseColor("#A0A0A0")
    private val colAccent = Color.parseColor("#37C2D0")

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

        // 뒤로가기 → 홈
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.slide_in_left,
                            R.anim.slide_out_right,
                            R.anim.slide_in_right,
                            R.anim.slide_out_left
                        )
                        .replace(R.id.fragment_container, HomeFragment())
                        .disallowAddToBackStack()
                        .commit()
                }
            })

        // 월 스크롤 시 타이틀 페이드
        binding.calendarView.monthScrollListener = { month ->
            updateMonthText(month.yearMonth)
            binding.textViewMonthYear.alpha = 0f
            binding.textViewMonthYear.animate().alpha(1f).setDuration(200).start()
        }
        // 오버스크롤 글로우 제거(영상 느낌, 안정감)
        binding.calendarView.overScrollMode = View.OVER_SCROLL_NEVER

        val currentMonth = YearMonth.now()
        val daysOfWeek = daysOfWeek(DayOfWeek.SUNDAY)
        selectedDate = LocalDate.now()

        setupCalendar(currentMonth, daysOfWeek)
        setupEventListeners()
        loadHolidays()
        updateMonthText(currentMonth)
        setupAds(view)

        // 로컬 캐시 갱신(Firestore→SharedPreferences) 후 캘린더 리프레시
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirestoreManager.getAllDreamDates(requireContext(), userId) {
                binding.calendarView.notifyCalendarChanged()
                // 선택일 요약도 최신으로
                selectedDate?.let { updateSelectedDayCard(it) }
            }
        } else {
            selectedDate?.let { updateSelectedDayCard(it) }
        }
    }

    private fun setupCalendar(currentMonth: YearMonth, daysOfWeek: List<DayOfWeek>) {
        binding.calendarView.setup(
            currentMonth.minusMonths(12),
            currentMonth.plusMonths(12),
            daysOfWeek.first()
        )
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
                    // header는 example_month_header.xml에서 요일만 표현하므로 별도 bind 불필요
                }
            }
    }

    private fun bindDayView(container: DayViewContainer, day: CalendarDay) {
        container.textView.text = day.date.dayOfMonth.toString()
        val isSelected = selectedDate == day.date
        val isToday = day.date == LocalDate.now()
        val holiday = holidays.find { it.date == day.date }

        // 기록 도트(강도) 반영
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

        container.view.setOnClickListener {
            handleDayClick(day.date, holiday)
        }

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
                        holiday != null -> colSun
                        day.date.dayOfWeek == DayOfWeek.SUNDAY -> colSun
                        day.date.dayOfWeek == DayOfWeek.SATURDAY -> colSat
                        else -> colText
                    }
                )
            }
        }
    }

    private fun handleDayClick(date: LocalDate, holiday: Holiday?) {
        val oldDate = selectedDate
        selectedDate = date
        oldDate?.let { binding.calendarView.notifyDateChanged(it) }
        binding.calendarView.notifyDateChanged(date)

        // 월 텍스트 갱신
        updateMonthText(YearMonth.from(date))

        // 공휴일 라벨 토글
        if (holiday != null) {
            binding.holidayTextView.text = holiday.name
            binding.holidayTextView.visibility = View.VISIBLE
        } else {
            binding.holidayTextView.text = ""
            binding.holidayTextView.visibility = View.GONE
        }

        // 하단 요약 카드만 갱신 (상세는 버튼으로만 열기)
        updateSelectedDayCard(date)

    }

    private fun setupEventListeners() {
        binding.buttonPreviousMonth.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.minusMonths(1)
                ?.let { targetMonth ->
                    binding.calendarView.smoothScrollToMonth(targetMonth)
                    updateMonthText(targetMonth)
                }
        }
        binding.buttonNextMonth.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.plusMonths(1)
                ?.let { targetMonth ->
                    binding.calendarView.smoothScrollToMonth(targetMonth)
                    updateMonthText(targetMonth)
                }
        }
    }

    private fun loadHolidays() {
        try {
            HolidayApi.fetchHolidays(
                2025,
                onSuccess = {
                    holidays.clear()
                    holidays.addAll(it)
                    binding.calendarView.notifyCalendarChanged()
                },
                onError = { it.printStackTrace() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupAds(view: View) {
        try {
            MobileAds.initialize(requireContext())
            val adView = view.findViewById<AdView>(R.id.adViewCalendar)
            adView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMonthText(month: YearMonth) {
        binding.textViewMonthYear.text = dateFormatter.format(month)
    }

    // --- 요약 카드 & 데이터 유틸 ---

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun getDreamArray(date: LocalDate): JSONArray {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val prefs = if (userId != null) {
            requireContext().getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)
        } else {
            requireContext().getSharedPreferences("dream_history", Context.MODE_PRIVATE)
        }
        return JSONArray(prefs.getString(date.toString(), "[]") ?: "[]")
    }

    private fun getDreamCount(date: LocalDate): Int = getDreamArray(date).length()

    private fun checkHasDreams(date: LocalDate): Boolean = getDreamCount(date) > 0

    private fun previewOf(obj: JSONObject): String {
        val first = obj.optString("dream").replace("\n", " ").trim()
        return if (first.length > 50) first.substring(0, 50) + "…" else first
    }

    private fun updateSelectedDayCard(date: LocalDate) {
        val arr = getDreamArray(date)
        if (arr.length() == 0) {
            binding.selectedDayCard.visibility = View.GONE
            return
        }

        val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREA) // 월/화/...
        binding.selectedDateTitle.text = "$date ($dow)"

        val limit = minOf(2, arr.length())
        val previews = buildString {
            for (i in 0 until limit) {
                append("• ").append(previewOf(arr.getJSONObject(i)))
                if (i < limit - 1) append("\n")
            }
            if (arr.length() > limit) append("\n외 ${arr.length() - limit}개 더 있음")
        }
        binding.selectedDreamPreview.text = previews
        binding.selectedDayCard.visibility = View.VISIBLE

        binding.btnOpenDreamList.setOnClickListener {
            DreamListDialog.newInstance(date).show(parentFragmentManager, "DreamListDialog")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ---- Day/Month View Containers ----
class DayViewContainer(view: View) : ViewContainer(view) {
    val textView: TextView = view.findViewById(R.id.calendarDayText)
    val dreamIndicator: View = view.findViewById(R.id.dreamIndicator)
}

class MonthHeaderViewContainer(view: View) : ViewContainer(view)
