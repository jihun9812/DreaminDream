package com.example.dreamindream

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.dreamindream.databinding.ActivityFeedbackBinding
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

        if (title.isBlank()) { alert("제목을 입력해주세요"); return }
        if (message.isBlank()) { alert("내용을 입력해주세요"); return }

        setBusy(true)

        lifecycleScope.launchWhenStarted {
            try {
                // 익명 로그인 보장 (규칙 create: auth != null 대응)
                if (Firebase.auth.currentUser == null) {
                    Firebase.auth.signInAnonymously().await()
                }

                val now = System.currentTimeMillis()
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(now))

                val data = hashMapOf(
                    "createdAt" to now,
                    "createdAtStr" to dateStr,
                    "title" to title,
                    "message" to message,
                    "userId" to (Firebase.auth.currentUser?.uid ?: "guest"),
                    "installId" to Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
                    "app" to "DreamInDream",
                    "status" to "new",
                    "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "os" to "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                    "appVersion" to BuildConfig.VERSION_NAME
                )

                withTimeout(10_000L) {
                    Firebase.firestore.collection("feedback").add(data).await()
                }

                MaterialAlertDialogBuilder(this@FeedbackActivity)
                    .setTitle("전송 완료")
                    .setMessage("접수되었습니다. 빠르게 확인하겠습니다.")
                    .setPositiveButton("확인") { _, _ -> finish() }
                    .show()
            } catch (e: Exception) {
                alert("전송에 실패했습니다. 잠시 후 다시 시도해주세요.\n\n${e.message ?: e}")
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
            .setTitle("안내")
            .setMessage(msg)
            .setPositiveButton("확인", null)
            .show()
    }
}
