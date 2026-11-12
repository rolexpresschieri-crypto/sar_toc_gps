package it.ansmi.tocsar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ansmi.tocsar.geo.TravelCourse
import it.ansmi.tocsar.geo.acceptFix
import it.ansmi.tocsar.geo.createCompassGateway
import it.ansmi.tocsar.geo.createLocationGateway
import it.ansmi.tocsar.geo.isFresh
import org.jetbrains.compose.resources.painterResource
import tocsar.composeapp.generated.resources.Res
import tocsar.composeapp.generated.resources.goniometro
import kotlin.math.min

private val CompassBlue = Color(0xFF1565C0)

@Composable
fun CompassScreen(onBack: () -> Unit) {
    val compass = remember { createCompassGateway() }
    val location = remember { createLocationGateway() }
    var heading by remember { mutableStateOf<Double?>(null) }
    var travelCourse by remember { mutableStateOf<TravelCourse?>(null) }
    var nightMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val stopHeading = compass.watchHeading { heading = it }
        val stopCourse = location.watchFixes(2f) { fix ->
            travelCourse = travelCourse.acceptFix(fix)
        }
        onDispose {
            stopHeading()
            stopCourse()
        }
    }

    AppBackHandler(enabled = true, onBack = onBack)

    val useGpsCourse = travelCourse.isFresh()
    val travelHeading = if (useGpsCourse) travelCourse?.degrees else heading
    val headingLabel = if (useGpsCourse) {
        "Rotta: ${travelHeading?.let { "${it.toInt()}°" } ?: "-"}"
    } else {
        "Nord: ${travelHeading?.let { "${it.toInt()}°" } ?: "-"}"
    }
    val bg = if (nightMode) Color(0xFF121212) else Color(0xFFF3EEF6)
    val fg = if (nightMode) Color.White else Color(0xFF212121)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CompassBlue)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "↑",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(
                text = "BUSSOLA",
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
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = headingLabel,
                color = fg,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val compassSize = min(420f, min(maxWidth.value, maxHeight.value) - 8f).dp
                Box(modifier = Modifier.size(compassSize), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(Res.drawable.goniometro),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(-((travelHeading ?: 0.0).toFloat())),
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
                        text = headingLabel,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
