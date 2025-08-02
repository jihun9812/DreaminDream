package com.example.dreamindream

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DreamListDialog : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var dreamList: List<DreamEntry>
    private lateinit var selectedDate: LocalDate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.getString("date")?.let {
            selectedDate = LocalDate.parse(it)
        } ?: run {
            selectedDate = LocalDate.now() // 안전하게 대체
        }

        dreamList = loadDreamsForDate(requireContext(), selectedDate)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_dream_list, container, false)

        //  날짜 텍스트 세팅
        val titleView = view.findViewById<TextView>(R.id.textDreamListTitle)
        val formatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일")
        val formattedDate = selectedDate.format(formatter)
        titleView.text = formattedDate + "의 꿈들"

        recyclerView = view.findViewById(R.id.recyclerDreamList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = DreamPreviewAdapter(dreamList) { entry ->
            dismiss()
            DreamFragment.showDreamResultDialog(requireContext(), entry.result)
        }

        return view
    }

    private fun loadDreamsForDate(context: Context, date: LocalDate): List<DreamEntry> {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val primaryKey = if (userId != null) "dream_history_$userId" else "dream_history"
        val fallbackKey = "dream_history"

        val prefsPrimary = context.getSharedPreferences(primaryKey, Context.MODE_PRIVATE)
        val prefsFallback = context.getSharedPreferences(fallbackKey, Context.MODE_PRIVATE)

        val json = prefsPrimary.getString(date.toString(), null)
            ?: prefsFallback.getString(date.toString(), null)
            ?: return emptyList()

        return try {
            val array = JSONArray(json)
            val list = mutableListOf<DreamEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val dream = obj.getString("dream")
                val result = obj.getString("result")
                list.add(DreamEntry(dream, result))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    companion object {
        fun newInstance(date: LocalDate): DreamListDialog {
            val dialog = DreamListDialog()
            dialog.arguments = Bundle().apply {
                putString("date", date.toString())
            }
            return dialog
        }
    }
}
