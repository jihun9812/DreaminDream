package com.example.dreamindream

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dreamindream.databinding.FragmentCalendarBinding
import com.google.android.gms.ads.AdRequest
import com.google.firebase.auth.FirebaseAuth
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.view.*
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate? = null
    private val holidays = mutableListOf<Holiday>()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월")

    private val colorRed = Color.parseColor("#F44336")
    private val colorBlue = Color.parseColor("#2196F3")
    private val colorBlack = Color.parseColor("#000000")
    private val colorOrange = Color.parseColor("#FF9800")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뒤로가기 시 홈으로 슬라이드 전환
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val current = parentFragmentManager.findFragmentById(R.id.fragment_container)
                    if (current !is HomeFragment) {
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
                }
            })


        val currentMonth = YearMonth.now()
        val daysOfWeek = daysOfWeek(DayOfWeek.SUNDAY)
        selectedDate = LocalDate.now()

        setupCalendar(currentMonth, daysOfWeek)
        setupEventListeners()
        loadHolidays()
        updateMonthText(currentMonth)
        loadDreamsForDate(selectedDate!!)
        setupRecyclerView()
        setupAds()

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirestoreManager.getAllDreamDates(requireContext(), userId) {
                binding.calendarView.notifyCalendarChanged()
            }
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

        binding.calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthHeaderViewContainer> {
            override fun create(view: View): MonthHeaderViewContainer = MonthHeaderViewContainer(view)
            override fun bind(container: MonthHeaderViewContainer, month: CalendarMonth) {
                // 요일 헤더는 레이아웃에서 처리
            }
        }
    }

    private fun bindDayView(container: DayViewContainer, day: CalendarDay) {
        container.textView.text = day.date.dayOfMonth.toString()

        val isSelected = selectedDate == day.date
        val isToday = day.date == LocalDate.now()
        val holiday = holidays.find { it.date == day.date }

        val hasDreams = checkHasDreams(day.date)
        container.dreamIndicator.visibility = if (hasDreams) View.VISIBLE else View.GONE

        when {
            isSelected -> {
                container.textView.setBackgroundResource(R.drawable.day_selected_background)
                container.textView.setTextColor(Color.WHITE)
            }
            isToday -> {
                container.textView.setBackgroundResource(R.drawable.day_today_background)
                container.textView.setTextColor(colorOrange)
            }
            else -> {
                container.textView.setBackgroundResource(android.R.color.transparent)
                container.textView.setTextColor(
                    when {
                        holiday != null -> colorRed
                        day.date.dayOfWeek == DayOfWeek.SUNDAY -> colorRed
                        day.date.dayOfWeek == DayOfWeek.SATURDAY -> colorBlue
                        else -> colorBlack
                    }
                )
            }
        }

        container.view.setOnClickListener {
            handleDayClick(day.date, holiday)
        }
    }

    private fun checkHasDreams(date: LocalDate): Boolean {
        val prefs = requireContext().getSharedPreferences("dream_history", 0)
        val dreamArray = JSONArray(prefs.getString(date.toString(), "[]") ?: "[]")
        return dreamArray.length() > 0
    }

    private fun handleDayClick(date: LocalDate, holiday: Holiday?) {
        val oldDate = selectedDate
        selectedDate = date

        oldDate?.let { binding.calendarView.notifyDateChanged(it) }
        binding.calendarView.notifyDateChanged(date)

        updateMonthText(date.yearMonth)
        loadDreamsForDate(date)
        binding.holidayTextView.text = holiday?.name ?: ""
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
            HolidayApi.fetchHolidays(2025,
                onSuccess = { result ->
                    holidays.clear()
                    holidays.addAll(result)
                    binding.calendarView.notifyCalendarChanged()
                },
                onError = {
                    it.printStackTrace()
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadDreamsForDate(date: LocalDate) {
        try {
            val prefs = requireContext().getSharedPreferences("dream_history", 0)
            val dreamArray = JSONArray(prefs.getString(date.toString(), "[]") ?: "[]")
            val dreamList = mutableListOf<DreamEntry>()

            for (i in 0 until dreamArray.length()) {
                val obj = dreamArray.getJSONObject(i)
                dreamList.add(DreamEntry(
                    obj.getString("dream"),
                    obj.getString("result")
                ))
            }

            binding.recyclerViewDreams.adapter = DreamAdapter(dreamList)
        } catch (e: Exception) {
            e.printStackTrace()
            binding.recyclerViewDreams.adapter = DreamAdapter(emptyList())
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerViewDreams.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupAds() {
        try {
            binding.adViewCalendar.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMonthText(month: YearMonth) {
        binding.textViewMonthYear.text = dateFormatter.format(month)
    }

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
