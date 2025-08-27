// file: app/src/main/java/com/example/dreamindream/WeeklyHistoryBottomSheet.kt
package com.example.dreamindream

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
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

    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
    private var keys: MutableList<String> = mutableListOf()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext())
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(
                DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            )
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val userId = uid
        if (userId == null) {
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

            // ✅ 저번 주만 삭제 허용
            val deletableKey = WeekUtils.previousWeekKey(WeekUtils.weekKey(), 1)
            val baseKey = WeekUtils.weekKey() // 오늘 기준 (이번 주)

            rv.adapter = SimpleAdapter(
                keys = keys,
                currentKey = currentWeekKey,
                deletableKey = deletableKey,
                baseKey = baseKey,
                onItemClick = { pos ->
                    val key = keys.getOrNull(pos) ?: return@SimpleAdapter
                    if (key == currentWeekKey) {
                        dismissAllowingStateLoss()
                    } else {
                        onPick(key)
                        dismissAllowingStateLoss()
                    }
                },
                onItemDelete = { pos ->
                    val key = keys.getOrNull(pos) ?: return@SimpleAdapter
                    if (key != deletableKey) {
                        Toast.makeText(requireContext(), "지난 주 리포트만 삭제할 수 있어요.", Toast.LENGTH_SHORT).show()
                        return@SimpleAdapter
                    }
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("지난 주 리포트 삭제")
                        .setMessage("${relativeLabel(key, baseKey)} 리포트를 삭제할까요?")
                        .setPositiveButton("삭제") { d, _ ->
                            FirestoreManager.deleteWeeklyReport(userId, key) {
                                val p = pos.coerceIn(0, keys.lastIndex)
                                if (p in keys.indices) {
                                    keys.removeAt(p)
                                    rv.adapter?.notifyItemRemoved(p)
                                    if (keys.isEmpty()) {
                                        Toast.makeText(requireContext(), "리포트가 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            d.dismiss()
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            )
        }

        return rv
    }

    // 상대라벨 헬퍼
    private fun relativeLabel(key: String, baseKey: String): String =
        WeekUtils.relativeLabel(key, baseKey)

    private class InfoVH(val tv: MaterialTextView) : RecyclerView.ViewHolder(tv)

    private class VH(row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val title: MaterialTextView = row.getChildAt(0) as MaterialTextView
        val delete: ImageButton = row.getChildAt(1) as ImageButton
    }

    private class SimpleAdapter(
        private val keys: List<String>,
        private val currentKey: String?,
        private val deletableKey: String,
        private val baseKey: String,
        private val onItemClick: (Int) -> Unit,
        private val onItemDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(28, 24, 16, 24)
                minimumHeight = (48f * ctx.resources.displayMetrics.density).toInt()
            }
            val tv = MaterialTextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 16f
            }
            val btn = ImageButton(ctx, null, android.R.attr.borderlessButtonStyle).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                contentDescription = "삭제"
            }
            row.addView(tv)
            row.addView(btn)
            return VH(row)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val key = keys[position]

            val rel = WeekUtils.relativeLabel(key, baseKey) // "이번 주 / 저번 주 / N주 전"
            holder.title.text = if (key == currentKey) "• $rel (현재)" else rel
            holder.itemView.setOnClickListener { onItemClick(position) }

            // ✅ 휴지통 아이콘: 저번 주만 노출
            holder.delete.visibility = if (key == deletableKey) View.VISIBLE else View.GONE
            holder.delete.setOnClickListener { onItemDelete(position) }
        }

        override fun getItemCount(): Int = keys.size
    }
}
