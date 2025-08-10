package com.example.dreamindream

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DreamListDialog : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DreamPreviewAdapter
    private lateinit var dreamList: MutableList<DreamEntry>
    private lateinit var selectedDate: LocalDate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedDate = arguments?.getString("date")?.let { LocalDate.parse(it) } ?: LocalDate.now()
        dreamList = loadDreamsForDate(requireContext(), selectedDate).toMutableList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_dream_list, container, false)

        // 제목 날짜 설정
        val titleView = view.findViewById<TextView>(R.id.textDreamListTitle)
        val formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
        val title = getString(R.string.dream_list_title, formattedDate)
        titleView.text = title



        // 리사이클러뷰 설정
        recyclerView = view.findViewById(R.id.recyclerDreamList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = DreamPreviewAdapter(
            dreamList,
            onItemClick = { entry ->
                dismiss()
                DreamFragment.showResultDialog(requireContext(), entry.result)
            },
            onDreamDelete = { position ->
                val deleted = dreamList.removeAt(position)
                saveDreamList(requireContext(), selectedDate, dreamList)
                adapter.notifyItemRemoved(position)
                Toast.makeText(requireContext(), "꿈이 삭제되었습니다.", Toast.LENGTH_SHORT).show()

                if (dreamList.isEmpty()) dismiss()
            }
        )

        recyclerView.adapter = adapter
        return view
    }

    private fun loadDreamsForDate(context: Context, date: LocalDate): List<DreamEntry> {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val prefsName = if (userId != null) "dream_history_$userId" else "dream_history"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = prefs.getString(date.toString(), null) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                DreamEntry(obj.getString("dream"), obj.getString("result"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveDreamList(context: Context, date: LocalDate, list: List<DreamEntry>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = context.getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE).edit()

        if (list.isEmpty()) {
            prefs.remove(date.toString())
        } else {
            val array = JSONArray().apply {
                list.forEach {
                    put(JSONObject().apply {
                        put("dream", it.dream)
                        put("result", it.result)
                    })
                }
            }
            prefs.putString(date.toString(), array.toString())
        }
        prefs.apply()
    }

    companion object {
        fun newInstance(date: LocalDate): DreamListDialog {
            return DreamListDialog().apply {
                arguments = Bundle().apply {
                    putString("date", date.toString())
                }
            }
        }
    }
}
