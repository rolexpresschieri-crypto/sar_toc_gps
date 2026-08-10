package it.ansmi.tocsar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import it.ansmi.tocsar.sharePlainText
import it.ansmi.tocsar.geo.MapTrackOverlay
import it.ansmi.tocsar.geo.TrackListItem
import it.ansmi.tocsar.geo.TrackPoint
import it.ansmi.tocsar.geo.WaypointItem
import it.ansmi.tocsar.geo.bearingDeg
import it.ansmi.tocsar.geo.createCompassGateway
import it.ansmi.tocsar.geo.createGpsLocalStore
import it.ansmi.tocsar.geo.createLocationGateway
import it.ansmi.tocsar.geo.encodeTrkFile
import it.ansmi.tocsar.geo.encodeWaypointFile
import it.ansmi.tocsar.geo.formatAlt0
import it.ansmi.tocsar.geo.formatCoord6
import it.ansmi.tocsar.geo.haversineDistanceM
import it.ansmi.tocsar.geo.loadAllWaypoints
import it.ansmi.tocsar.geo.loadCompassHeadingOffset
import it.ansmi.tocsar.geo.looksLikeTrkContent
import it.ansmi.tocsar.geo.looksLikeWaypointContent
import it.ansmi.tocsar.geo.normalizeHeadingDegrees
import it.ansmi.tocsar.geo.parseCoord
import it.ansmi.tocsar.geo.parseTrkFile
import it.ansmi.tocsar.geo.parseWaypointFile
import it.ansmi.tocsar.geo.pickGpsImportFile
import it.ansmi.tocsar.geo.safeGpsFileStem
import it.ansmi.tocsar.geo.saveCompassHeadingOffset
import it.ansmi.tocsar.geo.OperatorGpsTracking
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import tocsar.composeapp.generated.resources.Res
import tocsar.composeapp.generated.resources.goniometro
import kotlin.math.min
import kotlin.math.roundToInt

private val GpsBlue = Color(0xFF1565C0)
private val CompassGuideDayYellow = Color(0xFFFFEB3B)
private val TrackColors = listOf("#1565C0", "#00838F", "#6A1B9A", "#EF6C00", "#2E7D32")
/** Distanza minima tra un punto e il successivo in tracking TRK (come TocAppBuild). */
private const val TrackPointMinDistanceM = 3f

