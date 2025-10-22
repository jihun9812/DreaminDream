package com.dreamindream.app

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dreamindream.app.databinding.ActivityFeedbackBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ▼ 추가: 버튼 스타일 통일용 import
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        // --- 스타일 통일 (aireport의 심화분석 버튼과 동일) ---
        run {
            val d = resources.displayMetrics.density
            val r = 12f * d
            val btn = binding.btnSend

            // 배경 교체 시 크기/패딩 보존
            val pL = btn.paddingLeft
            val pT = btn.paddingTop
            val pR = btn.paddingRight
            val pB = btn.paddingBottom
            val minW = btn.minWidth
            val minH = btn.minHeight

            btn.isAllCaps = false
            btn.setTextColor(Color.BLACK)
            btn.backgroundTintList = null

            val gradient = GradientDrawable().apply {
                cornerRadius = r
                colors = intArrayOf(
                    Color.parseColor("#FFFEDCA6"),  // 연한 골드
                    Color.parseColor("#FF8BAAFF")   // 은은한 보라
                )
                orientation = GradientDrawable.Orientation.TL_BR
                gradientType = GradientDrawable.LINEAR_GRADIENT
                shape = GradientDrawable.RECTANGLE
            }

            val rippleCs = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))

            if (btn is com.google.android.material.button.MaterialButton) {
                btn.rippleColor = rippleCs
                btn.background = gradient
                btn.setPadding(pL, pT, pR, pB)
                btn.minWidth = minW
                btn.minHeight = minH
            } else {
                btn.background = RippleDrawable(rippleCs, gradient, null)
                btn.setPadding(pL, pT, pR, pB)
                btn.minWidth = minW
                btn.minHeight = minH
            }
        }

        // 클릭 리스너는 스타일 적용 뒤에
        binding.btnSend.setOnClickListener { submit() }
    }

    private fun submit() {
        val title = binding.editTitle.text?.toString()?.trim().orEmpty()
        val message = binding.editMessage.text?.toString()?.trim().orEmpty()

        if (title.isBlank()) { alert(getString(R.string.fb_title_need)); return }
        if (message.isBlank()) { alert(getString(R.string.fb_msg_need)); return }

        setBusy(true)

        lifecycleScope.launchWhenStarted {
            try {
                // 익명 로그인 보장 (보안규칙: auth != null)
                if (Firebase.auth.currentUser == null) {
                    Firebase.auth.signInAnonymously().await()
                }

                val now = System.currentTimeMillis()
                val datePattern = getString(R.string.fb_date_format) // "yyyy-MM-dd HH:mm:ss"
                val dateStr = SimpleDateFormat(datePattern, Locale.getDefault()).format(Date(now))

                val appName = getString(R.string.app_name)

                // 상위 호환: 기존 평면 필드 + info 오브젝트 동시 저장
                val data = hashMapOf(
                    "createdAt" to now,
                    "createdAtStr" to dateStr,
                    "title" to title,
                    "message" to message,
                    "userId" to (Firebase.auth.currentUser?.uid ?: "guest"),
                    "installId" to Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
                    "app" to appName,
                    "status" to "new",
                    "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "os" to "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                    "appVersion" to BuildConfig.VERSION_NAME,
                    // 신규 info 블럭(Functions에서 우선 사용)
                    "info" to mapOf(
                        "appVersion" to BuildConfig.VERSION_NAME,
                        "os" to "Android ${Build.VERSION.RELEASE}",
                        "sdk" to Build.VERSION.SDK_INT,
                        "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "userId" to (Firebase.auth.currentUser?.uid ?: "guest"),
                        "installId" to Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                    )
                )

                withTimeout(10_000L) {
                    Firebase.firestore.collection("feedback").add(data).await()
                }

                MaterialAlertDialogBuilder(this@FeedbackActivity)
                    .setTitle(R.string.fb_sent_title)
                    .setMessage(R.string.fb_sent_body)
                    .setPositiveButton(R.string.common_ok) { _, _ -> finish() }
                    .show()
            } catch (e: Exception) {
                alert(
                    getString(R.string.fb_send_fail_title) + "\n\n" +
                            getString(R.string.fb_send_fail_fmt, e.message ?: e.toString())
                )
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.btnSend.isEnabled = !busy
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun alert(msg: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.common_notice)
            .setMessage(msg)
            .setPositiveButton(R.string.common_ok, null)
            .show()
    }
}
