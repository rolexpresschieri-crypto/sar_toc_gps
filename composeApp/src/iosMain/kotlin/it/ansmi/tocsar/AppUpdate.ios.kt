package it.ansmi.tocsar

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun appVersionCode(): Int = 0

actual fun isAndroidAppUpdateSupported(): Boolean = false

actual fun openExternalUrl(url: String) {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return
    val nsUrl = NSURL.URLWithString(trimmed) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

actual suspend fun downloadAndInstallApk(url: String): String? = "Aggiornamento APK solo su Android"
