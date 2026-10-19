package it.ansmi.tocsar.ui

import androidx.compose.runtime.Composable

@Composable
expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)
