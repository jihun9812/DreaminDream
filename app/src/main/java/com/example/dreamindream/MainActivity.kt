// file: app/src/main/java/com/example/dreamindream/MainActivity.kt
package com.example.dreamindream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.example.dreamindream.ads.AdManager

class MainActivity : AppCompatActivity() {

    private var lastBackPressedAt = 0L
    private val backIntervalMs = 1800L
    private val REQ_NOTI = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 로그인 체크 ---
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish(); return
        }

        // --- AdMob 초기화 + 테스트 디바이스 등록(디버그만) ---
        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(
                        listOf(
                            AdRequest.DEVICE_ID_EMULATOR,
                            "38F4242F488E9C927543337A4DCCD32C" // 네가 쓰던 ID 유지
                        )
                    ).build()
            )
        }
        MobileAds.initialize(this)
        AdManager.initialize(this) // 보상형 광고 워밍업

        // --- 알림 권한 + FCM 토큰 저장 보장 ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTI
                )
            } else {
                onNotificationsReady()
            }
        } else {
            onNotificationsReady()
        }

        setContentView(R.layout.activity_main)

        // 시스템 바 색상
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_background)

        // --- 최초 실행 초기화(네 코드 유지) ---
        val prefsFirst = getSharedPreferences("first_run_check", Context.MODE_PRIVATE)
        if (prefsFirst.getBoolean("isFirstRun", true)) {
            try {
                listOf("user_info", "user_prefs", "dream_history", "fortune", "fortune_result")
                    .forEach { getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply() }
                filesDir?.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e("Init", "초기화 실패", e)
            }
            prefsFirst.edit().putBoolean("isFirstRun", false).apply()
        }

        // 첫 진입 프래그먼트
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .disallowAddToBackStack()
                .commit()
        }

        // 뒤로가기 핸들러
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fm = supportFragmentManager
                val current = fm.findFragmentById(R.id.fragment_container)
                if (current is HomeFragment) {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressedAt <= backIntervalMs) {
                        finish()
                    } else {
                        lastBackPressedAt = now
                        Toast.makeText(this@MainActivity, "한 번 더 누르면 종료됩니다", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    fm.beginTransaction()
                        .setCustomAnimations(
                            R.anim.slide_in_left, R.anim.slide_out_right,
                            R.anim.slide_in_right, R.anim.slide_out_left
                        )
                        .replace(R.id.fragment_container, HomeFragment())
                        .commit()
                }
            }
        })
    }

    // 권한 결과
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTI && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            onNotificationsReady()
        }
    }

    // ====== 알림 사용 준비 완료 시 공통 처리 ======
    private fun onNotificationsReady() {
        // 1) 최신 토큰을 안전하게 저장(문서 없으면 생성)
        MyFirebaseMessagingService.ensureTokenSaved()

        // (원한다면 중복 안전차원으로 한 번 더 저장) — set(merge) 사용
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result ?: return@addOnCompleteListener
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnCompleteListener
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .set(
                        mapOf(
                            "fcmToken" to token,
                            "last_token_at" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
                    .addOnSuccessListener { Log.d("FCM", "토큰 저장(merge) 완료: $token") }
                    .addOnFailureListener { Log.e("FCM", "토큰 저장 실패", it) }
            } else {
                Log.e("FCM", "토큰 획득 실패", task.exception)
            }
        }

        // 2) 토픽 구독(중복 방지 플래그 포함)
        subscribeToDailyDream()
    }

    private fun subscribeToDailyDream() {
        val prefs = getSharedPreferences("fcm_topic_check", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("dailyDreamSubscribed", false)) {
            FirebaseMessaging.getInstance().subscribeToTopic("dailyDream")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        prefs.edit().putBoolean("dailyDreamSubscribed", true).apply()
                        Log.d("FCM", "✅ dailyDream 토픽 구독 완료")
                    } else {
                        Log.e("FCM", "❌ 토픽 구독 실패", task.exception)
                    }
                }
        }
    }
}
