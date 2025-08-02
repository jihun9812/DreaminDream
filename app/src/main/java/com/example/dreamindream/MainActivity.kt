package com.example.dreamindream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 로그인 안 되어 있으면 LoginActivity로 이동
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 🔔 Android 13 이상 알림 권한 요청 및 토픽 구독
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            } else {
                subscribeToDailyDream()
            }
        } else {
            subscribeToDailyDream()
        }

        // 🔔 FCM 토큰 Firestore에 저장
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .update("fcmToken", token)
                        .addOnSuccessListener {
                            Log.d("FCM", "토큰 저장 완료: $token")
                        }
                        .addOnFailureListener {
                            Log.e("FCM", "토큰 저장 실패", it)
                        }
                } else {
                    Log.d("FCM", "사용자 없음 - 토큰 저장 생략")
                }
            } else {
                Log.e("FCM", "토큰 획득 실패", task.exception)
            }
        }

        // ✅ 광고 초기화
        MobileAds.initialize(this)
        setContentView(R.layout.activity_main)

        // ✅ 상태바/내비게이션 색상 설정
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_background)

        // ✅ 최초 실행 시 데이터 초기화
        val prefs = getSharedPreferences("first_run_check", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("isFirstRun", true)
        if (isFirstRun) {
            try {
                val prefsList = listOf(
                    "user_info", "user_prefs", "dream_history", "fortune", "fortune_result"
                )
                prefsList.forEach { name ->
                    getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
                }
                filesDir?.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e("Init", "초기화 실패", e)
            }
            prefs.edit().putBoolean("isFirstRun", false).apply()
        }

        // ✅ 홈 프래그먼트 로딩
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        // ✅ 뒤로가기 처리
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

    // 🔔 알림 권한 허용 후 토픽 구독 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("알림권한", "사용자가 알림 권한을 허용함")
                subscribeToDailyDream()
            } else {
                Log.d("알림권한", "사용자가 알림 권한을 거부함")
            }
        }
    }

    private fun subscribeToDailyDream() {
        val prefs = getSharedPreferences("fcm_topic_check", Context.MODE_PRIVATE)
        val alreadySubscribed = prefs.getBoolean("dailyDreamSubscribed", false)

        if (!alreadySubscribed) {
            FirebaseMessaging.getInstance().subscribeToTopic("dailyDream")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FCM", "✅ dailyDream 토픽 구독 완료")
                        prefs.edit().putBoolean("dailyDreamSubscribed", true).apply()
                    } else {
                        Log.e("FCM", "❌ 토픽 구독 실패", task.exception)
                    }
                }
        }
    }
}
