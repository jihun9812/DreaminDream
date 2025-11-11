package com.dreamindream.app

import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleKit {

    /** 라벨 → BCP47 태그 매핑(방어적으로 다국어 표기 허용) */
    fun toLangTag(label: String): String = when (label.trim()) {
        "한국어", "Korean" -> "ko"
        "English" -> "en"
        "हिन्दी", "हिंदी", "Hindi" -> "hi"
        "العربية", "Arabic" -> "ar"
        "中文", "简体中文", "繁體中文", "Chinese" -> "zh"
        else -> "en"
    }

    /** 언어 태그 직접 적용 */
    fun apply(langTag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (langTag == "system") LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(langTag)
        )
    }

    /** 페이드아웃 후 적용(옵션) */
    fun applyWithMotion(surface: View, label: String, onEnd: (() -> Unit)? = null) {
        val tag = toLangTag(label)
        surface.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                apply(tag)
                surface.post {
                    surface.alpha = 1f
                    (surface.context as? androidx.fragment.app.FragmentActivity)?.recreate()
                    onEnd?.invoke()
                }
            }
            .start()
    }
}
