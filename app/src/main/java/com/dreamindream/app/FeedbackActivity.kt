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

class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        binding.topAppBar.setNavigationOnClickListener { finish() }

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
