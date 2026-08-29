package it.ansmi.tocsar.backend

import platform.Foundation.NSUserDefaults

actual object OperatorSessionStore {
    private const val Key = "operator_session_id"
    private val defaults get() = NSUserDefaults.standardUserDefaults

    actual fun loadSessionId(): String? =
        defaults.stringForKey(Key)?.trim()?.takeIf { it.isNotEmpty() }

    actual fun saveSessionId(sessionId: String?) {
        val value = sessionId?.trim()?.takeIf { it.isNotEmpty() }
        if (value == null) {
            defaults.removeObjectForKey(Key)
        } else {
            defaults.setObject(value, Key)
        }
        defaults.synchronize()
    }
}
