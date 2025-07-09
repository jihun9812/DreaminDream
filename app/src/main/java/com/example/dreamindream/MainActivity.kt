package com.example.dreamindream

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

        // 상태바, 내비게이션바 색상 설정
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_background)

        // 첫 실행 시 HomeFragment 로드
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        // 백버튼 동작 커스터마이징
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
