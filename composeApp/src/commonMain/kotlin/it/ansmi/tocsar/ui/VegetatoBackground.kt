package it.ansmi.tocsar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import it.ansmi.tocsar.TocColors
import it.ansmi.tocsar.VegetatoTextureOpacity
import org.jetbrains.compose.resources.painterResource
import tocsar.composeapp.generated.resources.Res
import tocsar.composeapp.generated.resources.bg_vegetato

@Composable
fun VegetatoBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TocColors.Background),
        )
        Image(
            painter = painterResource(Res.drawable.bg_vegetato),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(VegetatoTextureOpacity),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
        content()
    }
}
