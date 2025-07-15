package com.example.dreamindream

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dreamindream.databinding.FragmentCalendarBinding
import com.google.android.gms.ads.AdRequest
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val currentMonth = YearMonth.now()
        val daysOfWeek = daysOfWeek(DayOfWeek.SUNDAY)

        binding.calendarView.setup(
            currentMonth.minusMonths(12),
            currentMonth.plusMonths(12),
            daysOfWeek.first()
        )
        binding.calendarView.scrollToMonth(currentMonth)

        updateMonthText(currentMonth)

        HolidayApi.fetchHolidays(2025,
            onSuccess = { result ->
                holidays.clear()
                holidays.addAll(result)
                binding.calendarView.notifyCalendarChanged()
            },
            onError = { it.printStackTrace() }
        )

        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.textView.text = day.date.dayOfMonth.toString()
                val isSelected = selectedDate == day.date
                val isToday = day.date == LocalDate.now()
                val holiday = holidays.find { it.date == day.date }

                container.textView.setBackgroundResource(
                    if (isSelected) R.drawable.day_selected_background else android.R.color.transparent
                )

                container.textView.setTextColor(
                    when {
                        holiday != null -> Color.RED
                        day.date.dayOfWeek == DayOfWeek.SUNDAY -> Color.RED
                        day.date.dayOfWeek == DayOfWeek.SATURDAY -> Color.BLUE
                        else -> Color.BLACK
                    }
                )

                container.view.setOnClickListener {
                    val oldDate = selectedDate
                    selectedDate = if (selectedDate == day.date) null else day.date
                    oldDate?.let { binding.calendarView.notifyDateChanged(it) }
                    binding.calendarView.notifyDateChanged(day.date)

                    selectedDate?.let {
                        updateMonthText(it.yearMonth)

                        val prefs = requireContext().getSharedPreferences("dream_history", 0)
                        val dreamArray = JSONArray(prefs.getString(it.toString(), "[]") ?: "[]")
                        val dreamList = mutableListOf<DreamEntry>()
                        for (i in 0 until dreamArray.length()) {
                            val obj = dreamArray.getJSONObject(i)
                            dreamList.add(DreamEntry(obj.getString("dream"), obj.getString("result")))
                        }
                        binding.recyclerViewDreams.adapter = DreamAdapter(dreamList)

                        binding.holidayTextView.text = holiday?.name ?: ""
                    }
                }
            }
        }

        binding.calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<EmptyViewContainer> {
            override fun create(view: View): EmptyViewContainer = EmptyViewContainer(view)
            override fun bind(container: EmptyViewContainer, month: CalendarMonth) {}
        }

        binding.buttonPreviousMonth.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.minusMonths(1)?.let {
                binding.calendarView.scrollToMonth(it)
                updateMonthText(it)
            }
        }

        binding.buttonNextMonth.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.plusMonths(1)?.let {
                binding.calendarView.scrollToMonth(it)
                updateMonthText(it)
            }
        }

        binding.recyclerViewDreams.layoutManager = LinearLayoutManager(requireContext())
        binding.adViewCalendar.loadAd(AdRequest.Builder().build())

        binding.backButton.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
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
}

class EmptyViewContainer(view: View) : ViewContainer(view)