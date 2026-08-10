package it.ansmi.tocsar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import tocsar.composeapp.generated.resources.Res
import tocsar.composeapp.generated.resources.black_ops_one
import tocsar.composeapp.generated.resources.logo_ansmi

@Composable
fun SplashScreen() {
    val logoScale = remember { Animatable(0.28f) }
    val titleOpacity = remember { Animatable(0f) }
    val signatureOpacity = remember { Animatable(0f) }
    val blackOps = FontFamily(Font(Res.font.black_ops_one))

    LaunchedEffect(Unit) {
        launch {
            logoScale.animateTo(
                targetValue = 1.55f,
                animationSpec = tween(durationMillis = 4300, easing = EaseOutCubic),
            )
        }
        launch {
            titleOpacity.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ((0.9f - 0.24f) * 4300).toInt(),
                    delayMillis = (0.24f * 4300).toInt(),
                    easing = EaseInOut,
                ),
            )
        }
        launch {
            signatureOpacity.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ((1f - 0.58f) * 4300).toInt(),
                    delayMillis = (0.58f * 4300).toInt(),
                    easing = EaseIn,
                ),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 122.dp)
                .scale(logoScale.value)
                .size(210.dp)
                .clip(CircleShape),
        ) {
            Image(
                painter = painterResource(Res.drawable.logo_ansmi),
                contentDescription = "Logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Text(
            text = "TACTICAL\nOPERATIONS\nCENTER",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 246.dp)
                .alpha(titleOpacity.value),
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = Color.White,
                fontSize = 44.sp,
                lineHeight = (44 * 1.04f).sp,
                fontFamily = blackOps,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.6.sp,
                shadow = Shadow(color = Color.Black, blurRadius = 8f),
            ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 38.dp)
                .alpha(signatureOpacity.value)
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = "by R. Ronco",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    shadow = Shadow(color = Color.Black, blurRadius = 8f),
                ),
            )
        }
    }
}