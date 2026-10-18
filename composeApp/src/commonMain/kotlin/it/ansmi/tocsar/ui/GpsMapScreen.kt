package it.ansmi.tocsar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import it.ansmi.tocsar.readBatteryPercent
import it.ansmi.tocsar.geo.MapTrackOverlay
import it.ansmi.tocsar.geo.TrackPoint
import it.ansmi.tocsar.geo.WaypointItem
import it.ansmi.tocsar.geo.ComuneBoundaryGeom
import it.ansmi.tocsar.geo.createCompassGateway
import it.ansmi.tocsar.geo.createLocationGateway
import it.ansmi.tocsar.geo.TravelCourse
import it.ansmi.tocsar.geo.acceptFix
import it.ansmi.tocsar.geo.countsAsTravelStep
import it.ansmi.tocsar.geo.haversineDistanceM
import it.ansmi.tocsar.geo.trackHorizDistanceM
import it.ansmi.tocsar.geo.isFresh
import it.ansmi.tocsar.geo.bearingDeg
import it.ansmi.tocsar.backend.LiveOperatorPin
import it.ansmi.tocsar.backend.TocSarFacade
import it.ansmi.tocsar.backend.loadTocSarConfig
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

data class GpsMapModel(
    val baseLat: Double?,
    val baseLon: Double?,
    val baseLabel: String?,
    val overlayWaypoints: List<WaypointItem>,
    val overlayTracks: List<MapTrackOverlay>,
    val liveTrail: List<TrackPoint>,
    val liveRecording: Boolean,
    /** Nome sotto la freccia: login se presente, altrimenti "GPS". */
    val navigatorLabel: String = "GPS",
    /** Ente della sessione: i pin in mappa restano nello stesso organization_id. */
    val organizationId: String = "",
    /** Altri operatori online visibili al navigatore corrente. */
    val liveOperators: List<LiveOperatorPin> = emptyList(),
    val measureA: MapMeasurePoint? = null,
    val measureB: MapMeasurePoint? = null,
    val comuneBoundaries: List<ComuneBoundaryGeom> = emptyList(),
    val visibleComuneIds: Set<String> = emptySet(),
)

/** Punto scelto in mappa per distanza/direzione: operatore, WP o posizione propria. */
sealed class MapMeasurePoint {
    abstract val id: String
    abstract val label: String
    abstract val latitude: Double
    abstract val longitude: Double
    abstract val altitudeM: Double?

    data class Operator(val pin: LiveOperatorPin) : MapMeasurePoint() {
        override val id get() = "op:${pin.sessionId}"
        override val label get() = pin.operatorCode
        override val latitude get() = pin.latitude
        override val longitude get() = pin.longitude
        override val altitudeM get() = null
    }

    data class Waypoint(val wp: WaypointItem) : MapMeasurePoint() {
        override val id get() = "wp:${wp.name.uppercase()}"
        override val label get() = wp.name
        override val latitude get() = wp.lat
        override val longitude get() = wp.lon
        override val altitudeM get() = wp.alt
    }

    data class Self(
        val code: String,
        override val latitude: Double,
        override val longitude: Double,
    ) : MapMeasurePoint() {
        override val id get() = "self"
        override val label get() = code
        override val altitudeM get() = null
    }
}

/** Tap WP/operatore → misura; tap TRK → condivisione; tap freccia GPS → misura da te (con destinazione: anche GPS). */
sealed class MapOverlayTap {
    data class Waypoint(val wp: WaypointItem) : MapOverlayTap()
    data class Track(val name: String, val points: List<TrackPoint>) : MapOverlayTap()
    data class Operator(val pin: LiveOperatorPin) : MapOverlayTap()
    data object Self : MapOverlayTap()
}

enum class MapBasemap {
    Streets,
    Topographic,
    Satellite,
    ;

    val menuLabel: String
        get() = when (this) {
            Streets -> "Stradale (OSM)"
            Topographic -> "Topografica (curve quota)"
            Satellite -> "Satellite / ortofoto"
        }

    val supportsTrails: Boolean
        get() = true

    val attribution: String
        get() = when (this) {
            Streets -> "OpenStreetMap contributors"
            Topographic -> "OSM, SRTM · stile OpenTopoMap (CC-BY-SA)"
            Satellite -> "Esri, Maxar, Earthstar Geographics"
        }
}

