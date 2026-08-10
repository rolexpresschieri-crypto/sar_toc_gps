package it.ansmi.tocsar.geo

/** Offset utente (°): correzione prua/mappa dopo calibrazione (come TocAppBuild). */
expect fun loadCompassHeadingOffset(): Double

expect fun saveCompassHeadingOffset(degrees: Double)
