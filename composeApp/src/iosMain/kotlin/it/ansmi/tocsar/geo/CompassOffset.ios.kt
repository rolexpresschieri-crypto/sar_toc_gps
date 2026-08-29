package it.ansmi.tocsar.geo

import platform.Foundation.NSUserDefaults

private const val Key = "toc.compass_heading_offset_deg_v2"

actual fun loadCompassHeadingOffset(): Double =
    NSUserDefaults.standardUserDefaults.doubleForKey(Key)

actual fun saveCompassHeadingOffset(degrees: Double) {
    val n = normalizeHeadingDegrees(degrees) ?: 0.0
    NSUserDefaults.standardUserDefaults.setDouble(n, Key)
}
