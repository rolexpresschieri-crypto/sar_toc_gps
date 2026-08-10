package it.ansmi.tocsar.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.preference.PreferenceManager
import android.util.TypedValue
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import it.ansmi.tocsar.AndroidAppContext
import it.ansmi.tocsar.geo.MapTrackOverlay
import it.ansmi.tocsar.geo.WaypointItem
import it.ansmi.tocsar.geo.splitTrackSegments
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NavLineId = "NAV_TO_BASE"
private const val GpsMarkerId = "GPS_FIX"
private const val BaseMarkerId = "BASE_TARGET"
private const val LiveTrailId = "TRK_LIVE"

actual fun currentFixClock(): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date())

private val OpenTopoSource = XYTileSource(
    "OpenTopoMap",
    0,
    17,
    256,
    ".png",
    arrayOf(
        "https://a.tile.opentopomap.org/",
        "https://b.tile.opentopomap.org/",
        "https://c.tile.opentopomap.org/",
    ),
    "© OpenTopoMap (CC-BY-SA)",
)

private val EsriImagerySource = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    0,
    19,
    256,
    "",
    arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "© Esri",
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$baseUrl$z/$y/$x"
    }
}

private val WaymarkedHikingSource = XYTileSource(
    "WaymarkedHiking",
    0,
    18,
    256,
    ".png",
    arrayOf("https://tile.waymarkedtrails.org/hiking/"),
    "© waymarkedtrails.org",
)

private class MapRuntimeState {
    var lastBasemap: MapBasemap? = null
    var lastTrails: Boolean? = null
    var lastOverlayKey: String? = null
    var lastOrientation: Float = Float.NaN
    var lastDeviceKey: String? = null
    var lastZoomReported: Double = Double.NaN
    var lastFitKey: String? = null
    var userAdjustedView: Boolean = false
    var lastLiveTrailSize: Int = 0
    var lastLiveOperatorsKey: String? = null
    var navLine: Polyline? = null
    var liveTrailLines: MutableList<Polyline> = mutableListOf()
    var operatorMarkers: MutableList<Marker> = mutableListOf()
    var gpsMarker: Marker? = null
    var baseMarker: Marker? = null
    var onOverlayShare: ((MapOverlayTap) -> Unit)? = null
}

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
    remember(Unit) {
        val appCtx = AndroidAppContext.require().applicationContext
        Configuration.getInstance().load(
            appCtx,
            PreferenceManager.getDefaultSharedPreferences(appCtx),
        )
        Configuration.getInstance().userAgentValue = appCtx.packageName
        true
    }

    val trailsOverlayRef = remember { arrayOfNulls<TilesOverlay>(1) }
    val state = remember { MapRuntimeState() }
    state.onOverlayShare = onOverlayShare

    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { map ->
                map.setTileSource(TileSourceFactory.MAPNIK)
                map.setMultiTouchControls(true)
                map.controller.setZoom(14.0)
                map.isTilesScaledToDpi = true
                map.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_MOVE -> {
                            if (event.pointerCount == 1 || event.pointerCount >= 2) {
                                state.userAdjustedView = true
                                onUserGesture()
                            }
                        }
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            if (event.pointerCount >= 2) {
                                state.userAdjustedView = true
                                onUserGesture()
                            }
                        }
                    }
                    false
                }
                map.addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        reportZoom(map, state, onZoomChanged)
                        return false
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        reportZoom(map, state, onZoomChanged)
                        return false
                    }
                })
                val trailsProvider = MapTileProviderBasic(ctx.applicationContext, WaymarkedHikingSource)
                trailsOverlayRef[0] = TilesOverlay(trailsProvider, ctx).apply {
                    loadingBackgroundColor = AndroidColor.TRANSPARENT
                    loadingLineColor = AndroidColor.TRANSPARENT
                }
                rebuildOverlays(
                    map, trailsOverlayRef[0], basemap, trailsEnabled, model,
                    deviceLat, deviceLon, followMode, state,
                )
                state.lastBasemap = basemap
                state.lastTrails = trailsEnabled
                state.lastOverlayKey = staticOverlayKey(model)
                applyLive(map, followMode, deviceLat, deviceLon, mapOrientationDeg, model, state)
                scheduleFit(map, model, deviceLat, deviceLon, state, onZoomChanged)
            }
        },
        update = { map ->
            state.onOverlayShare = onOverlayShare
            val key = staticOverlayKey(model)
            val needRebuild =
                state.lastBasemap != basemap ||
                    state.lastTrails != trailsEnabled ||
                    state.lastOverlayKey != key

            if (needRebuild) {
                rebuildOverlays(
                    map, trailsOverlayRef[0], basemap, trailsEnabled, model,
                    deviceLat, deviceLon, followMode, state,
                )
                state.lastBasemap = basemap
                state.lastTrails = trailsEnabled
                state.lastOverlayKey = key
                state.userAdjustedView = false
                state.lastFitKey = null
                scheduleFit(map, model, deviceLat, deviceLon, state, onZoomChanged)
            }

            applyLive(map, followMode, deviceLat, deviceLon, mapOrientationDeg, model, state)
            reportZoom(map, state, onZoomChanged)
        },
        modifier = modifier,
    )
}

