package it.ansmi.tocsar.backend

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile

actual fun loadTocSarConfig(): TocSarConfig? {
    fromBundledJson()?.let { return it }
    return fromInfoPlist()
}

private fun fromBundledJson(): TocSarConfig? {
    val path = NSBundle.mainBundle.pathForResource("supabase-config", ofType = "json") ?: return null
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val ns = NSString.create(data = data, encoding = NSUTF8StringEncoding) ?: return null
    val text = ns.toString()
    return runCatching {
        val obj = Json.parseToJsonElement(text).jsonObject
        fun str(key: String): String =
            obj[key]?.jsonPrimitive?.content?.trim()?.trim('"').orEmpty()
        val url = str("SUPABASE_URL")
        val key = str("SUPABASE_ANON_KEY")
        if (url.isBlank() || key.isBlank() || url.contains("YOUR-PROJECT") || !url.startsWith("https://")) {
            return null
        }
        TocSarConfig(
            supabaseUrl = url,
            supabaseAnonKey = key,
            tocBackendUrl = str("TOC_BACKEND_URL"),
        )
    }.getOrNull()
}

private fun fromInfoPlist(): TocSarConfig? {
    val info = NSBundle.mainBundle.infoDictionary ?: return null
    fun str(key: String): String {
        val raw = info[key] as? String ?: return ""
        return raw.trim().trim('"')
    }
    val url = str("SUPABASE_URL")
    val key = str("SUPABASE_ANON_KEY")
    if (url.isBlank() || key.isBlank() || url.contains("YOUR-PROJECT") || !url.startsWith("https://")) {
        return null
    }
    return TocSarConfig(
        supabaseUrl = url,
        supabaseAnonKey = key,
        tocBackendUrl = str("TOC_BACKEND_URL"),
    )
}
