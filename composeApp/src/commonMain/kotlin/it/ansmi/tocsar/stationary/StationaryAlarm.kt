package it.ansmi.tocsar.stationary

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Test: 20 s. Produzione prevista: 120_000 (2 min). */
const val STATIONARY_THRESHOLD_MS = 20_000L
const val STATIONARY_SOUND_MS = 15_000L
const val STATIONARY_MOVE_M = 10.0

data class StationarySample(
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
)

private data class TrackState(
    val lat: Double,
    val lon: Double,
    val settledAtMs: Long,
)

/**
 * Monitor fermo condiviso (solo LUPO in app): stessa logica del TOC.
 * Esclusione e reset campana restano in memoria finché LUPO è loggato.
 */
object LupoStationaryController {
    private val tracks = mutableMapOf<String, TrackState>()
    private val sounded = mutableSetOf<String>()
    private val excluded = mutableSetOf<String>()

    private val _stationaryIds = MutableStateFlow<Set<String>>(emptySet())
    val stationaryIds: StateFlow<Set<String>> = _stationaryIds.asStateFlow()

    private val _excludedIds = MutableStateFlow<Set<String>>(emptySet())
    val excludedIds: StateFlow<Set<String>> = _excludedIds.asStateFlow()

    fun clear() {
        tracks.clear()
        sounded.clear()
        excluded.clear()
        _stationaryIds.value = emptySet()
        _excludedIds.value = emptySet()
        stopStationaryBips()
    }

    /** Reset allarme: toglie UI/suono e riavvia il timer. */
    fun dismiss(sessionId: String) {
        if (excluded.contains(sessionId)) return
        val now = epochNow()
        val prev = tracks[sessionId]
        if (prev != null) {
            tracks[sessionId] = prev.copy(settledAtMs = now)
        }
        sounded.remove(sessionId)
        stopStationaryBips()
        if (_stationaryIds.value.contains(sessionId)) {
            _stationaryIds.value = _stationaryIds.value - sessionId
        }
    }

    /**
     * Toggle esclusione:
     * ON (rosso) = ferma allarme; OFF (verde) = riparte il timer.
     */
    fun toggleExclude(sessionId: String) {
        val now = epochNow()
        if (excluded.contains(sessionId)) {
            excluded.remove(sessionId)
            val prev = tracks[sessionId]
            if (prev != null) {
                tracks[sessionId] = prev.copy(settledAtMs = now)
            }
            sounded.remove(sessionId)
            _excludedIds.value = excluded.toSet()
            return
        }
        excluded.add(sessionId)
        sounded.remove(sessionId)
        stopStationaryBips()
        if (_stationaryIds.value.contains(sessionId)) {
            _stationaryIds.value = _stationaryIds.value - sessionId
        }
        _excludedIds.value = excluded.toSet()
    }

    /**
     * Aggiorna lo stato dagli operatori online con GPS.
     * [liveIds] = sessioni ancora online (anche senza GPS) per cleanup.
     */
    fun ingest(samples: List<StationarySample>, liveIds: Set<String>) {
        val now = epochNow()

        for (key in tracks.keys.toList()) {
            if (key !in liveIds) {
                tracks.remove(key)
                sounded.remove(key)
                if (excluded.remove(key)) {
                    _excludedIds.value = excluded.toSet()
                }
            }
        }
        for (key in excluded.toList()) {
            if (key !in liveIds) {
                excluded.remove(key)
                _excludedIds.value = excluded.toSet()
            }
        }

        val next = linkedSetOf<String>()
        for (s in samples) {
            if (!s.latitude.isFinite() || !s.longitude.isFinite()) continue
            val prev = tracks[s.sessionId]
            if (prev == null) {
                tracks[s.sessionId] = TrackState(s.latitude, s.longitude, now)
                continue
            }
            val moved = haversineM(prev.lat, prev.lon, s.latitude, s.longitude)
            if (moved >= STATIONARY_MOVE_M) {
                tracks[s.sessionId] = TrackState(s.latitude, s.longitude, now)
                sounded.remove(s.sessionId)
                continue
            }
            tracks[s.sessionId] = TrackState(s.latitude, s.longitude, prev.settledAtMs)
            if (s.sessionId in excluded) continue
            if (now - prev.settledAtMs >= STATIONARY_THRESHOLD_MS) {
                next.add(s.sessionId)
                if (sounded.add(s.sessionId)) {
                    playStationaryBips(STATIONARY_SOUND_MS)
                }
            }
        }

        if (next != _stationaryIds.value) {
            _stationaryIds.value = next
        }
    }

    private fun epochNow(): Long = it.ansmi.tocsar.epochMs()
}

private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    fun toRad(d: Double) = d * PI / 180.0
    val dLat = toRad(lat2 - lat1)
    val dLon = toRad(lon2 - lon1)
    val a =
        sin(dLat / 2) * sin(dLat / 2) +
            cos(toRad(lat1)) * cos(toRad(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}
