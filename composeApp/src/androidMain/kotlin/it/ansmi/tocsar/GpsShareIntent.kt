package it.ansmi.tocsar

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import java.io.BufferedReader
import java.io.InputStreamReader

/** Legge ACTION_VIEW / ACTION_SEND e accoda l'import WP/TRK. */
fun ComponentActivity.ingestGpsShareIntent(intent: Intent?) {
    if (intent == null) return
    when (intent.action) {
        Intent.ACTION_VIEW -> {
            val uri = intent.data ?: return
            readUriToPending(uri, intent.type)
        }
        Intent.ACTION_SEND -> {
            val stream = intent.getParcelableExtraCompatible(Intent.EXTRA_STREAM)
            if (stream != null) {
                readUriToPending(stream, intent.type)
                return
            }
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (text.isNotEmpty()) {
                val hint =
                    intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.ifBlank { null }
                        ?: "CONDIVISO.trk"
                PendingGpsImport.offer(hint, text)
            }
        }
    }
}

private fun ComponentActivity.readUriToPending(uri: Uri, mime: String?) {
    try {
        val name = resolveDisplayName(uri) ?: guessNameFromMime(mime)
        val text = contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        } ?: return
        PendingGpsImport.offer(name, text)
    } catch (_: Exception) {
        // ignore: utente potrà usare Importa file
    }
}

private fun ComponentActivity.resolveDisplayName(uri: Uri): String? {
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val display = cursor.getString(idx)?.trim().orEmpty()
                        if (display.isNotEmpty()) return display
                    }
                }
            }
    }
    val seg = uri.lastPathSegment?.substringAfterLast('/')?.trim().orEmpty()
    return seg.takeIf { it.isNotEmpty() && !it.uppercase().contains("DOC_ENCODED") }
}

private fun guessNameFromMime(mime: String?): String =
    when {
        mime?.contains("html", ignoreCase = true) == true -> "IMPORT.txt"
        else -> "IMPORT.trk"
    }

@Suppress("DEPRECATION")
private fun Intent.getParcelableExtraCompatible(key: String): Uri? {
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, Uri::class.java)
    } else {
        @Suppress("UNCHECKED_CAST")
        getParcelableExtra(key) as? Uri
    }
}
