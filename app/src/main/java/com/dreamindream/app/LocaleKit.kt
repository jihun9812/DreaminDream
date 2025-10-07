package com.dreamindream.app

import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleKit {

    /** "한국어" / "English" 라벨 → BCP47 언어 태그 */
    fun toLangTag(label: String): String = when (label) {
        "English" -> "en"
        else -> "ko"
    }

    /** 언어 태그 직접 적용 */
    fun apply(langTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langTag))
    }

    /**
     * 가벼운 페이드 아웃 모션 후 언어 적용 → 액티비티 재생성
     * @param surface 모션을 줄 뷰(예: settings의 패널 박스)
     * @param label   스피너 라벨(한국어/English)
     * */
    fun applyWithMotion(surface: View, label: String, onEnd: (() -> Unit)? = null) {
        val tag = toLangTag(label)
        surface.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                apply(tag)
                // recreate를 호출하면 새 locale로 UI가 다시 그려짐
                surface.post {
                    surface.alpha = 1f
                    (surface.context as? androidx.fragment.app.FragmentActivity)?.recreate()
                    onEnd?.invoke()
                }
            }
            .start()
    }
}
