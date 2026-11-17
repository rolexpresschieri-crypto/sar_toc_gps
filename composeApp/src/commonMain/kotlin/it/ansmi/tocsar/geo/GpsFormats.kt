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
    /** Epoch ms (wall clock) al momento del fix; null = traccia senza tempo (pre-1.0.75 timed). */
    val timeMs: Long? = null,
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
    /** Cartella Storage (es. NOVALESA). Solo TRK di missione. */
    val missionGroup: String? = null,
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

/** Etichetta TRK missione: solo il numero (01_Zona… → 01; …_01 → 01). */
fun displayMissionTrackLabel(name: String): String {
    val stem = name.trim().substringBeforeLast('.').ifBlank { name.trim() }
    Regex("""^(\d+)(?:_|$)""").find(stem)?.groupValues?.getOrNull(1)?.let { return it }
    return Regex("""_(\d+)$""").find(stem)?.groupValues?.getOrNull(1) ?: stem
}

/** Solo il numero da mettere in mappa; null se il nome non ha un codice numerico. */
fun displayTrackMapNumber(name: String): String? {
    val label = displayMissionTrackLabel(name)
    return label.takeIf { it.matches(Regex("""^\d+$""")) }
}

/** Punto etichetta TRK: metà del segmento con più punti (come TOC). */
fun trackLabelAnchor(points: List<TrackPoint>): TrackPoint? {
    val segments = splitTrackSegments(points)
    val best = segments.maxByOrNull { it.size } ?: return null
    if (best.isEmpty()) return null
    return best[(best.size - 1) / 2]
}

/** Etichetta TRK locale: solo il nominativo dopo CODICE_TRK_ (es. LUPO_TRK_bonifica → bonifica). */
fun displayLocalTrackLabel(name: String): String {
    val stem = name.trim().substringBeforeLast('.').ifBlank { name.trim() }
    val marker = "_TRK_"
    val idx = stem.indexOf(marker, ignoreCase = true)
    if (idx >= 0) {
        val rest = stem.substring(idx + marker.length).trim('_')
        if (rest.isNotEmpty()) {
            return rest
        }
    }
    return stem
}

fun encodeTrkFile(points: List<TrackPoint>): String {
    val withTime = points.any { it.timeMs != null }
    val buf = StringBuilder(if (withTime) "lat,lon,alt,time\n" else "lat,lon,alt\n")
    for (p in points) {
        if (p.gapBefore) buf.append("#GAP\n")
        buf.append(p.lat.formatCoord6()).append(',')
            .append(p.lon.formatCoord6()).append(',')
            .append(p.alt?.let { it.toInt().toString() } ?: "")
        if (withTime) {
            buf.append(',').append(p.timeMs?.toString().orEmpty())
        }
        buf.append('\n')
    }
    return buf.toString()
}

/** Inverte il senso della traccia (import LAND spesso salva dalla fine). */
fun reverseTrackPoints(points: List<TrackPoint>): List<TrackPoint> {
    val n = points.size
    if (n < 2) return points
    return List(n) { i ->
        val src = points[n - 1 - i]
        src.copy(gapBefore = if (i == 0) false else points[n - i].gapBefore)
    }
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
        val timeMs = if (csv.size > 3) parseTrkTimeMs(csv[3]) else null
        return TrackPoint(lat, lon, alt, timeMs = timeMs)
    }
    return null
}

/** Epoch ms, oppure secondi Unix se il valore è tipicamente < 1e12. */
fun parseTrkTimeMs(raw: String): Long? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val n = t.replace(',', '.').toDoubleOrNull() ?: return null
    if (!n.isFinite() || n <= 0.0) return null
    val asLong = n.toLong()
    return when {
        asLong >= 1_000_000_000_000L -> asLong // già ms
        asLong >= 1_000_000_000L -> asLong * 1000L // secondi
        else -> null
    }
}

/** GPX 1.1: Garmin, OsmAnd, Locus, Organic Maps, Google Earth. */
fun encodeWaypointGpx(name: String, lat: Double, lon: Double, alt: Double?): String {
    val safe = xmlEscape(name.trim())
    val ele = if (alt != null) "\n    <ele>${alt.formatAlt0()}</ele>" else ""
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"TocSar\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <wpt lat=\"").append(lat.formatCoord6())
        append("\" lon=\"").append(lon.formatCoord6()).append("\">")
        append(ele).append('\n')
        append("    <name>").append(safe).append("</name>\n")
        append("  </wpt>\n")
        append("</gpx>\n")
    }
}