private val ScaleMetricSteps = listOf(
    15_000_000, 8_000_000, 4_000_000, 2_000_000, 1_000_000,
    500_000, 250_000, 100_000, 50_000, 25_000, 15_000, 8_000,
    4_000, 2_000, 1_000, 500, 250, 100, 50, 25, 10, 5, 2, 1,
)

/**
 * Mappa a schermo intero: controlli in overlay, striscia stato in basso.
 * La freccia resta in alto e la mappa ruota sotto.
 */
@Composable
fun GpsMapScreen(
    model: GpsMapModel,
    tripRunning: Boolean = false,
    tripLinked: Boolean = false,
    tripElapsedMs: Long = 0L,
    tripDistM: Double = 0.0,
    tripDistBase: Double = 0.0,
    onToggleTrip: () -> Unit = {},
    onResetTrip: () -> Unit = {},
    onBack: () -> Unit,
    onOverlayShare: (MapOverlayTap) -> Unit = {},
    onSaveOperatorWaypoint: (LiveOperatorPin) -> Unit = {},
    /** Posizione GPS corrente → dialogo waypoint già usato da INS WP. */
    onInsertWaypoint: (latitude: Double, longitude: Double, altitudeM: Double?, accuracyM: Float?) -> Unit = { _, _, _, _ -> },
    /** Coppia tua posizione + destinazione: avvia distanza/bearing/freccia sullo schermo GPS. */
    onNavigateFromSelf: (MapMeasurePoint) -> Unit = {},
    onNavigateToWaypoint: (WaypointItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val location = remember { createLocationGateway() }
    val compass = remember { createCompassGateway() }

    var basemap by remember { mutableStateOf(MapBasemap.Streets) }
    var trailsEnabled by remember { mutableStateOf(true) }
    var followMode by remember {
        mutableStateOf(
            !model.liveRecording &&
                model.overlayWaypoints.isEmpty() &&
                model.overlayTracks.isEmpty(),
        )
    }
    // Con WP/TRK overlay o REC: nord in alto (niente rotazione) così si vede la traccia
    var northDynamic by remember {
        mutableStateOf(
            !model.liveRecording &&
                model.overlayWaypoints.isEmpty() &&
                model.overlayTracks.isEmpty(),
        )
    }
    var layersOpen by remember { mutableStateOf(false) }
    var showCoords by remember { mutableStateOf(true) }
    var confiniOpen by remember { mutableStateOf(false) }
    var visibleComuneIds by remember { mutableStateOf(setOf<String>()) }
    var comuneGeoms by remember { mutableStateOf<List<ComuneBoundaryGeom>>(emptyList()) }
    var mapZoom by remember { mutableStateOf(14.0) }

    var deviceLat by remember { mutableStateOf<Double?>(null) }
    var deviceLon by remember { mutableStateOf<Double?>(null) }
    var deviceAcc by remember { mutableStateOf<Float?>(null) }
    var deviceAlt by remember { mutableStateOf<Double?>(null) }
    var deviceSpeed by remember { mutableStateOf<Float?>(null) }
    var travelCourse by remember { mutableStateOf<TravelCourse?>(null) }
    var batteryPct by remember { mutableStateOf(readBatteryPercent()) }
    var heading by remember { mutableStateOf<Double?>(null) }
    var liveOperators by remember { mutableStateOf<List<LiveOperatorPin>>(emptyList()) }
    var measureA by remember { mutableStateOf<MapMeasurePoint?>(null) }
    var measureB by remember { mutableStateOf<MapMeasurePoint?>(null) }

    val facade = remember { loadTocSarConfig()?.let { TocSarFacade(it) } }
    val selfCode = model.navigatorLabel.trim().uppercase().ifBlank { "GPS" }

    LaunchedEffect(facade, model.organizationId) {
        val api = facade ?: return@LaunchedEffect
        val orgId = model.organizationId.trim()
        if (orgId.isEmpty()) {
            comuneGeoms = emptyList()
            return@LaunchedEffect
        }
        comuneGeoms = runCatching { api.loadComuneBoundaries(orgId) }.getOrDefault(emptyList())
    }

    LaunchedEffect(facade, selfCode, model.organizationId) {
        val api = facade ?: return@LaunchedEffect
        val orgId = model.organizationId.trim()
        if (orgId.isEmpty()) {
            liveOperators = emptyList()
            return@LaunchedEffect
        }
        while (isActive) {
            runCatching { api.loadLiveOperators(selfCode, orgId) }
                .onSuccess { all ->
                    // Escludi te stesso: sei già la freccia GPS
                    liveOperators = all.filter {
                        !it.operatorCode.equals(selfCode, ignoreCase = true)
                    }
                }
            delay(2_000L)
        }
    }

    LaunchedEffect(liveOperators, deviceLat, deviceLon) {
        measureA = measureA?.refreshed(liveOperators, deviceLat, deviceLon)
        measureB = measureB?.refreshed(liveOperators, deviceLat, deviceLon)
    }

    DisposableEffect(Unit) {
        val stopLoc = location.watchFixes(2f) { fix ->
            deviceLat = fix.latitude
            deviceLon = fix.longitude
            deviceAcc = fix.accuracyM
            deviceAlt = fix.altitude
            // In auto la bussola è spesso capovolta. La rotta GPS segue la strada.
            if (fix.isGpsProvider) {
                deviceSpeed = fix.speedMps
                travelCourse = travelCourse.acceptFix(fix)
            }
        }
        val stopCompass = compass.watchHeading { heading = it }
        onDispose {
            stopLoc()
            stopCompass()
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            batteryPct = readBatteryPercent()
            delay(20_000L)
        }
    }

    // In movimento la prua è la rotta GPS (direzione reale). Da fermo, la bussola.
    val useGpsCourse = northDynamic && travelCourse.isFresh()
    val mapHeading = if (useGpsCourse) travelCourse?.degrees else heading
    // Freccia fissa verso l'alto → ruota la mappa sotto
    val mapRotation = if (northDynamic) -(mapHeading ?: 0.0).toFloat() else 0f
    val markerLabel = selfCode
    val mapModel = model.copy(
        liveOperators = liveOperators,
        measureA = measureA,
        measureB = measureB,
        comuneBoundaries = comuneGeoms,
        visibleComuneIds = visibleComuneIds,
    )
    val scale = pickScaleBar(mapZoom, maxBarWidthPx = 120.0)
    val altitudeM = deviceAlt?.takeIf { it.isFinite() && it > 0.0 }
    val selfSelected = measureA is MapMeasurePoint.Self || measureB is MapMeasurePoint.Self

    fun selectMeasure(point: MapMeasurePoint) {
        when {
            measureA?.id == point.id -> {
                measureA = measureB
                measureB = null
            }
            measureB?.id == point.id -> measureB = null
            measureA == null -> measureA = point
            measureB == null -> measureB = point
            else -> {
                measureA = measureB
                measureB = point
            }
        }
        val dest = destinationIfSelfPair(measureA, measureB)
        if (dest != null) onNavigateFromSelf(dest)
    }

    fun onMapTap(tap: MapOverlayTap) {
        when (tap) {
            is MapOverlayTap.Operator -> selectMeasure(MapMeasurePoint.Operator(tap.pin))
            is MapOverlayTap.Waypoint -> selectMeasure(MapMeasurePoint.Waypoint(tap.wp))
            is MapOverlayTap.Self -> {
                val lat = deviceLat
                val lon = deviceLon
                if (lat != null && lon != null) {
                    selectMeasure(MapMeasurePoint.Self(selfCode, lat, lon))
                }
            }
            else -> onOverlayShare(tap)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101418)),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            PlatformMapLayer(
                basemap = basemap,
                trailsEnabled = trailsEnabled,
                model = mapModel,
                deviceLat = deviceLat,
                deviceLon = deviceLon,
                followMode = followMode,
                mapOrientationDeg = mapRotation,
                onUserGesture = { /* solo pan 1 dito: gestito in Android */ followMode = false },
                onZoomChanged = { z -> mapZoom = z },
                onOverlayShare = { tap -> onMapTap(tap) },
                modifier = Modifier.fillMaxSize(),
            )

            // Scala outdoor: testo + segmento. La quota è nella striscia in basso.
            if (scale != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.88f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        formatScaleDistance(scale.metricM),
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    ScaleBarSegment(barWidthDp = scale.barPx.toFloat().coerceIn(24f, 120f))
                    val bLat = model.baseLat
                    val bLon = model.baseLon
                    if (bLat != null && bLon != null && deviceLat != null && deviceLon != null) {
                        val distM = haversineDistanceM(deviceLat!!, deviceLon!!, bLat, bLon)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "→ ${model.baseLabel ?: "BASE"}: ${formatNavDistance(distM)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1565C0))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MapIconButton(onClick = onBack) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    if (model.liveRecording) {
                        Text(
                            "REC",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFCE2B37))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        MapIconButton(onClick = { layersOpen = true }) {
                            Text("⧉", color = Color.White, fontSize = 18.sp)
                        }
                        DropdownMenu(expanded = layersOpen, onDismissRequest = { layersOpen = false }) {
                            MapBasemap.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.menuLabel,
                                            fontWeight = if (option == basemap) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        basemap = option
                                        layersOpen = false
                                    },
                                )
                            }
                        }
                    }
                    OrangeWpButton(
                        onClick = { showCoords = !showCoords },
                        dimmed = !showCoords,
                    )
                }
                // Icona vettoriale (non emoji): contrasto alto su fondo scuro
                MapIconButton(
                    onClick = { trailsEnabled = !trailsEnabled },
                    active = trailsEnabled,
                ) {
                    TrailsPathIcon(
                        enabled = trailsEnabled,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box {
                    MapIconButton(
                        onClick = { confiniOpen = true },
                        active = visibleComuneIds.isNotEmpty(),
                    ) {
                        Text("C", color = if (visibleComuneIds.isNotEmpty()) Color(0xFFFF1A1A) else Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    DropdownMenu(
                        expanded = confiniOpen,
                        onDismissRequest = { confiniOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tutti i confini", fontWeight = FontWeight.Bold) },
                            onClick = {
                                visibleComuneIds = comuneGeoms.map { it.id }.toSet()
                                followMode = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Nessuno") },
                            onClick = { visibleComuneIds = emptySet() },
                        )
                        if (comuneGeoms.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Nessun comune. Aggiungili da TOC Anagrafica.") },
                                onClick = { confiniOpen = false },
                            )
                        }
                        comuneGeoms.forEach { comune ->
                            val on = comune.id in visibleComuneIds
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = on,
                                            onCheckedChange = null,
                                        )
                                        Text(comune.name)
                                    }
                                },
                                onClick = {
                                    visibleComuneIds =
                                        if (on) visibleComuneIds - comune.id else visibleComuneIds + comune.id
                                    if (!on) followMode = false
                                },
                            )
                        }
                    }
                }
            }
            }

            CompassOverlayCard(
                heading = if (northDynamic) mapHeading else heading,
                gpsCourse = useGpsCourse,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(10.dp),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .then(
                        if (showCoords) {
                            Modifier
                        } else {
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        },
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (deviceLat != null) {
                    MapIconButton(onClick = { followMode = true }) {
                        Text(if (followMode) "⊕" else "⊙", color = Color.White, fontSize = 18.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (northDynamic) Color(0xFF0B9F3A) else Color(0xFFD91F2A))
                        .border(1.2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                        .clickable { northDynamic = !northDynamic },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("X", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }

            // Freccia sul punto geografico (centro); label sotto, non centrata col blocco
            if (followMode && deviceLat != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(
                            1.2.dp,
                            if (selfSelected) Color(0xFFFFEB3B) else Color.White.copy(alpha = 0.85f),
                            CircleShape,
                        )
                        .clickable {
                            onMapTap(MapOverlayTap.Self)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(30.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.5f, size.height * 0.08f)
                            lineTo(size.width * 0.18f, size.height * 0.92f)
                            lineTo(size.width * 0.5f, size.height * 0.72f)
                            lineTo(size.width * 0.82f, size.height * 0.92f)
                            close()
                        }
                        drawPath(path, color = Color(0xFFCE2B37))
                    }
                }
                Text(
                    markerLabel,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            val a = measureA
            if (a != null) {
                MapMeasureCard(
                    first = a,
                    second = measureB,
                    guidingTo = destinationIfSelfPair(a, measureB)?.label,
                    onSaveOperator = onSaveOperatorWaypoint,
                    onNavigateToWaypoint = onNavigateToWaypoint,
                    onClear = {
                        measureA = null
                        measureB = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 12.dp, top = 168.dp)
                        .zIndex(8f),
                )
            } else if (model.overlayWaypoints.isNotEmpty() || model.baseLat != null) {
                Text(
                    "Tocca un pin, poi un altro (o la tua freccia) per distanza e direzione",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 12.dp, top = 168.dp)
                        .zIndex(8f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Text(
                text = if (trailsEnabled) {
                    "© ${basemap.attribution} · sentieri © waymarkedtrails.org (CC BY-SA)"
                } else {
                    "© ${basemap.attribution}"
                },
                color = Color.Black.copy(alpha = 0.62f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp, start = 48.dp, end = 48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        val odoDist = if (tripRunning && tripLinked) {
            (liveTrailDistanceM(model.liveTrail, deviceLat, deviceLon, deviceAcc, deviceSpeed) - tripDistBase)
                .coerceAtLeast(0.0)
        } else {
            tripDistM
        }
        if (showCoords) LiveStatusBar(
            callsign = selfCode,
            lat = deviceLat,
            lon = deviceLon,
            accuracyM = deviceAcc,
            altitudeM = altitudeM,
            batteryPct = batteryPct,
            odoRunning = tripRunning,
            elapsedMs = tripElapsedMs,
            distanceM = odoDist,
            onToggleOdo = onToggleTrip,
            onResetOdo = onResetTrip,
            onSaveWaypoint = {
                val lat = deviceLat
                val lon = deviceLon
                if (lat != null && lon != null) {
                    onInsertWaypoint(lat, lon, altitudeM, deviceAcc)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFE14A))
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

@Composable
expect fun PlatformMapLayer(
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
    modifier: Modifier = Modifier,
)

@Composable
private fun MapMeasureCard(
    first: MapMeasurePoint,
    second: MapMeasurePoint?,
    guidingTo: String?,
    onSaveOperator: (LiveOperatorPin) -> Unit,
    onNavigateToWaypoint: (WaypointItem) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dist = second?.let {
        haversineDistanceM(first.latitude, first.longitude, it.latitude, it.longitude)
    }
    val bearing = second?.let {
        bearingDeg(first.latitude, first.longitude, it.latitude, it.longitude)
    }
    val savePins = listOfNotNull(
        (first as? MapMeasurePoint.Operator)?.pin,
        (second as? MapMeasurePoint.Operator)?.pin,
    )
    Column(
        modifier = modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF21A1A1A))
            .border(2.dp, Color(0xFFFFEB3B), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            "MISURA",
            color = Color(0xFFFFEB3B),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (second == null) {
            Text(
                if (first is MapMeasurePoint.Self) {
                    "${first.label} · tocca WP o operatore di destinazione"
                } else {
                    "${first.label} · tocca un altro pin, o la tua freccia GPS per andarci"
                },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                "${first.label} → ${second.label}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Dist: ${formatNavDistance(dist ?: 0.0)}",
                color = Color(0xFFE0E0E0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Dir: ${bearing?.roundToInt() ?: "—"}° ${bearing?.let { cardinalIt16(it) }.orEmpty()}".trim(),
                color = Color(0xFFE0E0E0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            if (guidingTo != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "GPS: freccia verso $guidingTo — ← per vederla",
                    color = Color(0xFFFFEB3B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (savePins.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            savePins.forEachIndexed { idx, pin ->
                if (idx > 0) Spacer(modifier = Modifier.height(4.dp))
                MeasureActionChip("Salva WP ${pin.operatorCode}") { onSaveOperator(pin) }
            }
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
        val navWp = listOfNotNull(
            (second as? MapMeasurePoint.Waypoint)?.wp,
            (first as? MapMeasurePoint.Waypoint)?.wp,
        ).firstOrNull()
        if (navWp != null) {
            MeasureActionChip("Naviga verso ${navWp.name}") { onNavigateToWaypoint(navWp) }
            Spacer(modifier = Modifier.height(4.dp))
        }
        MeasureActionChip("Annulla", muted = true, onClick = onClear)
    }
}

@Composable
private fun MeasureActionChip(
    label: String,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (muted) Color.White.copy(alpha = 0.12f) else Color(0xFF1565C0))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun cardinalIt16(bearing: Double): String {
    val dirs = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO",
    )
    val idx = (((bearing % 360.0 + 360.0) % 360.0 + 11.25) / 22.5).toInt() % 16
    return dirs[idx]
}

@Composable
private fun OrangeWpButton(
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (dimmed) Color(0xFFFF9021).copy(alpha = 0.45f) else Color(0xFFFF9021))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("WP", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MapIconButton(
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .then(
                if (active) {
                    Modifier.border(2.dp, Color(0xFFFFEB3B), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Sentiero a zigzag: leggibile a differenza dell'emoji scarpone. */
@Composable
private fun TrailsPathIcon(enabled: Boolean, modifier: Modifier = Modifier) {
    val ink = if (enabled) Color(0xFFFFEB3B) else Color.White.copy(alpha = 0.72f)
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.78f)
            lineTo(size.width * 0.32f, size.height * 0.38f)
            lineTo(size.width * 0.52f, size.height * 0.72f)
            lineTo(size.width * 0.72f, size.height * 0.28f)
            lineTo(size.width * 0.88f, size.height * 0.55f)
        }
        drawPath(
            path = path,
            color = ink,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        // Punto “partenza” per suggerire un percorso
        drawCircle(
            color = ink,
            radius = stroke * 0.85f,
            center = Offset(size.width * 0.12f, size.height * 0.78f),
        )
    }
}

@Composable
private fun CompassOverlayCard(
    heading: Double?,
    gpsCourse: Boolean,
    modifier: Modifier = Modifier,
) {
    val h = heading
    Column(
        modifier = modifier
            .width(86.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.66f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NORD", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (gpsCourse) "rotta GPS" else "magnetico",
            color = if (gpsCourse) Color(0xFF90CAF9) else Color(0xFFBDBDBD),
            fontSize = 8.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Freccia verso il NORD magnetico reale (come TocAppBuild: angolo = -heading)
        Canvas(modifier = Modifier.size(34.dp).rotate(-((h ?: 0.0).toFloat()))) {
            val path = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.05f)
                lineTo(size.width * 0.2f, size.height * 0.95f)
                lineTo(size.width * 0.5f, size.height * 0.72f)
                lineTo(size.width * 0.8f, size.height * 0.95f)
                close()
            }
            drawPath(path, color = Color(0xFFFFEB3B))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            if (h == null) "—°" else "${h.roundToInt()}°",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (gpsCourse) "rotta" else "prua",
            color = Color(0xFFBDBDBD),
            fontSize = 8.sp,
        )
    }
}

@Composable
private fun ScaleBarSegment(barWidthDp: Float) {
    val ink = Color.Black
    Canvas(
        modifier = Modifier
            .width(barWidthDp.dp)
            .height(10.dp),
    ) {
        val y = size.height * 0.55f
        drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.6f)
        drawLine(ink, Offset(0f, y - 5f), Offset(0f, y + 2f), strokeWidth = 2.6f)
        drawLine(ink, Offset(size.width, y - 5f), Offset(size.width, y + 2f), strokeWidth = 2.6f)
        drawLine(ink, Offset(size.width / 2f, y - 3f), Offset(size.width / 2f, y + 1f), strokeWidth = 2f)
    }
}

private val StatusBlue = Color(0xFF0D47A1)
private val StatusGreen = Color(0xFF1B5E20)
private val StatusLabel = Color(0xFF4A3800)
private val StatusValue = Color(0xFF1A1200)

@Composable
private fun LiveStatusBar(
    callsign: String,
    lat: Double?,
    lon: Double?,
    accuracyM: Float?,
    altitudeM: Double?,
    batteryPct: Int?,
    odoRunning: Boolean,
    elapsedMs: Long,
    distanceM: Double,
    onToggleOdo: () -> Unit,
    onResetOdo: () -> Unit,
    onSaveWaypoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSave = lat != null && lon != null
    val batteryColor = when {
        batteryPct == null -> StatusLabel
        batteryPct <= 15 -> Color(0xFFD32F2F)
        else -> StatusGreen
    }
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFFE6B000)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 18.dp),
        ) {
            // Larghezze minime del testo pieno: sigla 8 lettere, coordinate, "100%".
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val siglaW = maxWidth * 0.24f
                val battW = maxWidth * 0.18f
                val posW = maxWidth - siglaW - battW
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatusCell(
                        label = "SIGLA",
                        value = callsign,
                        valueSize = 26.sp,
                        modifier = Modifier.width(siglaW),
                    )
                    StatusCell(
                        label = "POSIZIONE",
                        value = if (lat != null && lon != null) {
                            "${formatStatusCoord(lat)}, ${formatStatusCoord(lon)}"
                        } else {
                            "—"
                        },
                        subtitle = accuracyM?.roundToInt()?.let { "±$it m" },
                        valueSize = 20.sp,
                        modifier = Modifier.width(posW),
                    )
                    StatusCell(
                        label = "BATT.",
                        value = batteryPct?.let { "$it%" } ?: "—",
                        valueColor = batteryColor,
                        valueSize = 26.sp,
                        align = TextAlign.End,
                        modifier = Modifier.width(battW),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // I numeri stanno solo fra i due tasti: non possono disegnarsi sopra ST o WP.
            Box(modifier = Modifier.fillMaxWidth()) {
                val st = 56.dp
                val wp = 60.dp
                val gap = 8.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = st + gap, end = wp + gap)
                        .clipToBounds(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusCell(
                        label = "QUOTA",
                        value = altitudeM?.let { formatStatusAltitude(it) } ?: "—",
                        valueColor = StatusBlue,
                        valueSize = 22.sp,
                        align = TextAlign.Center,
                        modifier = Modifier.weight(0.85f),
                    )
                    StatusCell(
                        label = "TIME",
                        value = formatElapsedHms(elapsedMs),
                        valueColor = if (odoRunning) StatusBlue else StatusValue,
                        valueSize = 20.sp,
                        align = TextAlign.Center,
                        onLongClick = onResetOdo,
                        modifier = Modifier.weight(1.05f),
                    )
                    StatusCell(
                        label = "DIST",
                        value = formatOdoMeters(distanceM),
                        valueColor = if (odoRunning) StatusBlue else StatusValue,
                        valueSize = 18.sp,
                        align = TextAlign.Center,
                        modifier = Modifier.weight(1.15f),
                    )
                }
                TripToggleButton(
                    running = odoRunning,
                    onClick = onToggleOdo,
                    onLongClick = onResetOdo,
                    size = st,
                    modifier = Modifier.align(Alignment.CenterStart).zIndex(2f),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .zIndex(2f)
                        .size(wp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canSave) Color(0xFFFF9021) else Color(0xFFBDBDBD))
                        .clickable(enabled = canSave, onClick = onSaveWaypoint),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "WP",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TripToggleButton(
    running: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    size: androidx.compose.ui.unit.Dp = 46.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(if (running) Color(0xFFCE2B37) else Color(0xFF00838F))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("ST", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StatusCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = StatusValue,
    valueSize: TextUnit = 22.sp,
    subtitle: String? = null,
    align: TextAlign = TextAlign.Start,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clipToBounds()
            .padding(horizontal = 2.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = when (align) {
            TextAlign.Center -> Alignment.CenterHorizontally
            TextAlign.End, TextAlign.Right -> Alignment.End
            else -> Alignment.Start
        },
    ) {
        Text(
            label,
            color = StatusLabel,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            textAlign = align,
            overflow = TextOverflow.Clip,
        )
        FittedValue(
            text = value,
            color = valueColor,
            preferred = valueSize,
            align = align,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = Color(0xFF4A3800),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Resta dentro la colonna: se non entra, si riduce. Non esce sul tasto accanto. */
@Composable
private fun FittedValue(
    text: String,
    color: Color,
    preferred: TextUnit,
    align: TextAlign,
) {
    var size by remember(text, preferred) { mutableStateOf(preferred) }
    Text(
        text,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false,
        textAlign = align,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && size.value > 13f) {
                size = (size.value * 0.92f).sp
            }
        },
    )
}

private fun formatStatusCoord(value: Double): String {
    val scaled = (value * 100_000.0).roundToInt() / 100_000.0
    return scaled.toString()
}

private fun formatStatusAltitude(meters: Double): String = "${meters.roundToInt()}m"

private fun formatElapsedHms(elapsedMs: Long): String {
    val total = (elapsedMs / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

/** Metri con migliaia «.» e un decimale «,» — es. mt. 1.234,5 */
private fun formatOdoMeters(meters: Double): String {
    val safe = if (meters.isFinite() && meters > 0.0) meters else 0.0
    val tenths = (safe * 10.0).roundToInt()
    val whole = tenths / 10
    val frac = tenths % 10
    val grouped = whole.toString().reversed().chunked(3).joinToString(".").reversed()
    return "mt. $grouped,$frac"
}

private fun liveTrailDistanceM(
    points: List<TrackPoint>,
    lat: Double?,
    lon: Double?,
    accuracyM: Float?,
    speedMps: Float?,
): Double {
    var sum = trackHorizDistanceM(points)
    val last = points.lastOrNull() ?: return sum
    if (lat != null && lon != null) {
        val tail = haversineDistanceM(last.lat, last.lon, lat, lon)
        if (countsAsTravelStep(tail, accuracyM, speedMps)) sum += tail
    }
    return sum
}

private data class ScalePick(val metricM: Int, val barPx: Double)

private fun pickScaleBar(zoom: Double, maxBarWidthPx: Double): ScalePick? {
    val mPerPx = 156543.03392 * cos(45.0 * PI / 180.0) / 2.0.pow(zoom)
    var chosen: ScalePick? = null
    for (metric in ScaleMetricSteps.asReversed()) {
        val px = metric / mPerPx
        if (px <= maxBarWidthPx) {
            chosen = ScalePick(metric, px)
        }
    }
    return chosen
}

private fun formatScaleDistance(meters: Int): String {
    if (meters < 1000) return "$meters,0 m"
    val km = meters / 1000.0
    return if (km >= 10) {
        "${km.roundToInt()} km"
    } else {
        val one = ((km * 10).roundToInt() / 10.0).toString().replace('.', ',')
        "$one km"
    }
}

private fun formatNavDistance(m: Double): String =
    if (m >= 1000) "${((m / 1000.0) * 100).toInt() / 100.0} km" else "${m.roundToInt()} m"

private fun MapMeasurePoint.refreshed(
    operators: List<LiveOperatorPin>,
    selfLat: Double?,
    selfLon: Double?,
): MapMeasurePoint = when (this) {
    is MapMeasurePoint.Operator ->
        operators.find { it.sessionId == pin.sessionId }?.let { copy(pin = it) } ?: this
    is MapMeasurePoint.Waypoint -> this
    is MapMeasurePoint.Self ->
        if (selfLat != null && selfLon != null) copy(latitude = selfLat, longitude = selfLon) else this
}

/** Destinazione se uno dei due punti è la tua posizione GPS. */
private fun destinationIfSelfPair(a: MapMeasurePoint?, b: MapMeasurePoint?): MapMeasurePoint? {
    if (a == null || b == null) return null
    return when {
        a is MapMeasurePoint.Self && b !is MapMeasurePoint.Self -> b
        b is MapMeasurePoint.Self && a !is MapMeasurePoint.Self -> a
        else -> null
    }
}

internal fun MapMeasurePoint?.measureKey(): String =
    when (this) {
        null -> ""
        is MapMeasurePoint.Operator ->
            "op:${pin.sessionId}:${(pin.latitude * 1e5).toInt()}:${(pin.longitude * 1e5).toInt()}"
        is MapMeasurePoint.Waypoint ->
            "wp:${wp.name}:${(wp.lat * 1e5).toInt()}:${(wp.lon * 1e5).toInt()}"
        is MapMeasurePoint.Self ->
            "self:${(latitude * 1e5).toInt()}:${(longitude * 1e5).toInt()}"
    }

internal fun GpsMapModel.selectedOperatorSessionIds(): Set<String> =
    listOfNotNull(
        (measureA as? MapMeasurePoint.Operator)?.pin?.sessionId,
        (measureB as? MapMeasurePoint.Operator)?.pin?.sessionId,
    ).toSet()

internal fun GpsMapModel.selectedWaypointNames(): Set<String> =
    listOfNotNull(
        (measureA as? MapMeasurePoint.Waypoint)?.wp?.name,
        (measureB as? MapMeasurePoint.Waypoint)?.wp?.name,
    ).map { it.uppercase() }.toSet()
