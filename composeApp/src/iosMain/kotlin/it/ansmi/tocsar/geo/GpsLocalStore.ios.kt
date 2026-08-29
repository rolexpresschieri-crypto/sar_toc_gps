@file:OptIn(ExperimentalForeignApi::class)

package it.ansmi.tocsar.geo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual fun createGpsLocalStore(): GpsLocalStore = IosGpsLocalStore()

actual suspend fun pickGpsImportFile(): Pair<String, String>? = null

@Serializable
private data class StoredWaypoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
)

private val storeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private class IosGpsLocalStore : GpsLocalStore {
    private fun rootDir(): String {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val docs = paths.firstOrNull() as? String ?: error("Documents non disponibile")
        val dir = "$docs/gps_local"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return dir
    }

    private fun waypointsPath(): String = "${rootDir()}/waypoints.json"

    private fun tracksDir(): String {
        val dir = "${rootDir()}/tracks"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return dir
    }

    private fun writeUtf8(path: String, text: String) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private fun readUtf8(path: String): String? =
        NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)

    override suspend fun loadWaypoints(): List<WaypointItem> = withContext(Dispatchers.Default) {
        val raw = readUtf8(waypointsPath()) ?: return@withContext emptyList()
        runCatching {
            storeJson.decodeFromString<List<StoredWaypoint>>(raw).map {
                WaypointItem(name = it.name, lat = it.lat, lon = it.lon, alt = it.alt, isLocal = true)
            }.sortedBy { it.name }
        }.getOrElse { emptyList() }
    }

    override suspend fun upsertWaypoint(wp: WaypointItem) = withContext(Dispatchers.Default) {
        val current = loadWaypoints().toMutableList()
        val idx = current.indexOfFirst { it.name.equals(wp.name, ignoreCase = true) }
        if (idx >= 0) current[idx] = wp.copy(isLocal = true) else current.add(wp.copy(isLocal = true))
        writeWaypoints(current.sortedBy { it.name })
    }

    override suspend fun deleteWaypoint(name: String) = withContext(Dispatchers.Default) {
        writeWaypoints(loadWaypoints().filterNot { it.name.equals(name, ignoreCase = true) })
    }

    private fun writeWaypoints(items: List<WaypointItem>) {
        val stored = items.map { StoredWaypoint(it.name, it.lat, it.lon, it.alt) }
        writeUtf8(waypointsPath(), storeJson.encodeToString(stored))
    }

    override suspend fun loadTracks(): List<TrackListItem> = withContext(Dispatchers.Default) {
        val dir = tracksDir()
        val files = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, error = null)
            ?: return@withContext emptyList()
        files.mapNotNull { raw ->
            val name = raw as? String ?: return@mapNotNull null
            if (!name.endsWith(".trk", ignoreCase = true)) return@mapNotNull null
            val stem = name.removeSuffix(".trk").removeSuffix(".TRK")
            val body = readUtf8("$dir/$name") ?: return@mapNotNull null
            TrackListItem(name = stem, nPoints = parseTrkFile(body).size)
        }.sortedBy { it.name }
    }

    override suspend fun upsertTrack(name: String, trkBody: String) = withContext(Dispatchers.Default) {
        val stem = safeGpsFileStem(name)
        writeUtf8("${tracksDir()}/$stem.trk", trkBody)
    }

    override suspend fun deleteTrack(name: String) = withContext(Dispatchers.Default) {
        val stem = safeGpsFileStem(name)
        NSFileManager.defaultManager.removeItemAtPath("${tracksDir()}/$stem.trk", error = null)
        Unit
    }

    override suspend fun readTrackBody(name: String): String = withContext(Dispatchers.Default) {
        val stem = safeGpsFileStem(name)
        readUtf8("${tracksDir()}/$stem.trk") ?: error("Traccia non trovata: $name")
    }

    override suspend fun fetchTrackPoints(name: String): List<TrackPoint> {
        val points = parseTrkFile(readTrackBody(name))
        if (points.size < 2) error("Traccia $name: ${points.size} punti")
        return points
    }
}
