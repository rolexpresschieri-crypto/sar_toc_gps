package it.ansmi.tocsar.geo

/**
 * WP/TRK di missione dal TOC (Storage + mission_gps_files).
 * I WP locali restano sul telefono; ZZ_ nei file TOC vengono copiati in locale.
 */
data class MissionGpsContent(
    val waypoints: List<WaypointItem>,
    val tracks: List<MapTrackOverlay>,
)

suspend fun loadAllWaypoints(
    store: GpsLocalStore,
    missionFromToc: List<WaypointItem>,
): List<WaypointItem> {
    var local = store.loadWaypoints()
    val localNames = local.map { it.name.uppercase() }.toSet()

    var migrated = false
    for (wp in missionFromToc) {
        val upper = wp.name.uppercase()
        if (!upper.startsWith(AppWaypointPrefix)) continue
        if (localNames.contains(upper)) continue
        store.upsertWaypoint(
            WaypointItem(
                name = wp.name,
                lat = wp.lat,
                lon = wp.lon,
                alt = wp.alt,
                isLocal = true,
            ),
        )
        migrated = true
    }
    if (migrated) {
        local = store.loadWaypoints()
    }
    val localNames2 = local.map { it.name.uppercase() }.toSet()
    val mission = missionFromToc.filter { w ->
        val upper = w.name.uppercase()
        if (upper.startsWith(AppWaypointPrefix)) return@filter false
        !localNames2.contains(upper)
    }
    return local + mission
}
