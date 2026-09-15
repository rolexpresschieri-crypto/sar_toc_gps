package it.ansmi.tocsar

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

actual fun openAppSystemSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}

actual fun unusedAppPermissionHint(): String =
    "iPhone: Impostazioni → TOC SAR → Posizione «Sempre» e Notifiche attive, " +
        "altrimenti in tasca GPS e avvisi TOC si fermano."
