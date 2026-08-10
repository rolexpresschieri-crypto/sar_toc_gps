package it.ansmi.tocsar.geo

interface GpsLocalStore {
    suspend fun loadWaypoints(): List<WaypointItem>
    suspend fun upsertWaypoint(wp: WaypointItem)
    suspend fun deleteWaypoint(name: String)

    suspend fun loadTracks(): List<TrackListItem>
    suspend fun upsertTrack(name: String, trkBody: String)
    suspend fun deleteTrack(name: String)
    suspend fun readTrackBody(name: String): String
    suspend fun fetchTrackPoints(name: String): List<TrackPoint>
}

expect fun createGpsLocalStore(): GpsLocalStore

/** Pick a text GPS file; returns name+content or null if cancelled. */
expect suspend fun pickGpsImportFile(): Pair<String, String>?
