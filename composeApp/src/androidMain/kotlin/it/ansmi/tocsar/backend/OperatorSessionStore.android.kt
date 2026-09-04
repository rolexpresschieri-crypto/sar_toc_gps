package it.ansmi.tocsar.backend

import android.content.Context
import it.ansmi.tocsar.AndroidAppContext

actual object OperatorSessionStore {
    private const val Prefs = "toc_sar_session"
    private const val Key = "operator_session_id"
    private const val OrgKey = "organization_code"

    private fun prefs() =
        AndroidAppContext.require().getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    actual fun loadSessionId(): String? =
        prefs().getString(Key, null)?.trim()?.takeIf { it.isNotEmpty() }

    actual fun saveSessionId(sessionId: String?) {
        prefs().edit().apply {
            val value = sessionId?.trim()?.takeIf { it.isNotEmpty() }
            if (value == null) remove(Key) else putString(Key, value)
        }.apply()
    }

    actual fun loadOrganizationCode(): String? =
        prefs().getString(OrgKey, null)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    actual fun saveOrganizationCode(code: String?) {
        prefs().edit().apply {
            val value = code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            if (value == null) remove(OrgKey) else putString(OrgKey, value)
        }.apply()
    }
}