/** Chiave overlay statici (WP/TRK salvati, BASE) — esclude liveTrail e operatori online. */
private fun staticOverlayKey(model: GpsMapModel): String =
    buildString {
        append(model.baseLat).append(',').append(model.baseLon).append('|')
        append(model.baseLabel).append('|')
        append(model.navigatorLabel).append('|')
        append(model.overlayWaypoints.joinToString { it.name }).append('|')
        append(model.overlayTracks.joinToString { "${it.name}:${it.points.size}" })
    }

private fun liveOperatorsKey(model: GpsMapModel): String =
    model.liveOperators.joinToString("|") {
        "${it.sessionId}:${(it.latitude * 1e5).toInt()}:${(it.longitude * 1e5).toInt()}:${it.mapColorHex}"
    }

private fun rebuildOverlays(
    map: MapView,
    trailsOverlay: TilesOverlay?,
    basemap: MapBasemap,
    trailsEnabled: Boolean,
    model: GpsMapModel,
    deviceLat: Double?,
    deviceLon: Double?,
    followMode: Boolean,
    state: MapRuntimeState,
) {
    when (basemap) {
        MapBasemap.Streets -> map.setTileSource(TileSourceFactory.MAPNIK)
        MapBasemap.Topographic -> map.setTileSource(OpenTopoSource)
        MapBasemap.Satellite -> map.setTileSource(EsriImagerySource)
    }

    map.overlays.clear()
    state.navLine = null
    state.gpsMarker = null
    state.baseMarker = null
    state.liveTrailLines.clear()
    state.operatorMarkers.clear()
    state.lastLiveTrailSize = -1
    state.lastLiveOperatorsKey = null

    if (trailsEnabled && trailsOverlay != null) {
        map.overlays.add(trailsOverlay)
    }

    val baseLat = model.baseLat
    val baseLon = model.baseLon
    if (baseLat != null && baseLon != null) {
        val label = model.baseLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "BASE"
        val home = createHomeTargetBitmap(map, label)
        val baseWp = WaypointItem(label, baseLat, baseLon, null)
        val marker = Marker(map).apply {
            position = GeoPoint(baseLat, baseLon)
            title = BaseMarkerId
            relatedObject = baseWp
            setAnchor(Marker.ANCHOR_CENTER, home.hotV)
            icon = BitmapDrawable(map.context.resources, home.bitmap)
            setInfoWindow(null)
            setOnMarkerClickListener { m, _ ->
                val wp = m.relatedObject as? WaypointItem
                if (wp != null) state.onOverlayShare?.invoke(MapOverlayTap.Waypoint(wp))
                true
            }
        }
        state.baseMarker = marker
        map.overlays.add(marker)
    }

    for (w in model.overlayWaypoints) {
        // WP overlay: stesso stile casa solo se non coincide col target BASE
        if (baseLat != null && baseLon != null &&
            kotlin.math.abs(w.lat - baseLat) < 1e-6 &&
            kotlin.math.abs(w.lon - baseLon) < 1e-6
        ) {
            continue
        }
        val home = createHomeTargetBitmap(map, w.name)
        map.overlays.add(
            Marker(map).apply {
                position = GeoPoint(w.lat, w.lon)
                title = w.name
                relatedObject = w
                setAnchor(Marker.ANCHOR_CENTER, home.hotV)
                icon = BitmapDrawable(map.context.resources, home.bitmap)
                setInfoWindow(null)
                setOnMarkerClickListener { m, _ ->
                    val wp = m.relatedObject as? WaypointItem
                    if (wp != null) state.onOverlayShare?.invoke(MapOverlayTap.Waypoint(wp))
                    true
                }
            },
        )
    }

    for (t in model.overlayTracks) {
        val stroke = try {
            AndroidColor.parseColor(t.colorHex)
        } catch (_: Exception) {
            AndroidColor.parseColor("#C62828")
        }
        for (segment in splitTrackSegments(t.points)) {
            if (segment.size < 2) continue
            map.overlays.add(
                Polyline().apply {
                    setPoints(segment.map { GeoPoint(it.lat, it.lon) })
                    outlinePaint.color = stroke
                    outlinePaint.strokeWidth = 14f
                    title = t.name
                    relatedObject = t
                    setOnClickListener { poly, _, _ ->
                        val trk = poly.relatedObject as? MapTrackOverlay
                        if (trk != null) {
                            state.onOverlayShare?.invoke(MapOverlayTap.Track(trk.name, trk.points))
                        }
                        true
                    }
                },
            )
        }
    }

    if (baseLat != null && baseLon != null && deviceLat != null && deviceLon != null) {
        val line = Polyline().apply {
            title = NavLineId
            outlinePaint.color = AndroidColor.parseColor("#1565C0")
            outlinePaint.strokeWidth = 10f
            setPoints(listOf(GeoPoint(deviceLat, deviceLon), GeoPoint(baseLat, baseLon)))
        }
        state.navLine = line
        map.overlays.add(line)
    }

    // Fix sulla posizione geografica (anche dopo pan) — non il pin default osmdroid
    if (!followMode && deviceLat != null && deviceLon != null) {
        ensureGpsMarker(map, state, model.navigatorLabel, deviceLat, deviceLon)
    }

    updateOnlineOperatorMarkers(map, model, state)

    map.invalidate()
}

