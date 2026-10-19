package it.ansmi.tocsar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.ansmi.tocsar.epochMs
import it.ansmi.tocsar.geo.MapTrackOverlay
import it.ansmi.tocsar.geo.OperatorGpsTracking
import it.ansmi.tocsar.geo.WaypointItem
import it.ansmi.tocsar.geo.countsAsTravelStep
import it.ansmi.tocsar.geo.haversineDistanceM
import it.ansmi.tocsar.geo.trackHorizDistanceM
import kotlin.math.roundToInt

/**
 * Cronometro, waypoint e tracce in mappa.
 * Resta vivo uscendo dalla mappa e dallo schermo GPS.
 * TIME/DIST si fermano con STOP. WP e TRK in mappa si tolgono solo con Clear data.
 */
object GpsFieldSession {
    var tripRunning by mutableStateOf(false)
    var tripLinked by mutableStateOf(false)
    var tripStartMs by mutableStateOf(0L)
    var tripElapsedMs by mutableStateOf(0L)
    var tripDistM by mutableStateOf(0.0)
    var tripFrozenElapsed by mutableStateOf(0L)
    var tripFrozenDist by mutableStateOf(0.0)
    var tripDistBase by mutableStateOf(0.0)
    /** true mentre l'odometro manuale deve ricevere i fix anche fuori dallo schermo GPS. */
    var manualArmed by mutableStateOf(false)
    var trkActive by mutableStateOf(false)

    var overlayWaypoints by mutableStateOf<List<WaypointItem>>(emptyList())
    var overlayTracks by mutableStateOf<List<MapTrackOverlay>>(emptyList())
    val selectedWpFlags = mutableStateListOf<String>()
    val selectedTrkFlags = mutableStateListOf<String>()

    private val manualTrip = ManualTrip()
    /** ST ha fermato il cronometro mentre il TRK continua: non riarmarlo da solo. */
    private var clockHeld = false

    fun onTravelFix(lat: Double, lon: Double, accuracyM: Float, speedMps: Float?) {
        if (!manualTrip.running) return
        manualTrip.onFix(lat, lon, accuracyM, speedMps)
        tripDistM = manualTrip.distM
    }

    fun releaseClockHold() {
        clockHeld = false
    }

    fun armFromTrack() {
        if (clockHeld) return
        if (tripRunning && tripLinked) return
        setManual(false)
        val start = OperatorGpsTracking.trkStartedAtMs().takeIf { it > 0L } ?: epochMs()
        tripStartMs = start
        tripElapsedMs = (epochMs() - start).coerceAtLeast(0L)
        tripDistM = trackHorizDistanceM(OperatorGpsTracking.trkPointsSnapshot())
        tripDistBase = 0.0
        tripLinked = true
        tripRunning = true
        trkActive = true
    }

    fun freeze() {
        if (!tripRunning) return
        tripFrozenElapsed = if (tripStartMs > 0L) {
            (epochMs() - tripStartMs).coerceAtLeast(0L)
        } else {
            tripElapsedMs
        }
        tripFrozenDist = if (tripLinked) {
            (trackHorizDistanceM(OperatorGpsTracking.trkPointsSnapshot()) - tripDistBase).coerceAtLeast(0.0)
        } else {
            manualTrip.distM
        }
        tripDistBase = 0.0
        tripRunning = false
        tripLinked = false
        setManual(false)
    }

    fun resetTrip() {
        setManual(false)
        manualTrip.reset()
        clockHeld = false
        tripRunning = false
        tripLinked = false
        tripStartMs = 0L
        tripElapsedMs = 0L
        tripDistM = 0.0
        tripFrozenElapsed = 0L
        tripFrozenDist = 0.0
        tripDistBase = 0.0
    }

    /** Azzera TIME e DIST. Se è in marcia, riparte da zero; se è fermo, resta a zero. */
    fun resetClock() {
        val now = epochMs()
        if (tripRunning && tripLinked) {
            tripStartMs = now
            tripElapsedMs = 0L
            tripDistBase = trackHorizDistanceM(OperatorGpsTracking.trkPointsSnapshot())
            return
        }
        if (tripRunning) {
            manualTrip.reset()
            setManual(true)
            tripStartMs = now
            tripElapsedMs = 0L
            tripDistM = 0.0
            tripDistBase = 0.0
            return
        }
        manualTrip.reset()
        setManual(false)
        tripStartMs = 0L
        tripElapsedMs = 0L
        tripDistM = 0.0
        tripDistBase = 0.0
        tripFrozenElapsed = 0L
        tripFrozenDist = 0.0
    }

    fun toggle() {
        if (tripRunning) {
            clockHeld = true
            freeze()
            return
        }
        clockHeld = false
        manualTrip.carry(tripFrozenDist.coerceAtLeast(0.0))
        setManual(true)
        tripLinked = false
        tripDistBase = 0.0
        val carriedMs = tripFrozenElapsed.coerceAtLeast(0L)
        tripStartMs = epochMs() - carriedMs
        tripElapsedMs = carriedMs
        tripDistM = manualTrip.distM
        tripRunning = true
    }

    fun clearAll() {
        resetTrip()
        overlayWaypoints = emptyList()
        overlayTracks = emptyList()
        selectedWpFlags.clear()
        selectedTrkFlags.clear()
        trkActive = false
    }

    private fun setManual(running: Boolean) {
        manualTrip.running = running
        manualArmed = running
    }
}

fun formatFieldElapsed(elapsedMs: Long): String {
    val total = (elapsedMs / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

/** Metri con migliaia «.» e un decimale «,» — es. mt. 1.234,5 */
fun formatFieldMeters(meters: Double): String {
    val safe = if (meters.isFinite() && meters > 0.0) meters else 0.0
    val tenths = (safe * 10.0).roundToInt()
    val whole = tenths / 10
    val frac = tenths % 10
    val grouped = whole.toString().reversed().chunked(3).joinToString(".").reversed()
    return "mt. $grouped,$frac"
}

private class ManualTrip {
    var running: Boolean = false
    var distM: Double = 0.0
    private var lastLat = Double.NaN
    private var lastLon = Double.NaN

    fun reset() {
        distM = 0.0
        lastLat = Double.NaN
        lastLon = Double.NaN
    }

    fun carry(dist: Double) {
        distM = dist.coerceAtLeast(0.0)
        lastLat = Double.NaN
        lastLon = Double.NaN
    }

    fun onFix(lat: Double, lon: Double, accuracyM: Float, speedMps: Float?) {
        if (!running) return
        if (lastLat.isNaN() || lastLon.isNaN()) {
            lastLat = lat
            lastLon = lon
            return
        }
        val step = haversineDistanceM(lastLat, lastLon, lat, lon)
        if (countsAsTravelStep(step, accuracyM, speedMps)) {
            distM += step
            lastLat = lat
            lastLon = lon
        } else if (step > 300.0) {
            lastLat = lat
            lastLon = lon
        }
    }
}
