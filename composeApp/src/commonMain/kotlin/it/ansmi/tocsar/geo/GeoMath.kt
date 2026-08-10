package it.ansmi.tocsar.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracyM: Float,
    val timestampMs: Long = 0L,
    /** es. "gps", "network", "fused" */
    val provider: String = "",
    val hasAltitude: Boolean = false,
) {
    val isGpsProvider: Boolean
        get() = provider.contains("gps", ignoreCase = true)
}

fun parseCoord(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()

fun haversineDistanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = toRad(lat2 - lat1)
    val dLon = toRad(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(toRad(lat1)) * cos(toRad(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun bearingDeg(latFrom: Double, lonFrom: Double, latTo: Double, lonTo: Double): Double {
    val dLon = toRad(lonTo - lonFrom)
    val y = sin(dLon) * cos(toRad(latTo))
    val x = cos(toRad(latFrom)) * sin(toRad(latTo)) -
        sin(toRad(latFrom)) * cos(toRad(latTo)) * cos(dLon)
    var b = atan2(y, x) * 180.0 / kotlin.math.PI
    if (b < 0) b += 360.0
    return b
}

fun normalizeHeadingDegrees(raw: Double?): Double? {
    if (raw == null || raw.isNaN()) return null
    var h = raw % 360.0
    if (h < 0) h += 360.0
    return h
}

private fun toRad(deg: Double): Double = deg * kotlin.math.PI / 180.0

/** Filtro TRK: auto (preciso) e piedi (GPS rumoroso) senza stelle da rete. */
private const val MaxTrackFirstFixAccuracyM = 80f
private const val MaxTrackFixAccuracyM = 100f
private const val MaxTrackNetworkAccuracyM = 60f
private const val MaxTrackAccuracyRegression = 3.5f
private const val MaxTrackSpeedMps = 35.0
private const val MinAllowedJumpM = 80.0
private const val MaxTrackFixAgeMs = 30_000L

/** Pausa lunga (es. telefonata) + salto: riprendi senza collegare il buco con una retta. */
const val TrackGapMinElapsedMs = 45_000L
const val TrackGapMinJumpM = 150.0

fun isTrackGap(
    lastPoint: TrackPoint?,
    fix: GeoFix,
    nowMs: Long,
    lastAcceptedWallMs: Long,
): Boolean {
    if (lastPoint == null || lastAcceptedWallMs <= 0L) return false
    val elapsedMs = nowMs - lastAcceptedWallMs
    if (elapsedMs < TrackGapMinElapsedMs) return false
    val jumpM = haversineDistanceM(lastPoint.lat, lastPoint.lon, fix.latitude, fix.longitude)
    return jumpM >= TrackGapMinJumpM
}

/**
 * @param nowMs orologio wall (System.currentTimeMillis)
 * @param lastAcceptedWallMs quando è stato accettato l’ultimo punto TRK (0 = nessuno)
 */
fun shouldAcceptTrackFix(
    lastPoint: TrackPoint?,
    lastFix: GeoFix?,
    fix: GeoFix,
    nowMs: Long,
    lastAcceptedWallMs: Long = 0L,
): Boolean {
    if (fix.accuracyM <= 0f || !fix.accuracyM.isFinite()) return false
    if (fix.timestampMs > 0L && nowMs - fix.timestampMs > MaxTrackFixAgeMs) return false

    // Rete: solo se ragionevolmente precisa (evita alt=0 / stelle)
    if (!fix.isGpsProvider && fix.accuracyM > MaxTrackNetworkAccuracyM) return false

    if (lastPoint == null) {
        return fix.accuracyM <= MaxTrackFirstFixAccuracyM
    }

    if (fix.accuracyM > MaxTrackFixAccuracyM) return false
    if (lastFix != null &&
        fix.accuracyM > 50f &&
        fix.accuracyM > lastFix.accuracyM * MaxTrackAccuracyRegression
    ) {
        return false
    }

    val elapsedSec = maxOf(
        1L,
        if (lastAcceptedWallMs > 0L) (nowMs - lastAcceptedWallMs).coerceAtLeast(0L) / 1000L
        else 1L,
    )
    val jumpM = haversineDistanceM(lastPoint.lat, lastPoint.lon, fix.latitude, fix.longitude)
    val prevAcc = lastFix?.accuracyM ?: fix.accuracyM
    // Slack ampia: a piedi GPS può saltare 50–80 m di rumore; in auto elapsed*35 copre la velocità
    val allowedJump = maxOf(
        MinAllowedJumpM,
        elapsedSec * MaxTrackSpeedMps + prevAcc + fix.accuracyM * 4.0,
    )
    return jumpM <= allowedJump
}
