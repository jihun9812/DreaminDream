// file: app/src/main/java/com/example/dreamindream/WeeklyHistoryBottomSheet.kt
package com.example.dreamindream

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth

class WeeklyHistoryBottomSheet(
    private val currentWeekKey: String?,
    private val onPick: (String) -> Unit,
    private val maxItems: Int = 26
) : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "WeeklyHistoryBottomSheet"
        fun showOnce(
            fm: FragmentManager,
            currentWeekKey: String?,
            onPick: (String) -> Unit,
            maxItems: Int = 26
        ) {
            val existing = fm.findFragmentByTag(TAG)
            if (existing is WeeklyHistoryBottomSheet && existing.isAdded) return

            val sheet = WeeklyHistoryBottomSheet(currentWeekKey, onPick, maxItems)
            if (fm.isStateSaved) {
                fm.beginTransaction().add(sheet, TAG).commitAllowingStateLoss()
            } else {
                sheet.show(fm, TAG)
            }
        }
    }

    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
    private var keys: MutableList<String> = mutableListOf()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext())

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val userId: String = uid ?: run {
            rv.adapter = object : RecyclerView.Adapter<InfoVH>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InfoVH {
                    val tv = MaterialTextView(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(28, 28, 28, 28)
                        textSize = 16f
                        text = "로그인이 필요합니다."
                    }
                    return InfoVH(tv)
                }
                override fun onBindViewHolder(holder: InfoVH, position: Int) {}
                override fun getItemCount(): Int = 1
            }
            return rv
        }

        FirestoreManager.listWeeklyReportKeys(userId, limit = maxItems) { list ->
            keys = list.toMutableList()

            rv.adapter = SimpleAdapter(
                keys = keys,
                currentKey = currentWeekKey,
                deletableKey = keys.firstOrNull(),          // 최신 것만 삭제
                baseKey = WeekUtils.weekKey(),
                onItemClick = { idx ->
                    val picked = keys[idx]
                    onPick(picked)
                    dismissAllowingStateLoss()
                },
                onItemDelete = { idx ->
                    val key = keys[idx]
                    if (key != keys.firstOrNull()) {
                        Toast.makeText(requireContext(), "가장 최근 주만 삭제할 수 있어요.", Toast.LENGTH_SHORT).show()
                        return@SimpleAdapter
                    }
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("리포트 삭제")
                        .setMessage("‘${WeekUtils.relativeLabel(key, WeekUtils.weekKey())}’ 리포트를 삭제할까요?\n(심화분석도 함께 삭제됩니다)")
                        .setPositiveButton("삭제") { _, _ ->
                            FirestoreManager.deleteWeeklyReport(userId, key) {
                                if (idx in keys.indices) {
                                    keys.removeAt(idx)
                                    rv.adapter?.notifyItemRemoved(idx)
                                }
                            }
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            )
        }

        return rv
    }

    private class InfoVH(val tv: MaterialTextView) : RecyclerView.ViewHolder(tv)

    private class VH(row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val title: MaterialTextView = row.getChildAt(0) as MaterialTextView
        val delete: ImageButton = row.getChildAt(1) as ImageButton
    }

    private class SimpleAdapter(
        private val keys: List<String>,
        private val currentKey: String?,
        private val deletableKey: String?,
        private val baseKey: String,
        private val onItemClick: (Int) -> Unit,
        private val onItemDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(28, 24, 12, 24)
                minimumHeight = (48f * ctx.resources.displayMetrics.density).toInt()
            }
            val tv = MaterialTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 16f
            }
            val btn = ImageButton(ctx, null, android.R.attr.borderlessButtonStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setImageResource(android.R.drawable.ic_menu_delete) // 내장 아이콘
                contentDescription = "삭제"
                setBackgroundColor(0x00000000)
            }
            row.addView(tv); row.addView(btn)
            return VH(row).also {
                it.title.text = ""
                it.delete.setImageDrawable(btn.drawable)
            }
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val key = keys[position]
            val rel = WeekUtils.relativeLabel(key, baseKey)
            holder.title.text = if (key == currentKey) "• $rel (현재)" else rel
            holder.itemView.setOnClickListener { onItemClick(position) }

            holder.delete.visibility = if (key == deletableKey) View.VISIBLE else View.GONE
            holder.delete.setOnClickListener { onItemDelete(position) }
        }

        override fun getItemCount(): Int = keys.size
    }
}
