package it.ansmi.tocsar.backend

import it.ansmi.tocsar.BuildConfig

actual fun loadTocSarConfig(): TocSarConfig? {
    val url = BuildConfig.SUPABASE_URL.trim()
    val key = BuildConfig.SUPABASE_ANON_KEY.trim()
    if (url.isBlank() || key.isBlank() || url.contains("YOUR-PROJECT")) {
        return null
    }
    return TocSarConfig(
        supabaseUrl = url,
        supabaseAnonKey = key,
        tocBackendUrl = BuildConfig.TOC_BACKEND_URL.trim(),
    )
}
