package it.ansmi.tocsar.geo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.core.content.ContextCompat
import it.ansmi.tocsar.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs

actual fun createLocationGateway(): LocationGateway = AndroidLocationGateway(AndroidAppContext.require())

actual fun createCompassGateway(): CompassGateway = AndroidCompassGateway(AndroidAppContext.require())

private class AndroidLocationGateway(
    private val context: Context,
) : LocationGateway {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override suspend fun ensurePermission(): Boolean = withContext(Dispatchers.Main) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentFix(): GeoFix? = withContext(Dispatchers.Main) {
        if (!ensurePermission()) return@withContext null
        // Solo last-known fresco: un GPS di ieri (accuracy ok) non deve finire sul TOC.
        val lastGps = lastKnownGps()
        if (
            lastGps != null &&
            lastGps.accuracy <= 60f &&
            lastGps.ageMs() < GpsPublishPolicy.MAX_FIX_AGE_MS
        ) {
            return@withContext lastGps.toFix()
        }
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (location.ageMs() > GpsPublishPolicy.MAX_FIX_AGE_MS) return
                    locationManager.removeUpdates(this)
                    if (cont.isActive) cont.resume(location.toFix())
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
            try {
                val provider = bestProvider()
                if (provider == null) {
                    val fresh = lastGps?.takeIf { it.ageMs() < GpsPublishPolicy.MAX_FIX_AGE_MS }
                    cont.resume(fresh?.toFix())
                    return@suspendCancellableCoroutine
                }
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (_: SecurityException) {
                cont.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun watchFixes(minDistanceM: Float, gpsOnly: Boolean, onFix: (GeoFix) -> Unit): () -> Unit {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return {}
        }

        // Filtro distanza solo qui (dopo i fix OS): se lo fa anche requestLocationUpdates
        // insieme a un seed in UI, i punti successivi restano bloccati.
        var lastLat: Double? = null
        var lastLon: Double? = null
        val listener = LocationListener { location ->
            if (location.ageMs() > GpsPublishPolicy.MAX_FIX_AGE_MS) return@LocationListener
            if (minDistanceM > 0f && lastLat != null && lastLon != null) {
                val out = FloatArray(1)
                Location.distanceBetween(lastLat!!, lastLon!!, location.latitude, location.longitude, out)
                if (out[0] < minDistanceM) return@LocationListener
            }
            lastLat = location.latitude
            lastLon = location.longitude
            onFix(location.toFix())
        }

        val providers = buildList {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (!gpsOnly && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        if (providers.isEmpty()) return {}

        return try {
            // minDistance OS = 0: altrimenti Android + seed UI scartano quasi tutto
            for (provider in providers) {
                locationManager.requestLocationUpdates(
                    provider,
                    1000L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            ({ locationManager.removeUpdates(listener) })
        } catch (_: SecurityException) {
            {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownGps(): Location? {
        return try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun bestProvider(): String? {
        return when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }
}

private fun Location.ageMs(): Long {
    val elapsed = elapsedRealtimeNanos
    if (elapsed > 0L) {
        return ((SystemClock.elapsedRealtimeNanos() - elapsed) / 1_000_000L).coerceAtLeast(0L)
    }
    return if (time > 0L) (System.currentTimeMillis() - time).coerceAtLeast(0L) else Long.MAX_VALUE
}

private fun Location.toFix(): GeoFix = GeoFix(
    latitude = latitude,
    longitude = longitude,
    altitude = if (hasAltitude()) altitude else 0.0,
    accuracyM = accuracy,
    timestampMs = time,
    provider = provider.orEmpty(),
    hasAltitude = hasAltitude(),
)

private class AndroidCompassGateway(
    private val context: Context,
) : CompassGateway {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Allineato a flutter_compass (TocAppBuild): remap per rotazione display + pitch. */
    override fun watchHeading(onHeading: (Double?) -> Unit): () -> Unit {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)

        val rotationMatrix = FloatArray(9)
        val adjustedRotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val truncatedRv = FloatArray(4)
        val gravityValues = FloatArray(3)
        val magneticValues = FloatArray(3)

        var rotationVectorValue: FloatArray? = null
        var hasGravity = false
        var hasMag = false
        var nextEmitAt = 0L

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val useRotationVector = rotationSensor != null

        fun rotationVectorFrom(event: SensorEvent): FloatArray {
            return if (event.values.size > 4) {
                System.arraycopy(event.values, 0, truncatedRv, 0, 4)
                truncatedRv
            } else {
                event.values
            }
        }

        fun lowPass(newValues: FloatArray, smoothed: FloatArray) {
            val alpha = 0.45f
            for (i in newValues.indices) {
                smoothed[i] = smoothed[i] + alpha * (newValues[i] - smoothed[i])
            }
        }

        fun worldAxesForFlat(): Pair<Int, Int> = when (display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }

        fun worldAxesForUpright(): Pair<Int, Int> = when (display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Z to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
        }

        fun worldAxesForUpsideDown(): Pair<Int, Int> = when (display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_Z
            Surface.ROTATION_270 -> SensorManager.AXIS_Z to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_MINUS_Z
        }

        fun worldAxesForFaceDown(): Pair<Int, Int> = when (display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_MINUS_Y
        }

        fun updateOrientation() {
            val now = SystemClock.elapsedRealtime()
            if (now < nextEmitAt) return

            val rv = rotationVectorValue
            if (rv != null) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, rv)
            } else if (hasGravity && hasMag) {
                if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, magneticValues)) {
                    return
                }
            } else {
                return
            }

            var (axisX, axisY) = worldAxesForFlat()
            SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, adjustedRotationMatrix)
            SensorManager.getOrientation(adjustedRotationMatrix, orientation)

            val pitch = orientation[1]
            val roll = orientation[2]
            when {
                pitch < -PI / 4 -> {
                    val a = worldAxesForUpright()
                    axisX = a.first
                    axisY = a.second
                }
                pitch > PI / 4 -> {
                    val a = worldAxesForUpsideDown()
                    axisX = a.first
                    axisY = a.second
                }
                abs(roll) > PI / 2 -> {
                    val a = worldAxesForFaceDown()
                    axisX = a.first
                    axisY = a.second
                }
            }

            SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, adjustedRotationMatrix)
            SensorManager.getOrientation(adjustedRotationMatrix, orientation)

            val rawDeg = Math.toDegrees(orientation[0].toDouble())
            val offset = loadCompassHeadingOffset()
            val heading = normalizeHeadingDegrees(rawDeg + offset) ?: return
            nextEmitAt = now + 32
            mainHandler.post { onHeading(heading) }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        rotationVectorValue = rotationVectorFrom(event).clone()
                        updateOrientation()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        if (!useRotationVector) {
                            lowPass(event.values, gravityValues)
                            hasGravity = true
                            updateOrientation()
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        if (!useRotationVector) {
                            lowPass(event.values, magneticValues)
                            hasMag = true
                            updateOrientation()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // ~30 ms come flutter_compass
        val delayUs = 30_000
        if (useRotationVector) {
            sensorManager.registerListener(listener, rotationSensor, delayUs)
        } else {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(listener, it, delayUs)
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                sensorManager.registerListener(listener, it, delayUs)
            }
        }

        return {
            sensorManager.unregisterListener(listener)
            mainHandler.removeCallbacksAndMessages(null)
        }
    }
}
