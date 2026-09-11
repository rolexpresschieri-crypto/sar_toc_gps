package it.ansmi.tocsar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val CHANNEL_ID = "toc_sar_toc_push"
private const val NOTIFICATION_ID = 42002

actual fun showTocPushNotification(title: String, body: String) {
    val context = runCatching { AndroidAppContext.require() }.getOrNull() ?: return
    ensureChannel()
    val openApp =
        PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    val notification =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gps_notification)
            .setContentTitle(title.ifBlank { "TOC SAR" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setContentIntent(openApp)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
    runCatching {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

actual fun clearTocPushNotification() {
    val context = runCatching { AndroidAppContext.require() }.getOrNull() ?: return
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}

private fun ensureChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val context = runCatching { AndroidAppContext.require() }.getOrNull() ?: return
    val mgr = context.getSystemService(NotificationManager::class.java) ?: return
    val channel =
        NotificationChannel(
            CHANNEL_ID,
            "Notifiche TOC",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Messaggi inviati dalla sala operativa"
            enableVibration(true)
        }
    mgr.createNotificationChannel(channel)
}
