package it.ansmi.tocsar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream
import java.io.File

private const val MaxJpegBytes = 3_000_000
private const val MaxSidePx = 1600

/**
 * Bridge fotocamera/galleria: registrato in [MainActivity], usato da [pickFieldPhotoJpeg].
 */
object FieldPhotoPicker {
    @Volatile
    private var activity: ComponentActivity? = null

    @Volatile
    private var pending: CompletableDeferred<ByteArray?>? = null

    @Volatile
    private var captureUri: Uri? = null

    @Volatile
    var galleryLauncher: (() -> Unit)? = null

    @Volatile
    var cameraLauncher: ((Uri) -> Unit)? = null

    @Volatile
    var cameraPermissionLauncher: (() -> Unit)? = null

    fun bind(activity: ComponentActivity) {
        this.activity = activity
    }

    fun unbind(activity: ComponentActivity) {
        if (this.activity === activity) this.activity = null
    }

    fun onGalleryUri(uri: Uri?) {
        completeFromUri(uri)
    }

    fun onCameraTaken(ok: Boolean) {
        val uri = captureUri
        captureUri = null
        if (!ok || uri == null) {
            complete(null)
            return
        }
        completeFromUri(uri)
    }

    fun onCameraPermission(granted: Boolean) {
        if (!granted) {
            complete(null)
            return
        }
        launchCameraCapture()
    }

    suspend fun pick(fromCamera: Boolean): ByteArray? {
        if (activity == null) return null
        pending?.cancel()
        val deferred = CompletableDeferred<ByteArray?>()
        pending = deferred
        if (fromCamera) {
            val act = activity ?: run {
                complete(null)
                return deferred.await()
            }
            val granted =
                ContextCompat.checkSelfPermission(act, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            if (granted) {
                launchCameraCapture()
            } else {
                val ask = cameraPermissionLauncher
                if (ask == null) {
                    complete(null)
                } else {
                    ask()
                }
            }
        } else {
            galleryLauncher?.invoke() ?: complete(null)
        }
        return deferred.await()
    }

    private fun launchCameraCapture() {
        val act = activity
        val launch = cameraLauncher
        if (act == null || launch == null) {
            complete(null)
            return
        }
        try {
            val file = File(act.cacheDir, "toc_field_photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", file)
            captureUri = uri
            launch(uri)
        } catch (_: Exception) {
            captureUri = null
            complete(null)
        }
    }

    private fun completeFromUri(uri: Uri?) {
        val act = activity
        if (uri == null || act == null) {
            complete(null)
            return
        }
        try {
            val raw = act.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (raw == null || raw.isEmpty()) {
                complete(null)
                return
            }
            complete(compressToJpeg(raw))
        } catch (_: Exception) {
            complete(null)
        }
    }

    private fun complete(value: ByteArray?) {
        val cont = pending
        pending = null
        cont?.complete(value)
    }
}

actual suspend fun pickFieldPhotoJpeg(fromCamera: Boolean): ByteArray? =
    FieldPhotoPicker.pick(fromCamera)

fun ComponentActivity.registerFieldPhotoPicker() {
    val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        FieldPhotoPicker.onGalleryUri(uri)
    }
    val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        FieldPhotoPicker.onCameraTaken(ok)
    }
    val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        FieldPhotoPicker.onCameraPermission(granted)
    }
    FieldPhotoPicker.galleryLauncher = { gallery.launch("image/*") }
    FieldPhotoPicker.cameraLauncher = { uri -> camera.launch(uri) }
    FieldPhotoPicker.cameraPermissionLauncher = { camPerm.launch(Manifest.permission.CAMERA) }
    FieldPhotoPicker.bind(this)
}

private fun compressToJpeg(raw: ByteArray): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    var sample = 1
    val w = bounds.outWidth.coerceAtLeast(1)
    val h = bounds.outHeight.coerceAtLeast(1)
    while (w / sample > MaxSidePx || h / sample > MaxSidePx) {
        sample *= 2
    }
    val decoded =
        BitmapFactory.decodeByteArray(
            raw,
            0,
            raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return raw
    var quality = 82
    var out = ByteArray(0)
    try {
        while (quality >= 40) {
            val stream = ByteArrayOutputStream()
            decoded.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            out = stream.toByteArray()
            if (out.size <= MaxJpegBytes) break
            quality -= 8
        }
    } finally {
        if (!decoded.isRecycled) decoded.recycle()
    }
    return if (out.isNotEmpty()) out else raw
}

actual fun decodeJpegImageBitmap(bytes: ByteArray): ImageBitmap? {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return bmp.asImageBitmap()
}
