package it.ansmi.tocsar.backend

import android.content.Context
import it.ansmi.tocsar.AndroidAppContext

actual object OperatorSessionStore {
    private const val Prefs = "toc_sar_session"
    private const val Key = "operator_session_id"

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
}
