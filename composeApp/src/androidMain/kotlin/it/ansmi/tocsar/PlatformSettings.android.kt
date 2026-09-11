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

actual fun unusedAppPermissionHint(): String =
    "Android: Impostazioni -> TOC SAR -> «Gestisci l'app se inutilizzata» -> " +
        "disattiva «Rimuovi le autorizzazioni se l'app non viene usata», " +
        "altrimenti il telefono toglie GPS e notifiche da solo."
