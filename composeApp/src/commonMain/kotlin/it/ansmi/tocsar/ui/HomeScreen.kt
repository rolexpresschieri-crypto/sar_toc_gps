package it.ansmi.tocsar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ansmi.tocsar.openAppSystemSettings
import it.ansmi.tocsar.ui.theme.TacticalDisabled
import it.ansmi.tocsar.ui.theme.TacticalFrame
import it.ansmi.tocsar.ui.theme.TacticalGreen
import it.ansmi.tocsar.ui.theme.TacticalMuted
import it.ansmi.tocsar.ui.theme.TacticalNavy
import it.ansmi.tocsar.ui.theme.TacticalOrange
import it.ansmi.tocsar.ui.theme.TacticalRed
import it.ansmi.tocsar.ui.theme.TacticalYellow
import org.jetbrains.compose.resources.painterResource
import tocsar.composeapp.generated.resources.Res
import tocsar.composeapp.generated.resources.logo_ucrs

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

private const val UnusedAppHint =
    "Android: Impostazioni -> TOC SAR -> «Gestisci l'app se inutilizzata» -> " +
        "disattiva «Rimuovi le autorizzazioni se l'app non viene usata», " +
        "altrimenti il telefono toglie GPS e notifiche da solo."

data class OperatorSession(
    val sessionId: String,
    val eventId: String,
    val operatorId: String,
    val operatorCode: String,
    val displayName: String,
    val loginLabel: String,
)

@Composable
fun HomeScreen(
    session: OperatorSession?,
    tocMessage: String?,
    gpsStatusLabel: String?,
    onResetNotification: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSendNotify: () -> Unit,
    onSendPhoto: () -> Unit,
    onOpenGps: () -> Unit,
    onOpenSettings: () -> Unit = { openAppSystemSettings() },
) {
    val isLogged = session != null
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scroll)
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TacticalShell {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SarTitleBlock()
                Spacer(modifier = Modifier.height(24.dp))

                NotificationPanel(
                    message = tocMessage,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                BodyText(
                    text = "Reset notifica: solo sul telefono (log «notifica chiusa»). " +
                        "La chiusura evento è solo dal TOC.",
                    fontSize = 12,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                MainButton(
                    label = "Reset notifica",
                    backgroundColor = if (isLogged) TacticalNavy else TacticalDisabled,
                    foregroundColor = if (isLogged) Color.White else TacticalMuted,
                    onClick = if (isLogged) onResetNotification else null,
                    modifier = Modifier.padding(bottom = 20.dp),
                )

                val boxColor = if (isLogged) TacticalGreen else Color.Black.copy(alpha = 0.48f)
                val borderColor = Color.White.copy(alpha = if (isLogged) 0.35f else 0.55f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(boxColor, RoundedCornerShape(12.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (isLogged) {
                            "${session!!.operatorCode} · ${session.displayName} + ${session.loginLabel}"
                        } else {
                            "Nessun operatore loggato"
                        },
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                        ),
                    )
                }

                if (isLogged) {
                    Spacer(modifier = Modifier.height(14.dp))
                    gpsStatusLabel?.let { label ->
                        BodyText(
                            text = label,
                            fontSize = 13,
                            color = TacticalYellow,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    BodyText(
                        text = UnusedAppHint,
                        fontSize = 12,
                        color = TacticalYellow,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    MainButton(
                        label = "Impostazioni TOC SAR",
                        backgroundColor = TacticalNavy,
                        foregroundColor = Color.White,
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                MainButton(
                    label = "Log-in",
                    backgroundColor = if (isLogged) TacticalDisabled else TacticalGreen,
                    foregroundColor = if (isLogged) TacticalMuted else Color.White,
                    onClick = if (!isLogged) onLogin else null,
                )
                Spacer(modifier = Modifier.height(18.dp))
                MainButton(
                    label = "Log-out",
                    backgroundColor = if (isLogged) TacticalOrange else TacticalDisabled,
                    foregroundColor = if (isLogged) Color.White else TacticalMuted,
                    onClick = if (isLogged) onLogout else null,
                )
                Spacer(modifier = Modifier.height(18.dp))
                MainButton(
                    label = "INVIA NOTIFICA A TOC",
                    backgroundColor = if (isLogged) TacticalRed else TacticalDisabled,
                    foregroundColor = if (isLogged) Color.White else TacticalMuted,
                    onClick = if (isLogged) onSendNotify else null,
                )
                Spacer(modifier = Modifier.height(18.dp))
                MainButton(
                    label = "INVIA FOTO A TOC",
                    backgroundColor = if (isLogged) TacticalNavy else TacticalDisabled,
                    foregroundColor = if (isLogged) Color.White else TacticalMuted,
                    onClick = if (isLogged) onSendPhoto else null,
                )
                Spacer(modifier = Modifier.height(18.dp))
                MainButton(
                    label = "GPS",
                    backgroundColor = if (isLogged) TacticalYellow else TacticalDisabled,
                    foregroundColor = if (isLogged) Color.Black else TacticalMuted,
                    onClick = if (isLogged) onOpenGps else null,
                )
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
    }
}

@Composable
private fun SarTitleBlock() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.logo_ucrs),
            contentDescription = "Reparto Cinofilo da Soccorso",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .height(120.dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
        )
        Text(
            text = "Tracking",
            textAlign = TextAlign.Center,
            style = WhiteTitleStyle.copy(fontSize = 32.sp, letterSpacing = 0.8.sp),
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "Operatori SAR",
            textAlign = TextAlign.Center,
            style = WhiteTitleStyle.copy(
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.0.sp,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun TacticalShell(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(42.dp))
                .border(3.dp, TacticalFrame, RoundedCornerShape(42.dp)),
            shape = RoundedCornerShape(42.dp),
            color = Color.Transparent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
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
            .background(TacticalNavy, RoundedCornerShape(10.dp))
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
