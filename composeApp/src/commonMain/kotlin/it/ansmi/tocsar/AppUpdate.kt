package it.ansmi.tocsar

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import it.ansmi.tocsar.backend.loadTocSarConfig
import it.ansmi.tocsar.backend.network.createPlatformHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Manifest remoto su TOC: /app/android-latest.json */
@Serializable
data class AndroidLatestManifest(
    @SerialName("versionName") val versionName: String = "",
    @SerialName("versionCode") val versionCode: Int = 0,
    @SerialName("apkUrl") val apkUrl: String = "",
    @SerialName("message") val message: String = "",
)

expect fun appVersionCode(): Int

expect fun openExternalUrl(url: String)

/**
 * Android: scarica APK e apre l'installer.
 * Restituisce null se ok, oppure messaggio errore/guida.
 */
expect suspend fun downloadAndInstallApk(url: String): String?

/** Solo Android forza l'aggiornamento APK; iOS ignora. */
expect fun isAndroidAppUpdateSupported(): Boolean

private val updateJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private const val DefaultUpdateManifestUrl =
    "https://toc-sar.vercel.app/app/android-latest.json"

fun androidUpdateManifestUrl(): String {
    val base = loadTocSarConfig()?.tocBackendUrl?.trim()?.trimEnd('/').orEmpty()
    return if (base.isNotEmpty()) {
        "$base/app/android-latest.json"
    } else {
        DefaultUpdateManifestUrl
    }
}

/**
 * Se il server richiede una versione più nuova, restituisce il manifest.
 * In caso di errore di rete → null (non blocca il campo).
 */
suspend fun fetchRequiredAndroidUpdate(): AndroidLatestManifest? {
    if (!isAndroidAppUpdateSupported()) return null
    val localCode = appVersionCode()
    if (localCode <= 0) return null
    val url = androidUpdateManifestUrl()
    return runCatching {
        val http = createPlatformHttpClient { }
        try {
            val body = http.get(url).bodyAsText()
            val remote = updateJson.decodeFromString<AndroidLatestManifest>(body)
            if (remote.versionCode > localCode && remote.apkUrl.trim().isNotEmpty()) {
                remote
            } else {
                null
            }
        } finally {
            http.close()
        }
    }.getOrNull()
}
