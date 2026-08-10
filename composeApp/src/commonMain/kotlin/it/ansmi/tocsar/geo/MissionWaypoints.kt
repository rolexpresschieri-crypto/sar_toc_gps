package it.ansmi.tocsar.geo

/** Stesso Sheet di TocAppBuild: WP di missione (non ZZ_). */
const val WaypointsSheetUrl =
    "https://docs.google.com/spreadsheets/d/1rH4keENeG_4Z0_y3Aus1gVOeWReczHgAny-VczD5rzQ/gviz/tq?tqx=out:json&sheet=Foglio1"

expect suspend fun fetchSheetWaypoints(): List<WaypointItem>

/**
 * Locali + Sheet come TocAppBuild:
 * - ZZ_ sullo Sheet → migrazione in locale
 * - WP missione (non ZZ_) restano isLocal=false
 */
suspend fun loadAllWaypoints(store: GpsLocalStore): List<WaypointItem> {
    var local = store.loadWaypoints()
    val sheet = fetchSheetWaypoints()
    val localNames = local.map { it.name.uppercase() }.toSet()

    var migrated = false
    for (wp in sheet) {
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
    val missionSheet = sheet.filter { w ->
        val upper = w.name.uppercase()
        if (upper.startsWith(AppWaypointPrefix)) return@filter false
        !localNames2.contains(upper)
    }
    return local + missionSheet
}
