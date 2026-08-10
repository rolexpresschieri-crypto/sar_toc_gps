package it.ansmi.tocsar

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

actual fun sharePlainText(subject: String, text: String, fileNameHint: String) {
    val context = AndroidAppContext.require()
    val safe = fileNameHint.replace(Regex("[^\\w.\\-]+"), "_").ifBlank { "gps.txt" }
    val file = File(context.cacheDir, safe)
    file.writeText(text)
    val uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) {
        null
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        if (uri != null) {
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(subject, uri)
        } else {
            putExtra(Intent.EXTRA_TEXT, text)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
