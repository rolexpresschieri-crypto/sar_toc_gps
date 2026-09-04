package it.ansmi.tocsar.geo

import it.ansmi.tocsar.AndroidAppContext
import it.ansmi.tocsar.location.GpsTrackingController
import it.ansmi.tocsar.location.GpsTrackingRuntime

actual object OperatorGpsTracking {
    actual fun start(sessionId: String): Boolean {
        val ctx = runCatching { AndroidAppContext.require() }.getOrNull() ?: return false
        return GpsTrackingController.start(ctx, sessionId)
    }

    actual fun stop() {
        val ctx = runCatching { AndroidAppContext.require() }.getOrNull() ?: return
        GpsTrackingController.stop(ctx)
    }

    actual fun startTrkRecording() {
        val ctx = runCatching { AndroidAppContext.require() }.getOrNull() ?: return
        GpsTrackingController.startTrk(ctx)
    }

    actual fun stopTrkRecording(): TrkRecordingResult {
        val ctx = runCatching { AndroidAppContext.require() }.getOrNull()
        val (points, durationMs) =
            if (ctx == null) {
                GpsTrackingRuntime.stopTrkAndTakePoints()
            } else {
                GpsTrackingController.stopTrk(ctx)
            }
        return TrkRecordingResult(points = points, durationMs = durationMs)
    }

    actual fun isTrkRecording(): Boolean = GpsTrackingRuntime.trkRecording

    actual fun trkPointCount(): Int = GpsTrackingRuntime.trkCount()

    actual fun trkPointsSnapshot(): List<TrackPoint> = GpsTrackingRuntime.trkSnapshot()

    actual fun statusLabel(): String? = GpsTrackingRuntime.status.value?.label
}
