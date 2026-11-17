package it.ansmi.tocsar.geo

import it.ansmi.tocsar.AndroidAppContext
import it.ansmi.tocsar.GpsImportPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

actual fun createGpsLocalStore(): GpsLocalStore = AndroidGpsLocalStore()

actual suspend fun pickGpsImportFile(): Pair<String, String>? = GpsImportPicker.pick()

private class AndroidGpsLocalStore : GpsLocalStore {
    private fun rootDir(): File {
        val dir = File(AndroidAppContext.require().filesDir, "gps_local")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun waypointsFile(): File = File(rootDir(), "waypoints.json")

    private fun tracksDir(): File {
        val dir = File(rootDir(), "tracks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override suspend fun loadWaypoints(): List<WaypointItem> = withContext(Dispatchers.IO) {
        val file = waypointsFile()
        if (!file.exists()) return@withContext emptyList()
        try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val name = m.optString("name").trim()
                    val lat = parseCoord(m.opt("lat")?.toString().orEmpty()) ?: continue
                    val lon = parseCoord(m.opt("lon")?.toString().orEmpty()) ?: continue
                    if (name.isEmpty()) continue
                    val alt = parseCoord(m.opt("alt")?.toString().orEmpty())
                    add(WaypointItem(name = name, lat = lat, lon = lon, alt = alt, isLocal = true))
                }
            }.sortedBy { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun upsertWaypoint(wp: WaypointItem) = withContext(Dispatchers.IO) {
        val current = loadWaypoints().toMutableList()
        val idx = current.indexOfFirst { it.name.equals(wp.name, ignoreCase = true) }
        if (idx >= 0) current[idx] = wp.copy(isLocal = true) else current.add(wp.copy(isLocal = true))
        writeWaypoints(current.sortedBy { it.name })
    }

    override suspend fun deleteWaypoint(name: String) = withContext(Dispatchers.IO) {
        writeWaypoints(loadWaypoints().filterNot { it.name.equals(name, ignoreCase = true) })
    }

    private fun writeWaypoints(items: List<WaypointItem>) {
        val arr = JSONArray()
        for (w in items) {
            arr.put(
                JSONObject().apply {
                    put("name", w.name)
                    put("lat", w.lat)
                    put("lon", w.lon)
                    if (w.alt != null) put("alt", w.alt) else put("alt", JSONObject.NULL)
                },
            )
        }
        waypointsFile().writeText(arr.toString(2))
    }

    override suspend fun loadTracks(): List<TrackListItem> = withContext(Dispatchers.IO) {
        tracksDir().listFiles()
            ?.filter { it.isFile && (it.extension.equals("gpx", true) || it.extension.equals("trk", true)) }
            ?.map { f ->
                val name = f.nameWithoutExtension
                val n = parseGpxTrackOrTrk(f.readText()).size
                TrackListItem(name = name, nPoints = n)
            }
            ?.distinctBy { it.name.lowercase() }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    override suspend fun upsertTrack(name: String, trkBody: String): Unit = withContext(Dispatchers.IO) {
        val stem = safeGpsFileStem(name)
        // GPX ufficiale; rimuove eventuale .trk legacy con lo stesso stem
        File(tracksDir(), "$stem.gpx").writeText(trkBody)
        File(tracksDir(), "$stem.trk").delete()
        Unit
    }

    override suspend fun deleteTrack(name: String): Unit = withContext(Dispatchers.IO) {
        val stem = safeGpsFileStem(name)
        File(tracksDir(), "$stem.gpx").delete()
        File(tracksDir(), "$stem.trk").delete()
        Unit
    }

    override suspend fun readTrackBody(name: String): String = withContext(Dispatchers.IO) {
        val stem = safeGpsFileStem(name)
        val gpx = File(tracksDir(), "$stem.gpx")
        val trk = File(tracksDir(), "$stem.trk")
        when {
            gpx.exists() -> gpx.readText()
            trk.exists() -> trk.readText()
            else -> error("Traccia non trovata: $name")
        }
    }

    override suspend fun fetchTrackPoints(name: String): List<TrackPoint> {
        val points = parseGpxTrackOrTrk(readTrackBody(name))
        if (points.size < 2) error("Traccia $name: ${points.size} punti letti in locale")
        return points
    }
}
