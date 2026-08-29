package it.ansmi.tocsar.geo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLActivityTypeOtherNavigation
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.Foundation.NSError
import platform.Foundation.NSThread
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toGeoFix(): GeoFix {
    val latLon = coordinate.useContents { latitude to longitude }
    val timestampMs =
        ((timestamp.timeIntervalSinceReferenceDate + 978307200.0) * 1000.0).toLong()
    return GeoFix(
        latitude = latLon.first,
        longitude = latLon.second,
        altitude = altitude,
        accuracyM = horizontalAccuracy.toFloat(),
        timestampMs = timestampMs,
        provider = "gps",
        hasAltitude = verticalAccuracy >= 0,
    )
}

private fun isLocationAuthorized(status: CLAuthorizationStatus): Boolean =
    status == kCLAuthorizationStatusAuthorizedAlways ||
        status == kCLAuthorizationStatusAuthorizedWhenInUse

@OptIn(ExperimentalForeignApi::class)
private class IosLocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    var onLocations: ((List<CLLocation>) -> Unit)? = null
    var onAuthChange: (() -> Unit)? = null
    var onHeading: ((Double) -> Unit)? = null

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val locs = didUpdateLocations.mapNotNull { it as? CLLocation }
        if (locs.isNotEmpty()) onLocations?.invoke(locs)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onAuthChange?.invoke()
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) = Unit

    override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
        val heading = didUpdateHeading.trueHeading
        if (heading < 0) return
        val withOffset = normalizeHeadingDegrees(heading + loadCompassHeadingOffset()) ?: return
        onHeading?.invoke(withOffset)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun runOnMain(block: () -> Unit) {
    if (NSThread.isMainThread) {
        block()
    } else {
        dispatch_async(dispatch_get_main_queue(), block)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosLocationGateway : LocationGateway {
    private val delegate = IosLocationDelegate()
    private val manager = CLLocationManager().apply {
        delegate = this@IosLocationGateway.delegate
        desiredAccuracy = kCLLocationAccuracyBestForNavigation
        distanceFilter = kCLDistanceFilterNone
        pausesLocationUpdatesAutomatically = false
        activityType = CLActivityTypeOtherNavigation
    }
    private var askedAlways = false
    private var askedFullAccuracy = false

    private fun applyBackgroundFlag() {
        manager.allowsBackgroundLocationUpdates =
            manager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways
    }

    private fun requestAlwaysIfNeeded() {
        if (askedAlways) return
        if (manager.authorizationStatus != kCLAuthorizationStatusAuthorizedWhenInUse) return
        askedAlways = true
        manager.requestAlwaysAuthorization()
    }

    private fun requestFullAccuracyIfNeeded() {
        if (askedFullAccuracy) return
        askedFullAccuracy = true
        manager.requestTemporaryFullAccuracyAuthorizationWithPurposeKey("FullAccuracyPurpose") { _ ->
            applyBackgroundFlag()
        }
    }

    override suspend fun ensurePermission(): Boolean = withContext(Dispatchers.Main) {
        when (val status = manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse,
            -> {
                requestAlwaysIfNeeded()
                requestFullAccuracyIfNeeded()
                applyBackgroundFlag()
                true
            }
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> false
            kCLAuthorizationStatusNotDetermined -> suspendCancellableCoroutine { cont ->
                delegate.onAuthChange = {
                    val now = manager.authorizationStatus
                    if (now != kCLAuthorizationStatusNotDetermined && cont.isActive) {
                        requestAlwaysIfNeeded()
                        requestFullAccuracyIfNeeded()
                        applyBackgroundFlag()
                        cont.resume(isLocationAuthorized(now))
                    }
                }
                manager.requestWhenInUseAuthorization()
            }
            else -> isLocationAuthorized(status)
        }
    }

    override suspend fun currentFix(): GeoFix? = withContext(Dispatchers.Main) {
        if (!ensurePermission()) return@withContext null
        manager.location?.takeIf { it.horizontalAccuracy >= 0 }?.toGeoFix()
    }

    override fun watchFixes(
        minDistanceM: Float,
        gpsOnly: Boolean,
        onFix: (GeoFix) -> Unit,
    ): () -> Unit {
        runOnMain {
            manager.distanceFilter = kCLDistanceFilterNone
            manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
            applyBackgroundFlag()
            requestFullAccuracyIfNeeded()
            var lastLat: Double? = null
            var lastLon: Double? = null
            delegate.onLocations = handler@{ locs ->
                val best = locs
                    .filter { it.horizontalAccuracy >= 0 }
                    .minByOrNull { it.horizontalAccuracy }
                    ?: return@handler
                val fix = best.toGeoFix()
                if (minDistanceM > 0f && lastLat != null && lastLon != null) {
                    val d = haversineDistanceM(lastLat!!, lastLon!!, fix.latitude, fix.longitude)
                    if (d < minDistanceM) return@handler
                }
                lastLat = fix.latitude
                lastLon = fix.longitude
                onFix(fix)
            }
            manager.startUpdatingLocation()
        }
        return {
            runOnMain {
                manager.stopUpdatingLocation()
                delegate.onLocations = null
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosCompassGateway : CompassGateway {
    private val delegate = IosLocationDelegate()
    private val manager = CLLocationManager().apply {
        delegate = this@IosCompassGateway.delegate
        headingFilter = 1.0
    }

    override fun watchHeading(onHeading: (Double?) -> Unit): () -> Unit {
        if (!CLLocationManager.headingAvailable()) {
            return {}
        }
        delegate.onHeading = onHeading
        manager.startUpdatingHeading()
        return {
            manager.stopUpdatingHeading()
            delegate.onHeading = null
        }
    }
}

actual fun createLocationGateway(): LocationGateway = IosLocationGateway()

actual fun createCompassGateway(): CompassGateway = IosCompassGateway()
