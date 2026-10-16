package it.ansmi.tocsar

import android.content.Intent
import android.content.pm.PackageManager
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
    val send = Intent(Intent.ACTION_SEND).apply {
        // .txt + text/plain: WhatsApp lo accetta come documento, non come messaggio vuoto.
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        if (uri != null) {
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
    if (uri != null) {
        val targets = context.packageManager.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in targets) {
            context.grantUriPermission(
                info.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
    val chooser = Intent.createChooser(send, subject).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (uri != null) {
            clipData = android.content.ClipData.newRawUri(subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(chooser)
}
