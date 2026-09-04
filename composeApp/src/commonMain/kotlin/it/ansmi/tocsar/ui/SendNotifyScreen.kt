package it.ansmi.tocsar.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ansmi.tocsar.geo.GeoFix
import it.ansmi.tocsar.geo.createLocationGateway
import it.ansmi.tocsar.geo.formatCoord6
import it.ansmi.tocsar.ui.theme.TacticalGreen
import it.ansmi.tocsar.ui.theme.TacticalNavy
import it.ansmi.tocsar.ui.theme.TacticalRed
import it.ansmi.tocsar.ui.theme.TacticalYellow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private val NotifyKinds = listOf("Supporto", "Trovato", "Sanitario", "Altro")

internal fun buildTocAlarmMessage(
    kind: String,
    note: String,
    fix: GeoFix?,
): String {
    val parts = mutableListOf(kind.trim())
    note.trim().takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    if (fix != null) {
        val acc = fix.accuracyM.takeIf { it > 0f }?.let { " ±${it.roundToInt()}m" }.orEmpty()
        parts.add("GPS ${fix.latitude.formatCoord6()} / ${fix.longitude.formatCoord6()}$acc")
    }
    return parts.joinToString(" · ")
}

@Composable
fun SendNotifyScreen(
    operatorCode: String,
    isSending: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSend: (message: String) -> Unit,
) {
    var kind by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var gpsFix by remember { mutableStateOf<GeoFix?>(null) }
    var gpsLabel by remember { mutableStateOf("GPS: in attesa di fix…") }

    LaunchedEffect(Unit) {
        val location = createLocationGateway()
        if (!location.ensurePermission()) {
            gpsLabel = "GPS: permesso assente (si invia comunque)"
            return@LaunchedEffect
        }
        val fix = withTimeoutOrNull(8_000L) { location.currentFix() }
        if (fix != null) {
            gpsFix = fix
            val acc = fix.accuracyM.takeIf { it > 0f }?.let { " ±${it.roundToInt()} m" }.orEmpty()
            gpsLabel = "GPS ${fix.latitude.formatCoord6()} / ${fix.longitude.formatCoord6()}$acc"
        } else {
            gpsLabel = "GPS: nessun fix (si invia comunque)"
        }
    }

    val noteRequired = kind == "Altro"
    val canSend =
        !isSending &&
            kind != null &&
            (!noteRequired || note.trim().length >= 3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0607).copy(alpha = 0.78f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "INVIA NOTIFICA A TOC",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = operatorCode,
            color = TacticalYellow,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Arriva in sala operativa (tabella allarmi). Scegli un tipo; la nota è facoltativa tranne per Altro.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotifyKinds.take(2).forEach { label ->
                NotifyKindChip(
                    label = label,
                    selected = kind == label,
                    enabled = !isSending,
                    onClick = { kind = label },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotifyKinds.drop(2).forEach { label ->
                NotifyKindChip(
                    label = label,
                    selected = kind == label,
                    enabled = !isSending,
                    onClick = { kind = label },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 200) note = it },
            label = { Text(if (noteRequired) "Nota (obbligatoria)" else "Nota (facoltativa)") },
            enabled = !isSending,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            colors = notifyFieldColors(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = gpsLabel,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorMessage,
                color = TacticalYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                val selected = kind ?: return@Button
                onSend(buildTocAlarmMessage(selected, note, gpsFix))
            },
            enabled = canSend,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TacticalRed),
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(22.dp).padding(vertical = 2.dp),
                )
            } else {
                Text("INVIA AL TOC", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onBack,
            enabled = !isSending,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TacticalNavy),
        ) {
            Text("Indietro", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun NotifyKindChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) TacticalYellow else Color.White.copy(alpha = 0.35f)
    val bg = if (selected) TacticalRed.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun notifyFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color.White.copy(alpha = 0.5f),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
    disabledLabelColor = Color.White.copy(alpha = 0.4f),
    cursorColor = TacticalGreen,
    focusedIndicatorColor = TacticalGreen,
    unfocusedIndicatorColor = Color.White.copy(alpha = 0.4f),
)
