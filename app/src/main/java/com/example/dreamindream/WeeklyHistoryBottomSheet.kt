package com.example.dreamindream

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

class WeeklyHistoryBottomSheet(
    private val currentWeekKey: String?,
    private val onPick: (String) -> Unit,
    private val maxItems: Int = 26,
    private val onEmptyCta: (() -> Unit)? = null,          // 비어있을 때 CTA(옵션)
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
            val resId = requireContext().resources.getIdentifier(
                "design_bottom_sheet", "id", "com.google.android.material"
            )
            val sheet = dlg.findViewById<View>(resId)
            sheet?.setBackgroundColor(Color.TRANSPARENT)
            sheet?.let { BottomSheetBehavior.from(it).isDraggable = true }
        }
        return dlg
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()

        // Root (브랜드 골드 바탕)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, 10.dp(ctx))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FDCA60"))
                cornerRadius = 22.dp(ctx).toFloat()
                setStroke(1.dp(ctx), Color.parseColor("#E0B34A"))
            }
        }

        // Handle
        val handle = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(43.dp(ctx), 4.dp(ctx)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 8.dp(ctx); bottomMargin = 8.dp(ctx)
            }
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(Color.parseColor("#802A2A2A"))
            }
        }

        // Header
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(20.dp(ctx), 6.dp(ctx), 8.dp(ctx), 6.dp(ctx))
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { dismissAllowingStateLoss() }
        }
        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = MaterialTextView(ctx).apply {
            text = "분석 기록"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#1F2234"))
        }
        val meta = MaterialTextView(ctx).apply {
            text = ""
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#5B5F6A"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 2.dp(ctx), 0, 0)
        }
        titleCol.addView(title); titleCol.addView(meta)
        header.addView(titleCol)

        // List
        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rv.addItemDecoration(MaterialDividerItemDecoration(ctx, LinearLayoutManager.VERTICAL).apply {
            dividerColor = Color.parseColor("#33000000")
            dividerInsetStart = 20.dp(ctx); dividerInsetEnd = 20.dp(ctx)
            dividerThickness = 1.dp(ctx)
        })

        root.addView(handle)
        root.addView(header)
        root.addView(rv)

        // 로그인 가드
        val userId: String = uid ?: run {
            meta.text = "로그인 필요"
            rv.adapter = object : RecyclerView.Adapter<InfoVH>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InfoVH {
                    val tv = MaterialTextView(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        setPadding(24.dp(context), 20.dp(context), 24.dp(context), 24.dp(context))
                        text = "로그인이 필요합니다."
                        setTextColor(Color.parseColor("#1F2234"))
                        textSize = 16f
                    }
                    return InfoVH(tv)
                }
                override fun onBindViewHolder(holder: InfoVH, position: Int) {}
                override fun getItemCount(): Int = 1
            }
            return root
        }

        // 데이터 로드
        FirestoreManager.listWeeklyReportKeys(userId, limit = maxItems) { list ->
            keys = list.toMutableList()

            // ===== 빈 상태 전용 UI =====
            if (keys.isEmpty()) {
                meta.text = "첫 리포트를 만들려면 이번 주 꿈을 2개 이상 기록하세요."

                // 리스트 숨기고, 비어있는 전용 섹션 추가
                rv.visibility = View.GONE

                // Hero 카드 (보라 그라데이션 · 글래스 느낌)
                val hero = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        leftMargin = 16.dp(ctx); rightMargin = 16.dp(ctx); topMargin = 6.dp(ctx); bottomMargin = 10.dp(ctx)
                    }
                    setPadding(16.dp(ctx), 14.dp(ctx), 16.dp(ctx), 14.dp(ctx))
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.parseColor("#352D49"), Color.parseColor("#221B2E"))
                    ).apply {
                        cornerRadius = 18.dp(ctx).toFloat()
                    }
                }

                val icon = TextView(ctx).apply {
                    text = "✨"
                    textSize = 22f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(36.dp(ctx), 36.dp(ctx)).apply {
                        rightMargin = 10.dp(ctx)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = 999f
                        setColor(Color.parseColor("#33FFFFFF"))
                    }
                }
                val heroTextCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val heroTitle = MaterialTextView(ctx).apply {
                    text = "아직 생성된 리포트가 없어요"
                    setTextColor(Color.parseColor("#FFE9F2"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }
                val heroSub = MaterialTextView(ctx).apply {
                    text = "이번 주 꿈을 최소 2개 기록하면 자동으로 주간 요약을 만들어 드려요."
                    setTextColor(Color.parseColor("#BFE1FF"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, 4.dp(ctx), 0, 0)
                }
                heroTextCol.addView(heroTitle); heroTextCol.addView(heroSub)
                hero.addView(icon); hero.addView(heroTextCol)

                // 가이드 칩 3개
                val chipRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        leftMargin = 18.dp(ctx); rightMargin = 18.dp(ctx); topMargin = 6.dp(ctx)
                    }
                }
                chipRow.addView(makeChip(ctx, "이번 주 2개 이상", "#14FFFFFF", "#1F2234"))
                chipRow.addView(Space(ctx).apply { layoutParams = LinearLayout.LayoutParams(6.dp(ctx), 1) })
                chipRow.addView(makeChip(ctx, "키워드 적기", "#14FFFFFF", "#1F2234"))
                chipRow.addView(Space(ctx).apply { layoutParams = LinearLayout.LayoutParams(6.dp(ctx), 1) })
                chipRow.addView(makeChip(ctx, "감정 선택", "#14FFFFFF", "#1F2234"))

                // CTA
                val cta = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "꿈 기록하러 가기"
                    setTextColor(Color.parseColor("#1F2234"))
                    strokeWidth = 1.dp(ctx)
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#332A355C"))
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF8DC"))
                    cornerRadius = 16.dp(ctx)
                    insetTop = 10; insetBottom = 10
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        leftMargin = 16.dp(ctx); rightMargin = 16.dp(ctx); topMargin = 14.dp(ctx); bottomMargin = 14.dp(ctx)
                    }
                }
                cta.setOnClickListener {
                    dismissAllowingStateLoss()
                    onEmptyCta?.invoke()
                }

                // 붙이기
                root.addView(hero)
                root.addView(chipRow)
                root.addView(cta)

                return@listWeeklyReportKeys
            }

            // ===== 기존 리스트 화면 =====
            meta.text = "총 ${keys.size}주 · 최신순"

            rv.adapter = SimpleAdapter(
                keys = keys,
                currentKey = currentWeekKey,
                baseKey = WeekUtils.weekKey(),
                onItemClick = { idx ->
                    onPick(keys[idx]); dismissAllowingStateLoss()
                },
                onItemDelete = { idx ->
                    val key = keys[idx]
                    if (idx != 0) {
                        Toast.makeText(requireContext(), "가장 최근 주만 삭제할 수 있어요.", Toast.LENGTH_SHORT).show()
                        return@SimpleAdapter
                    }
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("리포트 삭제")
                        .setMessage("‘${WeekUtils.relativeLabel(key, WeekUtils.weekKey())}’ 리포트를 삭제할까요?")
                        .setPositiveButton("삭제") { _, _ ->
                            FirestoreManager.deleteWeeklyReport(userId, key) {
                                if (idx in keys.indices) {
                                    keys.removeAt(idx)
                                    rv.adapter?.notifyItemRemoved(idx)
                                    meta.text = "총 ${keys.size}주 · 최신순"
                                }
                            }
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            )
        }

        return root
    }

    // --- VHs ---
    private class InfoVH(val tv: MaterialTextView) : RecyclerView.ViewHolder(tv)

    private class VH(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val accent: View = row.findViewWithTag("accent")
        val title: MaterialTextView = row.findViewWithTag("title")
        val subtitle: MaterialTextView = row.findViewWithTag("subtitle")
        val chipWrap: LinearLayout = row.findViewWithTag("chipwrap")
        val chevron: TextView = row.findViewWithTag("chevron")
        val delete: ImageButton = row.findViewWithTag("delete")
    }

    // --- Adapter ---
    private class SimpleAdapter(
        private val keys: List<String>,
        private val currentKey: String?,
        private val baseKey: String,
        private val onItemClick: (Int) -> Unit,
        private val onItemDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                minimumHeight = 56.dp(ctx)
                val out = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
            }

            val accent = View(ctx).apply {
                tag = "accent"
                layoutParams = LinearLayout.LayoutParams(3.dp(ctx), ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.parseColor("#352D49"))
            }
            row.addView(accent)

            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = 16.dp(ctx); topMargin = 12.dp(ctx); bottomMargin = 12.dp(ctx)
                }
            }
            val title = MaterialTextView(ctx).apply {
                tag = "title"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.parseColor("#151823"))
            }
            val subtitle = MaterialTextView(ctx).apply {
                tag = "subtitle"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#525866"))
                typeface = Typeface.MONOSPACE
                setPadding(0, 4.dp(ctx), 0, 0)
            }
            textCol.addView(title); textCol.addView(subtitle)
            row.addView(textCol)

            val right = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    rightMargin = 8.dp(ctx)
                }
            }
            val chipWrap = LinearLayout(ctx).apply { tag = "chipwrap"; orientation = LinearLayout.HORIZONTAL }
            val chevron = MaterialTextView(ctx).apply {
                tag = "chevron"
                text = "›"
                setTextColor(Color.parseColor("#1F2234"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding(8.dp(ctx), 0, 8.dp(ctx), 0)
            }

            val delete = ImageButton(ctx, null, android.R.attr.borderlessButtonStyle).apply {
                tag = "delete"
                setImageDrawable(AppCompatResources.getDrawable(ctx, R.drawable.trash_can))
                imageTintList = ColorStateList.valueOf(Color.parseColor("#1F2234"))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(36.dp(ctx), 36.dp(ctx))
                setPadding(6.dp(ctx), 6.dp(ctx), 6.dp(ctx), 6.dp(ctx))
                contentDescription = "삭제"
                setBackgroundColor(Color.TRANSPARENT)
            }

            right.addView(chipWrap)
            right.addView(chevron)
            right.addView(delete)
            row.addView(right)

            return VH(row)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ctx = holder.itemView.context
            val key = keys[position]
            val rel = WeekUtils.relativeLabel(key, baseKey)
            val isCurrent = (key == currentKey)

            holder.title.text = rel
            holder.subtitle.text = "$key · ${formatRangeFromWeekKey(key)}"
            holder.accent.setBackgroundColor(if (isCurrent) Color.parseColor("#4D352D49") else Color.parseColor("#352D49"))

            holder.chipWrap.removeAllViews()
            if (isCurrent) holder.chipWrap.addView(makeChip(ctx, "현재", "#332A355C", "#1F2234"))

            holder.delete.visibility = if (position == 0) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onItemClick(position) }
            holder.delete.setOnClickListener { onItemDelete(position) }
        }

        override fun getItemCount(): Int = keys.size

        private fun makeChip(ctx: Context, text: String, bgHex: String, fgHex: String): View =
            MaterialTextView(ctx).apply {
                setText(text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor(fgHex))
                setPadding(10.dp(ctx), 4.dp(ctx), 10.dp(ctx), 4.dp(ctx))
                background = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(Color.parseColor(bgHex))
                    setStroke(1.dp(ctx), Color.parseColor("#26000000"))
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = 6.dp(ctx)
                }
            }

        private fun formatRangeFromWeekKey(key: String): String = try {
            val (yStr, wStr) = key.split("-W")
            val year = yStr.toInt(); val week = wStr.toInt()
            val wf = WeekFields.ISO
            val base = LocalDate.of(year, 1, 4)
            val monday = base.with(wf.weekOfWeekBasedYear(), week.toLong()).with(wf.dayOfWeek(), 1)
            val sunday = monday.plusDays(6)
            val fmt = DateTimeFormatter.ofPattern("MM.dd", Locale.getDefault())
            "${fmt.format(monday)}–${fmt.format(sunday)}"
        } catch (_: Exception) { key }
    }

    // 공용 칩(빈 상태용)
    private fun makeChip(ctx: Context, text: String, bgHex: String, fgHex: String): View =
        MaterialTextView(ctx).apply {
            setText(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor(fgHex))
            setPadding(12.dp(ctx), 6.dp(ctx), 12.dp(ctx), 6.dp(ctx))
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(Color.parseColor(bgHex))
                setStroke(1.dp(ctx), Color.parseColor("#26000000"))
            }
        }
}

private fun Int.dp(ctx: Context) = (this * ctx.resources.displayMetrics.density).toInt()
