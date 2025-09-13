// app/src/main/java/com/example/dreamindream/WeeklyHistoryDialogFragment.kt
package com.example.dreamindream

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.view.KeyEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

/**
 * 빈 상태 전용 다이얼로그:
 * - 히어로 안내 + 가이드 칩 + "꿈 기록하러 가기" CTA
 * - 히스토리/삭제/리스트 없음 (바텀시트가 담당)
 * - X 버튼으로만 닫힘 (밖 터치/백버튼 금지)
 */
class WeeklyHistoryDialogFragment(
    @Suppress("unused") private val currentWeekKey: String? = null, // 호환성 유지용(미사용)
    @Suppress("unused") private val onPick: (String) -> Unit = {},  // 호환성 유지용(미사용)
    @Suppress("unused") private val maxItems: Int = 0,              // 호환성 유지용(미사용)
    private val onEmptyCta: (() -> Unit)? = null                    // 꿈 기록 화면으로 이동
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
        // 밖 터치/백버튼으로 닫히지 않게 잠금
        isCancelable = false
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog_NoActionBar_MinWidth)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                window?.apply {
                    setBackgroundDrawable(GradientDrawable().apply {
                        cornerRadius = 22f
                        setColor(Color.parseColor("#FFF8DC")) // 라이트 골드
                        setStroke(1, Color.parseColor("#E0B34A"))
                    })
                    val w = (resources.displayMetrics.widthPixels * 0.92f).toInt()
                    setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
                    setDimAmount(0.45f)
                }
                // 밖 터치 금지
                setCanceledOnTouchOutside(false)
                // 백버튼 소비
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

        // ── Root
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // ── Header (제목 + 닫기)
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(18.dp(ctx), 14.dp(ctx), 12.dp(ctx), 6.dp(ctx))
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = MaterialTextView(ctx).apply {
            text = "분석 안내"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#1F2234"))
        }
        val meta = MaterialTextView(ctx).apply {
            text = "이번 주 꿈을 2개 이상 기록하면 주간 요약이 생성돼요."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#5B5F6A"))
            setPadding(0, 4.dp(ctx), 0, 0)
        }
        titleCol.addView(title); titleCol.addView(meta)

        val close = ImageButton(ctx, null, android.R.attr.borderlessButtonStyle).apply {
            // 프로젝트 리소스 의존 제거: 기본 X 아이콘 사용
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#1F2234"))
            layoutParams = LinearLayout.LayoutParams(36.dp(ctx), 36.dp(ctx))
            background = null
            contentDescription = "닫기"
            setOnClickListener { dismissAllowingStateLoss() } // X로만 닫힘
        }
        header.addView(titleCol); header.addView(close)
        root.addView(header)

        // ── Hero 카드 (보라 그라데이션)
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
            gravity = Gravity.CENTER
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
        heroCol.addView(heroTitle); heroCol.addView(heroSub)
        hero.addView(icon); hero.addView(heroCol)
        root.addView(hero)

        // ── 가이드 칩 3개
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
        chipRow.addView(chip("이번 주 2개 이상"))
        chipRow.addView(Space(ctx).apply { layoutParams = LinearLayout.LayoutParams(6.dp(ctx), 1) })
        chipRow.addView(chip("키워드 적기"))
        chipRow.addView(Space(ctx).apply { layoutParams = LinearLayout.LayoutParams(6.dp(ctx), 1) })
        chipRow.addView(chip("감정 선택"))
        root.addView(chipRow)

        // ── CTA: 꿈 기록하러 가기
        val cta = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "꿈 기록하러 가기"
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
                topMargin = 14.dp(ctx); bottomMargin = 14.dp(ctx)
            }
        }
        cta.setOnClickListener {
            // X로만 닫히게 하고 싶다면 여기서 닫지 않고 이동만 하도록 바꿀 수도 있음.
            dismissAllowingStateLoss()
            onEmptyCta?.invoke()
        }
        root.addView(cta)

        return root
    }
}

// dp 확장함수
private fun Int.dp(ctx: Context) = (this * ctx.resources.displayMetrics.density).toInt()
