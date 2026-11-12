package it.ansmi.tocsar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import it.ansmi.tocsar.geo.OperatorGpsTracking
import it.ansmi.tocsar.geo.trackHorizDistanceM
import it.ansmi.tocsar.openAppSystemSettings
import it.ansmi.tocsar.ui.theme.TacticalDisabled
import it.ansmi.tocsar.ui.theme.TacticalGreen
import it.ansmi.tocsar.ui.theme.TacticalMuted
import it.ansmi.tocsar.ui.theme.TacticalNavy
import it.ansmi.tocsar.ui.theme.TacticalRed
import it.ansmi.tocsar.ui.theme.TacticalYellow

private val WhiteTitleStyle = TextStyle(
    color = Color.White,
    fontWeight = FontWeight.ExtraBold,
    shadow = Shadow(color = Color.Black, blurRadius = 8f),
)

private val WhiteBodyStyle = TextStyle(
    color = Color.White,
    fontWeight = FontWeight.SemiBold,
    shadow = Shadow(color = Color.Black, blurRadius = 8f),
)

data class OperatorSession(
    val sessionId: String,
    val eventId: String,
    val operatorId: String,
    val operatorCode: String,
    val displayName: String,
    val loginLabel: String,
    val organizationId: String,
    val organizationCode: String,
)