private fun ensureGpsMarker(
    map: MapView,
    state: MapRuntimeState,
    label: String,
    lat: Double,
    lon: Double,
) {
    val icon = createGpsFixBitmap(map, label)
    val existing = state.gpsMarker
    if (existing != null && map.overlays.contains(existing)) {
        existing.position = GeoPoint(lat, lon)
        existing.setAnchor(Marker.ANCHOR_CENTER, icon.hotV)
        existing.icon = BitmapDrawable(map.context.resources, icon.bitmap)
        return
    }
    val marker = Marker(map).apply {
        position = GeoPoint(lat, lon)
        title = GpsMarkerId
        setAnchor(Marker.ANCHOR_CENTER, icon.hotV)
        this.icon = BitmapDrawable(map.context.resources, icon.bitmap)
        setInfoWindow(null)
    }
    state.gpsMarker = marker
    map.overlays.add(marker)
}

private fun scheduleFit(
    map: MapView,
    model: GpsMapModel,
    deviceLat: Double?,
    deviceLon: Double?,
    state: MapRuntimeState,
    onZoomChanged: (Double) -> Unit,
) {
    val fitKey = staticOverlayKey(model)
    val run = Runnable {
        if (state.userAdjustedView && state.lastFitKey == fitKey && map.width > 0) return@Runnable
        if (state.lastFitKey == fitKey && map.width > 0) return@Runnable
        if (map.width <= 0 || map.height <= 0) {
            map.post { scheduleFit(map, model, deviceLat, deviceLon, state, onZoomChanged) }
            return@Runnable
        }
        fitToContent(map, model, deviceLat, deviceLon)
        state.lastFitKey = fitKey
        reportZoom(map, state, onZoomChanged)
    }
    map.post(run)
}

