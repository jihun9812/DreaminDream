// app/src/main/java/com/example/dreamindream/WeeklyHistoryDialogFragment.kt
package com.dreamindream.app

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

class WeeklyHistoryDialogFragment(
    @Suppress("unused") private val currentWeekKey: String? = null,
    @Suppress("unused") private val onPick: (String) -> Unit = {},
    @Suppress("unused") private val maxItems: Int = 0,
    private val onEmptyCta: (() -> Unit)? = null
) : DialogFragment() {

    companion object {
        private const val TAG = "WeeklyHistoryDialog"

        fun showOnce(
            fm: FragmentManager,
            currentWeekKey: String? = null,
            onPick: (String) -> Unit = {},
            maxItems: Int = 0,
            onEmptyCta: (() -> Unit)? = null
        ) {
            if (fm.findFragmentByTag(TAG) != null) return
            WeeklyHistoryDialogFragment(currentWeekKey, onPick, maxItems, onEmptyCta).show(fm, TAG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog_NoActionBar_MinWidth)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                window?.apply {
                    setBackgroundDrawable(GradientDrawable().apply {
                        cornerRadius = 22f
                        setColor(Color.parseColor("#FFF8DC"))
                        setStroke(1, Color.parseColor("#E0B34A"))
                    })
                    val w = (resources.displayMetrics.widthPixels * 0.92f).toInt()
                    setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
                    setDimAmount(0.45f)
                }
                setCanceledOnTouchOutside(false)
                setOnKeyListener { _, keyCode, event ->
                    keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        // 스크롤 컨테이너
        val scroll = ScrollView(ctx).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, 0, 0, 20.dp(ctx))
        }

        // Root
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scroll.addView(root)

        // Header
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(18.dp(ctx), 14.dp(ctx), 12.dp(ctx), 6.dp(ctx))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = MaterialTextView(ctx).apply {
            text = ctx.getString(R.string.whd_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#1F2234"))
        }
        val meta = MaterialTextView(ctx).apply {
            text = ctx.getString(R.string.whd_generation_rule)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#5B5F6A"))
            setPadding(0, 4.dp(ctx), 0, 0)
        }
        titleCol.addView(title); titleCol.addView(meta)

        val close = ImageButton(ctx, null, android.R.attr.borderlessButtonStyle).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#1F2234"))
            layoutParams = LinearLayout.LayoutParams(36.dp(ctx), 36.dp(ctx))
            background = null
            contentDescription = ctx.getString(R.string.whd_close)
            setOnClickListener { dismissAllowingStateLoss() }
        }
        header.addView(titleCol); header.addView(close)
        root.addView(header)

        // Hero
        val hero = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 16.dp(ctx); rightMargin = 16.dp(ctx); topMargin = 6.dp(ctx)
            }
            setPadding(16.dp(ctx), 16.dp(ctx), 16.dp(ctx), 16.dp(ctx))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#352D49"), Color.parseColor("#221B2E"))
            ).apply { cornerRadius = 18.dp(ctx).toFloat() }
        }
        val icon = TextView(ctx).apply {
            text = "✨"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(36.dp(ctx), 36.dp(ctx)).apply {
                rightMargin = 10.dp(ctx)
            }
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(Color.parseColor("#33FFFFFF"))
            }
        }
        val heroCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val heroTitle = MaterialTextView(ctx).apply {
            text = ctx.getString(R.string.whd_hero_title)
            setTextColor(Color.parseColor("#FFE9F2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        val heroSub = MaterialTextView(ctx).apply {
            text = ctx.getString(R.string.whd_hero_sub)
            setTextColor(Color.parseColor("#BFE1FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 4.dp(ctx), 0, 0)
        }
        heroCol.addView(heroTitle); heroCol.addView(heroSub)
        hero.addView(icon); hero.addView(heroCol)
        root.addView(hero)

        // Chips
        fun chip(text: String) = MaterialTextView(ctx).apply {
            setText(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#1F2234"))
            setPadding(12.dp(ctx), 6.dp(ctx), 12.dp(ctx), 6.dp(ctx))
            background = GradientDrawable().apply {
                cornerRadius = 999f
                setColor(Color.parseColor("#14FFFFFF"))
                setStroke(1.dp(ctx), Color.parseColor("#26000000"))
            }
        }
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 18.dp(ctx); rightMargin = 18.dp(ctx); topMargin = 8.dp(ctx) }
        }
        chipRow.addView(chip(ctx.getString(R.string.whd_chip_instant)))
        chipRow.addView(Space(ctx).apply { layoutParams = LinearLayout.LayoutParams(6.dp(ctx), 1) })
        chipRow.addView(chip(ctx.getString(R.string.whd_chip_week_range)))
        root.addView(chipRow)

        // Pro 안내
        val proInfo = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 18.dp(ctx); rightMargin = 18.dp(ctx); topMargin = 8.dp(ctx) }
            setPadding(12.dp(ctx), 12.dp(ctx), 12.dp(ctx), 12.dp(ctx))
            background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(Color.parseColor("#FFF4ECD6"))
                setStroke(1.dp(ctx), Color.parseColor("#E0B34A"))
            }
        }
        val proIcon = TextView(ctx).apply {
            text = "🎬"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#1F2234"))
            setPadding(2.dp(ctx), 0, 10.dp(ctx), 0)
        }
        val proText = MaterialTextView(ctx).apply {
            text = ctx.getString(R.string.whd_pro_text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setLineSpacing(0f, 1.1f)
            setTextColor(Color.parseColor("#3A3D4A"))
        }
        proInfo.addView(proIcon); proInfo.addView(proText)
        root.addView(proInfo)

        // CTA (Outlined style attr 제거 → 커스텀 스타일 수동 지정)
        val cta = MaterialButton(ctx).apply {
            text = ctx.getString(R.string.whd_cta_go_record)
            setTextColor(Color.parseColor("#1F2234"))
            strokeWidth = 1.dp(ctx)
            strokeColor = ColorStateList.valueOf(Color.parseColor("#332A355C"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF3C079"))
            cornerRadius = 16.dp(ctx)
            insetTop = 10; insetBottom = 10
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 16.dp(ctx); rightMargin = 16.dp(ctx)
                topMargin = 14.dp(ctx); bottomMargin = 20.dp(ctx)
            }
        }
        cta.setOnClickListener {
            dismissAllowingStateLoss()
            onEmptyCta?.invoke()
        }
        root.addView(cta)

        return scroll
    }
}

private fun Int.dp(ctx: Context) = (this * ctx.resources.displayMetrics.density).toInt()
