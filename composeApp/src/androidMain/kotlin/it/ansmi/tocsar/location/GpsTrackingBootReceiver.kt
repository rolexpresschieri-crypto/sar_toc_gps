package it.ansmi.tocsar.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Dopo riavvio o aggiornamento APK il foreground service non riparte da solo:
 * la sessione resterebbe online sul TOC col pin di ieri.
 */
class GpsTrackingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val sessionId = GpsTrackingSessionStore.load(context) ?: return
        GpsTrackingController.start(context, sessionId)
    }
}