/** Inquadra WP/TRK overlay (come TocAppBuild); altrimenti base/device/live. */
private fun fitToContent(
    map: MapView,
    model: GpsMapModel,
    deviceLat: Double?,
    deviceLon: Double?,
) {
    val hasOverlays = model.overlayWaypoints.isNotEmpty() || model.overlayTracks.isNotEmpty()
    val pts = mutableListOf<GeoPoint>()
    if (hasOverlays) {
        model.overlayWaypoints.forEach { pts.add(GeoPoint(it.lat, it.lon)) }
        model.overlayTracks.forEach { t -> t.points.forEach { pts.add(GeoPoint(it.lat, it.lon)) } }
    } else {
        if (model.baseLat != null && model.baseLon != null) {
            pts.add(GeoPoint(model.baseLat, model.baseLon))
        }
        if (deviceLat != null && deviceLon != null) {
            pts.add(GeoPoint(deviceLat, deviceLon))
        }
        model.liveTrail.forEach { pts.add(GeoPoint(it.lat, it.lon)) }
    }

    when {
        pts.size >= 2 -> {
            val box = BoundingBox.fromGeoPoints(pts)
            val latSpan = kotlin.math.abs(box.latNorth - box.latSouth)
            val lonSpan = kotlin.math.abs(box.lonEast - box.lonWest)
            // Traccia corta / punti vicini: evita bbox degenere → zoom mondiale
            if (latSpan < 1e-5 && lonSpan < 1e-5) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(pts.first())
            } else {
                try {
                    map.zoomToBoundingBox(box.increaseByScale(1.4f), false, 80)
                } catch (_: Exception) {
                    map.controller.setZoom(15.0)
                    map.controller.setCenter(pts.first())
                }
                if (map.zoomLevelDouble < 11.0) {
                    map.controller.setZoom(15.0)
                    map.controller.setCenter(pts.first())
                }
            }
        }
        pts.size == 1 -> {
            map.controller.setZoom(15.0)
            map.controller.setCenter(pts.first())
        }
        else -> {
            map.controller.setZoom(12.0)
            map.controller.setCenter(GeoPoint(45.07, 7.68))
        }
    }
}

private fun applyLive(
    map: MapView,
    followMode: Boolean,
    deviceLat: Double?,
    deviceLon: Double?,
    mapOrientationDeg: Float,
    model: GpsMapModel,
    state: MapRuntimeState,
) {
    if (state.lastOrientation != mapOrientationDeg) {
        map.mapOrientation = mapOrientationDeg
        state.lastOrientation = mapOrientationDeg
    }

    updateLiveTrail(map, model, state)
    updateOnlineOperatorMarkers(map, model, state)

    val baseLat = model.baseLat
    val baseLon = model.baseLon
    if (baseLat != null && baseLon != null && deviceLat != null && deviceLon != null) {
        val line = state.navLine
        if (line != null) {
            line.setPoints(listOf(GeoPoint(deviceLat, deviceLon), GeoPoint(baseLat, baseLon)))
        } else {
            val created = Polyline().apply {
                title = NavLineId
                outlinePaint.color = AndroidColor.parseColor("#1565C0")
                outlinePaint.strokeWidth = 10f
                setPoints(listOf(GeoPoint(deviceLat, deviceLon), GeoPoint(baseLat, baseLon)))
            }
            state.navLine = created
            map.overlays.add(created)
        }
    }

    if (deviceLat != null && deviceLon != null) {
        if (followMode) {
            // Follow: freccia Compose al centro — togli marker geografico se presente
            state.gpsMarker?.let { m ->
                map.overlays.remove(m)
                state.gpsMarker = null
            }
            val deviceKey = "${(deviceLat * 1e5).toInt()}:${(deviceLon * 1e5).toInt()}:F"
            if (state.lastDeviceKey != deviceKey) {
                map.controller.setCenter(GeoPoint(deviceLat, deviceLon))
                state.lastDeviceKey = deviceKey
            }
        } else {
            // Pan: fix resta sulla posizione reale (freccia+nome come TocAppBuild)
            ensureGpsMarker(map, state, model.navigatorLabel, deviceLat, deviceLon)
            state.lastDeviceKey = "${(deviceLat * 1e5).toInt()}:${(deviceLon * 1e5).toInt()}:P"
        }
    }

    map.invalidate()
}

/** Operatori online: aggiorna pin senza rebuild WP/TRK / senza refit zoom. */
private fun updateOnlineOperatorMarkers(
    map: MapView,
    model: GpsMapModel,
    state: MapRuntimeState,
) {
    val key = liveOperatorsKey(model)
    if (key == state.lastLiveOperatorsKey) return
    state.lastLiveOperatorsKey = key

    state.operatorMarkers.forEach { map.overlays.remove(it) }
    state.operatorMarkers.clear()

    for (op in model.liveOperators) {
        val pin = createOperatorPinBitmap(map, op.operatorCode, op.mapColorHex)
        val marker = Marker(map).apply {
            position = GeoPoint(op.latitude, op.longitude)
            title = op.operatorCode
            relatedObject = op
            setAnchor(Marker.ANCHOR_CENTER, pin.hotV)
            icon = BitmapDrawable(map.context.resources, pin.bitmap)
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ -> true }
        }
        state.operatorMarkers.add(marker)
        map.overlays.add(marker)
    }
}

