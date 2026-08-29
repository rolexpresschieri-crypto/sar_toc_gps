@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class, BetaInteropApi::class)

package it.ansmi.tocsar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import it.ansmi.tocsar.geo.TrackPoint
import it.ansmi.tocsar.geo.WaypointItem
import it.ansmi.tocsar.geo.splitTrackSegments
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSSelectorFromString
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapTypeSatellite
import platform.MapKit.MKMapTypeStandard
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKOverlayLevelAboveLabels
import platform.MapKit.MKOverlayLevelAboveRoads
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPolyline
import platform.MapKit.MKPolylineRenderer
import platform.MapKit.MKTileOverlay
import platform.MapKit.MKTileOverlayRenderer
import platform.MapKit.MKUserLocation
import platform.MapKit.addOverlay
import platform.MapKit.removeOverlay
import platform.UIKit.UIColor
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIView
import platform.darwin.NSObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

private const val KindGps = "toc.gps"
private const val KindWp = "toc.wp"
private const val KindOp = "toc.op"
private const val KindBase = "toc.base"

private class PolyStyle(
    val overlay: MKPolyline,
    val color: UIColor,
    val width: Double,
)

private class TaggedPin(
    val annotation: MKPointAnnotation,
    val tap: MapOverlayTap,
)

private class IosMapRuntime {
    var onUserGesture: () -> Unit = {}
    var onZoomChanged: (Double) -> Unit = {}
    var onOverlayShare: (MapOverlayTap) -> Unit = {}
    var lastBasemap: MapBasemap? = null
    var lastTrails: Boolean? = null
    var lastOverlayKey: String? = null
    var lastOperatorsKey: String? = null
    var lastLiveTrailSize: Int = -1
    var lastFollowKey: String? = null
    var lastFitKey: String? = null
    var lastHeading: Float = Float.NaN
    var lastPinsStyleKey: String? = null
    var selectedWpNames: Set<String> = emptySet()
    var selectedOpCodes: Set<String> = emptySet()
    var selfSelected: Boolean = false
    var userAdjustedView: Boolean = false
    var tileOverlays: List<MKTileOverlay> = emptyList()
    var trackOverlays: List<MKPolyline> = emptyList()
    var liveTrailOverlays: List<MKPolyline> = emptyList()
    var navLine: MKPolyline? = null
    var measureLine: MKPolyline? = null
    var pinAnnotations: List<TaggedPin> = emptyList()
    var gpsAnnotation: MKPointAnnotation? = null
    var lastSelectAtMs: Long = 0L
    val polyStyles = mutableListOf<PolyStyle>()
    val delegate = IosMapDelegate(this)
    val tapTarget = IosMapTapTarget(this)
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class IosMapTapTarget(
    private val state: IosMapRuntime,
) : NSObject(), UIGestureRecognizerDelegateProtocol {
    var map: MKMapView? = null

    @ObjCAction
    fun handleTap(sender: UITapGestureRecognizer) {
        val mapView = map ?: return
        hitTestPins(mapView, state, sender.locationInView(mapView))
    }

    override fun gestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer,
    ): Boolean = true
}

private class IosMapDelegate(
    private val state: IosMapRuntime,
) : NSObject(), MKMapViewDelegateProtocol {

    override fun mapViewDidChangeVisibleRegion(mapView: MKMapView) {
        if (isUserDrivenGesture(mapView)) {
            state.userAdjustedView = true
            state.onUserGesture()
        }
        reportZoom(mapView, state)
    }

    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
        val overlay = rendererForOverlay
        if (overlay is MKTileOverlay) {
            return MKTileOverlayRenderer(overlay)
        }
        if (overlay is MKPolyline) {
            val style = state.polyStyles.firstOrNull { sameNative(it.overlay, overlay) }
            return MKPolylineRenderer(overlay).apply {
                strokeColor = style?.color ?: UIColor.redColor
                lineWidth = style?.width ?: 4.0
            }
        }
        return MKOverlayRenderer(overlay)
    }

    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        if (viewForAnnotation is MKUserLocation) return null
        val reuse = "toc-pin"
        val view =
            (mapView.dequeueReusableAnnotationViewWithIdentifier(reuse) as? MKMarkerAnnotationView)
                ?: MKMarkerAnnotationView(viewForAnnotation, reuse)
        view.annotation = viewForAnnotation
        view.canShowCallout = false
        view.animatesWhenAdded = false
        view.enabled = true
        view.displayPriority = 1000f
        view.clusteringIdentifier = null
        val kind = (viewForAnnotation as? MKPointAnnotation)?.subtitle
        val title = viewForAnnotation.title
        applyMarkerTint(view, kind, title, state)
        return view
    }

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val ann = didSelectAnnotationView.annotation ?: return
        handleSelect(mapView, ann)
    }

    private fun handleSelect(mapView: MKMapView, ann: MKAnnotationProtocol) {
        mapView.deselectAnnotation(ann, animated = false)
        tapForAnnotation(state, ann)?.let { emitTap(state, it) }
    }
}

