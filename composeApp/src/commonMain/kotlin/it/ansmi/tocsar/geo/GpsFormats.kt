package it.ansmi.tocsar.geo

data class WaypointItem(
    val name: String,
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    val isLocal: Boolean = true,
    /** Cartella Storage (es. WP_SESTRIERE). Solo WP di missione. */
    val missionGroup: String? = null,
)

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    /** Se true, inizia un nuovo tratto (buco GPS: chiamata, tunnel, …) — non collegare al punto precedente. */
    val gapBefore: Boolean = false,
)

/** Spezza la traccia nei segmenti continui (salta i buchi). */
fun splitTrackSegments(points: List<TrackPoint>): List<List<TrackPoint>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<TrackPoint>>()
    var current = mutableListOf<TrackPoint>()
    for (p in points) {
        if (p.gapBefore && current.isNotEmpty()) {
            segments.add(current)
            current = mutableListOf()
        }
        current.add(p.copy(gapBefore = false))
    }
    if (current.isNotEmpty()) segments.add(current)
    return segments
}

data class TrackListItem(
    val name: String,
    val nPoints: Int,
    val isMission: Boolean = false,
)

data class MapTrackOverlay(
    val name: String,
    val points: List<TrackPoint>,
    val colorHex: String = "#1565C0",
)

const val AppWaypointPrefix = "ZZ_"
const val AppTrackPrefix = "YY_"

fun formatAppWaypointName(freeName: String): String {
    var free = freeName.trim().uppercase().replace(Regex("\\s+"), "")
    when {
        free.startsWith(AppWaypointPrefix) -> free = free.removePrefix(AppWaypointPrefix)
        free.startsWith("ZZ") -> {
            free = free.removePrefix("ZZ")
            if (free.startsWith("_")) free = free.drop(1)
        }
    }
    return "$AppWaypointPrefix$free"
}

fun formatAppTrackName(freeName: String): String {
    var free = freeName.trim().uppercase().replace(Regex("\\s+"), "")
    when {
        free.startsWith(AppTrackPrefix) -> free = free.removePrefix(AppTrackPrefix)
        free.startsWith("YY") -> {
            free = free.removePrefix("YY")
            if (free.startsWith("_")) free = free.drop(1)
        }
    }
    return "$AppTrackPrefix$free"
}

fun safeGpsFileStem(name: String): String =
    name.trim().uppercase().replace(Regex("[^\\w\\-]+"), "_")

fun encodeTrkFile(points: List<TrackPoint>): String {
    val buf = StringBuilder("lat,lon,alt\n")
    for (p in points) {
        if (p.gapBefore) buf.append("#GAP\n")
        buf.append(p.lat.formatCoord6()).append(',')
            .append(p.lon.formatCoord6()).append(',')
            .append(p.alt?.let { it.toInt().toString() } ?: "")
            .append('\n')
    }
    return buf.toString()
}

fun parseTrkFile(raw: String): List<TrackPoint> {
    val out = mutableListOf<TrackPoint>()
    var nextIsGap = false
    for (line in raw.lineSequence()) {
        val t = line.trim()
        if (t.isEmpty()) continue
        if (t.equals("#GAP", ignoreCase = true) || t == "---") {
            nextIsGap = true
            continue
        }
        val pt = parseTrkLine(t) ?: continue
        out.add(if (nextIsGap) pt.copy(gapBefore = true) else pt)
        nextIsGap = false
    }
    return out
}

fun parseTrkLine(line: String): TrackPoint? {
    val t = line.trim()
    if (t.isEmpty()) return null
    val low = t.lowercase()
    if (low.startsWith("lat")) return null

    val tMatch = Regex(
        """^\s*T\s+A\s+([\d.,]+)\s*[°º]?\s*([NnSs])\s+([\d.,]+)\s*[°º]?\s*([EeWw])""",
        RegexOption.IGNORE_CASE,
    ).find(t)
    if (tMatch != null) {
        var lat = tMatch.groupValues[1].replace(',', '.').toDouble()
        var lon = tMatch.groupValues[3].replace(',', '.').toDouble()
        if (tMatch.groupValues[2].equals("S", true)) lat = -lat
        if (tMatch.groupValues[4].equals("W", true)) lon = -lon
        val alt = Regex("""\bs\s+([\d.,]+)""").find(t)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull()
        return TrackPoint(lat, lon, alt)
    }

    val csv = t.split(Regex("[,;]+"))
    if (csv.size >= 2) {
        val lat = parseCoord(csv[0]) ?: return null
        val lon = parseCoord(csv[1]) ?: return null
        val alt = if (csv.size > 2) parseCoord(csv[2]) else null
        return TrackPoint(lat, lon, alt)
    }
    return null
}

fun encodeWaypointFile(name: String, lat: Double, lon: Double, alt: Double?): String {
    val a = alt?.formatAlt0() ?: ""
    return buildString {
        append(name.trim()).append('\n')
        append("lat = ").append(lat.formatCoord6()).append('\n')
        append("lon = ").append(lon.formatCoord6()).append('\n')
        append("alt = ").append(a).append('\n')
    }
}

