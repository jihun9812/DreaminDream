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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dlg = BottomSheetDialog(requireContext())
        dlg.setOnShowListener {
            val id = resources.getIdentifier("design_bottom_sheet","id","com.google.android.material")
            dlg.findViewById<View>(id)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                BottomSheetBehavior.from(sheet).isDraggable = true
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
            tvMeta.text = getString(R.string.bs_meta_total_weeks_format, keys.size)

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
