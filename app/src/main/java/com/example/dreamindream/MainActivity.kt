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
import com.google.firebase.messaging.FirebaseMessaging
import com.example.dreamindream.ads.AdManager   // ← 추가

class MainActivity : AppCompatActivity() {

    private var lastBackPressedAt = 0L
    private val backIntervalMs = 1800L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(
                        listOf(
                            AdRequest.DEVICE_ID_EMULATOR,
                            "38F4242F488E9C927543337A4DCCD32C"
                        )
                    ).build()
            )
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish(); return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            } else subscribeToDailyDream()
        } else subscribeToDailyDream()

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .update("fcmToken", token)
                        .addOnSuccessListener { Log.d("FCM", "토큰 저장 완료: $token") }
                        .addOnFailureListener { Log.e("FCM", "토큰 저장 실패", it) }
                }
            } else {
                Log.e("FCM", "토큰 획득 실패", task.exception)
            }
        }

        // 광고 SDK 초기화
        MobileAds.initialize(this)
        AdManager.initialize(this)   // ← 추가: 보상형 광고 게이트 워밍업

        setContentView(R.layout.activity_main)

        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_background)

        val prefs = getSharedPreferences("first_run_check", Context.MODE_PRIVATE)
        if (prefs.getBoolean("isFirstRun", true)) {
            try {
                listOf("user_info", "user_prefs", "dream_history", "fortune", "fortune_result")
                    .forEach { getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply() }
                filesDir?.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) { Log.e("Init", "초기화 실패", e) }
            prefs.edit().putBoolean("isFirstRun", false).apply()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .disallowAddToBackStack()
                .commit()
        }

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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            subscribeToDailyDream()
        }
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
