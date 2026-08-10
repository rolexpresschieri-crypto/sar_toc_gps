package it.ansmi.tocsar.geo

import android.preference.PreferenceManager
import it.ansmi.tocsar.AndroidAppContext

private const val Key = "toc.compass_heading_offset_deg_v2"

actual fun loadCompassHeadingOffset(): Double {
    val ctx = AndroidAppContext.require().applicationContext
    return PreferenceManager.getDefaultSharedPreferences(ctx).getFloat(Key, 0f).toDouble()
}

actual fun saveCompassHeadingOffset(degrees: Double) {
    val ctx = AndroidAppContext.require().applicationContext
    val n = normalizeHeadingDegrees(degrees) ?: 0.0
    PreferenceManager.getDefaultSharedPreferences(ctx)
        .edit()
        .putFloat(Key, n.toFloat())
        .apply()
}
