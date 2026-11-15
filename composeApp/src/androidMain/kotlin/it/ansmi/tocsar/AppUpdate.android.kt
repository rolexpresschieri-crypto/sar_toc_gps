package it.ansmi.tocsar

import android.content.Intent
import android.net.Uri

actual fun appVersionCode(): Int = BuildConfig.VERSION_CODE

actual fun isAndroidAppUpdateSupported(): Boolean = true

actual fun openExternalUrl(url: String) {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return
    val context = AndroidAppContext.require()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
