package it.ansmi.tocsar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ansmi.tocsar.ui.theme.TacticalGreen
import it.ansmi.tocsar.ui.theme.TacticalNavy
import it.ansmi.tocsar.ui.theme.TacticalYellow

@Composable
fun LoginScreen(
    rememberedOrgCode: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onChangeOrganization: () -> Unit,
    onLogin: (organizationCode: String, operatorCode: String, password: String) -> Unit,
) {
    var orgCode by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val savedOrg = rememberedOrgCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
    val orgToSend = savedOrg ?: orgCode.trim().uppercase()
    val canSubmit =
        orgToSend.isNotEmpty() && code.trim().isNotEmpty() && password.isNotEmpty() && !isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0607).copy(alpha = 0.72f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Log-in operatore",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Codice TOC + codice + password",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (savedOrg != null) {
            Text(
                text = "TOC: $savedOrg",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Cambia TOC",
                color = TacticalYellow,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(enabled = !isLoading, onClick = onChangeOrganization)
                    .padding(8.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            OutlinedTextField(
                value = orgCode,
                onValueChange = { orgCode = it.uppercase() },
                label = { Text("Codice TOC") },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
                colors = fieldColors(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase() },
            label = { Text("Codice operatore") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
            ),
            colors = fieldColors(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (passwordVisible) KeyboardType.Text else KeyboardType.Password,
            ),
            trailingIcon = {
                PasswordEye(
                    visible = passwordVisible,
                    enabled = !isLoading,
                    onClick = { passwordVisible = !passwordVisible },
                )
            },
            colors = fieldColors(),
        )
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = TacticalYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { onLogin(orgToSend, code.trim(), password) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TacticalGreen),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(22.dp).padding(vertical = 2.dp),
                )
            } else {
                Text("Entra", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onBack,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TacticalNavy),
        ) {
            Text("Indietro", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun PasswordEye(visible: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            drawOval(
                color = Color.White,
                topLeft = Offset(w * 0.06f, h * 0.28f),
                size = Size(w * 0.88f, h * 0.44f),
                style = Stroke(width = 1.8.dp.toPx()),
            )
            drawCircle(color = Color.White, radius = w * 0.11f, center = Offset(w / 2f, h / 2f))
            if (!visible) {
                drawLine(
                    color = Color.White,
                    start = Offset(w * 0.18f, h * 0.78f),
                    end = Offset(w * 0.82f, h * 0.22f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
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
