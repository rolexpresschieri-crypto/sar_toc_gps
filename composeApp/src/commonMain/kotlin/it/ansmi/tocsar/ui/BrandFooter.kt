package it.ansmi.tocsar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ansmi.tocsar.appVersionName

@Composable
fun BrandFooter(
    showVersion: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (showVersion) appVersionName() else "",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "by Rolexpress Digital Service",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
        )
    }
}
