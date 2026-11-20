package it.ansmi.tocsar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.zIndex
import it.ansmi.tocsar.AndroidLatestManifest
import it.ansmi.tocsar.appVersionName
import it.ansmi.tocsar.downloadAndInstallApk
import it.ansmi.tocsar.openExternalUrl
import it.ansmi.tocsar.ui.theme.TacticalGreen
import it.ansmi.tocsar.ui.theme.TacticalMuted
import it.ansmi.tocsar.ui.theme.TacticalYellow
import kotlinx.coroutines.launch

@Composable
fun ForceUpdateOverlay(info: AndroidLatestManifest) {
    AppBackHandler(enabled = true) { /* bloccato finché non aggiorna */ }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A3066), RoundedCornerShape(16.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Aggiornamento obbligatorio",
                color = TacticalYellow,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = info.message.trim().ifBlank {
                    "È disponibile una nuova versione di TOC SAR. L'aggiornamento è obbligatorio per continuare."
                },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Installata: ${appVersionName()}\nNuova: ${info.versionName.ifBlank { info.versionCode.toString() }}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        status = "Download in corso…"
                        val err = downloadAndInstallApk(info.apkUrl)
                        busy = false
                        status = err
                            ?: "Se compare la schermata di installazione, conferma. Altrimenti apri il file APK scaricato."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TacticalGreen,
                    contentColor = Color.White,
                    disabledContainerColor = TacticalMuted,
                ),
            ) {
                Text(
                    text = if (busy) "Download…" else "Scarica e installa",
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                enabled = !busy,
                onClick = { openExternalUrl(info.apkUrl) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A4580),
                    contentColor = Color.White,
                    disabledContainerColor = TacticalMuted,
                ),
            ) {
                Text(
                    text = "Apri nel browser",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = status
                    ?: "Usa «Scarica e installa» (avvia il download di sistema). Su Samsung, se blocca: Impostazioni → Sicurezza → Blocco automatico OFF. Oppure apri scarica.html dal browser.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
