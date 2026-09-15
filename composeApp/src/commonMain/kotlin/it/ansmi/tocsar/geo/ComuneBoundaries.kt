package it.ansmi.tocsar.geo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LatLon(
    val lat: Double,
    val lon: Double,
)

data class ComunePolygonRings(
    val outer: List<LatLon>,
    val holes: List<List<LatLon>> = emptyList(),
)

data class ComuneBoundaryGeom(
    val id: String,
    val name: String,
    val polygons: List<ComunePolygonRings>,
)

private val geoJson = Json { ignoreUnknownKeys = true }

fun nameFromComuneGeoJson(raw: String, fallback: String): String {
    val root = runCatching { geoJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return fallback
    val direct = root["properties"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.trim()
    if (!direct.isNullOrEmpty()) {
        return direct
    }
    val first = root["features"]?.jsonArray?.firstOrNull()?.jsonObject
    val named = first?.get("properties")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.trim()
    return named?.takeIf { it.isNotEmpty() } ?: fallback
}

fun parseComuneGeoJson(id: String, name: String, raw: String): ComuneBoundaryGeom {
    val root = geoJson.parseToJsonElement(raw).jsonObject
    val polygons = mutableListOf<ComunePolygonRings>()
    when (root["type"]?.jsonPrimitive?.contentOrNull) {
        "FeatureCollection" ->
            root["features"]?.jsonArray?.forEach { feature ->
                feature.jsonObject["geometry"]?.jsonObject?.let { addGeometry(it, polygons) }
            }
        "Feature" ->
            root["geometry"]?.jsonObject?.let { addGeometry(it, polygons) }
        else -> addGeometry(root, polygons)
    }
    return ComuneBoundaryGeom(
        id = id,
        name = name,
        polygons = polygons.filter { it.outer.size >= 3 },
    )
}

private fun addGeometry(geom: JsonObject, out: MutableList<ComunePolygonRings>) {
    when (geom["type"]?.jsonPrimitive?.contentOrNull) {
        "Polygon" -> geom["coordinates"]?.jsonArray?.let { out.add(polygonFromCoords(it)) }
        "MultiPolygon" ->
            geom["coordinates"]?.jsonArray?.forEach { poly ->
                out.add(polygonFromCoords(poly.jsonArray))
            }
    }
}

private fun polygonFromCoords(rings: JsonArray): ComunePolygonRings {
    val parsed = rings.map { ring ->
        ring.jsonArray.mapNotNull { pt ->
            val arr = pt.jsonArray
            if (arr.size < 2) {
                null
            } else {
                LatLon(lat = arr[1].jsonPrimitive.double, lon = arr[0].jsonPrimitive.double)
            }
        }
    }
    return ComunePolygonRings(
        outer = parsed.firstOrNull().orEmpty(),
        holes = parsed.drop(1).filter { it.size >= 3 },
    )
}
