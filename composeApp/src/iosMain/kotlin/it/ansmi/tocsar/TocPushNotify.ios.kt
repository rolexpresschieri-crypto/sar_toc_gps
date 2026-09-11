package it.ansmi.tocsar

private var showPush: ((String, String) -> Unit)? = null
private var clearPush: (() -> Unit)? = null

object TocPushIosBridge {
    fun install(
        show: (String, String) -> Unit,
        clear: () -> Unit,
    ) {
        showPush = show
        clearPush = clear
    }
}

internal fun requestTocPushPermission() {
    // Permesso richiesto in AppDelegate (iOSApp.swift).
}

actual fun showTocPushNotification(title: String, body: String) {
    showPush?.invoke(
        title.ifBlank { "TOC SAR" },
        body.ifBlank { title.ifBlank { "TOC SAR" } },
    )
}

actual fun clearTocPushNotification() {
    clearPush?.invoke()
}
