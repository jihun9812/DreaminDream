package com.dreamindream.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.dreamindream.app.billing.SubscriptionManager
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

        // Edge-to-Edge
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

        // 로그인 체크
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            val intent = Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish(); return
        }

        // 광고 SDK 초기화: 비-프리미엄에서만
        val isPremium = SubscriptionManager.isPremium(this)
        if (!isPremium) {
            if (BuildConfig.DEBUG) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(
                            listOf(AdRequest.DEVICE_ID_EMULATOR, "38F4242F488E9C927543337A4DCCD32C")
                        ).build()
                )
            }
            MobileAds.initialize(this)
            AdManager.initialize(this)
        }

        // 알림 권한
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

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment(), "Home")
                .commit()
        }

        // 다이얼로그/외부에서 설정창을 열라고 넘긴 경우 처리(선택)
        intent?.let {
            if (it.getBooleanExtra("open_settings", false)) {
                openSettings(autolinkGoogle = it.getBooleanExtra("settings_autolink_google", false))
                it.removeExtra("open_settings")
                it.removeExtra("settings_autolink_google")
            }
        }

        // 뒤로가기
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

    /** ✅ Settings 화면을 열고, 필요하면 구글 통합을 자동 실행 */
    fun openSettings(autolinkGoogle: Boolean = false) {
        val f = SettingsFragment().apply {
            arguments = android.os.Bundle().apply {
                putBoolean("autolink_google", autolinkGoogle)
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, f, "Settings")
            .addToBackStack("Settings")
            .commit()
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
}
