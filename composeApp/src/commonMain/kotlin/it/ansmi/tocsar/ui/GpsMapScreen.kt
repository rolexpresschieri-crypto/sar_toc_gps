package it.ansmi.tocsar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ansmi.tocsar.geo.MapTrackOverlay
import it.ansmi.tocsar.geo.TrackPoint
import it.ansmi.tocsar.geo.WaypointItem
import it.ansmi.tocsar.geo.createCompassGateway
import it.ansmi.tocsar.geo.createLocationGateway
import it.ansmi.tocsar.geo.haversineDistanceM
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
    /** Altri operatori online (tutti vedono tutti, per ora). */
    val liveOperators: List<LiveOperatorPin> = emptyList(),
)
/** Tap su WP/TRK in mappa → condivisione come allegato. */
sealed class MapOverlayTap {
    data class Waypoint(val wp: WaypointItem) : MapOverlayTap()
    data class Track(val name: String, val points: List<TrackPoint>) : MapOverlayTap()
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
 * Mappa rientro: titolo MAPPA, freccia fissa in alto, mappa che ruota sotto.
 */
@Composable
fun GpsMapScreen(
    model: GpsMapModel,
    onBack: () -> Unit,
    onOverlayShare: (MapOverlayTap) -> Unit = {},
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
    var mapZoom by remember { mutableStateOf(14.0) }

    var deviceLat by remember { mutableStateOf<Double?>(null) }
    var deviceLon by remember { mutableStateOf<Double?>(null) }
    var deviceAcc by remember { mutableStateOf<Float?>(null) }
    var deviceAlt by remember { mutableStateOf<Double?>(null) }
    var deviceSpeed by remember { mutableStateOf<Float?>(null) }
    var deviceFixLabel by remember { mutableStateOf<String?>(null) }
    var heading by remember { mutableStateOf<Double?>(null) }
    var liveOperators by remember { mutableStateOf<List<LiveOperatorPin>>(emptyList()) }

    val facade = remember { loadTocSarConfig()?.let { TocSarFacade(it) } }
    val selfCode = model.navigatorLabel.trim().uppercase().ifBlank { "GPS" }

    LaunchedEffect(facade, selfCode) {
        val api = facade ?: return@LaunchedEffect
        while (isActive) {
            runCatching { api.loadLiveOperators() }
                .onSuccess { all ->
                    // Escludi te stesso: sei già la freccia GPS
                    liveOperators = all.filter {
                        !it.operatorCode.equals(selfCode, ignoreCase = true)
                    }
                }
            delay(2_000L)
        }
    }

    DisposableEffect(Unit) {
        val stopLoc = location.watchFixes(2f) { fix ->
            deviceLat = fix.latitude
            deviceLon = fix.longitude
            deviceAcc = fix.accuracyM
            deviceAlt = fix.altitude
            deviceSpeed = null
            deviceFixLabel = currentFixClock()
        }
        val stopCompass = compass.watchHeading { heading = it }
        onDispose {
            stopLoc()
            stopCompass()
        }
    }

    // Navigatore: freccia fissa verso l'alto → ruota la mappa sotto
    val mapRotation = if (northDynamic) -(heading ?: 0.0).toFloat() else 0f
    val markerLabel = selfCode
    val mapModel = model.copy(liveOperators = liveOperators)
    val scale = pickScaleBar(mapZoom, maxBarWidthPx = 120.0)
    val altText = deviceAlt?.takeIf { it.isFinite() && it > 0 }?.let { "${it.roundToInt()} m s.l.m." }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1565C0))
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "←",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                text = "MAPPA",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (model.liveRecording) {
                Text(
                    "REC",
                    color = Color(0xFFFF8A80),
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(end = 8.dp),
                )
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp)),
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
                onOverlayShare = onOverlayShare,
                modifier = Modifier.fillMaxSize(),
            )

            // Scala outdoor: testo + segmento + quota
            if (scale != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
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
                    if (altText != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(altText, color = Color.Black, fontSize = 12.sp)
                    }
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

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            }

            CompassOverlayCard(
                heading = heading,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
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
                        .border(1.2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
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

            if (deviceLat != null && deviceLon != null) {
                PositionOverlayCard(
                    lat = deviceLat!!,
                    lon = deviceLon!!,
                    accuracyM = deviceAcc,
                    speedMps = deviceSpeed,
                    fixLabel = deviceFixLabel,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp),
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
private fun CompassOverlayCard(heading: Double?, modifier: Modifier = Modifier) {
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
        Text("magnetico", color = Color(0xFFBDBDBD), fontSize = 8.sp)
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
        Text("prua", color = Color(0xFFBDBDBD), fontSize = 8.sp)
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

@Composable
private fun PositionOverlayCard(
    lat: Double,
    lon: Double,
    accuracyM: Float?,
    speedMps: Float?,
    fixLabel: String?,
    modifier: Modifier = Modifier,
) {
    val speedKmh = speedMps?.let { maxOf(0.0, it * 3.6) }
    Column(
        modifier = modifier
            .width(182.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.66f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            "POSIZIONE ATTUALE",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            "${((lat * 100000).roundToInt() / 100000.0)}, ${((lon * 100000).roundToInt() / 100000.0)}",
            color = Color(0xFFE0E0E0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Acc: ${accuracyM?.roundToInt()?.let { "$it m" } ?: "n/d"}",
            color = Color(0xFFE0E0E0),
            fontSize = 10.sp,
        )
        Text(
            "Vel: ${speedKmh?.let { "${(it * 10).roundToInt() / 10.0} km/h" } ?: "n/d"}",
            color = Color(0xFFE0E0E0),
            fontSize = 10.sp,
        )
        Text(
            "Fix: ${fixLabel ?: "n/d"}",
            color = Color(0xFFE0E0E0),
            fontSize = 10.sp,
        )
    }
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

expect fun currentFixClock(): String
