package com.ultrasys.ultrachat.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ultrasys.ultrachat.MainActivity
import com.ultrasys.ultrachat.R

/**
 * Системные уведомления о входящих сообщениях.
 * Канал создаётся один раз и использует фирменный звук «Тёплое дерево» + вибрацию.
 */
object MessageNotifier {
    const val EXTRA_PEER_ID = "com.ultrasys.ultrachat.extra.PEER_ID"

    // Android не позволяет менять звук уже созданного канала, поэтому для
    // нового фирменного звука заведён отдельный канал v3.
    private const val CHANNEL_ID = "ultrachat_messages_v3"
    private val LEGACY_CHANNEL_IDS = listOf("ultrachat_messages_v1", "ultrachat_messages_v2")
    private const val CHANNEL_NAME = "Сообщения"
    private const val CHANNEL_DESCRIPTION = "Входящие сообщения UltraChat"

    private fun notificationSound(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.notification_warm_wood}")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        // Убираем старые каналы, чтобы в настройках не было дублей.
        LEGACY_CHANNEL_IDS.forEach { id ->
            runCatching { manager.deleteNotificationChannel(id) }
        }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val sound = notificationSound(context)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESCRIPTION
            setSound(sound, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 220, 120, 220)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun notifyMessage(
        context: Context,
        peerId: String,
        title: String,
        body: String,
        isCreator: Boolean = false,
    ) {
        if (!hasPermission(context)) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        val cleanTitle = title.ifBlank { "UltraChat" }
        val cleanBody = body.ifBlank { "Новое сообщение" }
        val sound = notificationSound(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PEER_ID, peerId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(peerId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(cleanTitle)
            .setContentText(cleanBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 220, 120, 220))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        if (isCreator) {
            builder.setSubText("Создатель")
        }

        runCatching {
            manager.notify(notificationId(peerId), builder.build())
        }
    }

    fun cancel(context: Context, peerId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId(peerId))
        }
    }

    fun cancelAll(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancelAll()
        }
    }

    private fun notificationId(peerId: String): Int =
        1000 + (peerId.hashCode() and 0x00FFFFFF)
}