fun parseWaypointFile(raw: String): WaypointItem? {
    parseWaypointLabeledLines(raw)?.let { return it }
    for (line in raw.lineSequence()) {
        val t = line.trim()
        if (t.isEmpty()) continue
        val low = t.lowercase()
        if (low.startsWith("name") || low.startsWith("lat,")) continue
        val parts = t.split(Regex("[,;]+"))
        if (parts.size < 3) continue
        val maybeName = parts[0].trim()
        val lat = parseCoord(parts[1]) ?: continue
        val lon = parseCoord(parts[2]) ?: continue
        if (maybeName.isEmpty()) continue
        if (parseCoord(maybeName) != null && maybeName.length <= 12) continue
        val alt = if (parts.size > 3) parseCoord(parts[3]) else null
        return WaypointItem(formatAppWaypointName(maybeName), lat, lon, alt)
    }
    return null
}

/** Formato invio: nome / lat = / lon = / alt = */
private fun parseWaypointLabeledLines(raw: String): WaypointItem? {
    val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    if (lines.isEmpty()) return null

    var name: String? = null
    var lat: Double? = null
    var lon: Double? = null
    var alt: Double? = null
    var labeled = false

    for (line in lines) {
        val eq = Regex("""^\s*(lat|lon|long|longitude|log|alt|altitude)\s*=\s*(.+)\s*$""", RegexOption.IGNORE_CASE)
            .find(line)
        if (eq != null) {
            labeled = true
            val key = eq.groupValues[1].lowercase()
            val value = eq.groupValues[2].trim()
            when (key) {
                "lat" -> lat = parseCoord(value)
                "lon", "long", "longitude", "log" -> lon = parseCoord(value)
                "alt", "altitude" -> alt = parseCoord(value)
            }
        } else if (name == null && !line.contains('=') && !line.contains(',')) {
            name = line
        }
    }

    if (!labeled || name.isNullOrBlank() || lat == null || lon == null) return null
    return WaypointItem(formatAppWaypointName(name), lat, lon, alt)
}

fun looksLikeWaypointContent(raw: String): Boolean {
    if (Regex("""^\s*lat\s*=""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).containsMatchIn(raw) &&
        Regex("""^\s*(lon|log|long)\s*=""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).containsMatchIn(raw)
    ) {
        return true
    }
    if (Regex("""name\s*,\s*lat\s*,\s*lon""", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return true
    return Regex(
        """^\s*[A-Za-z0-9_\-]+\s*[,;]\s*-?\d+(\.\d+)?\s*[,;]\s*-?\d+(\.\d+)?""",
        setOf(RegexOption.MULTILINE),
    ).containsMatchIn(raw)
}

fun looksLikeTrkContent(raw: String): Boolean {
    if (Regex("""^\s*T\s+A\s+""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).containsMatchIn(raw)) {
        return true
    }
    return Regex("""lat\s*,\s*lon""", RegexOption.IGNORE_CASE).containsMatchIn(raw)
}

/** WP di missione da file TOC (CSV name,lat,lon[,alt] oppure blocco lat=/lon=). Senza prefisso ZZ_. */
fun parseMissionWaypoints(raw: String): List<WaypointItem> {
    val labeled = parseMissionLabeledBlocks(raw)
    if (labeled.isNotEmpty()) return labeled
    val csv = mutableListOf<WaypointItem>()
    for (line in raw.lineSequence()) {
        val t = line.trim()
        if (t.isEmpty()) continue
        val low = t.lowercase()
        if (low.startsWith("name") || low.startsWith("lat")) continue
        val parts = t.split(Regex("[,;]+"))
        if (parts.size < 3) continue
        val maybeName = parts[0].trim()
        if (maybeName.isEmpty()) continue
        val lat = parseCoord(parts[1]) ?: continue
        val lon = parseCoord(parts[2]) ?: continue
        if (parseCoord(maybeName) != null && maybeName.length <= 12) continue
        val alt = if (parts.size > 3) parseCoord(parts[3]) else null
        csv.add(WaypointItem(name = maybeName, lat = lat, lon = lon, alt = alt, isLocal = false))
    }
    return csv
}

private fun parseMissionLabeledBlocks(raw: String): List<WaypointItem> {
    val blocks = raw.split(Regex("\\r?\\n\\s*\\r?\\n"))
    val out = mutableListOf<WaypointItem>()
    for (block in blocks) {
        val wp = parseMissionLabeledBlock(block) ?: continue
        out.add(wp)
    }
    return out
}

private fun parseMissionLabeledBlock(raw: String): WaypointItem? {
    val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    if (lines.isEmpty()) return null
    var name: String? = null
    var lat: Double? = null
    var lon: Double? = null
    var alt: Double? = null
    var labeled = false
    for (line in lines) {
        val eq = Regex("""^\s*(lat|lon|long|longitude|log|alt|altitude)\s*=\s*(.+)\s*$""", RegexOption.IGNORE_CASE)
            .find(line)
        if (eq != null) {
            labeled = true
            val key = eq.groupValues[1].lowercase()
            val value = eq.groupValues[2].trim()
            when (key) {
                "lat" -> lat = parseCoord(value)
                "lon", "long", "longitude", "log" -> lon = parseCoord(value)
                "alt", "altitude" -> alt = parseCoord(value)
            }
        } else if (name == null && !line.contains('=') && !line.contains(',')) {
            name = line
        }
    }
    if (!labeled || name.isNullOrBlank() || lat == null || lon == null) return null
    return WaypointItem(name = name, lat = lat, lon = lon, alt = alt, isLocal = false)
}

fun Double.formatCoord6(): String {
    val scaled = kotlin.math.round(this * 1_000_000.0) / 1_000_000.0
    return scaled.toString()
}

fun Double.formatAlt0(): String = kotlin.math.round(this).toInt().toString()
