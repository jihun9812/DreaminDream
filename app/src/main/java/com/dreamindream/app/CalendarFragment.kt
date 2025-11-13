package com.dreamindream.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.dreamindream.app.databinding.FragmentCalendarBinding
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

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate? = null

    /** 앱 현재 언어 로케일 */
    private val appLocale: Locale
        get() = resources.configuration.locales[0]


    private val dateFormatter by lazy {
        DateTimeFormatter.ofPattern(getString(R.string.fmt_month_year), appLocale)
    }

    private val CAL_START_YEAR = 2024
    private val CAL_END_YEAR = 2030


    private val colSun = Color.parseColor("#FF6B6B")
    private val colSat = Color.parseColor("#6FA8FF")
    private val colText = Color.parseColor("#E8F1F8")
    private val colDim = Color.parseColor("#A0A0A0")
    private val colAccent = Color.parseColor("#37C2D0")


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


        adapter = DreamInlineAdapter(
            mutableListOf(),
            onOpen = { entry ->
                DreamFragment.showResultDialog(requireContext(), entry.result)
            },
            onDelete = { pos, _ -> confirmDelete { deleteEntryAt(pos) } }
        )
        binding.recyclerDreams.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDreams.adapter = adapter

        // Calendar
        binding.calendarView.monthScrollListener = { month ->
            updateMonthText(month.yearMonth)
            binding.textViewMonthYear.alpha = 0f
            binding.textViewMonthYear.animate().alpha(1f).setDuration(200).start()
        }
        binding.calendarView.overScrollMode = View.OVER_SCROLL_NEVER

        val currentMonth = YearMonth.now()
        val daysOfWeek = daysOfWeek(DayOfWeek.SUNDAY)
        selectedDate = LocalDate.now()

        setupCalendar(currentMonth, daysOfWeek)
        setupEventListeners()
        updateMonthText(currentMonth)
        setupAds(view)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirestoreManager.getAllDreamDates(requireContext(), userId) {
                binding.calendarView.notifyCalendarChanged()
                selectedDate?.let { refreshInlineListFor(it) }
            }
        } else {
            selectedDate?.let { refreshInlineListFor(it) }
        }
        fixArabicIcons()

    }
    private fun fixArabicIcons() {
        val lang = resources.configuration.locales[0].language
        if (lang == "ar") {
            // RTL 때문에 한 번 뒤집힌 걸 다시 한 번 뒤집어서 원래대로
            binding.buttonPreviousMonth.scaleX = -1f
            binding.buttonNextMonth.scaleX = -1f
        } else {
            // 다른 언어에서는 정상값
            binding.buttonPreviousMonth.scaleX = 1f
            binding.buttonNextMonth.scaleX = 1f
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
                override fun create(view: View) = MonthHeaderViewContainer(view)
                override fun bind(container: MonthHeaderViewContainer, month: CalendarMonth) {

                }
            }
    }

    private fun bindDayView(container: DayViewContainer, day: CalendarDay) {
        container.textView.text = day.date.dayOfMonth.toString()
        val isSelected = selectedDate == day.date
        val isToday = day.date == LocalDate.now()

        // 기록 도트(강도)
        val count = getDreamCount(day.date)
        container.dreamIndicator.apply {
            visibility = if (count > 0) View.VISIBLE else View.GONE
            if (count > 0) {
                val size = when {
                    count >= 3 -> 7.dp()
                    count >= 1 -> 4.dp()
                    else -> 6.dp()
                }
                layoutParams = (layoutParams as ViewGroup.LayoutParams).apply {
                    width = size; height = size
                }
                alpha = when {
                    count >= 5 -> 1f
                    count >= 3 -> 0.9f
                    else -> 0.8f
                }
            }
        }

        container.view.setOnClickListener { handleDayClick(day.date) }

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
                    when (day.date.dayOfWeek) {
                        DayOfWeek.SUNDAY -> colSun
                        DayOfWeek.SATURDAY -> colSat
                        else -> colText
                    }
                )
            }
        }
    }

    private fun handleDayClick(date: LocalDate) {
        val oldDate = selectedDate
        selectedDate = date
        oldDate?.let { binding.calendarView.notifyDateChanged(it) }
        binding.calendarView.notifyDateChanged(date)

        updateMonthText(YearMonth.from(date))
        refreshInlineListFor(date)
    }

    private fun setupEventListeners() {
        binding.buttonPreviousMonth.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.minusMonths(1)?.let {
                binding.calendarView.smoothScrollToMonth(it)
                updateMonthText(it)

            }
        }
        binding.buttonNextMonth.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.plusMonths(1)?.let {
                binding.calendarView.smoothScrollToMonth(it)
                updateMonthText(it)
            }
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



    private fun refreshInlineListFor(date: LocalDate) {
        // 요일 약칭도 앱 언어로
        val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale)
        binding.dreamListTitle.text = getString(R.string.dream_list_title, date.toString(), dow)

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


        val newArr = JSONArray()
        for (i in 0 until arr.length()) if (i != pos) newArr.put(arr.getJSONObject(i))
        saveDreamArray(date, newArr)


        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirestoreManager.updateDreamsForDate(
                uid = uid,
                dateKey = date.toString(),
                itemsJson = newArr.toString(),
                onComplete = { /* no-op */ }
            )
        }

        adapter.removeAt(pos)
        if (adapter.itemCount == 0) {
            binding.emptyDreamText.visibility = View.VISIBLE
            binding.recyclerDreams.visibility = View.GONE
        }

        binding.calendarView.notifyDateChanged(date)
    }

    private fun confirmDelete(onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cal_delete_title)
            .setMessage(R.string.cal_delete_message)
            .setPositiveButton(R.string.common_delete) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.common_cancel, null)
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
