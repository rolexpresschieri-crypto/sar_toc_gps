package it.ansmi.tocsar.geo

import it.ansmi.tocsar.backend.GpsPosition
import it.ansmi.tocsar.backend.TocSarFacade
import it.ansmi.tocsar.backend.loadTocSarConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSLock
import kotlin.concurrent.Volatile

private const val TrackPointMinDistanceM = 3.0

private fun nowMs(): Long =
    ((NSDate().timeIntervalSinceReferenceDate + 978307200.0) * 1000.0).toLong()

private object IosGpsRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val location = createLocationGateway()
    private val facade: TocSarFacade? by lazy { loadTocSarConfig()?.let { TocSarFacade(it) } }

    @Volatile
    var sessionId: String? = null
        private set

    @Volatile
    var statusLabel: String? = null
        private set

    @Volatile
    var trkRecording: Boolean = false
        private set

    private val trackLock = NSLock()
    private val trackPoints = mutableListOf<TrackPoint>()

    private var stopWatch: (() -> Unit)? = null
    private var heartbeat: Job? = null
    private var lastPublished: GpsPosition? = null
    private var lastPublishedAtMs: Long? = null
    private var lastTrackFix: GeoFix? = null
    private var lastAcceptedWallMs: Long = 0L

    private inline fun <T> withTrackLock(block: () -> T): T {
        trackLock.lock()
        try {
            return block()
        } finally {
            trackLock.unlock()
        }
    }

    fun start(sessionId: String): Boolean {
        val id = sessionId.trim()
        if (id.isEmpty()) return false
        this.sessionId = id
        statusLabel = "GPS: in attesa di permesso…"
        scope.launch {
            if (!location.ensurePermission()) {
                statusLabel = "Login ok · concedi permesso GPS per tracking TOC"
                return@launch
            }
            withContext(Dispatchers.Main) { beginStreams() }
        }
        return true
    }

    fun stop() {
        stopWatch?.invoke()
        stopWatch = null
        heartbeat?.cancel()
        heartbeat = null
        sessionId = null
        lastPublished = null
        lastPublishedAtMs = null
        lastTrackFix = null
        lastAcceptedWallMs = 0L
        withTrackLock {
            trkRecording = false
            trackPoints.clear()
        }
        statusLabel = null
    }

    fun beginTrk() {
        withTrackLock {
            trackPoints.clear()
            trkRecording = true
        }
    }

    fun stopTrkAndTake(): List<TrackPoint> =
        withTrackLock {
            trkRecording = false
            val out = trackPoints.toList()
            trackPoints.clear()
            out
        }

    fun trkSnapshot(): List<TrackPoint> = withTrackLock { trackPoints.toList() }

    fun trkCount(): Int = withTrackLock { trackPoints.size }

    private fun beginStreams() {
        stopWatch?.invoke()
        heartbeat?.cancel()
        val sid = sessionId ?: return
        statusLabel = GpsPublishPolicy.accuracyLabel(null)
        stopWatch =
            location.watchFixes(minDistanceM = 0f, gpsOnly = false) { fix ->
                scope.launch { onFix(sid, fix) }
            }
        heartbeat =
            scope.launch {
                while (isActive && sessionId != null) {
                    delay(GpsPublishPolicy.MAP_REFRESH_INTERVAL_MS)
                    val now = nowMs()
                    val lastAt = lastPublishedAtMs
                    if (lastAt != null && now - lastAt < GpsPublishPolicy.MAP_REFRESH_INTERVAL_MS) {
                        continue
                    }
                    val fix = location.currentFix() ?: continue
                    onFix(sessionId ?: continue, fix)
                }
            }
    }

    private suspend fun onFix(sid: String, fix: GeoFix) {
        val now = nowMs()
        maybeRecordTrk(fix, now)
        maybePublish(
            sid,
            GpsPosition(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyM.toDouble(),
            ),
        )
    }

    private fun maybeRecordTrk(fix: GeoFix, now: Long) {
        if (!trkRecording) return
        val lastPoint = trkSnapshot().lastOrNull()
        if (!shouldAcceptTrackFix(lastPoint, lastTrackFix, fix, now, lastAcceptedWallMs)) return
        if (lastPoint != null) {
            val d = haversineDistanceM(lastPoint.lat, lastPoint.lon, fix.latitude, fix.longitude)
            if (d < TrackPointMinDistanceM) return
        }
        val gap = isTrackGap(lastPoint, fix, now, lastAcceptedWallMs)
        val recorded =
            withTrackLock {
                if (!trkRecording) {
                    false
                } else {
                    trackPoints.add(
                        TrackPoint(
                            lat = fix.latitude,
                            lon = fix.longitude,
                            alt = fix.altitude,
                            gapBefore = gap,
                        ),
                    )
                    true
                }
            }
        if (!recorded) return
        lastTrackFix = fix
        lastAcceptedWallMs = now
    }

    private suspend fun maybePublish(sid: String, position: GpsPosition) {
        val api = facade ?: return
        val now = nowMs()
        if (
            !GpsPublishPolicy.shouldPublish(
                position = position,
                lastPublished = lastPublished,
                lastPublishedAtMs = lastPublishedAtMs,
                nowMs = now,
            )
        ) {
            return
        }
        try {
            api.updatePosition(sid, position)
            lastPublished = position
            lastPublishedAtMs = now
            statusLabel = GpsPublishPolicy.accuracyLabel(position.accuracyMeters)
        } catch (_: Exception) {
            // rete assente: riprova al prossimo fix
        }
    }
}

actual object OperatorGpsTracking {
    actual fun start(sessionId: String): Boolean = IosGpsRuntime.start(sessionId)

    actual fun stop() = IosGpsRuntime.stop()

    actual fun startTrkRecording() = IosGpsRuntime.beginTrk()

    actual fun stopTrkRecording(): List<TrackPoint> = IosGpsRuntime.stopTrkAndTake()

    actual fun isTrkRecording(): Boolean = IosGpsRuntime.trkRecording

    actual fun trkPointCount(): Int = IosGpsRuntime.trkCount()

    actual fun trkPointsSnapshot(): List<TrackPoint> = IosGpsRuntime.trkSnapshot()

    actual fun statusLabel(): String? = IosGpsRuntime.statusLabel
}
