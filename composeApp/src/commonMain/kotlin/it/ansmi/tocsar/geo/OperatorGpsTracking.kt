package it.ansmi.tocsar.geo

/**
 * Avvio/stop tracking GPS verso TOC + registrazione TRK (foreground Android / CoreLocation iOS).
 */
expect object OperatorGpsTracking {
    fun start(sessionId: String): Boolean
    fun stop()
    fun startTrkRecording()
    /** Ferma TRK: punti + durata da START (ms). */
    fun stopTrkRecording(): TrkRecordingResult
    fun isTrkRecording(): Boolean
    fun trkPointCount(): Int
    fun trkPointsSnapshot(): List<TrackPoint>
    fun statusLabel(): String?
}

data class TrkRecordingResult(
    val points: List<TrackPoint>,
    val durationMs: Long,
)
