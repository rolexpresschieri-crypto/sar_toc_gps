package it.ansmi.tocsar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ansmi.tocsar.backend.OnlineOperatorSession
import it.ansmi.tocsar.backend.TocAdminOperatorCode
import it.ansmi.tocsar.backend.TocSarFacade
import it.ansmi.tocsar.ui.theme.TacticalFrame
import it.ansmi.tocsar.ui.theme.TacticalMuted
import it.ansmi.tocsar.ui.theme.TacticalNavy
import it.ansmi.tocsar.ui.theme.TacticalOrange
import it.ansmi.tocsar.ui.theme.TacticalRed
import it.ansmi.tocsar.ui.theme.TacticalYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun OnlineOperatorsScreen(
    facade: TocSarFacade,
    actorCode: String,
    organizationId: String,
    selfSessionId: String,
    onBack: () -> Unit,
    onForcedSelfLogout: () -> Unit,
    toast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var operators by remember { mutableStateOf<List<OnlineOperatorSession>>(emptyList()) }
    var busySessionId by remember { mutableStateOf<String?>(null) }
    var confirmTarget by remember { mutableStateOf<OnlineOperatorSession?>(null) }

    suspend fun reload() {
        operators = facade.loadOnlineOperatorSessions(organizationId)
        loading = false
    }

    LaunchedEffect(facade, organizationId) {
        loading = true
        while (isActive) {
            runCatching { reload() }
                .onFailure { e ->
                    loading = false
                    toast(e.message ?: "Errore elenco operatori")
                }
            delay(3_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1A12))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalNavy)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "←",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                text = "Operatori on line",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.padding(horizontal = 20.dp))
        }

        Text(
            text = "Admin $actorCode · forza log-out · flag «visibile» per gli altri",
            color = TacticalYellow,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$TocAdminOperatorCode vede tutti gli online. Gli altri vedono solo chi ha il flag " +
                "(anche $TocAdminOperatorCode, se visibile).",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            textAlign = TextAlign.Center,
        )

        when {
            loading && operators.isEmpty() -> {
                Text(
                    "Caricamento…",
                    color = TacticalMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
            operators.isEmpty() -> {
                Text(
                    "Nessun operatore on line",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(operators, key = { it.sessionId }) { op ->
                        OnlineOperatorRow(
                            op = op,
                            isSelf = op.sessionId == selfSessionId,
                            busy = busySessionId == op.sessionId,
                            onForceLogout = { confirmTarget = op },
                            onPeerVisibleChange = { checked ->
                                scope.launch {
                                    busySessionId = op.sessionId
                                    try {
                                        facade.setPeerVisible(op.sessionId, checked, actorCode)
                                        reload()
                                        toast(
                                            if (checked) {
                                                "${op.operatorCode}: visibile agli altri"
                                            } else {
                                                "${op.operatorCode}: nascosto (solo $TocAdminOperatorCode lo vede)"
                                            },
                                        )
                                    } catch (e: Exception) {
                                        toast(e.message ?: "Visibilità non aggiornata")
                                    } finally {
                                        busySessionId = null
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    confirmTarget?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (busySessionId == null) confirmTarget = null
            },
            title = {
                Text("Forza log-out", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text(
                    "Disconnettere ${target.operatorCode} (${target.operatorName})?\n" +
                        "Sparisce dalla mappa finché non fa di nuovo Log-in.",
                )
            },
            confirmButton = {
                Button(
                    enabled = busySessionId == null,
                    onClick = {
                        scope.launch {
                            busySessionId = target.sessionId
                            try {
                                facade.forceLogoutOperatorSession(target, actorCode)
                                toast("Log-out forzato: ${target.operatorCode}")
                                if (target.sessionId == selfSessionId) {
                                    confirmTarget = null
                                    onForcedSelfLogout()
                                    return@launch
                                }
                                confirmTarget = null
                                reload()
                            } catch (e: Exception) {
                                toast(e.message ?: "Force log-out non riuscito")
                            } finally {
                                busySessionId = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalRed),
                ) {
                    Text("Forza log-out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    enabled = busySessionId == null,
                    onClick = { confirmTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalMuted),
                ) {
                    Text("Annulla")
                }
            },
        )
    }
}

@Composable
private fun OnlineOperatorRow(
    op: OnlineOperatorSession,
    isSelf: Boolean,
    busy: Boolean,
    onForceLogout: () -> Unit,
    onPeerVisibleChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, TacticalFrame, RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            text = buildString {
                append(op.operatorCode)
                append(" · ")
                append(op.operatorName)
                if (isSelf) append(" (tu)")
            },
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (op.hasGpsFix) "GPS: posizione nota" else "GPS: nessun fix ancora",
            color = if (op.hasGpsFix) Color(0xFF81C784) else TacticalOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        op.loginAtIso?.takeIf { it.isNotBlank() }?.let { login ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Login: $login",
                color = TacticalMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = op.peerVisible,
                onCheckedChange = { if (!busy) onPeerVisibleChange(it) },
                enabled = !busy,
            )
            Text(
                text = if (op.peerVisible) {
                    "Visibile in mappa (agli altri operatori)"
                } else {
                    "Nascosto: solo $TocAdminOperatorCode lo vede in mappa"
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !busy) { onPeerVisibleChange(!op.peerVisible) },
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onForceLogout,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TacticalOrange,
                contentColor = Color.White,
                disabledContainerColor = TacticalMuted,
            ),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            Text(
                text = if (busy) "…" else "Forza log-out",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
            )
        }
    }
}
