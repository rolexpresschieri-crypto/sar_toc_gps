package it.ansmi.tocsar.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tocsar.composeapp.generated.resources.Res
import tocsar.composeapp.generated.resources.logo_ente_ana_to
import tocsar.composeapp.generated.resources.logo_ente_nvansmi
import tocsar.composeapp.generated.resources.logo_ucrs

fun normalizeEnteCode(orgCode: String?): String =
    orgCode?.trim()?.uppercase().orEmpty()

fun enteLogoResource(orgCode: String?): DrawableResource? {
    return when (normalizeEnteCode(orgCode)) {
        "NVANSMI" -> Res.drawable.logo_ente_nvansmi
        "ANA_TO" -> Res.drawable.logo_ente_ana_to
        "" -> null
        else -> Res.drawable.logo_ucrs
    }
}

@Composable
fun EnteLogoImage(
    orgCode: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    alignment: Alignment = Alignment.Center,
) {
    val res = enteLogoResource(orgCode) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = contentDescription ?: "Logo ${normalizeEnteCode(orgCode)}",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        alignment = alignment,
    )
}
