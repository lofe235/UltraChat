package com.ultrasys.ultrachat.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ultrasys.ultrachat.MainActivity
import com.ultrasys.ultrachat.R
import com.ultrasys.ultrachat.net.ConnectionHub
import com.ultrasys.ultrachat.net.LinkState
import com.ultrasys.ultrachat.net.TransportPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Держит процесс приложения живым, пока свёрнуто.
 *
 * Android засыпает вместе с активити: без foreground-сервиса система
 * замораживает процесс, сетевой сокет отваливается и уведомления о входящих
 * не приходят. Сервис показывает постоянное уведомление «UltraChat — на
 * связи» того же типа, что используют мессенджеры (remoteMessaging), и
 * обновляет его по состоянию соединения.
 */
class RelayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null
    private var lastText = ""

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startAsForeground(buildNotification("Подключение…"))
        watchJob = scope.launch {
            ConnectionHub.manager.state.collect { state ->
                val text = statusText(state)
                if (text != lastText) {
                    lastText = text
                    notify(buildNotification(text))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }
    }

    private fun notify(notification: Notification) {
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("UltraChat")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun statusText(state: LinkState): String = when {
        state.server.phase == TransportPhase.DISABLED -> "Сервер отключён в настройках"
        state.server.phase == TransportPhase.CONNECTED && state.serverPeers > 0 ->
            "На связи · собеседников: ${state.serverPeers}"
        state.server.phase == TransportPhase.CONNECTED -> "На связи"
        state.server.phase == TransportPhase.ERROR -> "Нет связи, переподключаюсь"
        else -> "Подключаюсь к серверу…"
    }

    companion object {
        private const val CHANNEL_ID = "ultrachat_connection_v1"
        private const val CHANNEL_NAME = "Соединение"
        private const val NOTIFICATION_ID = 4711

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, RelayService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, RelayService::class.java)) }
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Постоянное уведомление о работе UltraChat в фоне"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
        }
    }
}