@Composable
fun HomeScreen(
    session: OperatorSession?,
    tocMessage: String?,
    gpsStatusLabel: String?,
    rememberedOrgCode: String? = null,
    onResetNotification: () -> Unit,
    onLogout: () -> Unit,
    onSendNotify: () -> Unit,
    onSendPhoto: () -> Unit,
    onOpenGps: () -> Unit,
    onOpenOnlineOperators: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = { openAppSystemSettings() },
) {
    val isLogged = session != null
    val scroll = rememberScrollState()
    var menuOpen by remember { mutableStateOf(false) }
    val enteCode = session?.organizationCode ?: rememberedOrgCode

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 10.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (!enteCode.isNullOrBlank()) {
                        EnteLogoImage(
                            orgCode = enteCode,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier
                                .height(76.dp)
                                .fillMaxWidth(0.7f),
                        )
                    }
                }
                HamburgerToggle(
                    open = false,
                    onClick = { menuOpen = true },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Text(
                text = "Tracking",
                textAlign = TextAlign.Center,
                style = WhiteTitleStyle.copy(fontSize = 32.sp, letterSpacing = 0.8.sp),
            )
            Text(
                text = "Operatori SAR",
                textAlign = TextAlign.Center,
                style = WhiteTitleStyle.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.0.sp,
                ),
                modifier = Modifier.padding(top = 2.dp, bottom = 18.dp),
            )

            NotificationPanel(
                message = tocMessage,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            MainButton(
                label = "Reset notifica",
                backgroundColor = if (isLogged) TacticalNavy else TacticalDisabled,
                foregroundColor = if (isLogged) Color.White else TacticalMuted,
                onClick = if (isLogged) onResetNotification else null,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            val boxColor = if (isLogged) TacticalGreen else Color.Black.copy(alpha = 0.48f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(boxColor, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isLogged && session != null) {
                        "${session.organizationCode} · ${session.operatorCode} · ${session.displayName} + ${session.loginLabel}"
                    } else {
                        "Nessun operatore loggato"
                    },
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                    ),
                )
            }

            if (isLogged) {
                Spacer(modifier = Modifier.height(10.dp))
                gpsStatusLabel?.let { label ->
                    BodyText(
                        text = label,
                        fontSize = 13,
                        color = TacticalYellow,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                FieldSessionStatus()
                MainButton(
                    label = "Impostazioni TOC SAR",
                    backgroundColor = TacticalNavy,
                    foregroundColor = Color.White,
                    onClick = onOpenSettings,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                MainButton(
                    label = "Log-out",
                    backgroundColor = TacticalRed,
                    foregroundColor = Color.White,
                    onClick = onLogout,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Operatori SAR / unità cinofile: login = tracking, allarmi e push",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            }
        }

        if (menuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .background(Color.Black.copy(alpha = 0.78f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { menuOpen = false },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 16.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { menuOpen = false },
                        ),
                ) {
                    HamburgerToggle(open = true, onClick = { menuOpen = false })
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {},
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    HomeSideMenu(
                        isLogged = isLogged,
                        onSendNotify = {
                            menuOpen = false
                            onSendNotify()
                        },
                        onSendPhoto = {
                            menuOpen = false
                            onSendPhoto()
                        },
                        onOpenGps = {
                            menuOpen = false
                            onOpenGps()
                        },
                        onOpenOnlineOperators = onOpenOnlineOperators?.let { open ->
                            {
                                menuOpen = false
                                open()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSideMenu(
    isLogged: Boolean,
    onSendNotify: () -> Unit,
    onSendPhoto: () -> Unit,
    onOpenGps: () -> Unit,
    onOpenOnlineOperators: (() -> Unit)?,
) {
    val menuBg = if (isLogged) TacticalYellow else TacticalDisabled
    val menuFg = if (isLogged) Color.Black else TacticalMuted
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MainButton(
            label = "INVIA NOTIFICA A TOC",
            backgroundColor = menuBg,
            foregroundColor = menuFg,
            onClick = if (isLogged) onSendNotify else null,
        )
        Spacer(modifier = Modifier.height(14.dp))
        MainButton(
            label = "INVIA FOTO A TOC",
            backgroundColor = menuBg,
            foregroundColor = menuFg,
            onClick = if (isLogged) onSendPhoto else null,
        )
        Spacer(modifier = Modifier.height(14.dp))
        MainButton(
            label = "GPS",
            backgroundColor = menuBg,
            foregroundColor = menuFg,
            onClick = if (isLogged) onOpenGps else null,
        )
        if (onOpenOnlineOperators != null) {
            Spacer(modifier = Modifier.height(14.dp))
            MainButton(
                label = "Operatori on line",
                backgroundColor = TacticalYellow,
                foregroundColor = Color.Black,
                onClick = onOpenOnlineOperators,
            )
        }
    }
}

@Composable
private fun HamburgerToggle(open: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .border(2.dp, TacticalYellow, RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 3.dp.toPx()
            if (open) {
                drawLine(
                    color = TacticalYellow,
                    start = Offset(size.width * 0.18f, size.height * 0.18f),
                    end = Offset(size.width * 0.82f, size.height * 0.82f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = TacticalYellow,
                    start = Offset(size.width * 0.82f, size.height * 0.18f),
                    end = Offset(size.width * 0.18f, size.height * 0.82f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            } else {
                val left = size.width * 0.12f
                val right = size.width * 0.88f
                val y1 = size.height * 0.28f
                val y2 = size.height * 0.5f
                val y3 = size.height * 0.72f
                drawLine(TacticalYellow, Offset(left, y1), Offset(right, y1), stroke, StrokeCap.Round)
                drawLine(TacticalYellow, Offset(left, y2), Offset(right, y2), stroke, StrokeCap.Round)
                drawLine(TacticalYellow, Offset(left, y3), Offset(right, y3), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun FieldSessionStatus() {
    val running = GpsFieldSession.tripRunning
    val wpCount = GpsFieldSession.overlayWaypoints.size
    val trkCount = GpsFieldSession.overlayTracks.size
    val trkOn = GpsFieldSession.trkActive || OperatorGpsTracking.isTrkRecording()
    if (!running && wpCount == 0 && trkCount == 0 && !trkOn && GpsFieldSession.tripFrozenElapsed <= 0L) {
        return
    }
    val elapsed = if (running) GpsFieldSession.tripElapsedMs else GpsFieldSession.tripFrozenElapsed
    val dist = when {
        running && GpsFieldSession.tripLinked ->
            (trackHorizDistanceM(OperatorGpsTracking.trkPointsSnapshot()) - GpsFieldSession.tripDistBase)
                .coerceAtLeast(0.0)
        running -> GpsFieldSession.tripDistM
        else -> GpsFieldSession.tripFrozenDist
    }
    val line = buildString {
        if (running || elapsed > 0L || dist > 0.0) {
            append(if (running) "TIME " else "TIME fermo ")
            append(formatFieldElapsed(elapsed))
            append(" · DIST ")
            append(formatFieldMeters(dist))
        }
        if (trkOn) {
            if (isNotEmpty()) append(" · ")
            append("TRK attivo")
        }
        if (wpCount > 0 || trkCount > 0) {
            if (isNotEmpty()) append(" · ")
            append("$wpCount WP · $trkCount TRK in mappa")
        }
    }
    if (line.isBlank()) return
    BodyText(
        text = line,
        fontSize = 13,
        color = TacticalYellow,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun MainButton(
    label: String,
    backgroundColor: Color,
    foregroundColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = foregroundColor,
            disabledContainerColor = backgroundColor,
            disabledContentColor = foregroundColor,
        ),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun NotificationPanel(
    message: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(
                if (message.isNullOrBlank()) TacticalNavy else TacticalRed,
                RoundedCornerShape(10.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = WhiteBodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun BodyText(
    text: String,
    fontSize: Int = 14,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = WhiteBodyStyle.copy(fontSize = fontSize.sp, color = color),
    )
}
