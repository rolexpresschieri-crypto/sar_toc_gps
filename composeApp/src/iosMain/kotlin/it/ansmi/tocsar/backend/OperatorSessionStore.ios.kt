package it.ansmi.tocsar.backend

import platform.Foundation.NSUserDefaults

actual object OperatorSessionStore {
    private const val Key = "operator_session_id"
    private const val OrgKey = "organization_code"
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

    actual fun loadOrganizationCode(): String? =
        defaults.stringForKey(OrgKey)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    actual fun saveOrganizationCode(code: String?) {
        val value = code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (value == null) {
            defaults.removeObjectForKey(OrgKey)
        } else {
            defaults.setObject(value, OrgKey)
        }
        defaults.synchronize()
    }
}