@Composable
fun GpsScreen(
    onBack: () -> Unit,
    navigatorLabel: String? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val location = remember { createLocationGateway() }
    val compass = remember { createCompassGateway() }
    val store = remember { createGpsLocalStore() }
    val operatorPrefix = navigatorLabel?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "GPS"
    val waypointPrefix = "${operatorPrefix}_WP_"
    val trackPrefix = "${operatorPrefix}_TRK_"

    var nightMode by remember { mutableStateOf(false) }
    var latBase by remember { mutableStateOf("") }
    var lonBase by remember { mutableStateOf("") }
    var altBase by remember { mutableStateOf("") }
    var latPtg by remember { mutableStateOf("") }
    var lonPtg by remember { mutableStateOf("") }
    var altPtg by remember { mutableStateOf("") }
    var accBase by remember { mutableStateOf<Float?>(null) }
    var accPtg by remember { mutableStateOf<Float?>(null) }
    var loadingBase by remember { mutableStateOf(false) }
    var loadingPtg by remember { mutableStateOf(false) }
    var distanceM by remember { mutableStateOf<Double?>(null) }
    var bearingToBase by remember { mutableStateOf<Double?>(null) }
    var arrowRotation by remember { mutableStateOf(0.0) }
    var heading by remember { mutableStateOf<Double?>(null) }
    var isGoMode by remember { mutableStateOf(false) }
    var trkRecording by remember { mutableStateOf(OperatorGpsTracking.isTrkRecording()) }
    var showCalib by remember { mutableStateOf(false) }
    var showInsWp by remember { mutableStateOf(false) }
    var showSaveTrk by remember { mutableStateOf(false) }
    var showWpTrk by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var pendingSavePoints by remember { mutableStateOf<List<TrackPoint>>(emptyList()) }
    var selectedWpId by remember { mutableStateOf<String?>(null) }
    var overlayWaypoints by remember { mutableStateOf<List<WaypointItem>>(emptyList()) }
    var overlayTracks by remember { mutableStateOf<List<MapTrackOverlay>>(emptyList()) }
    val trackPoints = remember { mutableStateListOf<TrackPoint>() }

    var stopGoWatch by remember { mutableStateOf<(() -> Unit)?>(null) }

    val fg = if (nightMode) Color.White else Color(0xFF212121)
    val sub = if (nightMode) Color(0xFFBDBDBD) else Color(0xFF616161)
    val card = if (nightMode) Color(0xFF1E1E1E) else Color.White
    val bg = if (nightMode) Color(0xFF121212) else Color(0xFFF3EEF6)

    DisposableEffect(Unit) {
        val stop = compass.watchHeading { h ->
            heading = h
            val b = bearingToBase
            if (isGoMode && b != null && h != null) {
                arrowRotation = relativeArrowDeg(b, h)
            }
        }
        onDispose {
            stop()
            stopGoWatch?.invoke()
            // Non fermare TRK: continua nel foreground service (tasca / schermo spento)
        }
    }

    LaunchedEffect(trkRecording) {
        if (!trkRecording) return@LaunchedEffect
        while (isActive && OperatorGpsTracking.isTrkRecording()) {
            val pts = OperatorGpsTracking.trkPointsSnapshot()
            trackPoints.clear()
            trackPoints.addAll(pts)
            delay(1_000L)
        }
        trkRecording = OperatorGpsTracking.isTrkRecording()
    }

    // Sync stato se si rientra in GPS con TRK già attivo in service
    LaunchedEffect(Unit) {
        if (OperatorGpsTracking.isTrkRecording()) {
            trkRecording = true
            trackPoints.clear()
            trackPoints.addAll(OperatorGpsTracking.trkPointsSnapshot())
        }
    }

    fun updateArrow(bearing: Double?, head: Double?) {
        if (bearing != null && head != null) {
            arrowRotation = relativeArrowDeg(bearing, head)
        }
    }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun clearData() {
        stopGoWatch?.invoke()
        stopGoWatch = null
        if (OperatorGpsTracking.isTrkRecording()) {
            OperatorGpsTracking.stopTrkRecording()
        }
        latBase = ""; lonBase = ""; altBase = ""
        latPtg = ""; lonPtg = ""; altPtg = ""
        accBase = null; accPtg = null
        distanceM = null; bearingToBase = null
        isGoMode = false; trkRecording = false
        trackPoints.clear()
        selectedWpId = null
        overlayWaypoints = emptyList()
        overlayTracks = emptyList()
        toast("Dati GPS cancellati")
    }

    fun setFromGps(isBase: Boolean) {
        scope.launch {
            if (!location.ensurePermission()) {
                toast("Permesso GPS negato — concedilo nelle impostazioni")
                return@launch
            }
            if (isBase) loadingBase = true else loadingPtg = true
            try {
                val fix = location.currentFix()
                if (fix == null) {
                    toast("Nessun fix GPS — attendi segnale outdoor")
                    return@launch
                }
                if (isBase) {
                    latBase = fix.latitude.formatCoord6()
                    lonBase = fix.longitude.formatCoord6()
                    altBase = fix.altitude.formatAlt0()
                    accBase = fix.accuracyM
                    toast("BASE impostata")
                } else {
                    latPtg = fix.latitude.formatCoord6()
                    lonPtg = fix.longitude.formatCoord6()
                    altPtg = fix.altitude.formatAlt0()
                    accPtg = fix.accuracyM
                    toast("PATTUGLIA impostata")
                }
            } finally {
                loadingBase = false
                loadingPtg = false
            }
        }
    }

    fun calcolaRotta() {
        val latB = parseCoord(latBase); val lonB = parseCoord(lonBase)
        val latP = parseCoord(latPtg); val lonP = parseCoord(lonPtg)
        if (latB == null || lonB == null || latP == null || lonP == null) {
            toast("Inserisci coordinate BASE e PATTUGLIA")
            return
        }
        distanceM = haversineDistanceM(latP, lonP, latB, lonB)
        bearingToBase = bearingDeg(latP, lonP, latB, lonB)
        isGoMode = false
        toast("Rotta calcolata")
    }

    fun stopVaiABase() {
        stopGoWatch?.invoke()
        stopGoWatch = null
        isGoMode = false
    }

    fun vaiABase() {
        scope.launch {
            val latB = parseCoord(latBase); val lonB = parseCoord(lonBase)
            if (latB == null || lonB == null) {
                toast("Imposta prima la BASE")
                return@launch
            }
            if (!location.ensurePermission()) {
                toast("Permesso GPS negato")
                return@launch
            }
            stopGoWatch?.invoke()
            isGoMode = true
            stopGoWatch = location.watchFixes(2f) { fix ->
                latPtg = fix.latitude.formatCoord6()
                lonPtg = fix.longitude.formatCoord6()
                altPtg = fix.altitude.formatAlt0()
                accPtg = fix.accuracyM
                distanceM = haversineDistanceM(fix.latitude, fix.longitude, latB, lonB)
                val b = bearingDeg(fix.latitude, fix.longitude, latB, lonB)
                bearingToBase = b
                updateArrow(b, heading)
            }
            toast("Navigazione verso BASE attiva")
        }
    }

    fun toggleTrk() {
        scope.launch {
            if (trkRecording || OperatorGpsTracking.isTrkRecording()) {
                val points = OperatorGpsTracking.stopTrkRecording()
                trkRecording = false
                trackPoints.clear()
                trackPoints.addAll(points)
                if (points.size < 2) {
                    toast("Servono almeno 2 punti GPS per salvare la traccia")
                    trackPoints.clear()
                } else {
                    pendingSavePoints = points
                    showSaveTrk = true
                }
                return@launch
            }
            if (!location.ensurePermission()) {
                toast("Permesso GPS negato")
                return@launch
            }
            trackPoints.clear()
            // Non cancellare area/WP già in mappa: servono durante la bonifica insieme alla scia live
            OperatorGpsTracking.startTrkRecording()
            trkRecording = true
            toast(
                "TRK attivo anche in tasca · un punto ogni ${TrackPointMinDistanceM.toInt()} m " +
                    "(notifica «Tracking operatore»)",
            )
        }
    }

    fun openMap() {
        showMap = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Form GPS resta montato sotto: chiudendo la mappa, MAPPA resta visibile
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GpsBlue)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "←",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = {
                            stopVaiABase()
                            // TRK continua nel service se attivo
                            onBack()
                        })
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Text(
                    text = "GPS",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (nightMode) "Giorno" else "Notte",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { nightMode = !nightMode }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 26.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (nightMode) card else CompassGuideDayYellow)
                        .clickable { showCalib = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ℹ", fontSize = 18.sp, color = if (nightMode) sub else Color.Black)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Bussola imprecisa? Guida calibrazione (movimento a 8)",
                        color = if (nightMode) fg else Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text("›", fontSize = 22.sp, color = if (nightMode) sub else Color.Black)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoordsColumn(
                        title = "BASE",
                        accuracy = accBase,
                        buttonLabel = if (loadingBase) "..." else "Imposta BASE da GPS",
                        buttonColor = Color(0xFF009246),
                        lat = latBase, lon = lonBase, alt = altBase,
                        onLat = { latBase = it }, onLon = { lonBase = it }, onAlt = { altBase = it },
                        latLabel = "Lat BASE", lonLabel = "Lon BASE", altLabel = "Quota BASE (m)",
                        onGps = { setFromGps(true) },
                        card = card, fg = fg, sub = sub,
                        modifier = Modifier.weight(1f),
                    )
                    CoordsColumn(
                        title = "PATTUGLIA",
                        accuracy = accPtg,
                        buttonLabel = if (loadingPtg) "..." else "Imposta PTG da GPS",
                        buttonColor = Color(0xFFF9A825),
                        lat = latPtg, lon = lonPtg, alt = altPtg,
                        onLat = { latPtg = it }, onLon = { lonPtg = it }, onAlt = { altPtg = it },
                        latLabel = "Lat PTG", lonLabel = "Lon PTG", altLabel = "Quota PTG (m)",
                        onGps = { setFromGps(false) },
                        card = card, fg = fg, sub = sub,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GpsActionButton(
                        if (trkRecording) "STOP TRK" else "START TRK",
                        if (trkRecording) Color(0xFFCE2B37) else Color(0xFF00838F),
                        Modifier.weight(1f),
                        onClick = { toggleTrk() },
                    )
                    GpsActionButton("INS WP", Color(0xFFFF9021), Modifier.weight(1f)) {
                        showInsWp = true
                    }
                }
                if (trkRecording) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "REC · ${trackPoints.size} punti · ogni ${TrackPointMinDistanceM.toInt()} m — anche in tasca · STOP chiede il nome",
                        color = if (nightMode) Color(0xFFFF8A80) else Color(0xFFC62828),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GpsActionButton("Calcola rotta", Color(0xFF9E9E9E), Modifier.weight(1f), onClick = { calcolaRotta() })
                    GpsActionButton("Vai a BASE", Color(0xFF009246), Modifier.weight(1f), onClick = { vaiABase() })
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GpsActionButton("WP & TRK", Color(0xFF0D47A1), Modifier.weight(1f)) {
                        showWpTrk = true
                    }
                    GpsActionButton("MAPPA", Color(0xFF7E57C2), Modifier.weight(1f)) {
                        openMap()
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                GpsActionButton("Clear data", Color(0xFFCE2B37), Modifier.fillMaxWidth(), onClick = { clearData() })

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isGoMode) "Distanza BASE: ${formatDistance(distanceM)}" else "Distanza: ${formatDistance(distanceM)}",
                    color = fg,
                    fontSize = if (isGoMode) 22.sp else 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (!selectedWpId.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedWpId!!,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF009246))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (!isGoMode) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bearing: ${bearingToBase?.let { "${it.toInt()}°" } ?: "-"}",
                        color = fg,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val compassSize = min(400f, maxWidth.value - 8f).dp
                        Box(modifier = Modifier.size(compassSize), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(Res.drawable.goniometro),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .rotate(-((heading ?: 0.0).toFloat())),
                                contentScale = ContentScale.Fit,
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = compassSize * 0.03f)
                                    .width(2.dp)
                                    .height(compassSize * 0.34f)
                                    .background(Color.Black.copy(alpha = 0.75f)),
                            )
                            Text(
                                text = "Nord: ${heading?.let { "${it.toInt()}°" } ?: "-"}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nord: ${heading?.let { "${it.toInt()}°" } ?: "-"}",
                        color = fg,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        NavigationArrow(
                            rotationDeg = arrowRotation.toFloat(),
                            color = if (nightMode) Color.White else Color.Black,
                            modifier = Modifier.size(162.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GpsActionButton("Chiudi Vai a BASE", Color(0xFF9E9E9E), Modifier.width(240.dp), onClick = {
                        stopVaiABase()
                        toast("Navigazione fermata")
                    })
                }
            }
        }

        if (showMap) {
            GpsMapScreen(
                model = GpsMapModel(
                    baseLat = parseCoord(latBase),
                    baseLon = parseCoord(lonBase),
                    baseLabel = selectedWpId ?: "BASE",
                    overlayWaypoints = overlayWaypoints,
                    overlayTracks = overlayTracks,
                    // Area/WP selezionati + scia live insieme (zoom resta sugli overlay statici)
                    liveTrail = trackPoints.toList(),
                    liveRecording = trkRecording,
                    navigatorLabel = navigatorLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "GPS",
                ),
                // Solo chiude la mappa — non torna alla Home
                onBack = { showMap = false },
                onOverlayShare = { tap ->
                    when (tap) {
                        is MapOverlayTap.Waypoint -> {
                            sharePlainText(
                                subject = "Waypoint ${tap.wp.name}",
                                text = encodeWaypointFile(tap.wp.name, tap.wp.lat, tap.wp.lon, tap.wp.alt),
                                fileNameHint = "${safeGpsFileStem(tap.wp.name)}.wpt.txt",
                            )
                        }
                        is MapOverlayTap.Track -> {
                            sharePlainText(
                                subject = "Traccia ${tap.name}",
                                text = encodeTrkFile(tap.points),
                                fileNameHint = "${safeGpsFileStem(tap.name)}.trk",
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f),
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .zIndex(20f),
        )
    }

    if (showCalib) {
        var offsetDeg by remember { mutableStateOf(loadCompassHeadingOffset()) }
        AlertDialog(
            onDismissRequest = { showCalib = false },
            title = { Text("Calibrazione bussola", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text(
                        "Muovi il telefono a forma di 8 in aria, lento e ampio (10–15 s). " +
                            "La calibrazione del sensore la gestisce il telefono.",
                        color = Color(0xFF616161),
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Tieni il telefono in verticale come in navigazione. " +
                            "Se il Nord resta storto, usa la correzione sotto " +
                            "(es. ±90° se vedi la rosa ruotata di un quarto di giro).",
                        color = Color(0xFF616161),
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Correzione orientamento",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                    )
                    Text(
                        "Offset attuale: ${offsetDeg.roundToInt()}°",
                        color = Color(0xFF616161),
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (delta in listOf(-90, -15, 15, 90)) {
                            TextButton(
                                onClick = {
                                    val next = normalizeHeadingDegrees(offsetDeg + delta) ?: 0.0
                                    saveCompassHeadingOffset(next)
                                    offsetDeg = next
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (delta > 0) "+$delta°" else "$delta°", fontSize = 12.sp)
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            saveCompassHeadingOffset(0.0)
                            offsetDeg = 0.0
                        },
                    ) { Text("Azzera") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalib = false }) { Text("OK") }
            },
        )
    }

    if (showInsWp) {
        InsertWaypointDialog(
            operatorPrefix = operatorPrefix,
            initialLat = latBase,
            initialLon = lonBase,
            initialAlt = altBase,
            initialAcc = accBase,
            location = location,
            onDismiss = { showInsWp = false },
            onSaved = { wp, acc ->
                showInsWp = false
                latBase = wp.lat.formatCoord6()
                lonBase = wp.lon.formatCoord6()
                altBase = wp.alt?.formatAlt0().orEmpty()
                accBase = acc
                selectedWpId = wp.name
                toast("Waypoint ${wp.name} salvato in locale")
            },
            store = store,
            toast = ::toast,
        )
    }

    if (showSaveTrk) {
        SaveTrackDialog(
            points = pendingSavePoints,
            operatorPrefix = operatorPrefix,
            store = store,
            onDismiss = {
                showSaveTrk = false
                pendingSavePoints = emptyList()
                trackPoints.clear()
            },
            onSaved = { name, points ->
                showSaveTrk = false
                pendingSavePoints = emptyList()
                trackPoints.clear()
                // Mantieni area/WP già selezionati e aggiungi la traccia appena salvata
                overlayTracks = overlayTracks + MapTrackOverlay(
                    name = name,
                    points = points,
                    colorHex = TrackColors[overlayTracks.size % TrackColors.size],
                )
                toast("Traccia $name salvata in locale")
            },
            toast = ::toast,
        )
    }

    if (showWpTrk) {
        WpTrkDialog(
            operatorPrefix = operatorPrefix,
            store = store,
            onDismiss = { showWpTrk = false },
            onUseAsBase = { wp ->
                latBase = wp.lat.formatCoord6()
                lonBase = wp.lon.formatCoord6()
                altBase = wp.alt?.formatAlt0().orEmpty()
                accBase = null
                selectedWpId = wp.name
                overlayWaypoints = emptyList()
                overlayTracks = emptyList()
                showWpTrk = false
            },
            onShowOnMap = { wps, tracks ->
                showWpTrk = false
                if (wps.size == 1 && tracks.isEmpty()) {
                    val wp = wps.first()
                    latBase = wp.lat.formatCoord6()
                    lonBase = wp.lon.formatCoord6()
                    altBase = wp.alt?.formatAlt0().orEmpty()
                    selectedWpId = wp.name
                    overlayWaypoints = emptyList()
                    overlayTracks = emptyList()
                } else {
                    overlayWaypoints = wps
                    overlayTracks = tracks
                    selectedWpId = null
                }
                openMap()
            },
            toast = ::toast,
        )
    }
}

@Composable
private fun InsertWaypointDialog(
    operatorPrefix: String,
    initialLat: String,
    initialLon: String,
    initialAlt: String,
    initialAcc: Float?,
    location: it.ansmi.tocsar.geo.LocationGateway,
    store: it.ansmi.tocsar.geo.GpsLocalStore,
    onDismiss: () -> Unit,
    onSaved: (WaypointItem, Float?) -> Unit,
    toast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var nameFree by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(initialLat) }
    var lon by remember { mutableStateOf(initialLon) }
    var alt by remember { mutableStateOf(initialAlt) }
    var acc by remember { mutableStateOf(initialAcc) }
    var gpsLoading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val waypointPrefix = "${operatorPrefix}_WP_"
    val preview = formatLocalWaypointName(nameFree, operatorPrefix)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Inserisci waypoint", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = nameFree,
                    onValueChange = { nameFree = it.uppercase() },
                    label = { Text("Nome ($operatorPrefix" + "_WP_ + nome)") },
                    prefix = { Text(waypointPrefix) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Nome finale: ${if (preview == waypointPrefix) "${operatorPrefix}_WP_…" else preview}",
                    color = Color(0xFF616161),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (acc == null) "acc: -" else "acc: ${acc!!.toInt()}m",
                        fontSize = 12.sp,
                        color = Color(0xFF616161),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = !gpsLoading && !saving,
                        onClick = {
                            scope.launch {
                                if (!location.ensurePermission()) {
                                    toast("Permesso GPS negato")
                                    return@launch
                                }
                                gpsLoading = true
                                try {
                                    val fix = location.currentFix()
                                    if (fix == null) {
                                        toast("Nessun fix GPS")
                                        return@launch
                                    }
                                    lat = fix.latitude.formatCoord6()
                                    lon = fix.longitude.formatCoord6()
                                    alt = fix.altitude.formatAlt0()
                                    acc = fix.accuracyM
                                } finally {
                                    gpsLoading = false
                                }
                            }
                        },
                    ) { Text(if (gpsLoading) "GPS…" else "Da GPS") }
                }
                CoordField(lat, { lat = it }, "Lat", Color(0xFF212121), boldCoords = true)
                CoordField(lon, { lon = it }, "Lon", Color(0xFF212121), boldCoords = true)
                CoordField(alt, { alt = it }, "Quota (m)", Color(0xFF212121), boldCoords = true, altitude = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    scope.launch {
                        val fullName = formatLocalWaypointName(nameFree, operatorPrefix)
                        if (fullName == waypointPrefix) {
                            toast("Inserisci un nome per il waypoint")
                            return@launch
                        }
                        val latV = parseCoord(lat); val lonV = parseCoord(lon)
                        val altV = parseCoord(alt)
                        if (latV == null || lonV == null) {
                            toast("Lat e Lon obbligatorie")
                            return@launch
                        }
                        saving = true
                        try {
                            val wp = WaypointItem(fullName, latV, lonV, altV)
                            store.upsertWaypoint(wp)
                            onSaved(wp, acc)
                        } catch (e: Exception) {
                            toast("Salvataggio non riuscito: ${e.message}")
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "…" else "Salva") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Annulla") }
        },
    )
}

@Composable
private fun SaveTrackDialog(
    points: List<TrackPoint>,
    operatorPrefix: String,
    store: it.ansmi.tocsar.geo.GpsLocalStore,
    onDismiss: () -> Unit,
    onSaved: (String, List<TrackPoint>) -> Unit,
    toast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var nameFree by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val trackPrefix = "${operatorPrefix}_TRK_"
    val preview = formatLocalTrackName(nameFree, operatorPrefix)

    suspend fun save(shareAfter: Boolean) {
        val fullName = formatLocalTrackName(nameFree, operatorPrefix)
        if (fullName == trackPrefix) {
            toast("Inserisci un nome per la traccia")
            return
        }
        saving = true
        try {
            val body = encodeTrkFile(points)
            store.upsertTrack(fullName, body)
            onSaved(fullName, points)
            if (shareAfter) {
                sharePlainText(
                    subject = "Traccia $fullName",
                    text = body,
                    fileNameHint = "${safeGpsFileStem(fullName)}.trk",
                )
            }
        } catch (e: Exception) {
            toast("Salvataggio traccia non riuscito: ${e.message}")
            saving = false
        }
    }

    AlertDialog(
        onDismissRequest = { /* obbligatorio scegliere Salva / Annulla */ },
        title = { Text("Salva traccia", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("${points.size} punti registrati", color = Color(0xFF616161), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nameFree,
                    onValueChange = { nameFree = it.uppercase() },
                    label = { Text("Nome ($operatorPrefix" + "_TRK_ + nome)") },
                    prefix = { Text(trackPrefix) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Nome finale: ${if (preview == trackPrefix) "${operatorPrefix}_TRK_…" else preview}",
                    color = Color(0xFF616161),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = !saving,
                    onClick = { scope.launch { save(shareAfter = true) } },
                ) { Text(if (saving) "…" else "Salva + invia") }
                Button(
                    enabled = !saving,
                    onClick = { scope.launch { save(shareAfter = false) } },
                    colors = ButtonDefaults.buttonColors(containerColor = GpsBlue),
                ) { Text(if (saving) "…" else "Salva") }
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Annulla") }
        },
    )
}

@Composable
private fun WpTrkDialog(
    operatorPrefix: String,
    store: it.ansmi.tocsar.geo.GpsLocalStore,
    onDismiss: () -> Unit,
    onUseAsBase: (WaypointItem) -> Unit,
    onShowOnMap: (List<WaypointItem>, List<MapTrackOverlay>) -> Unit,
    toast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var wps by remember { mutableStateOf<List<WaypointItem>>(emptyList()) }
    var trks by remember { mutableStateOf<List<TrackListItem>>(emptyList()) }
    val selectedWp = remember { mutableStateListOf<String>() }
    val selectedTrk = remember { mutableStateListOf<String>() }
    var confirmDeleteWp by remember { mutableStateOf<WaypointItem?>(null) }
    var confirmDeleteTrk by remember { mutableStateOf<TrackListItem?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            wps = loadAllWaypoints(store)
            trks = store.loadTracks()
        } catch (e: Exception) {
            toast("Caricamento WP/TRK: ${e.message}")
        } finally {
            loading = false
        }
    }

    suspend fun reload() {
        wps = loadAllWaypoints(store)
        trks = store.loadTracks()
    }

    fun shareWp(wp: WaypointItem) {
        val body = encodeWaypointFile(wp.name, wp.lat, wp.lon, wp.alt)
        sharePlainText(
            subject = "Waypoint ${wp.name}",
            text = body,
            fileNameHint = "${safeGpsFileStem(wp.name)}.wpt.txt",
        )
    }

    val missionWps = wps.filter { !it.isLocal }
    val localWps = wps.filter { it.isLocal }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("WP & TRK", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (loading) {
                    Text("Caricamento WP missione e locali…", color = Color(0xFF616161), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    "WP MISSIONE (${missionWps.size}) — flag = MAPPA",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                )
                if (!loading && missionWps.isEmpty()) {
                    Text("Nessun waypoint di missione.", color = Color(0xFF616161), fontSize = 13.sp)
                }
                missionWps.forEach { wp ->
                    WaypointRow(
                        wp = wp,
                        selected = selectedWp.contains(wp.name),
                        busy = busy,
                        onSelect = { checked ->
                            if (checked) selectedWp.add(wp.name) else selectedWp.remove(wp.name)
                        },
                        showDelete = false,
                        onShare = { shareWp(wp) },
                        onBase = { onUseAsBase(wp) },
                        onDelete = {},
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "WP LOCALI (${localWps.size}) — flag = MAPPA",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                )
                if (!loading && localWps.isEmpty()) {
                    Text("Nessun waypoint locale.", color = Color(0xFF616161), fontSize = 13.sp)
                }
                localWps.forEach { wp ->
                    WaypointRow(
                        wp = wp,
                        selected = selectedWp.contains(wp.name),
                        busy = busy,
                        onSelect = { checked ->
                            if (checked) selectedWp.add(wp.name) else selectedWp.remove(wp.name)
                        },
                        showDelete = true,
                        onShare = { shareWp(wp) },
                        onBase = { onUseAsBase(wp) },
                        onDelete = { confirmDeleteWp = wp },
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "TRACCE LOCALI (YY_) — flag = MAPPA (${selectedTrk.size} sel.)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                )
                if (trks.isEmpty()) {
                    Text("Nessuna traccia locale.", color = Color(0xFF616161), fontSize = 13.sp)
                }
                trks.forEach { trk ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5))
                            .padding(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedTrk.contains(trk.name),
                                onCheckedChange = { checked ->
                                    if (checked) selectedTrk.add(trk.name) else selectedTrk.remove(trk.name)
                                },
                                enabled = !busy,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(trk.name, fontWeight = FontWeight.Bold)
                                Text("${trk.nPoints} punti", fontSize = 12.sp, color = Color(0xFF616161))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            val body = store.readTrackBody(trk.name)
                                            sharePlainText(
                                                subject = "Traccia ${trk.name}",
                                                text = body,
                                                fileNameHint = "${safeGpsFileStem(trk.name)}.trk",
                                            )
                                        } catch (e: Exception) {
                                            toast("Condivisione non riuscita: ${e.message}")
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            ) { Text("Invia") }
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            val pts = store.fetchTrackPoints(trk.name)
                                            onShowOnMap(
                                                emptyList(),
                                                listOf(MapTrackOverlay(trk.name, pts, TrackColors[0])),
                                            )
                                        } catch (e: Exception) {
                                            toast("Caricamento traccia non riuscito: ${e.message}")
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            ) { Text("MAPPA") }
                            TextButton(
                                enabled = !busy,
                                onClick = { confirmDeleteTrk = trk },
                            ) { Text("Elimina", color = Color(0xFFCE2B37)) }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !busy && !loading,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val picked = pickGpsImportFile()
                                if (picked == null) {
                                    toast("Import annullato")
                                    return@launch
                                }
                                val (fileName, body) = picked
                                when {
                                    looksLikeWaypointContent(body) -> {
                                        val wp = parseWaypointFile(body)
                                        if (wp == null) {
                                            toast("File WP non valido")
                                        } else {
                                            store.upsertWaypoint(wp)
                                            reload()
                                            toast("Importato WP ${wp.name}")
                                        }
                                    }
                                    looksLikeTrkContent(body) -> {
                                        val pts = parseTrkFile(body)
                                        if (pts.size < 2) {
                                            toast("Traccia troppo corta")
                                        } else {
                                            val name = formatLocalTrackName(
                                                fileName.substringBeforeLast('.').ifBlank { "IMPORT" },
                                                operatorPrefix,
                                            )
                                            store.upsertTrack(name, encodeTrkFile(pts))
                                            reload()
                                            toast("Importata traccia $name")
                                        }
                                    }
                                    else -> toast("Formato non riconosciuto (.trk / WP .wpt.txt)")
                                }
                            } catch (e: Exception) {
                                toast("Import non riuscito: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Importa file…") }
                Row {
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Chiudi") }
                    TextButton(
                        enabled = !busy && !loading,
                        onClick = {
                            scope.launch {
                                if (selectedWp.isEmpty() && selectedTrk.isEmpty()) {
                                    toast("Seleziona almeno un WP o TRK (flag)")
                                    return@launch
                                }
                                busy = true
                                try {
                                    val selectedWps = wps.filter { selectedWp.contains(it.name) }
                                    val overlays = mutableListOf<MapTrackOverlay>()
                                    var colorIdx = 0
                                    for (t in trks.filter { selectedTrk.contains(it.name) }) {
                                        val pts = store.fetchTrackPoints(t.name)
                                        if (pts.size < 2) continue
                                        overlays.add(
                                            MapTrackOverlay(
                                                t.name,
                                                pts,
                                                TrackColors[colorIdx % TrackColors.size],
                                            ),
                                        )
                                        colorIdx++
                                    }
                                    if (selectedTrk.isNotEmpty() && overlays.isEmpty()) {
                                        error("Le tracce selezionate non hanno punti leggibili")
                                    }
                                    onShowOnMap(selectedWps, overlays)
                                } catch (e: Exception) {
                                    toast("Caricamento selezione non riuscito: ${e.message}")
                                    busy = false
                                }
                            }
                        },
                    ) { Text("MAPPA (${selectedWp.size + selectedTrk.size})") }
                }
            }
        },
        dismissButton = null,
    )

    confirmDeleteWp?.let { wp ->
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDeleteWp = null },
            title = { Text("Elimina waypoint", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Eliminare il waypoint locale ${wp.name}?") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                store.deleteWaypoint(wp.name)
                                selectedWp.remove(wp.name)
                                reload()
                                toast("WP eliminato")
                                confirmDeleteWp = null
                            } catch (e: Exception) {
                                toast("Eliminazione WP fallita: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Elimina", color = Color(0xFFCE2B37)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmDeleteWp = null }) { Text("Annulla") }
            },
        )
    }

    confirmDeleteTrk?.let { trk ->
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDeleteTrk = null },
            title = { Text("Elimina traccia", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Eliminare la traccia ${trk.name} dal telefono?") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                store.deleteTrack(trk.name)
                                selectedTrk.remove(trk.name)
                                reload()
                                toast("TRK eliminato")
                                confirmDeleteTrk = null
                            } catch (e: Exception) {
                                toast("Eliminazione TRK fallita: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Elimina", color = Color(0xFFCE2B37)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmDeleteTrk = null }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun WaypointRow(
    wp: WaypointItem,
    selected: Boolean,
    busy: Boolean,
    onSelect: (Boolean) -> Unit,
    showDelete: Boolean,
    onShare: () -> Unit,
    onBase: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelect,
                enabled = !busy,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(wp.name, fontWeight = FontWeight.Bold)
                Text(
                    "${wp.lat.formatCoord6()} / ${wp.lon.formatCoord6()}" +
                        (wp.alt?.let { " / ${it.formatAlt0()}m" } ?: ""),
                    fontSize = 12.sp,
                    color = Color(0xFF616161),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(enabled = !busy, onClick = onShare) { Text("Invia") }
            TextButton(enabled = !busy, onClick = onBase) { Text("BASE") }
            if (showDelete) {
                TextButton(enabled = !busy, onClick = onDelete) {
                    Text("Elimina", color = Color(0xFFCE2B37))
                }
            }
        }
    }
}

@Composable
private fun CoordsColumn(
    title: String,
    accuracy: Float?,
    buttonLabel: String,
    buttonColor: Color,
    lat: String,
    lon: String,
    alt: String,
    onLat: (String) -> Unit,
    onLon: (String) -> Unit,
    onAlt: (String) -> Unit,
    latLabel: String,
    lonLabel: String,
    altLabel: String,
    onGps: () -> Unit,
    card: Color,
    fg: Color,
    sub: Color,
    modifier: Modifier = Modifier,
) {
    val accColor = when {
        accuracy == null -> Color.Gray
        accuracy <= 20f -> Color(0xFF4CAF50)
        accuracy <= 50f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(card)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = fg)
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accColor))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (accuracy == null) "acc: -" else "acc: ${accuracy.toInt()}m",
                color = sub,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        GpsActionButton(buttonLabel, buttonColor, Modifier.fillMaxWidth(), compact = true, onClick = onGps)
        Spacer(modifier = Modifier.height(8.dp))
        CoordField(lat, onLat, latLabel, fg, boldCoords = true)
        CoordField(lon, onLon, lonLabel, fg, boldCoords = true)
        CoordField(alt, onAlt, altLabel, fg, boldCoords = true, altitude = true)
    }
}

