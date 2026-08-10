package it.ansmi.tocsar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

actual fun currentFixClock(): String = "--/--/---- --:--"

@Composable
actual fun PlatformMapLayer(
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
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF37474F)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Mappa nativa su Android", color = Color.White)
    }
}
