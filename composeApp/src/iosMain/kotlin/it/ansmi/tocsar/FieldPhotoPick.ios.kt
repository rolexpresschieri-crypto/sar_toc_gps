package it.ansmi.tocsar

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual suspend fun pickFieldPhotoJpeg(fromCamera: Boolean): ByteArray? = null

actual fun decodeJpegImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
