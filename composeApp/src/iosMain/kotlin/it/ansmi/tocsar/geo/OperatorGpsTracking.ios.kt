package it.ansmi.tocsar.geo

actual object OperatorGpsTracking {
    actual fun start(sessionId: String): Boolean = false
    actual fun stop() = Unit
    actual fun startTrkRecording() = Unit
    actual fun stopTrkRecording(): List<TrackPoint> = emptyList()
    actual fun isTrkRecording(): Boolean = false
    actual fun trkPointCount(): Int = 0
    actual fun trkPointsSnapshot(): List<TrackPoint> = emptyList()
    actual fun statusLabel(): String? = null
}
