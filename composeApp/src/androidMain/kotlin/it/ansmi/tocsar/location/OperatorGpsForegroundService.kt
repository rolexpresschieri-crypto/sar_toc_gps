package it.ansmi.tocsar.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import it.ansmi.tocsar.MainActivity
import it.ansmi.tocsar.R
import it.ansmi.tocsar.backend.GpsPosition
import it.ansmi.tocsar.backend.TocSarFacade
import it.ansmi.tocsar.backend.loadTocSarConfig
import it.ansmi.tocsar.geo.GeoFix
import it.ansmi.tocsar.geo.GpsPublishPolicy
import it.ansmi.tocsar.geo.TrackPoint
import it.ansmi.tocsar.geo.createLocationGateway
import it.ansmi.tocsar.geo.haversineDistanceM
import it.ansmi.tocsar.geo.isTrackGap
import it.ansmi.tocsar.geo.shouldAcceptTrackFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * GPS continuo verso TOC + TRK locale in background (schermo spento / tasca).
 */
class OperatorGpsForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val location by lazy { createLocationGateway() }
    private val facade by lazy {
        loadTocSarConfig()?.let { TocSarFacade(it) }
    }

    private var sessionId: String? = null
    private var stopLocationUpdates: (() -> Unit)? = null
    private var heartbeatJob: Job? = null
    private var lastPublished: GpsPosition? = null
    private var lastPublishedAtMs: Long? = null

    private var lastTrackFix: GeoFix? = null
    private var lastAcceptedWallMs: Long = 0L
    private var lastGpsWallMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_TRK -> {
                // Tracking già avviato: solo flag TRK (runtime già beginTrk dal controller)
                return START_STICKY
            }
            ACTION_STOP_TRK -> {
                lastTrackFix = null
                lastAcceptedWallMs = 0L
                return START_STICKY
            }
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_SESSION_ID)
                if (id.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!beginTracking(id)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                return START_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun beginTracking(id: String): Boolean {
        if (!GpsLocationPermissions.hasFineLocation(this)) {
            Log.w(TAG, "beginTracking: permesso posizione assente")
            return false
        }
        return try {
            sessionId = id
            GpsTrackingSessionStore.save(this, id)
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            shutdownStreams()
            GpsTrackingRuntime.updateStatus(
                accuracyM = null,
                label = GpsPublishPolicy.accuracyLabel(null),
            )

            serviceScope.launch {
                val initial = location.currentFix()
                if (initial != null) {
                    onFix(initial)
                }
            }

            stopLocationUpdates =
                location.watchFixes(minDistanceM = 0f, gpsOnly = false) { fix ->
                    serviceScope.launch { onFix(fix) }
                }

            heartbeatJob =
                serviceScope.launch {
                    while (isActive) {
                        delay(GpsPublishPolicy.MAP_REFRESH_INTERVAL_MS)
                        if (sessionId == null) continue
                        val now = System.currentTimeMillis()
                        val lastAt = lastPublishedAtMs
                        if (lastAt != null && now - lastAt < GpsPublishPolicy.MAP_REFRESH_INTERVAL_MS) {
                            continue
                        }
                        val fix = location.currentFix() ?: continue
                        onFix(fix)
                    }
                }
            true
        } catch (e: Exception) {
            Log.e(TAG, "beginTracking fallito", e)
            false
        }
    }

    private suspend fun onFix(fix: GeoFix) {
        val now = System.currentTimeMillis()
        if (fix.isGpsProvider) lastGpsWallMs = now
        maybeRecordTrk(fix, now)
        maybePublish(
            GpsPosition(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyM.toDouble(),
            ),
        )
    }

    private fun maybeRecordTrk(fix: GeoFix, now: Long) {
        if (!GpsTrackingRuntime.trkRecording) return
        if (!fix.isGpsProvider && now - lastGpsWallMs < 8_000L) return
        val lastPoint = GpsTrackingRuntime.trkSnapshot().lastOrNull()
        if (!shouldAcceptTrackFix(lastPoint, lastTrackFix, fix, now, lastAcceptedWallMs)) {
            return
        }
        if (lastPoint != null) {
            val d = haversineDistanceM(lastPoint.lat, lastPoint.lon, fix.latitude, fix.longitude)
            if (d < TRACK_POINT_MIN_DISTANCE_M) return
        }
        val gap = isTrackGap(lastPoint, fix, now, lastAcceptedWallMs)
        GpsTrackingRuntime.addTrkPoint(
            TrackPoint(
                lat = fix.latitude,
                lon = fix.longitude,
                alt = fix.altitude,
                gapBefore = gap,
            ),
        )
        lastTrackFix = fix
        lastAcceptedWallMs = now
    }

    private suspend fun maybePublish(position: GpsPosition) {
        val sid = sessionId ?: return
        val api = facade ?: return
        val now = System.currentTimeMillis()
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
            GpsTrackingRuntime.updateStatus(
                accuracyM = position.accuracyMeters,
                label = GpsPublishPolicy.accuracyLabel(position.accuracyMeters),
            )
        } catch (_: Exception) {
            // Rete assente: riprova al prossimo fix.
        }
    }

    private fun buildNotification(): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val trk = if (GpsTrackingRuntime.trkRecording) {
            " · TRK ${GpsTrackingRuntime.trkCount()} pt"
        } else {
            ""
        }
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gps_notification)
            .setContentTitle("Tracking operatore attivo")
            .setContentText("Posizione al TOC anche in tasca$trk")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Tracking GPS operatore",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Posizione inviata al TOC durante il servizio"
            }
        mgr.createNotificationChannel(channel)
    }

    private fun shutdownStreams() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        stopLocationUpdates?.invoke()
        stopLocationUpdates = null
        lastPublished = null
        lastPublishedAtMs = null
        lastTrackFix = null
        lastAcceptedWallMs = 0L
    }

    private fun shutdown() {
        shutdownStreams()
        sessionId = null
        GpsTrackingRuntime.clear()
    }

    companion object {
        private const val TAG = "OperatorGpsFgService"
        private const val CHANNEL_ID = "toc_sar_gps_tracking"
        private const val NOTIFICATION_ID = 42001
        private const val TRACK_POINT_MIN_DISTANCE_M = 3.0

        const val ACTION_START = "it.ansmi.tocsar.gps.START"
        const val ACTION_STOP = "it.ansmi.tocsar.gps.STOP"
        const val ACTION_START_TRK = "it.ansmi.tocsar.gps.START_TRK"
        const val ACTION_STOP_TRK = "it.ansmi.tocsar.gps.STOP_TRK"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
