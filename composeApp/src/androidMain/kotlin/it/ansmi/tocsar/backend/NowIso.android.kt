package it.ansmi.tocsar.backend

import java.time.Instant

internal actual fun nowIso(): String = Instant.now().toString()
