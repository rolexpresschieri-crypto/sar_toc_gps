package it.ansmi.tocsar.geo

/**
 * Import WP/TRK da file o da "Apri con" / Condividi.
 * Mantiene il nome originale (solo sanifica), senza prefisso operatore.
 */
suspend fun importGpsFileContent(
    store: GpsLocalStore,
    fileName: String,
    body: String,
): String {
    return when {
        looksLikeWaypointContent(body) -> {
            val parsed = parseWaypointFile(body)
                ?: return "File WP non valido"
            val name = importedGpsNameFromWaypointBody(body, fileName)
            store.upsertWaypoint(parsed.copy(name = name, isLocal = true))
            "Importato WP $name"
        }
        looksLikeTrkContent(body) -> {
            val pts = parseTrkFile(body)
            if (pts.size < 2) return "Traccia troppo corta"
            val name = importedGpsNameFromFileName(fileName)
            store.upsertTrack(name, encodeTrkFile(pts))
            "Importata traccia $name"
        }
        else -> "Formato non riconosciuto (.trk / WP .wpt.txt)"
    }
}

/** Nome TRK/WP da file: stem originale, senza CODICE_TRK_ forzato. */
fun importedGpsNameFromFileName(fileName: String): String {
    var stem = fileName.trim()
    stem = stem.substringAfterLast('/').substringAfterLast('\\')
    // WhatsApp a volte: "nome.trk" o "DOC….trk"
    if (stem.contains('.')) {
        val lower = stem.lowercase()
        stem = when {
            lower.endsWith(".wpt.txt") -> stem.dropLast(".wpt.txt".length)
            lower.endsWith(".trk.txt") -> stem.dropLast(".trk.txt".length)
            else -> stem.substringBeforeLast('.')
        }
    }
    return sanitizeGpsStem(stem).ifBlank { "IMPORT" }
}

fun importedGpsNameFromWaypointBody(body: String, fileName: String): String {
    val fromBody = body.lineSequence()
        .map { it.trim() }
        .firstOrNull { line ->
            line.isNotEmpty() &&
                !line.contains('=') &&
                !line.contains(',') &&
                !line.lowercase().startsWith("lat")
        }
    return sanitizeGpsStem(fromBody ?: fileName).ifBlank { "WP" }
}

fun sanitizeGpsStem(raw: String): String {
    var free = raw.trim()
    free = free.substringBeforeLast('.').ifBlank { free }
    // Evita stem tipo "wpt" se era solo estensione confusa
    free = free.uppercase()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^A-Z0-9_\\-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
    return free
}
