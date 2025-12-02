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
import com.dreamindream.app.ui.login.LoginScreen
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

        // Edge-to-Edge
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // 광고 초기화 (테스트 디바이스 유지)
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

        // 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
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

        // 🔥 Compose 루트
        setContent {
            MaterialTheme {

                val auth = remember { FirebaseAuth.getInstance() }
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

                // FirebaseAuth 상태 변화 감지 → UI 자동 스위칭
                LaunchedEffect(Unit) {
                    auth.addAuthStateListener { firebaseAuth ->
                        isLoggedIn = firebaseAuth.currentUser != null
                    }
                }

                if (!isLoggedIn) {
                    // 🔹 로그인 화면 (Compose)
                    LoginScreen(
                        onLoginSuccess = {
                            // 로그인 성공 시 홈으로 전환
                            isLoggedIn = true
                        }
                    )
                } else {
                    // 🔹 실제 앱 메인 네비게이션
                    AppNavGraph()
                }
            }
        }
    }

    private fun onNotificationsReady() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result ?: return@addOnCompleteListener
                val uid =
                    FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnCompleteListener

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
