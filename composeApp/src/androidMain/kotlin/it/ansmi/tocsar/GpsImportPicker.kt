package it.ansmi.tocsar

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Bridge per GetContent: registrato in [MainActivity], usato da [pickGpsImportFile].
 */
object GpsImportPicker {
    @Volatile
    private var activity: ComponentActivity? = null

    @Volatile
    private var pending: CompletableDeferred<Pair<String, String>?>? = null

    fun bind(activity: ComponentActivity) {
        this.activity = activity
    }

    fun unbind(activity: ComponentActivity) {
        if (this.activity === activity) this.activity = null
    }

    fun onResult(uri: Uri?) {
        val cont = pending
        pending = null
        if (cont == null) return
        if (uri == null) {
            cont.complete(null)
            return
        }
        val act = activity
        if (act == null) {
            cont.complete(null)
            return
        }
        try {
            val name = resolveDisplayName(act, uri)
            val text = act.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: run {
                cont.complete(null)
                return
            }
            cont.complete(name to text)
        } catch (_: Exception) {
            cont.complete(null)
        }
    }

    /**
     * Preferisce OpenableColumns.DISPLAY_NAME (es. «AREA CAVALLI….trk»).
     * [Uri.lastPathSegment] su SAF/Drive è spesso un id tipo DOC_ENCODED_…, non il nome file.
     */
    private fun resolveDisplayName(act: ComponentActivity, uri: Uri): String {
        runCatching {
            act.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
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
        if (seg.isNotEmpty() && !looksLikeDocumentId(seg)) {
            return seg
        }
        return "IMPORT.trk"
    }

    private fun looksLikeDocumentId(name: String): Boolean {
        val u = name.uppercase()
        return u.contains("DOC_ENCODED") ||
            u.contains("CONTENT") ||
            u.startsWith("MSF:") ||
            u.startsWith("PRIMARY:") ||
            (u.length > 64 && u.count { it == '_' || it == '-' } > 4)
    }

    suspend fun pick(): Pair<String, String>? {
        if (activity == null) return null
        val launch = launcher ?: return null
        pending?.cancel()
        val deferred = CompletableDeferred<Pair<String, String>?>()
        pending = deferred
        launch("*/*")
        return deferred.await()
    }

    @Volatile
    var launcher: ((String) -> Unit)? = null
}

fun ComponentActivity.registerGpsImportPicker() {
    val contract = ActivityResultContracts.GetContent()
    val registered = registerForActivityResult(contract) { uri ->
        GpsImportPicker.onResult(uri)
    }
    GpsImportPicker.launcher = { mime -> registered.launch(mime) }
    GpsImportPicker.bind(this)
}
