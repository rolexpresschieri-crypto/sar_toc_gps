package it.ansmi.tocsar.geo

import it.ansmi.tocsar.backend.GpsPosition
import kotlin.math.roundToInt

/** Gate publish TOC: ~ogni 2 s se accuratezza accettabile. */
object GpsPublishPolicy {
    const val MAX_PUBLISH_ACCURACY_M = 50.0
    const val MIN_PUBLISH_INTERVAL_MS = 2_000L
    const val MAP_REFRESH_INTERVAL_MS = 2_000L
    private const val IMPROVEMENT_RATIO = 0.55

    fun shouldPublish(
        position: GpsPosition,
        lastPublished: GpsPosition?,
        lastPublishedAtMs: Long?,
        nowMs: Long,
    ): Boolean {
        val accuracy = position.accuracyMeters
        if (accuracy != null && accuracy > 0 && accuracy > MAX_PUBLISH_ACCURACY_M) {
            return false
        }
        if (lastPublished == null || lastPublishedAtMs == null) {
            return true
        }
        if (nowMs - lastPublishedAtMs >= MIN_PUBLISH_INTERVAL_MS) {
            return true
        }
        val lastAcc = lastPublished.accuracyMeters
        if (
            accuracy != null &&
            accuracy > 0 &&
            lastAcc != null &&
            lastAcc > 0 &&
            accuracy < lastAcc * IMPROVEMENT_RATIO
        ) {
            return true
        }
        return false
    }

    fun accuracyLabel(accuracyM: Double?): String {
        if (accuracyM == null || accuracyM <= 0) {
            return "GPS: in attesa di fix…"
        }
        return "GPS inviato al TOC · ± ${accuracyM.roundToInt()} m (anche in tasca)"
    }
}
