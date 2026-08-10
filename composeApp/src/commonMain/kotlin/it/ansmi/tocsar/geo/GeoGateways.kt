package it.ansmi.tocsar.geo

interface LocationGateway {
    suspend fun ensurePermission(): Boolean
    suspend fun currentFix(): GeoFix?
    /** Avvia aggiornamenti; ritorna funzione di cancel. [gpsOnly] evita fix di rete (salti TRK). */
    fun watchFixes(minDistanceM: Float = 2f, gpsOnly: Boolean = false, onFix: (GeoFix) -> Unit): () -> Unit
}

interface CompassGateway {
    /** Avvia bussola; ritorna funzione di cancel. Heading gradi 0..360 o null. */
    fun watchHeading(onHeading: (Double?) -> Unit): () -> Unit
}

expect fun createLocationGateway(): LocationGateway
expect fun createCompassGateway(): CompassGateway
