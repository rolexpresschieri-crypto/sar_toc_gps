package it.ansmi.tocsar.location

import android.content.Context

object GpsTrackingSessionStore {
    private const val Prefs = "toc_sar_gps_tracking"
    private const val KeySession = "session_id"

    fun save(context: Context, sessionId: String) {
        context.applicationContext
            .getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .edit()
            .putString(KeySession, sessionId)
            .apply()
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .edit()
            .remove(KeySession)
            .apply()
    }

    fun load(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .getString(KeySession, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
