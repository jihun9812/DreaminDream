package com.dreamindream.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dreamindream.app.ui.navigation.LoginNavGraph
import com.dreamindream.app.ui.navigation.AppNavGraph
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : BaseActivity() {

    private val REQ_NOTI = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 전역 Edge-to-Edge + 시스템바 완전 투명 + 흰색 아이콘 강제
        enableEdgeToEdge()
        // 앱 컨텐츠를 status/nav bar 아래까지 깔리게
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ✅ 여기서 진짜 TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // ✅ 상태바/내비게이션바 아이콘 "흰색"으로 고정 (어두운 배경용)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false      // false = 아이콘 흰색
            isAppearanceLightNavigationBars = false  // false = 아이콘 흰색
        }

        // 🔸 광고 초기화
        if (BuildConfig.DEBUG) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(
                        listOf(
                            AdRequest.DEVICE_ID_EMULATOR,
                            "38F4242F488E9C927543337A4DCCD32C"
                        )
                    )
                    .build()
            )
        }
        MobileAds.initialize(this)
        AdManager.initialize(this)

        // 🔸 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(perm),
                    REQ_NOTI
                )
            } else {
                onNotificationsReady()
            }
        } else {
            onNotificationsReady()
        }

        // 🔥 Compose 루트
        setContent {
            MaterialTheme {
                val auth = remember { FirebaseAuth.getInstance() }
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

                LaunchedEffect(Unit) {
                    auth.addAuthStateListener { firebaseAuth ->
                        isLoggedIn = firebaseAuth.currentUser != null
                    }
                }

                if (!isLoggedIn) {
                    LoginNavGraph(
                        onLoginSuccess = {
                            isLoggedIn = true
                        }
                    )
                } else {
                    AppNavGraph(
                        onLogout = {
                            // SettingsScreen 쪽에서 vm.logout() 이미 호출됨
                            isLoggedIn = false
                        }
                    )
                }
            }
        }
    }

    private fun onNotificationsReady() {
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
                    .addOnSuccessListener { Log.d("FCM", "Token saved: $token") }
                    .addOnFailureListener { e -> Log.e("FCM", "Token failed", e) }
            }
        }
    }
}
