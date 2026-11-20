package it.ansmi.tocsar

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual fun appVersionCode(): Int = BuildConfig.VERSION_CODE

actual fun isAndroidAppUpdateSupported(): Boolean = true

actual fun openExternalUrl(url: String) {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    val ok = startActivitySafe(intent) || startActivitySafe(Intent.createChooser(intent, "Apri link"))
    if (!ok) {
        copyToClipboard(trimmed)
        toast("Link copiato. Incollalo in Chrome e scarica l'APK.")
    }
}

/**
 * Su Samsung (S22/A56) il browser intent spesso non parte.
 * Ordine: DownloadManager → file+installer → browser → clipboard.
 */
actual suspend fun downloadAndInstallApk(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return "URL aggiornamento vuoto"
    val context = runCatching { AndroidAppContext.require() }.getOrNull()
        ?: return "Contesto Android non pronto"

    copyToClipboard(trimmed)

    // 1) DownloadManager (path più affidabile su Samsung)
    val dmStarted =
        runCatching { enqueueApkDownload(context, trimmed) }.getOrDefault(false)
    if (dmStarted) {
        toast("Download avviato. Apri la notifica a fine download e installa.")
        return "Download in corso (barra notifiche). Al termine tocca la notifica e conferma l'installazione. " +
            "Su Samsung: disattiva «Blocco automatico» se blocca l'APK. Link anche copiato negli appunti."
    }

    // 2) Download diretto + installer
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings =
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                )
            startActivitySafe(settings)
            enqueueApkDownload(context, trimmed)
            return "Consenti «Installa app sconosciute» per TOC SAR, poi riprova. " +
                "Oppure apri i Download e tocca l'APK."
        }

        val out =
            withContext(Dispatchers.IO) {
                downloadApkToCache(context, trimmed)
            }
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                out,
            )
        val install =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val started = startActivitySafe(install)
        if (started) return null
        return "APK scaricato ma l'installer non si è aperto. Vai in Download e apri toc_sar_update.apk"
    } catch (e: Exception) {
        openExternalUrl(trimmed)
        return e.message?.takeIf { it.isNotBlank() }
            ?: "Download fallito. Link copiato: incollalo in Chrome."
    }
}

private fun enqueueApkDownload(context: Context, url: String): Boolean {
    val dm = context.getSystemService(DownloadManager::class.java) ?: return false
    val name = "toc_sar_KMP_update.apk"
    val request =
        DownloadManager.Request(Uri.parse(url))
            .setTitle("TOC SAR aggiornamento")
            .setDescription("Download APK in corso…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
    dm.enqueue(request)
    return true
}

private fun downloadApkToCache(context: Context, url: String): File {
    val file = File(context.cacheDir, "toc_sar_update.apk")
    if (file.exists()) file.delete()
    var current = url
    var redirects = 0
    while (redirects < 5) {
        val conn =
            (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 120_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 TOC-SAR-Android-Updater")
            }
        val code = conn.responseCode
        if (code in 300..399) {
            val loc = conn.getHeaderField("Location")
            conn.disconnect()
            if (loc.isNullOrBlank()) error("Redirect senza Location")
            current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
            redirects++
            continue
        }
        if (code !in 200..299) {
            conn.disconnect()
            error("Download fallito (HTTP $code)")
        }
        conn.inputStream.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        conn.disconnect()
        break
    }
    if (!file.exists() || file.length() < 100_000L) {
        error("File APK non valido o troppo piccolo")
    }
    return file
}

private fun startActivitySafe(intent: Intent): Boolean {
    val activity = AndroidActivityHolder.get()
    return try {
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AndroidAppContext.require().startActivity(intent)
        }
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }
}

private fun copyToClipboard(text: String) {
    runCatching {
        val ctx = AndroidAppContext.require()
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TOC SAR APK", text))
    }
}

private fun toast(msg: String) {
    runCatching {
        val ctx = AndroidAppContext.require()
        val act = AndroidActivityHolder.get()
        val target = act ?: ctx
        if (act != null) {
            Toast.makeText(target, msg, Toast.LENGTH_LONG).show()
        } else {
            // Application context: ok su molti device
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
}
