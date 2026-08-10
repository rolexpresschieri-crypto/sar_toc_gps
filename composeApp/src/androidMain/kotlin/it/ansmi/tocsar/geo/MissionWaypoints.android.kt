package it.ansmi.tocsar.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun fetchSheetWaypoints(): List<WaypointItem> = withContext(Dispatchers.IO) {
    try {
        val conn = (URL(WaypointsSheetUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        parseGvizWaypoints(body)
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun parseGvizWaypoints(body: String): List<WaypointItem> {
    val m = Regex("""setResponse\(([\s\S]+)\);""").find(body) ?: return emptyList()
    val gviz = JSONObject(m.groupValues[1])
    val rows = gviz.optJSONObject("table")?.optJSONArray("rows") ?: return emptyList()
    val items = mutableListOf<WaypointItem>()
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        val c = row.optJSONArray("c") ?: continue
        fun cell(idx: Int): String? {
            val o = c.optJSONObject(idx) ?: return null
            if (o.isNull("v")) return null
            return o.opt("v")?.toString()?.trim()
        }
        val name = cell(0)?.takeIf { it.isNotEmpty() } ?: continue
        val lat = parseCoord(cell(1).orEmpty()) ?: continue
        val lon = parseCoord(cell(2).orEmpty()) ?: continue
        val alt = parseCoord(cell(3).orEmpty())
        items.add(WaypointItem(name = name, lat = lat, lon = lon, alt = alt, isLocal = false))
    }
    return items.sortedBy { it.name }
}
