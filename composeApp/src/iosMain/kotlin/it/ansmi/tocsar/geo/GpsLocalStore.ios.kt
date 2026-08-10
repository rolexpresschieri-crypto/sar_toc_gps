package it.ansmi.tocsar.geo

actual fun createGpsLocalStore(): GpsLocalStore = object : GpsLocalStore {
    private val waypoints = mutableListOf<WaypointItem>()
    private val tracks = mutableMapOf<String, String>()

    override suspend fun loadWaypoints(): List<WaypointItem> = waypoints.sortedBy { it.name }

    override suspend fun upsertWaypoint(wp: WaypointItem) {
        val idx = waypoints.indexOfFirst { it.name.equals(wp.name, ignoreCase = true) }
        if (idx >= 0) waypoints[idx] = wp.copy(isLocal = true) else waypoints.add(wp.copy(isLocal = true))
    }

    override suspend fun deleteWaypoint(name: String) {
        waypoints.removeAll { it.name.equals(name, ignoreCase = true) }
    }

    override suspend fun loadTracks(): List<TrackListItem> =
        tracks.map { (name, body) -> TrackListItem(name, parseTrkFile(body).size) }
            .sortedBy { it.name }

    override suspend fun upsertTrack(name: String, trkBody: String) {
        tracks[safeGpsFileStem(name)] = trkBody
    }

    override suspend fun deleteTrack(name: String) {
        tracks.remove(safeGpsFileStem(name))
    }

    override suspend fun readTrackBody(name: String): String =
        tracks[safeGpsFileStem(name)] ?: error("Traccia non trovata: $name")

    override suspend fun fetchTrackPoints(name: String): List<TrackPoint> {
        val points = parseTrkFile(readTrackBody(name))
        if (points.size < 2) error("Traccia $name: ${points.size} punti")
        return points
    }
}

actual suspend fun pickGpsImportFile(): Pair<String, String>? = null
