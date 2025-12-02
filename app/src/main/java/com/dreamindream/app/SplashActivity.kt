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

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val isLight =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_NO

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLight
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            // 🔥 여기서 로그인 여부 확인을 하지 않는다.
            // 이유: MainActivity가 이미 로그인 여부를 판단하고 LoginScreen/AppNavGraph로 분기하기 때문

            val intent = Intent(this, MainActivity::class.java)

            val options = ActivityOptions.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()

        }, 1800)
    }
}
