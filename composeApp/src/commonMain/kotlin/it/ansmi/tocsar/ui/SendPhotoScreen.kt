package it.ansmi.tocsar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ansmi.tocsar.decodeJpegImageBitmap
import it.ansmi.tocsar.geo.GeoFix
import it.ansmi.tocsar.geo.createLocationGateway
import it.ansmi.tocsar.geo.formatCoord6
import it.ansmi.tocsar.pickFieldPhotoJpeg
import it.ansmi.tocsar.ui.theme.TacticalGreen
import it.ansmi.tocsar.ui.theme.TacticalNavy
import it.ansmi.tocsar.ui.theme.TacticalRed
import it.ansmi.tocsar.ui.theme.TacticalYellow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
fun SendPhotoScreen(
    operatorCode: String,
    isSending: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSend: (jpeg: ByteArray, fix: GeoFix, note: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var gpsFix by remember { mutableStateOf<GeoFix?>(null) }
    var gpsLabel by remember { mutableStateOf("GPS: in attesa di fix…") }
    var pickHint by remember { mutableStateOf("Nessuna foto selezionata") }
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val location = createLocationGateway()
        if (!location.ensurePermission()) {
            gpsLabel = "GPS: permesso assente — serve un fix per inviare"
            return@LaunchedEffect
        }
        val fix = withTimeoutOrNull(8_000L) { location.currentFix() }
        if (fix != null) {
            gpsFix = fix
            val acc = fix.accuracyM.takeIf { it > 0f }?.let { " ±${it.roundToInt()} m" }.orEmpty()
            gpsLabel = "GPS ${fix.latitude.formatCoord6()} / ${fix.longitude.formatCoord6()}$acc"
        } else {
            gpsLabel = "GPS: nessun fix — esci all’aperto e riprova"
        }
    }

    val canSend = !isSending && !picking && jpeg != null && gpsFix != null
    val preview = remember(jpeg) { jpeg?.let { decodeJpegImageBitmap(it) } }

    fun pick(fromCamera: Boolean) {
        if (picking || isSending) return
        scope.launch {
            picking = true
            try {
                val bytes = pickFieldPhotoJpeg(fromCamera)
                if (bytes == null || bytes.isEmpty()) {
                    pickHint = "Selezione annullata"
                } else {
                    jpeg = bytes
                    pickHint = "Questa foto verrà inviata · ${bytes.size / 1024} KB"
                }
            } finally {
                picking = false
            }
        }
    }

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
            text = "INVIA FOTO A TOC",
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
            text = "Dopo l’invio i JPEG sono in Supabase → Storage → bucket squad-photos (percorso nella colonna storage_path).",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { pick(fromCamera = true) },
                enabled = !isSending && !picking,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TacticalNavy),
            ) {
                Text("Fotocamera", fontWeight = FontWeight.ExtraBold)
            }
            Button(
                onClick = { pick(fromCamera = false) },
                enabled = !isSending && !picking,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TacticalNavy),
            ) {
                Text("Galleria", fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (picking) "Apertura fotocamera/galleria…" else pickHint,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        if (preview != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Image(
                bitmap = preview,
                contentDescription = "Anteprima da inviare",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, TacticalYellow, RoundedCornerShape(12.dp)),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= 200) note = it },
            label = { Text("Nota (facoltativa, max 200)") },
            enabled = !isSending,
            modifier = Modifier.fillMaxWidth().height(100.dp),
            colors = photoFieldColors(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = gpsLabel,
            color = Color.White.copy(alpha = 0.85f),
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
                val bytes = jpeg ?: return@Button
                val fix = gpsFix ?: return@Button
                onSend(bytes, fix, note)
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
                    modifier = Modifier.height(22.dp).padding(vertical = 8.dp),
                )
            } else {
                Text("INVIA AL TOC", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onBack,
            enabled = !isSending && !picking,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TacticalGreen),
        ) {
            Text("Indietro", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun photoFieldColors() = TextFieldDefaults.colors(
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
