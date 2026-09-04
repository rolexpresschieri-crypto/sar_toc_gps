package it.ansmi.tocsar.location

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import it.ansmi.tocsar.geo.TrackPoint

object GpsTrackingController {
    private const val TAG = "GpsTrackingController"

    fun start(context: Context, sessionId: String): Boolean {
        val appContext = context.applicationContext
        if (!GpsLocationPermissions.hasFineLocation(appContext)) {
            Log.w(TAG, "start: permesso posizione assente")
            return false
        }
        GpsTrackingSessionStore.save(appContext, sessionId)
        val intent =
            Intent(appContext, OperatorGpsForegroundService::class.java).apply {
                action = OperatorGpsForegroundService.ACTION_START
                putExtra(OperatorGpsForegroundService.EXTRA_SESSION_ID, sessionId)
            }
        return try {
            ContextCompat.startForegroundService(appContext, intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService fallito", e)
            false
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        GpsTrackingSessionStore.clear(appContext)
        GpsTrackingRuntime.clear()
        val intent =
            Intent(appContext, OperatorGpsForegroundService::class.java).apply {
                action = OperatorGpsForegroundService.ACTION_STOP
            }
        try {
            appContext.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "stop service fallito", e)
        }
    }

    fun startTrk(context: Context) {
        val appContext = context.applicationContext
        GpsTrackingRuntime.beginTrk()
        val intent =
            Intent(appContext, OperatorGpsForegroundService::class.java).apply {
                action = OperatorGpsForegroundService.ACTION_START_TRK
            }
        try {
            appContext.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startTrk fallito", e)
        }
    }

    fun stopTrk(context: Context): Pair<List<TrackPoint>, Long> {
        val appContext = context.applicationContext
        val intent =
            Intent(appContext, OperatorGpsForegroundService::class.java).apply {
                action = OperatorGpsForegroundService.ACTION_STOP_TRK
            }
        try {
            appContext.startService(intent)
        } catch (_: Exception) {
        }
        return GpsTrackingRuntime.stopTrkAndTakePoints()
    }
}
