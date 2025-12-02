package com.dreamindream.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat


class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val sp = getSharedPreferences("app", Context.MODE_PRIVATE)

        // 1) 최신 키(태그) 우선
        var tag = sp.getString("app_lang_tag", null)

        // 2) 레거시 라벨 승격
        if (tag.isNullOrBlank()) {
            val legacyLabel = sp.getString("app_lang", null)
            if (!legacyLabel.isNullOrBlank()) {
                tag = when (legacyLabel) {
                    "English" -> "en"
                    "한국어" -> "ko"
                    "हिन्दी", "हिंदी" -> "hi"
                    "العربية" -> "ar"
                    "中文" -> "zh"
                    else -> null
                }
                if (tag != null) sp.edit().putString("app_lang_tag", tag).apply()
            }
        }

        // 3) 없으면 시스템 언어 추론
        if (tag.isNullOrBlank()) {
            val sys = resources.configuration.locales[0].language // e.g., ko, en, hi, ar, zh
            tag = when {
                sys.startsWith("ko") -> "ko"
                sys.startsWith("hi") -> "hi"
                sys.startsWith("ar") -> "ar"
                sys.startsWith("zh") -> "zh"
                else -> "en"
            }
            sp.edit().putString("app_lang_tag", tag).apply()
        }

        // 4) 적용
        val locales = if (tag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag!!)
        }
        AppCompatDelegate.setApplicationLocales(locales)


    }
}
