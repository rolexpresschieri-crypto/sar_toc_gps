package it.ansmi.tocsar

import android.content.Intent
import android.net.Uri
import android.provider.Settings

actual fun openAppSystemSettings() {
    val context = AndroidAppContext.require()
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
