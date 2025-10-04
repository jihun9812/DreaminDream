package com.example.dreamindream

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.dreamindream.billing.SubscriptionManager   // ✅ 추가

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val sp = getSharedPreferences("app", Context.MODE_PRIVATE)

        // 1) 최신 키(태그) 우선
        var tag = sp.getString("app_lang_tag", null)

        // 2) 레거시 라벨("English"/"한국어")만 있다면 태그로 승격
        if (tag.isNullOrBlank()) {
            val legacyLabel = sp.getString("app_lang", null)
            if (!legacyLabel.isNullOrBlank()) {
                tag = if (legacyLabel == "English") "en" else "ko"
                sp.edit().putString("app_lang_tag", tag).apply()
            }
        }

        // 3) 그래도 없으면 시스템 언어 기준 기본값(ko 우선)
        if (tag.isNullOrBlank()) {
            val sys = resources.configuration.locales[0].language
            tag = if (sys.startsWith("ko")) "ko" else "en"
            sp.edit().putString("app_lang_tag", tag).apply()
        }

        // 4) 적용
        val locales = if (tag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag!!)
        }
        AppCompatDelegate.setApplicationLocales(locales)

        // ✅ 프리미엄 상태 초기화 (한 줄)
        SubscriptionManager.init(this)
    }
}
