package com.example.dreamindream

import android.graphics.Color
import android.os.Bundle
import android.content.Context
import android.view.*
import android.widget.TextView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.view.*
import com.example.dreamindream.databinding.FragmentCalendarBinding
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import java.time.*
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        binding.calendarView.monthScrollListener = { month ->
            updateMonthText(month.yearMonth)
            binding.textViewMonthYear.alpha = 0f
            binding.textViewMonthYear.animate().alpha(1f).setDuration(200).start()
        }

        val currentMonth = YearMonth.now()
        val daysOfWeek = daysOfWeek(DayOfWeek.SUNDAY)
        selectedDate = LocalDate.now()

        setupCalendar(currentMonth, daysOfWeek)
        setupEventListeners()
        loadHolidays()
        updateMonthText(currentMonth)
        setupAds(view)

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
            override fun bind(container: MonthHeaderViewContainer, month: CalendarMonth) {}
        }
    }

    private fun bindDayView(container: DayViewContainer, day: CalendarDay) {
        container.textView.text = day.date.dayOfMonth.toString()
        val isSelected = selectedDate == day.date
        val isToday = day.date == LocalDate.now()
        val holiday = holidays.find { it.date == day.date }
        val hasDreams = checkHasDreams(day.date)

        container.dreamIndicator.visibility = if (hasDreams) View.VISIBLE else View.GONE

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
                container.textView.setTextColor(Color.parseColor("#A0A0A0"))
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
    }

    private fun checkHasDreams(date: LocalDate): Boolean {
        val prefs = requireContext().getSharedPreferences("dream_history", Context.MODE_PRIVATE)
        val dreamArray = JSONArray(prefs.getString(date.toString(), "[]") ?: "[]")
        return dreamArray.length() > 0
    }

    private fun handleDayClick(date: LocalDate, holiday: Holiday?) {
        val oldDate = selectedDate
        selectedDate = date
        oldDate?.let { binding.calendarView.notifyDateChanged(it) }
        binding.calendarView.notifyDateChanged(date)
        updateMonthText(date.yearMonth)

        if (holiday != null) {
            binding.holidayTextView.text = holiday.name
            binding.holidayTextView.visibility = View.VISIBLE
        } else {
            binding.holidayTextView.text = ""
            binding.holidayTextView.visibility = View.GONE
        }

        if (checkHasDreams(date)) {
            DreamListDialog.newInstance(date)
                .show(parentFragmentManager, "DreamListDialog")
        }
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
