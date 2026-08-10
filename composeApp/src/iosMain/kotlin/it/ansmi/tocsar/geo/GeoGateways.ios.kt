package it.ansmi.tocsar.geo

actual fun createLocationGateway(): LocationGateway = object : LocationGateway {
    override suspend fun ensurePermission(): Boolean = false
    override suspend fun currentFix(): GeoFix? = null
    override fun watchFixes(minDistanceM: Float, gpsOnly: Boolean, onFix: (GeoFix) -> Unit): () -> Unit = {}
}

actual fun createCompassGateway(): CompassGateway = object : CompassGateway {
    override fun watchHeading(onHeading: (Double?) -> Unit): () -> Unit = {}
}
