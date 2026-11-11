package io.github.offlinemobility.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the microphone receiver and relaying alive while the app screen is not visible.
 *
 * Android 14 only allows a microphone foreground service to start while the app is in the
 * foreground, so it is started from the screen and never restarted on its own (START_NOT_STICKY).
 */
class SignalService : Service() {

    private val engine: SignalEngine get() = (application as OfflineMobilityApp).engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Location type only when the user granted it; declaring a type without its permission throws.
            val location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                (if (location) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        engine.startListening()
        engine.refreshLocation()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engine.stopListening()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "주변 신호 듣기", NotificationManager.IMPORTANCE_LOW).apply {
                description = "통신이 끊겨도 주변 차량의 구조 신호를 받기 위해 마이크를 사용 중임을 알려요."
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("주변 차량의 신호를 듣고 있어요")
            .setContentText("소리는 기기 안에서만 분석하고 저장하지 않아요")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SignalService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SignalService::class.java))
        }
    }
}