actual fun currentFixClock(): String {
    val fmt = NSDateFormatter()
    fmt.dateFormat = "dd/MM/yyyy HH:mm"
    fmt.locale = NSLocale(localeIdentifier = "it_IT")
    return fmt.stringFromDate(NSDate())
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMapLayer(
    basemap: MapBasemap,
    trailsEnabled: Boolean,
    model: GpsMapModel,
    deviceLat: Double?,
    deviceLon: Double?,
    followMode: Boolean,
    mapOrientationDeg: Float,
    onUserGesture: () -> Unit,
    onZoomChanged: (Double) -> Unit,
    onOverlayShare: (MapOverlayTap) -> Unit,
    modifier: Modifier,
) {
    val state = remember { IosMapRuntime() }
    state.onUserGesture = onUserGesture
    state.onZoomChanged = onZoomChanged
    state.onOverlayShare = onOverlayShare

    UIKitView(
        factory = {
            MKMapView().apply {
                delegate = state.delegate
                showsUserLocation = false
                showsCompass = false
                zoomEnabled = true
                scrollEnabled = true
                rotateEnabled = true
                pitchEnabled = false
                state.tapTarget.map = this
                val tap = UITapGestureRecognizer(
                    target = state.tapTarget,
                    action = NSSelectorFromString("handleTap:"),
                )
                tap.cancelsTouchesInView = false
                tap.delegate = state.tapTarget
                addGestureRecognizer(tap)
            }
        },
        modifier = modifier,
        update = { map ->
            applyTiles(map, state, basemap, trailsEnabled)

            val overlayKey = staticOverlayKey(model)
            if (overlayKey != state.lastOverlayKey) {
                rebuildStaticOverlays(map, state, model)
                state.lastOverlayKey = overlayKey
                state.userAdjustedView = false
                state.lastFitKey = null
                state.lastLiveTrailSize = -1
                state.lastOperatorsKey = null
            }

            updateLiveTrail(map, state, model)
            updateOnlinePins(map, state, model)
            updateNavAndMeasure(map, state, model, deviceLat, deviceLon)
            updateGpsPin(map, state, model, followMode, deviceLat, deviceLon)
            applyHeading(map, state, mapOrientationDeg)
            applyFollowOrFit(map, state, model, followMode, deviceLat, deviceLon)
            applySelectionStyle(map, state, model)
            reportZoom(map, state)
        },
        onRelease = { map ->
            map.delegate = null
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = false,
        ),
    )
}

private fun applyMarkerTint(
    view: MKMarkerAnnotationView,
    kind: String?,
    title: String?,
    state: IosMapRuntime,
) {
    val yellow = UIColor.colorWithRed(1.0, 0.922, 0.231, alpha = 1.0)
    val selected =
        when (kind) {
            KindGps -> state.selfSelected
            KindWp, KindBase -> title?.uppercase() in state.selectedWpNames
            KindOp -> title?.uppercase() in state.selectedOpCodes
            else -> false
        }
    when (kind) {
        KindGps -> {
            view.markerTintColor =
                if (selected) yellow else UIColor.colorWithRed(0.808, 0.169, 0.216, alpha = 1.0)
            view.glyphText = "▲"
        }
        KindBase, KindWp -> {
            view.markerTintColor =
                if (selected) yellow else UIColor.colorWithRed(0.0, 0.573, 0.275, alpha = 1.0)
            view.glyphText = "⌂"
        }
        KindOp -> {
            view.markerTintColor =
                if (selected) yellow else UIColor.colorWithRed(0.027, 0.608, 0.259, alpha = 1.0)
            view.glyphText = title?.take(3)
        }
        else -> view.markerTintColor = UIColor.blueColor
    }
}

private fun applySelectionStyle(map: MKMapView, state: IosMapRuntime, model: GpsMapModel) {
    val wp = model.selectedWaypointNames()
    val opCodes =
        model.liveOperators
            .filter { it.sessionId in model.selectedOperatorSessionIds() }
            .map { it.operatorCode.uppercase() }
            .toSet()
    val self = model.measureA is MapMeasurePoint.Self || model.measureB is MapMeasurePoint.Self
    val key = "${wp.joinToString()}:${opCodes.joinToString()}:$self"
    if (key == state.lastPinsStyleKey) return
    state.lastPinsStyleKey = key
    state.selectedWpNames = wp
    state.selectedOpCodes = opCodes
    state.selfSelected = self
    map.annotations.forEach { raw ->
        val ann = raw as? MKPointAnnotation ?: return@forEach
        val view = map.viewForAnnotation(ann) as? MKMarkerAnnotationView ?: return@forEach
        applyMarkerTint(view, ann.subtitle, ann.title, state)
    }
}

private fun tileOverlay(template: String, maxZ: Long, replace: Boolean): MKTileOverlay =
    MKTileOverlay(uRLTemplate = template).apply {
        setCanReplaceMapContent(replace)
        setMaximumZ(maxZ)
    }

@OptIn(ExperimentalForeignApi::class)
private fun applyTiles(
    map: MKMapView,
    state: IosMapRuntime,
    basemap: MapBasemap,
    trailsEnabled: Boolean,
) {
    if (state.lastBasemap == basemap && state.lastTrails == trailsEnabled) return
    state.tileOverlays.forEach { map.removeOverlay(it) }
    when (basemap) {
        MapBasemap.Streets -> map.mapType = MKMapTypeStandard
        MapBasemap.Satellite -> map.mapType = MKMapTypeSatellite
        MapBasemap.Topographic -> map.mapType = MKMapTypeStandard
    }
    val next = mutableListOf<MKTileOverlay>()
    if (basemap == MapBasemap.Topographic) {
        next.add(tileOverlay("https://a.tile.opentopomap.org/{z}/{x}/{y}.png", 17, true))
    }
    if (trailsEnabled) {
        next.add(tileOverlay("https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png", 18, false))
    }
    // Tiles sotto strade/etichette; polilinee sopra.
    next.forEach { map.addOverlay(it, level = MKOverlayLevelAboveRoads) }
    state.tileOverlays = next
    state.lastBasemap = basemap
    state.lastTrails = trailsEnabled
}

private fun staticOverlayKey(model: GpsMapModel): String =
    buildString {
        append(model.baseLat).append(',').append(model.baseLon).append('|')
        append(model.baseLabel).append('|')
        append(model.overlayWaypoints.joinToString { it.name }).append('|')
        append(model.overlayTracks.joinToString { "${it.name}:${it.points.size}" })
    }

private fun operatorsKey(model: GpsMapModel): String =
    model.liveOperators.joinToString("|") {
        "${it.sessionId}:${(it.latitude * 1e5).toInt()}:${(it.longitude * 1e5).toInt()}:${it.mapColorHex}"
    }

@OptIn(ExperimentalForeignApi::class)
private fun rebuildStaticOverlays(map: MKMapView, state: IosMapRuntime, model: GpsMapModel) {
    val oldTracks = state.trackOverlays
    oldTracks.forEach { map.removeOverlay(it) }
    state.polyStyles.removeAll { style -> oldTracks.any { sameNative(it, style.overlay) } }
    state.trackOverlays = emptyList()
    state.pinAnnotations.forEach { map.removeAnnotation(it.annotation) }
    state.pinAnnotations = emptyList()

    val tracks = mutableListOf<MKPolyline>()
    for (t in model.overlayTracks) {
        val color = colorFromHex(t.colorHex, fallback = 0xC62828)
        for (segment in splitTrackSegments(t.points)) {
            val line = polylineOf(segment) ?: continue
            registerPoly(state, line, color, 5.0)
            map.addOverlay(line, level = MKOverlayLevelAboveLabels)
            tracks.add(line)
        }
    }
    state.trackOverlays = tracks

    val pins = mutableListOf<TaggedPin>()
    val baseLat = model.baseLat
    val baseLon = model.baseLon
    if (baseLat != null && baseLon != null) {
        val label = model.baseLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "BASE"
        val wp = WaypointItem(label, baseLat, baseLon, null)
        pins.add(makePin(label, baseLat, baseLon, KindBase, MapOverlayTap.Waypoint(wp)))
    }
    for (w in model.overlayWaypoints) {
        if (baseLat != null && baseLon != null &&
            abs(w.lat - baseLat) < 1e-6 &&
            abs(w.lon - baseLon) < 1e-6
        ) {
            continue
        }
        pins.add(makePin(w.name, w.lat, w.lon, KindWp, MapOverlayTap.Waypoint(w)))
    }
    pins.forEach { map.addAnnotation(it.annotation) }
    state.pinAnnotations = pins
}

private fun registerPoly(state: IosMapRuntime, overlay: MKPolyline, color: UIColor, width: Double) {
    state.polyStyles.removeAll { sameNative(it.overlay, overlay) }
    state.polyStyles.add(PolyStyle(overlay, color, width))
}

private fun makePin(
    title: String,
    lat: Double,
    lon: Double,
    kind: String,
    tap: MapOverlayTap,
): TaggedPin {
    val annotation = MKPointAnnotation()
    annotation.setCoordinate(CLLocationCoordinate2DMake(lat, lon))
    annotation.setTitle(title)
    annotation.setSubtitle(kind)
    return TaggedPin(annotation, tap)
}

@OptIn(ExperimentalForeignApi::class)
private fun updateLiveTrail(map: MKMapView, state: IosMapRuntime, model: GpsMapModel) {
    val n = model.liveTrail.size
    if (n == state.lastLiveTrailSize) return
    state.lastLiveTrailSize = n
    state.liveTrailOverlays.forEach { map.removeOverlay(it) }
    state.polyStyles.removeAll { style -> state.liveTrailOverlays.any { sameNative(it, style.overlay) } }
    val color = colorFromHex("#CE2B37", fallback = 0xCE2B37)
    val next = mutableListOf<MKPolyline>()
    for (segment in splitTrackSegments(model.liveTrail).filter { it.size >= 2 }) {
        val line = polylineOf(segment) ?: continue
        registerPoly(state, line, color, 4.0)
        map.addOverlay(line, level = MKOverlayLevelAboveLabels)
        next.add(line)
    }
    state.liveTrailOverlays = next
}

@OptIn(ExperimentalForeignApi::class)
private fun updateOnlinePins(map: MKMapView, state: IosMapRuntime, model: GpsMapModel) {
    val key = operatorsKey(model)
    if (key == state.lastOperatorsKey) return
    state.lastOperatorsKey = key
    val kept = state.pinAnnotations.filter { it.tap !is MapOverlayTap.Operator }
    val removed = state.pinAnnotations.filter { it.tap is MapOverlayTap.Operator }
    removed.forEach { map.removeAnnotation(it.annotation) }
    val added = model.liveOperators.map { op ->
        makePin(
            op.operatorCode,
            op.latitude,
            op.longitude,
            KindOp,
            MapOverlayTap.Operator(op),
        )
    }
    added.forEach { map.addAnnotation(it.annotation) }
    state.pinAnnotations = kept + added
}

@OptIn(ExperimentalForeignApi::class)
private fun updateNavAndMeasure(
    map: MKMapView,
    state: IosMapRuntime,
    model: GpsMapModel,
    deviceLat: Double?,
    deviceLon: Double?,
) {
    state.navLine?.let { map.removeOverlay(it) }
    state.polyStyles.removeAll { sameNative(it.overlay, state.navLine) }
    state.navLine = null
    val baseLat = model.baseLat
    val baseLon = model.baseLon
    if (baseLat != null && baseLon != null && deviceLat != null && deviceLon != null) {
        val line =
            polylineOf(
                listOf(
                    TrackPoint(deviceLat, deviceLon),
                    TrackPoint(baseLat, baseLon),
                ),
            )
        if (line != null) {
            registerPoly(state, line, colorFromHex("#1565C0", 0x1565C0), 3.5)
            map.addOverlay(line, level = MKOverlayLevelAboveLabels)
            state.navLine = line
        }
    }

    state.measureLine?.let { map.removeOverlay(it) }
    state.polyStyles.removeAll { sameNative(it.overlay, state.measureLine) }
    state.measureLine = null
    val a = model.measureA
    val b = model.measureB
    if (a != null && b != null) {
        val line =
            polylineOf(
                listOf(
                    TrackPoint(a.latitude, a.longitude),
                    TrackPoint(b.latitude, b.longitude),
                ),
            )
        if (line != null) {
            registerPoly(state, line, colorFromHex("#FFEB3B", 0xFFEB3B), 3.0)
            map.addOverlay(line, level = MKOverlayLevelAboveLabels)
            state.measureLine = line
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun updateGpsPin(
    map: MKMapView,
    state: IosMapRuntime,
    model: GpsMapModel,
    followMode: Boolean,
    deviceLat: Double?,
    deviceLon: Double?,
) {
    if (followMode || deviceLat == null || deviceLon == null) {
        state.gpsAnnotation?.let { map.removeAnnotation(it) }
        state.gpsAnnotation = null
        return
    }
    val existing = state.gpsAnnotation
    if (existing != null) {
        existing.setCoordinate(CLLocationCoordinate2DMake(deviceLat, deviceLon))
        existing.setTitle(model.navigatorLabel.trim().ifBlank { "GPS" })
        return
    }
    val pin = MKPointAnnotation()
    pin.setCoordinate(CLLocationCoordinate2DMake(deviceLat, deviceLon))
    pin.setTitle(model.navigatorLabel.trim().ifBlank { "GPS" })
    pin.setSubtitle(KindGps)
    state.gpsAnnotation = pin
    map.addAnnotation(pin)
}

@OptIn(ExperimentalForeignApi::class)
private fun applyHeading(map: MKMapView, state: IosMapRuntime, mapOrientationDeg: Float) {
    if (state.lastHeading == mapOrientationDeg) return
    state.lastHeading = mapOrientationDeg
    val heading = ((-mapOrientationDeg.toDouble()) % 360.0 + 360.0) % 360.0
    val camera = map.camera
    if (abs(camera.heading - heading) < 0.8) return
    camera.heading = heading
    map.setCamera(camera, animated = false)
}

@OptIn(ExperimentalForeignApi::class)
private fun applyFollowOrFit(
    map: MKMapView,
    state: IosMapRuntime,
    model: GpsMapModel,
    followMode: Boolean,
    deviceLat: Double?,
    deviceLon: Double?,
) {
    if (followMode && deviceLat != null && deviceLon != null) {
        val key = "${(deviceLat * 1e5).toInt()}:${(deviceLon * 1e5).toInt()}"
        if (key != state.lastFollowKey) {
            map.setCenterCoordinate(CLLocationCoordinate2DMake(deviceLat, deviceLon), animated = true)
            state.lastFollowKey = key
        }
        return
    }
    state.lastFollowKey = null
    fitIfNeeded(map, state, model, deviceLat, deviceLon)
}

@OptIn(ExperimentalForeignApi::class)
private fun fitIfNeeded(
    map: MKMapView,
    state: IosMapRuntime,
    model: GpsMapModel,
    deviceLat: Double?,
    deviceLon: Double?,
) {
    val fitKey = staticOverlayKey(model)
    if (state.lastFitKey == fitKey) return
    if (state.userAdjustedView && state.lastFitKey != null) return

    val hasOverlays = model.overlayWaypoints.isNotEmpty() || model.overlayTracks.isNotEmpty()
    val coords = mutableListOf<Pair<Double, Double>>()
    if (hasOverlays) {
        model.overlayWaypoints.forEach { coords.add(it.lat to it.lon) }
        model.overlayTracks.forEach { t -> t.points.forEach { coords.add(it.lat to it.lon) } }
    } else {
        if (model.baseLat != null && model.baseLon != null) {
            coords.add(model.baseLat!! to model.baseLon!!)
        }
        if (deviceLat != null && deviceLon != null) {
            coords.add(deviceLat to deviceLon)
        }
        model.liveTrail.forEach { coords.add(it.lat to it.lon) }
    }
    if (coords.isEmpty()) {
        state.lastFitKey = fitKey
        return
    }
    if (coords.size == 1) {
        map.setRegion(
            MKCoordinateRegionMakeWithDistance(
                CLLocationCoordinate2DMake(coords.first().first, coords.first().second),
                1200.0,
                1200.0,
            ),
            animated = false,
        )
        state.lastFitKey = fitKey
        return
    }

    val minLat = coords.minOf { it.first }
    val maxLat = coords.maxOf { it.first }
    val minLon = coords.minOf { it.second }
    val maxLon = coords.maxOf { it.second }
    val latDelta = ((maxLat - minLat) * 1.5).coerceAtLeast(0.004)
    val lonDelta = ((maxLon - minLon) * 1.5).coerceAtLeast(0.004)
    val center = CLLocationCoordinate2DMake((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
    map.setRegion(
        MKCoordinateRegionMake(
            center,
            MKCoordinateSpanMake(latDelta, lonDelta),
        ),
        animated = false,
    )
    state.lastFitKey = fitKey
}

private fun polylineOf(points: List<TrackPoint>): MKPolyline? {
    if (points.size < 2) return null
    return memScoped {
        val arr = allocArray<CLLocationCoordinate2D>(points.size)
        val stride = sizeOf<CLLocationCoordinate2D>()
        points.forEachIndexed { i, p ->
            val dest = interpretCPointer<CLLocationCoordinate2D>(arr.rawValue + stride * i)
                ?: return@forEachIndexed
            val src = CLLocationCoordinate2DMake(p.lat, p.lon)
            platform.posix.memcpy(
                dest,
                src.getPointer(this),
                sizeOf<CLLocationCoordinate2D>().toULong(),
            )
        }
        MKPolyline.polylineWithCoordinates(arr, count = points.size.toULong())
    }
}

private fun colorFromHex(hex: String, fallback: Long): UIColor {
    val h = hex.trim().removePrefix("#")
    val v = h.toLongOrNull(16) ?: fallback
    val r = ((v shr 16) and 0xFF) / 255.0
    val g = ((v shr 8) and 0xFF) / 255.0
    val b = (v and 0xFF) / 255.0
    return UIColor.colorWithRed(r, green = g, blue = b, alpha = 1.0)
}

@OptIn(ExperimentalForeignApi::class)
private fun reportZoom(map: MKMapView, state: IosMapRuntime) {
    val lonDelta = map.region.useContents { span.longitudeDelta }
    if (lonDelta <= 0.0 || !lonDelta.isFinite()) return
    val zoom = (ln(360.0 / lonDelta) / ln(2.0)).coerceIn(1.0, 20.0)
    state.onZoomChanged(zoom)
}

private fun isUserDrivenGesture(map: MKMapView): Boolean {
    val roots = mutableListOf<UIView>(map)
    map.subviews.forEach { child -> (child as? UIView)?.let { roots.add(it) } }
    for (v in roots) {
        val recognizers = v.gestureRecognizers ?: continue
        for (raw in recognizers) {
            val gr = raw as? platform.UIKit.UIGestureRecognizer ?: continue
            val st = gr.state
            if (st == UIGestureRecognizerStateBegan || st == UIGestureRecognizerStateChanged) {
                return true
            }
        }
    }
    return false
}

private fun emitTap(state: IosMapRuntime, tap: MapOverlayTap) {
    val now = (NSDate().timeIntervalSinceReferenceDate * 1000.0).toLong()
    if (now - state.lastSelectAtMs < 350L) return
    state.lastSelectAtMs = now
    state.onOverlayShare(tap)
}

@OptIn(ExperimentalForeignApi::class)
private fun hitTestPins(
    map: MKMapView,
    state: IosMapRuntime,
    point: kotlinx.cinterop.CValue<CGPoint>,
) {
    val tap = point.useContents { x to y }
    var bestTap: MapOverlayTap? = null
    var bestD = 56.0

    fun consider(ann: MKPointAnnotation, action: MapOverlayTap) {
        val p = map.convertCoordinate(ann.coordinate, toPointToView = map)
        val d = p.useContents {
            hypot(x - tap.first, y - tap.second)
        }
        if (d < bestD) {
            bestD = d
            bestTap = action
        }
    }

    state.gpsAnnotation?.let { consider(it, MapOverlayTap.Self) }
    state.pinAnnotations.forEach { consider(it.annotation, it.tap) }
    bestTap?.let { emitTap(state, it) }
}

private fun tapForAnnotation(state: IosMapRuntime, ann: MKAnnotationProtocol): MapOverlayTap? {
    state.gpsAnnotation?.let { gps ->
        if (sameNative(ann, gps) || annotationLooksLike(ann, gps)) return MapOverlayTap.Self
    }
    state.pinAnnotations.firstOrNull { pin ->
        sameNative(ann, pin.annotation) || annotationLooksLike(ann, pin.annotation)
    }?.let { return it.tap }
    return null
}

@OptIn(ExperimentalForeignApi::class)
private fun annotationLooksLike(a: MKAnnotationProtocol, b: MKPointAnnotation): Boolean {
    if (a.title != b.title) return false
    val subA = (a as? MKPointAnnotation)?.subtitle
    if (subA != b.subtitle) return false
    val ca = a.coordinate.useContents { latitude to longitude }
    val cb = b.coordinate.useContents { latitude to longitude }
    return abs(ca.first - cb.first) < 1e-6 && abs(ca.second - cb.second) < 1e-6
}

private fun sameNative(a: Any?, b: Any?): Boolean {
    if (a == null || b == null) return false
    val oa = a as? NSObject ?: return false
    val ob = b as? NSObject ?: return false
    return oa.objcPtr() == ob.objcPtr()
}
