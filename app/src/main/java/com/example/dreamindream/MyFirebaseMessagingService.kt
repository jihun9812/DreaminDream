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
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "DreamInDream"
        val message = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "알림이 도착했어요!"
        val target = remoteMessage.data["navigateTo"] ?: "home"

        showNotification(title, message, target)
    }

    private fun showNotification(title: String, message: String, navigateTo: String) {
        val channelId = "dreamin_channel"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ✅ 채널 설정: 사운드, 중요도, 진동 등
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                channelId,
                "DreamInDream 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "꿈해몽 푸시 알림 채널"
                enableLights(true)
                enableVibration(true)
                setSound(soundUri, Notification.AUDIO_ATTRIBUTES_DEFAULT)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // ✅ 인텐트 설정 (눌렀을 때)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigateTo", navigateTo)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ✅ PNG 아이콘을 LargeIcon으로 넣기
        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_notification_blue)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // 상단바용 흰색 벡터
            .setLargeIcon(largeIcon) // 배너/본문용 컬러 아이콘
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 배너/헤드업 알림용
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 잠금화면 표시용
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }
}
