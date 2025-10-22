package com.dreamindream.app

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DreamPickerDialogFragment : DialogFragment() {

    data class Item(
        val idRef: String,     // "yyyy-MM-dd|{entryId}"
        val dateKey: String,
        val dream: String,
        val interp: String,
        val ts: Long
    )

    companion object {
        private const val TAG = "DreamPickerDialog"
        private const val ARG_WEEK_KEY = "weekKey"
        private const val ARG_MIN = "min"
        private const val ARG_MAX = "max"

        fun showOnce(
            fm: FragmentManager,
            weekKey: String = WeekUtils.weekKey(),
            minPick: Int = 2,
            maxPick: Int = 4,
            onPicked: (List<String>) -> Unit
        ) {
            if (fm.findFragmentByTag(TAG) != null) return
            DreamPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WEEK_KEY, weekKey)
                    putInt(ARG_MIN, minPick)
                    putInt(ARG_MAX, maxPick)
                }
                this.onPicked = onPicked
            }.show(fm, TAG)
        }
    }

    private var onPicked: ((List<String>) -> Unit)? = null
    private val minPick by lazy { arguments?.getInt(ARG_MIN) ?: 2 }
    private val maxPick by lazy { arguments?.getInt(ARG_MAX) ?: 4 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog_NoActionBar_MinWidth)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                window?.apply {
                    setLayout((resources.displayMetrics.widthPixels * 0.96f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
                    setDimAmount(0.45f)
                }
                setCanceledOnTouchOutside(false)
            }
        }
    }

    // 작은 흔들림 + 햅틱
    private fun shake(v: View) {
        v.animate().translationX(10f).setDuration(40).withEndAction {
            v.animate().translationX(-10f).setDuration(40).withEndAction {
                v.animate().translationX(0f).setDuration(40).start()
            }.start()
        }.start()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.dialog_dream_picker, container, false)
        val weekKey = requireArguments().getString(ARG_WEEK_KEY) ?: WeekUtils.weekKey()

        val title = root.findViewById<TextView>(R.id.dp_title)
        val sub   = root.findViewById<TextView>(R.id.dp_sub)
        val list  = root.findViewById<RecyclerView>(R.id.dp_list)
        val badge = root.findViewById<TextView>(R.id.dp_badge)
        val ok    = root.findViewById<Button>(R.id.dp_ok)
        val cancel= root.findViewById<Button>(R.id.dp_cancel)
        val progress = root.findViewById<ProgressBar>(R.id.dp_progress)
        val empty = root.findViewById<TextView>(R.id.dp_empty)

        title.text = "주간 리포트 만들기"
        sub.text = "이번 주 리포트에 포함할 꿈을 2~4개 선택하세요."

        val adapter = Adapter(maxPick).apply {
            onSelectionChanged = {
                val c = selectedCount
                badge.text = "$c / $maxPick"

                val okEnabled = (c in minPick..maxPick)   // ✅ 고정 (버튼 활성화 조건)
                ok.isEnabled = okEnabled

                badge.setTextColor(if (okEnabled) Color.parseColor("#1F2234") else Color.RED)
                if (!okEnabled && c > 0) {
                    badge.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    shake(badge)
                }
            }
            onMaxExceeded = {
                badge.performHapticFeedback(HapticFeedbackConstants.REJECT)
                shake(badge)
            }
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        ok.isEnabled = false
        ok.setOnClickListener {
            val selected = adapter.selectedIdRefs()
            if (selected.size !in minPick..maxPick) {
                badge.performHapticFeedback(HapticFeedbackConstants.REJECT)
                shake(badge)
                return@setOnClickListener
            }
            onPicked?.invoke(selected)
            dismissAllowingStateLoss()
        }
        cancel.setOnClickListener { dismissAllowingStateLoss() }

        // 데이터 로드
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            sub.text = "로그인이 필요합니다"
            ok.isEnabled = false
        } else {
            progress.isVisible = true
            loadWeekEntries(uid, weekKey) { items ->
                progress.isVisible = false
                val sorted = items.sortedBy { it.ts }
                adapter.submit(sorted)
                adapter.preselectLast(minOf(2, sorted.size, maxPick)) // 처음 2개 프리셀렉트
                empty.isVisible = sorted.isEmpty()
            }
        }

        return root
    }

    /** 이번 주 7일 entries 수집 → Item 리스트로 변환 */
    private fun loadWeekEntries(uid: String, weekKey: String, onDone: (List<Item>) -> Unit) {
        val dates = WeekUtils.weekDateKeys(weekKey)
        if (dates.isEmpty()) { onDone(emptyList()); return }

        val db = FirebaseFirestore.getInstance()
        val out = mutableListOf<Item>()
        var done = 0

        fun completeIfReady() {
            if (++done == dates.size) onDone(out)
        }

        for (dateKey in dates) {
            db.collection("users").document(uid)
                .collection("dreams").document(dateKey)
                .collection("entries")
                .get()
                .addOnSuccessListener { snap ->
                    snap.forEach { d ->
                        val dream = d.getString("dream").orEmpty()
                        val interp = d.getString("result").orEmpty()
                        val ts = d.getLong("timestamp") ?: 0L
                        if (dream.isNotBlank() || interp.isNotBlank()) {
                            out += Item(
                                idRef = "$dateKey|${d.id}",
                                dateKey = dateKey,
                                dream = dream,
                                interp = interp,
                                ts = ts
                            )
                        }
                    }
                    completeIfReady()
                }
                .addOnFailureListener { completeIfReady() }
        }
    }

    // ───────────── RecyclerView ─────────────

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: ViewGroup = v.findViewById(R.id.row_root)
        val card: MaterialCardView = v.findViewById(R.id.row_card)
        val date: TextView = v.findViewById(R.id.row_date)
        val dream: TextView = v.findViewById(R.id.row_dream)
        val interp: TextView = v.findViewById(R.id.row_interp)
        val check: ImageView = v.findViewById(R.id.row_check) // ✅ 오버레이 체크
    }

    class Adapter(private val maxPick: Int) : RecyclerView.Adapter<VH>() {
        private val data = mutableListOf<Item>()
        private val selected = LinkedHashSet<String>()
        var onSelectionChanged: (() -> Unit)? = null
        var onMaxExceeded: (() -> Unit)? = null
        val selectedCount get() = selected.size

        fun submit(items: List<Item>) {
            data.clear(); data.addAll(items)
            notifyDataSetChanged()
            onSelectionChanged?.invoke()
        }

        fun selectedIdRefs(): List<String> = selected.toList()

        fun preselectLast(n: Int) {
            selected.clear()
            data.takeLast(n).forEach { selected += it.idRef }
            notifyDataSetChanged()
            onSelectionChanged?.invoke()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dream_pick_row, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = data[pos]
            h.date.text = item.dateKey
            h.dream.text = item.dream.ifBlank { "(꿈 텍스트 없음)" }
            h.interp.text = item.interp

            val isSel = selected.contains(item.idRef)
            bindCardState(h, isSel)

            h.root.setOnClickListener {
                toggleSelect(item.idRef, pos)
                bindCardState(h, selected.contains(item.idRef))
            }
        }

        private fun bindCardState(h: VH, isSel: Boolean) {
            val d = h.card.resources.displayMetrics.density
            h.card.strokeWidth = if (isSel) (2 * d).toInt() else 0
            h.card.setCardBackgroundColor(if (isSel) Color.parseColor("#F1F6FF") else Color.WHITE)
            h.check.isVisible = isSel
            val ctx = h.itemView.context
            h.check.contentDescription = ctx.getString(if (isSel) R.string.checked else R.string.unchecked)
        }

        private fun toggleSelect(idRef: String, pos: Int) {
            val cur = selected.contains(idRef)
            val next = !cur
            if (next && !cur) {
                if (selected.size >= maxPick) {
                    onMaxExceeded?.invoke()
                    return
                }
                selected += idRef
            } else if (!next && cur) {
                selected -= idRef
            } else return
            notifyItemChanged(pos)
            onSelectionChanged?.invoke()
        }
    }
}
