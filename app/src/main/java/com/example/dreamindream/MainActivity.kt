package com.example.dreamindream

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.MobileAds

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this)
        setContentView(R.layout.activity_main)

        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_background)

        // 최초 설치(실행) 시에만 데이터 초기화
        val prefs = getSharedPreferences("first_run_check", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("isFirstRun", true)
        if (isFirstRun) {
            try {
                // 모든 SharedPreferences 초기화
                val prefsList = listOf(
                    "user_info", "user_prefs", "dream_history", "fortune", "fortune_result"
                )
                prefsList.forEach { name ->
                    getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
                }
                // 내부 파일 삭제
                filesDir?.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) { /* 무시 */ }
            // 플래그 저장: 다음부터는 초기화X
            prefs.edit().putBoolean("isFirstRun", false).apply()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fm = supportFragmentManager
                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                } else {
                    finish()
                }
            }
        })
    }
}
