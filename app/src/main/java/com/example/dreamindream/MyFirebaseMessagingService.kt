package com.example.dreamindream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        const val CHANNEL_ID = "dreamin_channel" // 서버 channelId와 동일

        /** 앱 시작/로그인 직후 1회 호출해 현재 토큰 저장 */
        fun ensureTokenSaved() {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "getToken 실패: ${task.exception?.message}")
                    return@addOnCompleteListener
                }
                task.result?.let { saveToken(it) }
            }
        }

        private fun saveToken(token: String) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(
                    mapOf("fcmToken" to token, "last_token_at" to System.currentTimeMillis()),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .addOnSuccessListener { Log.d(TAG, "토큰 저장 완료") }
                .addOnFailureListener { e -> Log.e(TAG, "토큰 저장 실패", e) }
        }
    }

    /** 토큰 갱신될 때마다 저장 (설치/복구/업데이트 등) */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken: $token")
        saveToken(token)
    }

    /** 포그라운드 수신 시 표시(백그라운드는 OS가 표시 가능) */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "DreamInDream"
        val message = remoteMessage.notification?.body  ?: remoteMessage.data["body"]  ?: "알림이 도착했어요!"
        val target = remoteMessage.data["navigateTo"] ?: "home"
        showNotification(title, message, target)
    }

    private fun showNotification(title: String, message: String, navigateTo: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ch = NotificationChannel(CHANNEL_ID, "DreamInDream 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "꿈해몽 푸시 알림 채널"
                enableLights(true); enableVibration(true)
                setSound(sound, Notification.AUDIO_ATTRIBUTES_DEFAULT)
            }
            nm.createNotificationChannel(ch)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigateTo", navigateTo)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_notification_blue)

        val noti = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)    // 흰색 벡터(상단바)
            .setLargeIcon(largeIcon)                     // 배너용 컬러 PNG
            .setContentTitle(title).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pi).setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), noti)
    }
}
