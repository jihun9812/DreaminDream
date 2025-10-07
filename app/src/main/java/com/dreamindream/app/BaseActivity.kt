package com.dreamindream.app

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val cfg = Configuration(newBase.resources.configuration)

        // 1) 글꼴 스케일 고정 (sp가 커지지 않도록)
        if (cfg.fontScale != 1f) cfg.fontScale = 1f

        // 2) 화면 요소 크기(디스플레이 크기) 고정: dp 스케일을 안정화
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val stable = DisplayMetrics.DENSITY_DEVICE_STABLE    // 기기 기본 DPI
            if (cfg.densityDpi != stable) cfg.densityDpi = stable
        }

        val wrapped = newBase.createConfigurationContext(cfg)
        super.attachBaseContext(wrapped)
    }
}
