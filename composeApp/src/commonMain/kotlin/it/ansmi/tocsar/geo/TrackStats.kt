package it.ansmi.tocsar.geo

import kotlin.math.hypot

data class TrackStats(
    /** Lunghezza 3D (pianta + dislivello), dopo filtro spike GPS. */
    val distanceM: Double,
    /** Solo haversine lat/lon, per confronto. */
    val distanceHorizM: Double,
    val durationMs: Long,
    /** Naismith/Langmuir: 5 km/h in pianta + 1 h / 600 m salita + 1 h / 1500 m discesa. */
    val estimatedDurationMs: Long,
    val avgSpeedKmh: Double?,
    val elevGainM: Double,
    val elevLossM: Double,
    val startAltM: Double?,
    val endAltM: Double?,
    val netElevM: Double?,
    val nPoints: Int,
)

/**
 * Distanza 3D su quota lisciata.
 * Dislivello: isteresi 5 m (evita che i salti GPS in su vengano scartati e il ritorno lento contato come discesa).
 */
fun computeTrackStats(
    points: List<TrackPoint>,
    durationMs: Long,
): TrackStats {
    val high = points.mapNotNull { it.alt?.takeIf { v -> v.isFinite() && v > 30.0 } }
    val mountain = high.size >= 5 && high.average() > 200.0
    val cleaned =
        points.map { p ->
            val a = p.alt?.takeIf { it.isFinite() }
            p.copy(alt = if (mountain && a != null && a <= 5.0) null else a)
        }
    val alts = smoothAltitudes(cleaned, window = 11)
    var horiz = 0.0
    var dist3d = 0.0
    for (i in 1 until points.size) {
        if (points[i].gapBefore) continue
        val prev = points[i - 1]
        val cur = points[i]
        val stepHoriz = haversineDistanceM(prev.lat, prev.lon, cur.lat, cur.lon)
        horiz += stepHoriz
        val a0 = alts[i - 1]
        val a1 = alts[i]
        val dAlt = if (a0 != null && a1 != null) a1 - a0 else 0.0
        dist3d += hypot(stepHoriz, dAlt)
    }
    val (gain, loss) = accumulateElevHysteresis(alts)
    val startAlt = alts.firstOrNull { it != null }
    val endAlt = alts.lastOrNull { it != null }
    val net =
        if (startAlt != null && endAlt != null) endAlt - startAlt else null
    val recordedMs = durationMs.coerceAtLeast(0L)
    val durationS = recordedMs / 1000.0
    val speed =
        if (durationS >= 1.0 && dist3d > 0.0) {
            (dist3d / durationS) * 3.6
        } else {
            null
        }
    return TrackStats(
        distanceM = dist3d,
        distanceHorizM = horiz,
        durationMs = recordedMs,
        estimatedDurationMs = estimateDurationMs(horiz, gain, loss),
        avgSpeedKmh = speed,
        elevGainM = gain,
        elevLossM = loss,
        startAltM = startAlt,
        endAltM = endAlt,
        netElevM = net,
        nPoints = points.size,
    )
}

/** 5 km/h sul piano + Naismith salita + Langmuir discesa. */
fun estimateDurationMs(distanceHorizM: Double, elevGainM: Double, elevLossM: Double): Long {
    val hours =
        distanceHorizM.coerceAtLeast(0.0) / 1000.0 / 5.0 +
            elevGainM.coerceAtLeast(0.0) / 600.0 +
            elevLossM.coerceAtLeast(0.0) / 1500.0
    return (hours * 3_600_000.0).toLong().coerceAtLeast(0L)
}

fun formatTrackDistance(distanceM: Double): String =
    if (distanceM >= 1000.0) {
        "${(kotlin.math.round(distanceM / 10.0) / 100.0)} km"
    } else {
        "${kotlin.math.round(distanceM).toInt()} m"
    }

fun formatTrackDurationMin(durationMs: Long): String {
    if (durationMs <= 0L) return "—"
    val min = durationMs / 60000.0
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

fun formatTrackAltM(alt: Double?): String =
    if (alt == null) "—" else "${kotlin.math.round(alt).toInt()} m"

fun formatTrackNetElev(netM: Double?): String {
    if (netM == null) return "—"
    val n = kotlin.math.round(netM).toInt()
    return if (n >= 0) "+$n m" else "−${-n} m"
}

internal fun smoothAltitudes(points: List<TrackPoint>, window: Int = 5): List<Double?> {
    val n = points.size
    if (n == 0) return emptyList()
    val filled = Array(n) { i -> points[i].alt?.takeIf { it.isFinite() } }

    fun forEachSegment(block: (start: Int, endExclusive: Int) -> Unit) {
        var start = 0
        while (start < n) {
            var end = start + 1
            while (end < n && !points[end].gapBefore) end++
            block(start, end)
            start = end
        }
    }

    forEachSegment { start, end ->
        for (i in start until end) {
            if (filled[i] != null) continue
            var p = i - 1
            while (p >= start && filled[p] == null) p--
            var q = i + 1
            while (q < end && filled[q] == null) q++
            if (p >= start && q < end) {
                val t = (i - p).toDouble() / (q - p)
                filled[i] = filled[p]!! * (1.0 - t) + filled[q]!! * t
            }
        }
    }

    val out = arrayOfNulls<Double>(n)
    val half = (window / 2).coerceAtLeast(1)
    forEachSegment { start, end ->
        for (i in start until end) {
            var s = 0.0
            var c = 0
            val from = maxOf(start, i - half)
            val to = minOf(end - 1, i + half)
            for (j in from..to) {
                val v = filled[j] ?: continue
                s += v
                c++
            }
            out[i] = if (c == 0) null else s / c
        }
    }
    return out.toList()
}

/** Isteresi: conta +/− solo dopo almeno [hystM] dal riferimento. */
internal fun accumulateElevHysteresis(
    alts: List<Double?>,
    hystM: Double = 5.0,
): Pair<Double, Double> {
    var gain = 0.0
    var loss = 0.0
    var ref: Double? = null
    for (a in alts) {
        if (a == null) continue
        val r = ref
        if (r == null) {
            ref = a
            continue
        }
        if (a >= r + hystM) {
            gain += a - r
            ref = a
        } else if (a <= r - hystM) {
            loss += r - a
            ref = a
        }
    }
    return gain to loss
}
