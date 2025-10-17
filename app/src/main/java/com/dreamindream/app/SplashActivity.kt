package com.dreamindream.app

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.res.Configuration

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Edge-to-Edge + 투명 시스템바
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // ✅ 아이콘 명암 설정 (하단 흰 띠 방지)
        val isLight =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_NO
        WindowInsetsControllerCompat(window, window.decorView).apply {
            // 상태바: 라이트/다크에 맞춰 자동
            isAppearanceLightStatusBars = isLight
            // 네비게이션바: 항상 밝은 아이콘(=false) → 대비 스크림 방지
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val intent = if (user != null) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            val options = ActivityOptions.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()
        }, 1500)
    }
}
