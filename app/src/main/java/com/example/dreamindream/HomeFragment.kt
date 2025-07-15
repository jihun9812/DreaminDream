package com.example.dreamindream

import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val aiMessage = view.findViewById<TextView>(R.id.ai_message)
        val btnDream = view.findViewById<Button>(R.id.btn_dream)

        //  오늘의 GPT 메시지 불러오기
        aiMessage.text = "✨ 오늘의 해몽 메시지를 불러오는 중이에요..."
        DailyMessageManager.getMessage(requireContext()) { msg ->
            activity?.runOnUiThread {
                aiMessage.text = msg
            }
        }

        // 공통 클릭 애니메이션
        fun View.applyScaleClick(action: () -> Unit) {
            this.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
                action()
            }
        }

        // 해몽 버튼
        btnDream.applyScaleClick {
            navigateTo(DreamFragment())
        }

        // 캘린더 버튼
        view.findViewById<Button>(R.id.btn_calendar).applyScaleClick {
            navigateTo(CalendarFragment())
        }

        // 운세 버튼
        view.findViewById<Button>(R.id.btn_fortune).applyScaleClick {
            navigateTo(FortuneFragment())
        }

        // 설정 버튼
        view.findViewById<ImageButton>(R.id.btn_settings).applyScaleClick {
            navigateTo(SettingsFragment())
        }

        return view
    }

    private fun navigateTo(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}