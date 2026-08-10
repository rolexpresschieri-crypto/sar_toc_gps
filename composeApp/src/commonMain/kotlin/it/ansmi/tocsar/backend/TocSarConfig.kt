package it.ansmi.tocsar.backend

data class TocSarConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val tocBackendUrl: String = "",
) {
    init {
        require(supabaseUrl.isNotBlank()) { "SUPABASE_URL mancante" }
        require(supabaseAnonKey.isNotBlank()) { "SUPABASE_ANON_KEY mancante" }
    }

    val restBaseUrl: String
        get() = supabaseUrl.trimEnd('/') + "/rest/v1/"
}

expect fun loadTocSarConfig(): TocSarConfig?
