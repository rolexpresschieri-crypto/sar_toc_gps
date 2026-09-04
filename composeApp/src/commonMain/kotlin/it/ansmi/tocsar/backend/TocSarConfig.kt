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

    val storageObjectUrl: String
        get() = supabaseUrl.trimEnd('/') + "/storage/v1/object/"
}

expect fun loadTocSarConfig(): TocSarConfig?
