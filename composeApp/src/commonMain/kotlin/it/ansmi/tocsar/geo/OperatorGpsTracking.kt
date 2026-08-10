package it.ansmi.tocsar.geo

/**
 * Avvio/stop tracking GPS verso TOC + registrazione TRK in background (foreground service Android).
 * Su iOS: no-op per ora.
 */
expect object OperatorGpsTracking {
    fun start(sessionId: String): Boolean
    fun stop()
    fun startTrkRecording()
    /** Ferma TRK e restituisce i punti accumulati (può essere vuoto). */
    fun stopTrkRecording(): List<TrackPoint>
    fun isTrkRecording(): Boolean
    fun trkPointCount(): Int
    fun trkPointsSnapshot(): List<TrackPoint>
    fun statusLabel(): String?
}
