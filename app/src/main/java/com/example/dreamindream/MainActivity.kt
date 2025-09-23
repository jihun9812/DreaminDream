package com.example.dreamindream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.dreamindream.ads.AdManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : BaseActivity() {

    private var lastBackPressedAt = 0L
    private val exitGapMs = 1800L

    private var lastBackHandledAt = 0L
    private val minBackGapMs = 300L   // 연타 디바운스

    private val REQ_NOTI = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 최초 실행 시: 모든 로그인 상태 초기화(익명 포함) ---
        val prefsFirst = getSharedPreferences("first_run_check", Context.MODE_PRIVATE)
        if (prefsFirst.getBoolean("isFirstRun", true)) {
            try {
                // 로그인 세션 초기화(중요)
                FirebaseAuth.getInstance().signOut()

                // SharedPreferences/로컬 파일 초기화(기존 로직 유지)
                listOf("user_info", "user_prefs", "dream_history", "fortune", "fortune_result")
                    .forEach { getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply() }
                filesDir?.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) { Log.e("Init", "초기화 실패", e) }
            prefsFirst.edit().putBoolean("isFirstRun", false).apply()
        }

        // --- 로그인 체크: 로그인 안돼 있으면 LoginActivity로 이동 ---
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            val intent = Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish(); return
        }

        // --- AdMob 초기화 + 테스트 디바이스 ---
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
        MobileAds.initialize(this)
        AdManager.initialize(this)

        // --- 알림 권한 + (필요 시) FCM 토큰 저장 ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTI
                )
            } else onNotificationsReady()
        } else onNotificationsReady()

        setContentView(R.layout.activity_main)

        // 시스템 바 색
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.dark_background)

        // 첫 진입: 홈을 루트로
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment(), "Home")
                .commit()
        }

        // 뒤로가기 - 단일 파이프라인
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackHandledAt < minBackGapMs) return
                lastBackHandledAt = now

                hideKeyboard()

                if (dismissTopDialogIfAny()) return

                val fm = supportFragmentManager
                val current = fm.findFragmentById(R.id.fragment_container)

                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                    return
                }

                if (current !is HomeFragment) {
                    fm.beginTransaction()
                        .setCustomAnimations(
                            R.anim.slide_in_left, R.anim.slide_out_right,
                            R.anim.slide_in_right, R.anim.slide_out_left
                        )
                        .replace(R.id.fragment_container, HomeFragment(), "Home")
                        .commit()
                    return
                }

                if (SystemClock.elapsedRealtime() - lastBackPressedAt <= exitGapMs) {
                    finish()
                } else {
                    lastBackPressedAt = SystemClock.elapsedRealtime()
                    Toast.makeText(this@MainActivity, "한 번 더 누르면 종료됩니다", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_UP) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTI && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) onNotificationsReady()
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            currentFocus?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
        } catch (_: Exception) {}
    }

    private fun dismissTopDialogIfAny(): Boolean {
        fun FragmentManager.closeIfAny(): Boolean {
            for (f in fragments) {
                if (f is DialogFragment && f.dialog?.isShowing == true) {
                    f.dismissAllowingStateLoss()
                    return true
                }
                if (f.childFragmentManager.closeIfAny()) return true
            }
            return false
        }
        return supportFragmentManager.closeIfAny()
    }

    private fun onNotificationsReady() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result ?: return@addOnCompleteListener
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnCompleteListener
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .set(
                        mapOf("fcmToken" to token, "last_token_at" to System.currentTimeMillis()),
                        SetOptions.merge()
                    )
                    .addOnSuccessListener { Log.d("FCM", "토큰 저장(merge) 완료: $token") }
                    .addOnFailureListener { Log.e("FCM", "토큰 저장 실패", it) }
            } else {
                Log.e("FCM", "토큰 획득 실패", task.exception)
            }
        }
    }

    @Suppress("unused")
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
