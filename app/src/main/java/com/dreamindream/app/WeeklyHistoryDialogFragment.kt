package com.dreamindream.app

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton

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
        // 기존과 동일한 다이얼로그 테마 유지
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog_NoActionBar_MinWidth)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                window?.apply {
                    // 배경/코너/스트로크를 XML 드로어블로 교체
                    setBackgroundDrawableResource(R.drawable.bg_avatar_circle)

                    // 가로 92%로 설정
                    val w = (resources.displayMetrics.widthPixels * 0.92f).toInt()
                    setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)

                    // 딤 양 유지
                    setDimAmount(0.45f)

                    // 상태바/내비 컬러는 투명 유지 (선택)
                    statusBarColor = Color.TRANSPARENT
                    navigationBarColor = Color.TRANSPARENT
                }
                setCanceledOnTouchOutside(false)
                // 백버튼 막기
                setOnKeyListener { _, keyCode, event ->
                    keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // XML 레이아웃 inflate
        val view = inflater.inflate(R.layout.dialog_weekly_history, container, false)

        // 닫기 버튼
        view.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
            dismissAllowingStateLoss()
        }

        // CTA 버튼
        view.findViewById<MaterialButton>(R.id.btn_cta)?.setOnClickListener {
            dismissAllowingStateLoss()
            onEmptyCta?.invoke()
        }

        return view
    }
}