@Composable
private fun CoordField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    fg: Color,
    boldCoords: Boolean = false,
    altitude: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        textStyle = TextStyle(
            color = fg,
            fontSize = if (boldCoords) 18.sp else 14.sp,
            fontWeight = if (boldCoords) FontWeight.Bold else FontWeight.Normal,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (altitude) KeyboardType.Number else KeyboardType.Decimal,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = fg,
            unfocusedTextColor = fg,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun GpsActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .height(if (compact) 40.dp else 46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 13.sp else 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatDistance(m: Double?): String {
    if (m == null) return "-"
    return if (m >= 1000) "${((m / 1000.0) * 100).toInt() / 100.0} km" else "${m.toInt()} m"
}

private fun formatLocalWaypointName(freeName: String, operatorPrefix: String): String {
    val prefix = operatorPrefix.trim().uppercase().ifBlank { "GPS" }
    var free = sanitizeLocalGpsStem(freeName)
    if (free.startsWith("${prefix}_WP_")) free = free.removePrefix("${prefix}_WP_")
    if (free.startsWith("${prefix}_")) free = free.removePrefix("${prefix}_")
    if (free.startsWith("ZZ_")) free = free.removePrefix("ZZ_")
    if (free.startsWith("ZZ")) free = free.removePrefix("ZZ").removePrefix("_")
    free = free.trim('_').ifBlank { "WP" }
    return "${prefix}_WP_$free"
}

private fun formatLocalTrackName(freeName: String, operatorPrefix: String): String {
    val prefix = operatorPrefix.trim().uppercase().ifBlank { "GPS" }
    var free = sanitizeLocalGpsStem(freeName)
    if (free.startsWith("${prefix}_TRK_")) free = free.removePrefix("${prefix}_TRK_")
    if (free.startsWith("${prefix}_")) free = free.removePrefix("${prefix}_")
    if (free.startsWith("YY_")) free = free.removePrefix("YY_")
    if (free.startsWith("YY")) free = free.removePrefix("YY").removePrefix("_")
    free = free.trim('_').ifBlank { "TRK" }
    return "${prefix}_TRK_$free"
}

/** Spazi → _; toglie caratteri strani; senza estensione .trk/.txt. */
private fun sanitizeLocalGpsStem(raw: String): String {
    var free = raw.trim()
    free = free.substringBeforeLast('.').ifBlank { free }
    free = free.uppercase()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^A-Z0-9_\\-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
    return free
}

/** Relative heading to target, same as TocAppBuild: ((bearing - heading + 540) % 360) - 180 */
private fun relativeArrowDeg(bearingToTarget: Double, headingDeg: Double): Double =
    ((bearingToTarget - headingDeg + 540.0) % 360.0) - 180.0

@Composable
private fun NavigationArrow(
    rotationDeg: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.rotate(rotationDeg)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.02f)
            lineTo(w * 0.18f, h * 0.92f)
            lineTo(w * 0.5f, h * 0.72f)
            lineTo(w * 0.82f, h * 0.92f)
            close()
        }
        drawPath(path, color = color)
    }
}
