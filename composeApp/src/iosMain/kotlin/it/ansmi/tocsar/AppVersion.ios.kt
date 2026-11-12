package it.ansmi.tocsar

import platform.Foundation.NSBundle

actual fun appVersionName(): String =
    NSBundle.mainBundle.infoDictionary
        ?.get("CFBundleShortVersionString")
        as? String
        ?: "1.0.73"
