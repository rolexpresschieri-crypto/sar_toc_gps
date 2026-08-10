package it.ansmi.tocsar.location

import it.ansmi.tocsar.geo.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GpsTrackingRuntime {
    data class Status(
        val accuracyM: Double?,
        val label: String,
        val trkRecording: Boolean = false,
        val trkPointCount: Int = 0,
    )

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    @Volatile
    var trkRecording: Boolean = false
        private set

    private val trackPoints = mutableListOf<TrackPoint>()
    private val trackLock = Any()

    fun updateStatus(accuracyM: Double?, label: String) {
        val count: Int
        val recording: Boolean
        synchronized(trackLock) {
            count = trackPoints.size
            recording = trkRecording
        }
        _status.value = Status(
            accuracyM = accuracyM,
            label = label,
            trkRecording = recording,
            trkPointCount = count,
        )
    }

    fun beginTrk() {
        synchronized(trackLock) {
            trackPoints.clear()
            trkRecording = true
        }
        val cur = _status.value
        updateStatus(cur?.accuracyM, cur?.label ?: "TRK in registrazione…")
    }

    fun addTrkPoint(point: TrackPoint) {
        synchronized(trackLock) {
            if (!trkRecording) return
            trackPoints.add(point)
        }
        val cur = _status.value
        updateStatus(cur?.accuracyM, cur?.label ?: "TRK in registrazione…")
    }

    fun stopTrkAndTakePoints(): List<TrackPoint> {
        val out: List<TrackPoint>
        synchronized(trackLock) {
            trkRecording = false
            out = trackPoints.toList()
            trackPoints.clear()
        }
        val cur = _status.value
        updateStatus(cur?.accuracyM, cur?.label ?: "GPS inviato al TOC")
        return out
    }

    fun trkSnapshot(): List<TrackPoint> = synchronized(trackLock) { trackPoints.toList() }

    fun trkCount(): Int = synchronized(trackLock) { trackPoints.size }

    fun clear() {
        synchronized(trackLock) {
            trkRecording = false
            trackPoints.clear()
        }
        _status.value = null
    }
}
