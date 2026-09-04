package it.ansmi.tocsar.geo

data class TrackStats(
    val distanceM: Double,
    val durationMs: Long,
    val avgSpeedKmh: Double?,
    val elevGainM: Double,
    val elevLossM: Double,
    val nPoints: Int,
)

/**
 * Distanza sui tratti continui (salta i buchi GPS).
 * Dislivello: somma salite e discese tra quote consecutive (soglia 2 m, GPS rumoroso).
 */
fun computeTrackStats(
    points: List<TrackPoint>,
    durationMs: Long,
): TrackStats {
    var dist = 0.0
    var gain = 0.0
    var loss = 0.0
    val minStepM = 2.0
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        if (cur.gapBefore) continue
        dist += haversineDistanceM(prev.lat, prev.lon, cur.lat, cur.lon)
        val a0 = prev.alt
        val a1 = cur.alt
        if (a0 != null && a1 != null && a0.isFinite() && a1.isFinite()) {
            val d = a1 - a0
            if (d >= minStepM) gain += d
            else if (d <= -minStepM) loss += -d
        }
    }
    val durationS = durationMs.coerceAtLeast(0L) / 1000.0
    val speed =
        if (durationS >= 1.0 && dist > 0.0) {
            (dist / durationS) * 3.6
        } else {
            null
        }
    return TrackStats(
        distanceM = dist,
        durationMs = durationMs.coerceAtLeast(0L),
        avgSpeedKmh = speed,
        elevGainM = gain,
        elevLossM = loss,
        nPoints = points.size,
    )
}

fun formatTrackDistance(distanceM: Double): String =
    if (distanceM >= 1000.0) {
        "${(kotlin.math.round(distanceM / 10.0) / 100.0)} km"
    } else {
        "${kotlin.math.round(distanceM).toInt()} m"
    }

fun formatTrackDurationMin(durationMs: Long): String {
    val min = durationMs.coerceAtLeast(0L) / 60000.0
    val rounded = kotlin.math.round(min * 10.0) / 10.0
    return "$rounded min"
}

fun formatTrackSpeed(kmh: Double?): String =
    if (kmh == null) "—" else "${kotlin.math.round(kmh * 10.0) / 10.0} km/h"

fun formatTrackElev(gainM: Double, lossM: Double): String {
    val g = kotlin.math.round(gainM).toInt()
    val l = kotlin.math.round(lossM).toInt()
    return "+${g} m / −${l} m"
}
