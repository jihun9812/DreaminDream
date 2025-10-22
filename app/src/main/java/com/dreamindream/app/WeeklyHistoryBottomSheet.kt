package com.dreamindream.app

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*
import android.view.ViewGroup

class WeeklyHistoryBottomSheet(
    private val currentWeekKey: String?,
    private val onPick: (String) -> Unit,
    private val maxItems: Int = 26,
    private val onEmptyCta: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "WeeklyHistoryBottomSheet"
        fun showOnce(
            fm: FragmentManager,
            currentWeekKey: String?,
            onPick: (String) -> Unit,
            maxItems: Int = 26,
            onEmptyCta: (() -> Unit)? = null
        ) {
            (fm.findFragmentByTag(TAG) as? WeeklyHistoryBottomSheet)?.let { return }
            WeeklyHistoryBottomSheet(currentWeekKey, onPick, maxItems, onEmptyCta).show(fm, TAG)
        }
    }

    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid
    private var keys: MutableList<String> = mutableListOf()

    // sheet 뷰 참조를 보관해서 필요할 때 높이를 조절
    private var sheetView: View? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dlg = BottomSheetDialog(requireContext())
        dlg.setOnShowListener {
            val id = resources.getIdentifier("design_bottom_sheet","id","com.google.android.material")
            dlg.findViewById<View>(id)?.let { sheet ->
                sheetView = sheet
                // 배경 투명 유지
                sheet.setBackgroundColor(Color.TRANSPARENT)

                // behavior 기본 설정
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.isDraggable = true

                // 기본 peekHeight(보이는 높이)는 화면의 35% 정도로 설정 (적당히 작게 시작)
                val peek = (resources.displayMetrics.heightPixels * 0.35).toInt()
                try {
                    behavior.peekHeight = peek
                } catch (_: Exception) { /* 안전하게 무시 */ }

                // 기본적으로 wrap_content로 두어서 항목이 적으면 작게 보이도록 함
                val lp = sheet.layoutParams
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                sheet.layoutParams = lp
            }
        }
        return dlg
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.bottom_sheet_weekly_history, container, false)

        val tvTitle = v.findViewById<TextView>(R.id.txtTitle)
        val tvMeta  = v.findViewById<TextView>(R.id.txtMeta)
        val rv      = v.findViewById<RecyclerView>(R.id.rvWeeks)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.addItemDecoration(MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL).apply {
            dividerColor = Color.parseColor("#33000000")
            dividerInsetStart = (20 * resources.displayMetrics.density).toInt()
            dividerInsetEnd   = (20 * resources.displayMetrics.density).toInt()
            dividerThickness  = (1  * resources.displayMetrics.density).toInt()
        })

        // 로그인 필요 상태 처리
        val userId = uid ?: run {
            tvMeta.setText(R.string.bs_meta_login_required)
            rv.adapter = object : RecyclerView.Adapter<InfoVH>() {
                override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
                    InfoVH(layoutInflater.inflate(android.R.layout.simple_list_item_1, p, false) as TextView)
                override fun onBindViewHolder(h: InfoVH, i: Int) {
                    (h.itemView as TextView).apply {
                        text = getString(R.string.bs_login_required_msg)
                        textSize = 16f; setTextColor(Color.parseColor("#1F2234"))
                        setPadding(24, 20, 24, 24)
                    }
                }
                override fun getItemCount() = 1
            }
            return v
        }

        FirestoreManager.listWeeklyReportKeys(userId, limit = maxItems) { list ->
            keys = list.toMutableList()
            if (keys.isEmpty()) {
                dismissAllowingStateLoss()
                onEmptyCta?.invoke()
                return@listWeeklyReportKeys
            }

            // 메타(총 주 수) 업데이트
            tvMeta.text = getString(R.string.bs_meta_total_weeks_format, keys.size)

            // 어댑터 세팅
            rv.adapter = SimpleAdapter(
                keys,
                currentWeekKey,
                WeekUtils.weekKey(),
                onItemClick = { idx -> onPick(keys[idx]); dismissAllowingStateLoss() },
                onItemDelete = { idx ->
                    val key = keys[idx]
                    if (idx != 0) {
                        Toast.makeText(requireContext(), getString(R.string.bs_toast_delete_only_latest), Toast.LENGTH_SHORT).show()
                        return@SimpleAdapter
                    }
                    val rel = WeekUtils.relativeLabel(key, WeekUtils.weekKey())
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.bs_dialog_delete_title)
                        .setMessage(getString(R.string.bs_dialog_delete_msg_format, rel))
                        .setPositiveButton(R.string.common_delete) { _, _ ->
                            FirestoreManager.deleteWeeklyReport(userId, key) {
                                if (idx in keys.indices) {
                                    keys.removeAt(idx)
                                    rv.adapter?.notifyItemRemoved(idx)
                                    tvMeta.text = getString(R.string.bs_meta_total_weeks_format, keys.size)
                                    if (keys.isEmpty()) { dismissAllowingStateLoss(); onEmptyCta?.invoke() }
                                }
                            }
                        }
                        .setNegativeButton(R.string.common_cancel, null)
                        .show()
                }
            )

            // --- 핵심: 항목 수에 따라 바텀시트 최대 높이 적용 ---
            // 항목이 3개 이상이면 바텀시트 높이를 화면의 60%로 제한하여 RecyclerView가 내부 스크롤되도록 함.
            v.post {
                sheetView?.let { sheet ->
                    val lp = sheet.layoutParams
                    if (keys.size >= 3) {
                        val maxHeight = (resources.displayMetrics.heightPixels * 0.60).toInt()
                        lp.height = maxHeight
                    } else {
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    sheet.layoutParams = lp
                }
            }
        }
        return v
    }

    private class InfoVH(view: View) : RecyclerView.ViewHolder(view)

    private class VH(view: View) : RecyclerView.ViewHolder(view) {
        val accent: View       = view.findViewById(R.id.accent)
        val title: TextView    = view.findViewById(R.id.title)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
        val chipWrap: ViewGroup= view.findViewById(R.id.chipwrap)
        val chevron: TextView  = view.findViewById(R.id.chevron)
        val delete: ImageButton= view.findViewById(R.id.btnDelete)
    }

    private class SimpleAdapter(
        private val keys: List<String>,
        private val currentKey: String?,
        private val baseKey: String,
        private val onItemClick: (Int) -> Unit,
        private val onItemDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_week_history, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val ctx = h.itemView.context
            val key = keys[pos]
            val rel = WeekUtils.relativeLabel(key, baseKey)
            val isCurrent = (key == currentKey)

            h.title.text = rel
            h.subtitle.text = "$key · ${formatRangeFromWeekKey(key)}"
            h.accent.setBackgroundColor(if (isCurrent) Color.parseColor("#4D352D49") else Color.parseColor("#352D49"))

            h.chipWrap.removeAllViews()
            if (isCurrent) {
                val chip = LayoutInflater.from(ctx).inflate(R.layout.view_soft_chip, h.chipWrap, false) as TextView
                chip.text = ctx.getString(R.string.chip_current)
                h.chipWrap.addView(chip)
            }

            h.delete.visibility = if (pos == 0) View.VISIBLE else View.GONE
            h.delete.setImageDrawable(AppCompatResources.getDrawable(ctx, R.drawable.trash_can))

            h.itemView.setOnClickListener { onItemClick(pos) }
            h.delete.setOnClickListener { onItemDelete(pos) }
        }

        override fun getItemCount() = keys.size

        private fun formatRangeFromWeekKey(key: String): String = try {
            val (yStr, wStr) = key.split("-W")
            val year = yStr.toInt(); val week = wStr.toInt()
            val wf = WeekFields.ISO
            val base = LocalDate.of(year, 1, 4)
            val mon  = base.with(wf.weekOfWeekBasedYear(), week.toLong()).with(wf.dayOfWeek(), 1)
            val sun  = mon.plusDays(6)
            val fmt  = DateTimeFormatter.ofPattern("MM.dd", Locale.getDefault())
            "${fmt.format(mon)}–${fmt.format(sun)}"
        } catch (_: Exception) { key }
    }
}
