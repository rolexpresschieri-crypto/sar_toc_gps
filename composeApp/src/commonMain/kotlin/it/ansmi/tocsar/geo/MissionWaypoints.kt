package it.ansmi.tocsar.geo

import it.ansmi.tocsar.backend.network.createPlatformHttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Stesso Sheet di TocAppBuild: WP di missione (non ZZ_). */
const val WaypointsSheetUrl =
    "https://docs.google.com/spreadsheets/d/1rH4keENeG_4Z0_y3Aus1gVOeWReczHgAny-VczD5rzQ/gviz/tq?tqx=out:json&sheet=Foglio1"

private val gvizJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

suspend fun fetchSheetWaypoints(): List<WaypointItem> {
    val http = createPlatformHttpClient {}
    return try {
        val body = http.get(WaypointsSheetUrl) {
            header("User-Agent", "TOC-SAR/1.0 (it.ansmi.tocsar)")
            header("Accept", "application/json,text/plain,*/*")
        }.bodyAsText()
        parseGvizWaypoints(body)
    } catch (_: Exception) {
        emptyList()
    } finally {
        http.close()
    }
}

internal fun parseGvizWaypoints(body: String): List<WaypointItem> {
    val m = Regex("""setResponse\(([\s\S]+)\);""").find(body) ?: return emptyList()
    val root = runCatching { gvizJson.parseToJsonElement(m.groupValues[1]) }.getOrNull()
        ?.jsonObject ?: return emptyList()
    val rows = root["table"]?.jsonObject?.get("rows")?.jsonArray ?: return emptyList()
    val items = mutableListOf<WaypointItem>()
    for (rowEl in rows) {
        val row = rowEl as? JsonObject ?: continue
        val cells = row["c"] as? JsonArray ?: continue
        val name = gvizCell(cells, 0)?.takeIf { it.isNotEmpty() } ?: continue
        val lat = parseCoord(gvizCell(cells, 1).orEmpty()) ?: continue
        val lon = parseCoord(gvizCell(cells, 2).orEmpty()) ?: continue
        val alt = parseCoord(gvizCell(cells, 3).orEmpty())
        items.add(WaypointItem(name = name, lat = lat, lon = lon, alt = alt, isLocal = false))
    }
    return items.sortedBy { it.name }
}

private fun gvizCell(cells: JsonArray, idx: Int): String? {
    val el = cells.getOrNull(idx) ?: return null
    if (el is JsonNull) return null
    val obj = el as? JsonObject ?: return null
    val v = obj["v"] ?: return null
    if (v is JsonNull) return null
    val prim = v as? JsonPrimitive ?: return null
    return prim.content.trim().takeIf { it.isNotEmpty() }
}

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