/** TRK live: aggiorna polyline senza rebuild/refit (evita reset zoom). Supporta buchi GPS. */
private fun updateLiveTrail(
    map: MapView,
    model: GpsMapModel,
    state: MapRuntimeState,
) {
    val n = model.liveTrail.size
    if (n == state.lastLiveTrailSize) return

    val segments = splitTrackSegments(model.liveTrail).filter { it.size >= 2 }
    val prevCount = state.liveTrailLines.size

    // Stesso numero di tratti: aggiorna solo l’ultimo (punto nuovo sulla stessa linea)
    if (segments.size == prevCount && prevCount > 0 && n > state.lastLiveTrailSize) {
        val lastSeg = segments.last()
        val lastLine = state.liveTrailLines.last()
        if (map.overlays.contains(lastLine)) {
            lastLine.setPoints(lastSeg.map { GeoPoint(it.lat, it.lon) })
            lastLine.relatedObject = MapTrackOverlay("TRK_LIVE", model.liveTrail)
            state.lastLiveTrailSize = n
            return
        }
    }

    state.lastLiveTrailSize = n
    state.liveTrailLines.forEach { map.overlays.remove(it) }
    state.liveTrailLines.clear()
    if (segments.isEmpty()) return

    for (segment in segments) {
        val line = Polyline().apply {
            title = LiveTrailId
            setPoints(segment.map { GeoPoint(it.lat, it.lon) })
            outlinePaint.color = AndroidColor.parseColor("#CE2B37")
            outlinePaint.strokeWidth = 10f
            relatedObject = MapTrackOverlay("TRK_LIVE", model.liveTrail)
            setOnClickListener { poly, _, _ ->
                val trk = poly.relatedObject as? MapTrackOverlay
                if (trk != null && trk.points.size >= 2) {
                    state.onOverlayShare?.invoke(MapOverlayTap.Track(trk.name, trk.points))
                }
                true
            }
        }
        state.liveTrailLines.add(line)
        map.overlays.add(line)
    }
}

private fun reportZoom(map: MapView, state: MapRuntimeState, onZoomChanged: (Double) -> Unit) {
    val z = map.zoomLevelDouble
    if (state.lastZoomReported.isNaN() || kotlin.math.abs(z - state.lastZoomReported) > 0.01) {
        state.lastZoomReported = z
        onZoomChanged(z)
    }
}

private fun dp(map: MapView, value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, map.resources.displayMetrics)

private data class AnchoredBitmap(val bitmap: Bitmap, val hotV: Float)

/** Target BASE come TocAppBuild: casa verde + etichetta (hotspot = base casa). */
private fun createHomeTargetBitmap(map: MapView, label: String): AnchoredBitmap {
    val display = label.trim().ifBlank { "BASE" }
    val density = map.resources.displayMetrics.density
    val w = (132 * density).toInt().coerceAtLeast(120)
    val h = (96 * density).toInt().coerceAtLeast(90)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)

    val homePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#009246")
        style = Paint.Style.FILL
    }
    val cx = w / 2f
    val homeSize = 36f * density
    val homeTop = 4f * density
    val homeBottom = homeTop + homeSize * 0.95f
    val path = Path().apply {
        // tetto
        moveTo(cx, homeTop)
        lineTo(cx - homeSize * 0.55f, homeTop + homeSize * 0.42f)
        lineTo(cx + homeSize * 0.55f, homeTop + homeSize * 0.42f)
        close()
    }
    c.drawPath(path, homePaint)
    val body = RectF(
        cx - homeSize * 0.38f,
        homeTop + homeSize * 0.38f,
        cx + homeSize * 0.38f,
        homeBottom,
    )
    c.drawRect(body, homePaint)

    drawLabelPill(
        c = c,
        text = display,
        cx = cx,
        top = homeBottom + 4f * density,
        maxWidth = w - 8f * density,
        borderColor = AndroidColor.parseColor("#009246"),
        density = density,
    )
    return AnchoredBitmap(bmp, (homeBottom / h).coerceIn(0.05f, 0.95f))
}