fun parseGpxWaypoint(raw: String): WaypointItem? {
    val wpt = Regex(
        """<wpt\b([^>]*)>([\s\S]*?)</wpt>""",
        RegexOption.IGNORE_CASE,
    ).find(raw) ?: return null
    val attrs = wpt.groupValues[1]
    val body = wpt.groupValues[2]
    val lat = attr(attrs, "lat")?.let { parseCoord(it) } ?: return null
    val lon = attr(attrs, "lon")?.let { parseCoord(it) } ?: return null
    val name = Regex("""<name>([\s\S]*?)</name>""", RegexOption.IGNORE_CASE)
        .find(body)?.groupValues?.get(1)?.trim()?.let { xmlUnescape(it) }
    val ele = Regex("""<ele>([\s\S]*?)</ele>""", RegexOption.IGNORE_CASE)
        .find(body)?.groupValues?.get(1)?.let { parseCoord(it.trim()) }
    return WaypointItem(formatAppWaypointName(name?.takeIf { it.isNotBlank() } ?: "WP"), lat, lon, ele)
}

private fun attr(tag: String, key: String): String? =
    Regex("""\b${Regex.escape(key)}\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        .find(tag)?.groupValues?.get(1)

private fun xmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun xmlUnescape(s: String): String =
    s.replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")

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
    parseGpxWaypoint(raw)?.let { return it }
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
    if (Regex("""<wpt\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw)) return true
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
    if (looksLikeGpxTrack(raw)) return true
    if (Regex("""^\s*T\s+A\s+""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).containsMatchIn(raw)) {
        return true
    }
    return Regex("""lat\s*,\s*lon""", RegexOption.IGNORE_CASE).containsMatchIn(raw)
}

fun looksLikeGpxTrack(raw: String): Boolean =
    Regex("""<trkpt\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw)

/** GPX 1.1 traccia (trk/trkseg/trkpt), con tempo opzionale. */
fun encodeTrackGpx(name: String, points: List<TrackPoint>): String {
    val safe = xmlEscape(name.trim().ifBlank { "TRK" })
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"TocSar\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <trk>\n    <name>").append(safe).append("</name>\n")
        append("    <trkseg>\n")
        for (p in points) {
            if (p.gapBefore) {
                append("    </trkseg>\n    <trkseg>\n")
            }
            append("      <trkpt lat=\"").append(p.lat.formatCoord6())
            append("\" lon=\"").append(p.lon.formatCoord6()).append("\">")
            if (p.alt != null) {
                append("\n        <ele>").append(p.alt.toInt()).append("</ele>")
            }
            if (p.timeMs != null) {
                append("\n        <time>").append(iso8601Utc(p.timeMs)).append("</time>")
            }
            append("\n      </trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }
}

fun parseGpxTrack(raw: String): List<TrackPoint> {
    val out = mutableListOf<TrackPoint>()
    var gapNext = false
    val trkpt = Regex(
        """<trkpt\b([^>]*)>([\s\S]*?)</trkpt>""",
        RegexOption.IGNORE_CASE,
    )
    val segments = Regex(
        """<trkseg\b[^>]*>([\s\S]*?)</trkseg>""",
        RegexOption.IGNORE_CASE,
    ).findAll(raw).toList()
    val bodies =
        if (segments.isNotEmpty()) {
            segments.map { it.groupValues[1] }
        } else {
            listOf(raw)
        }
    for ((segIdx, segBody) in bodies.withIndex()) {
        var firstInSeg = true
        for (m in trkpt.findAll(segBody)) {
            val attrs = m.groupValues[1]
            val inner = m.groupValues[2]
            val lat = attr(attrs, "lat")?.let { parseCoord(it) } ?: continue
            val lon = attr(attrs, "lon")?.let { parseCoord(it) } ?: continue
            val ele = Regex("""<ele>([\s\S]*?)</ele>""", RegexOption.IGNORE_CASE)
                .find(inner)?.groupValues?.get(1)?.let { parseCoord(it.trim()) }
            val timeMs = Regex("""<time>([\s\S]*?)</time>""", RegexOption.IGNORE_CASE)
                .find(inner)?.groupValues?.get(1)?.trim()?.let { parseGpxTimeMs(it) }
            val gap = gapNext || (segIdx > 0 && firstInSeg)
            out.add(TrackPoint(lat, lon, ele, gapBefore = gap, timeMs = timeMs))
            gapNext = false
            firstInSeg = false
        }
        gapNext = true
    }
    return out
}

fun parseGpxTrackOrTrk(raw: String): List<TrackPoint> =
    if (looksLikeGpxTrack(raw)) parseGpxTrack(raw) else parseTrkFile(raw)

private fun iso8601Utc(epochMs: Long): String {
    // 2026-11-17T13:00:00Z — calcolo semplice UTC senza dipendenze
    val totalSec = epochMs / 1000L
    val days = totalSec / 86400L
    var rem = (totalSec % 86400L).toInt()
    val h = rem / 3600
    rem %= 3600
    val mi = rem / 60
    val s = rem % 60
    // Algoritmo civile da giorni dal 1970-01-01
    var z = days + 719468L
    val era = (if (z >= 0) z else z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    var y = (yoe + era * 400L).toInt()
    val doy = (doe - (365L * yoe + yoe / 4L - yoe / 100L)).toInt()
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    y += if (m <= 2) 1 else 0
    return buildString {
        append(y.toString().padStart(4, '0')).append('-')
        append(m.toString().padStart(2, '0')).append('-')
        append(d.toString().padStart(2, '0')).append('T')
        append(h.toString().padStart(2, '0')).append(':')
        append(mi.toString().padStart(2, '0')).append(':')
        append(s.toString().padStart(2, '0')).append('Z')
    }
}

private fun parseGpxTimeMs(raw: String): Long? {
    val t = raw.trim()
    // 2026-11-17T13:00:00Z oppure con offset
    val m = Regex(
        """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})""",
    ).find(t) ?: return null
    val y = m.groupValues[1].toInt()
    val mo = m.groupValues[2].toInt()
    val d = m.groupValues[3].toInt()
    val h = m.groupValues[4].toInt()
    val mi = m.groupValues[5].toInt()
    val s = m.groupValues[6].toInt()
    // Giorni da 1970-01-01 (algoritmo civile inverso)
    val y2 = y - if (mo <= 2) 1 else 0
    val era = (if (y2 >= 0) y2 else y2 - 399) / 400
    val yoe = y2 - era * 400
    val mp = mo + (if (mo > 2) -3 else 9)
    val doy = (153 * mp + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146097L + doe - 719468L
    return ((days * 86400L) + h * 3600L + mi * 60L + s) * 1000L
}

/** Rimuove BOM / zero-width: altrimenti il 1° WP del file (es. CP_01) finisce in fondo all’elenco. */
fun sanitizeGpsLabel(raw: String): String =
    raw.trim()
        .trimStart('\uFEFF', '\u200B', '\u200E', '\u200F')
        .trimEnd('\uFEFF', '\u200B', '\u200E', '\u200F')
        .trim()

/** Ordinamento naturale: CP_01 < CP_02 < CP_10 (non lessicografico puro). */
fun compareGpsLabels(a: String, b: String): Int {
    val left = sanitizeGpsLabel(a).uppercase()
    val right = sanitizeGpsLabel(b).uppercase()
    if (left == right) return 0
    val chunk = Regex("""\d+|\D+""")
    val ca = chunk.findAll(left).map { it.value }.toList()
    val cb = chunk.findAll(right).map { it.value }.toList()
    val n = minOf(ca.size, cb.size)
    for (i in 0 until n) {
        val x = ca[i]
        val y = cb[i]
        val nx = x.toLongOrNull()
        val ny = y.toLongOrNull()
        val cmp =
            if (nx != null && ny != null) {
                nx.compareTo(ny)
            } else {
                x.compareTo(y)
            }
        if (cmp != 0) return cmp
    }
    return ca.size.compareTo(cb.size)
}

/** WP di missione da file TOC (CSV name,lat,lon[,alt] oppure blocco lat=/lon=). Senza prefisso ZZ_. */
fun parseMissionWaypoints(raw: String): List<WaypointItem> {
    val text = sanitizeGpsLabel(raw)
    val labeled = parseMissionLabeledBlocks(text)
    if (labeled.isNotEmpty()) return labeled
    val csv = mutableListOf<WaypointItem>()
    for (line in text.lineSequence()) {
        val t = sanitizeGpsLabel(line)
        if (t.isEmpty()) continue
        val low = t.lowercase()
        if (low.startsWith("name") || low.startsWith("lat")) continue
        val parts = t.split(Regex("[,;]+"))
        if (parts.size < 3) continue
        val maybeName = sanitizeGpsLabel(parts[0])
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
    return WaypointItem(
        name = sanitizeGpsLabel(name),
        lat = lat,
        lon = lon,
        alt = alt,
        isLocal = false,
    )
}

fun Double.formatCoord6(): String {
    val scaled = kotlin.math.round(this * 1_000_000.0) / 1_000_000.0
    return scaled.toString()
}

fun Double.formatAlt0(): String = kotlin.math.round(this).toInt().toString()