/** Fix utente: cerchio+freccia sul punto geo; label subito sotto (stesso asse). */
private fun createGpsFixBitmap(map: MapView, label: String): AnchoredBitmap {
    val display = label.trim().ifBlank { "GPS" }.uppercase()
    val density = map.resources.displayMetrics.density
    val circleR = 22f * density
    val cy = 8f * density + circleR
    val labelTop = cy + circleR + 4f * density
    // Altezza stretta: freccia e label sullo stesso asse verticale
    val w = (120 * density).toInt().coerceAtLeast(110)
    val h = (labelTop + 22f * density).toInt().coerceAtLeast(90)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)

    val cx = w / 2f

    c.drawCircle(
        cx,
        cy,
        circleR,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(90, 0, 0, 0)
            style = Paint.Style.FILL
        },
    )
    c.drawCircle(
        cx,
        cy,
        circleR,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(220, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        },
    )

    val arrow = Path().apply {
        moveTo(cx, cy - circleR * 0.72f)
        lineTo(cx - circleR * 0.55f, cy + circleR * 0.62f)
        lineTo(cx, cy + circleR * 0.28f)
        lineTo(cx + circleR * 0.55f, cy + circleR * 0.62f)
        close()
    }
    c.drawPath(
        arrow,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#CE2B37")
            style = Paint.Style.FILL
        },
    )

    drawLabelPill(
        c = c,
        text = display,
        cx = cx,
        top = labelTop,
        maxWidth = w - 8f * density,
        borderColor = null,
        density = density,
    )
    // Hotspot = centro freccia (non centro bitmap: altrimenti freccia e label “si staccano”)
    return AnchoredBitmap(bmp, (cy / h).coerceIn(0.05f, 0.95f))
}

/** Pin altro operatore online: pallino colorato + codice. */
private fun createOperatorPinBitmap(
    map: MapView,
    code: String,
    colorHex: String,
): AnchoredBitmap {
    val display = code.trim().ifBlank { "?" }.uppercase()
    val density = map.resources.displayMetrics.density
    val fill = try {
        AndroidColor.parseColor(if (colorHex.startsWith("#")) colorHex else "#$colorHex")
    } catch (_: Exception) {
        AndroidColor.parseColor("#079B42")
    }
    val circleR = 16f * density
    val cy = 6f * density + circleR
    val labelTop = cy + circleR + 4f * density
    val w = (120 * density).toInt().coerceAtLeast(100)
    val h = (labelTop + 22f * density).toInt().coerceAtLeast(80)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    val cx = w / 2f

    c.drawCircle(
        cx,
        cy,
        circleR + 2f * density,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        },
    )
    c.drawCircle(
        cx,
        cy,
        circleR,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
            style = Paint.Style.FILL
        },
    )
    c.drawCircle(
        cx,
        cy,
        circleR * 0.35f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        },
    )
    drawLabelPill(
        c = c,
        text = display,
        cx = cx,
        top = labelTop,
        maxWidth = w - 8f * density,
        borderColor = fill,
        density = density,
    )
    return AnchoredBitmap(bmp, (cy / h).coerceIn(0.05f, 0.95f))
}

private fun drawLabelPill(
    c: AndroidCanvas,
    text: String,
    cx: Float,
    top: Float,
    maxWidth: Float,
    borderColor: Int?,
    density: Float,
) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 11f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    var display = text
    while (textPaint.measureText(display) > maxWidth - 16f * density && display.length > 3) {
        display = display.dropLast(2) + "…"
    }
    val tw = textPaint.measureText(display)
    val padX = 8f * density
    val padY = 3f * density
    val fm = textPaint.fontMetrics
    val th = fm.descent - fm.ascent
    val left = cx - tw / 2f - padX
    val right = cx + tw / 2f + padX
    val bottom = top + th + padY * 2
    val rr = RectF(left, top, right, bottom)
    c.drawRoundRect(
        rr,
        10f * density,
        10f * density,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(184, 0, 0, 0)
            style = Paint.Style.FILL
        },
    )
    if (borderColor != null) {
        c.drawRoundRect(
            rr,
            10f * density,
            10f * density,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                alpha = 230
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
            },
        )
    }
    c.drawText(display, cx, top + padY - fm.ascent, textPaint)
}
