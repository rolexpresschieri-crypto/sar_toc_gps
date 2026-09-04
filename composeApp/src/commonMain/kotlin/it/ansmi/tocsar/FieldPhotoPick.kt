package it.ansmi.tocsar

import androidx.compose.ui.graphics.ImageBitmap

/** JPEG da fotocamera o galleria. iOS: null finché non si porta il picker. */
expect suspend fun pickFieldPhotoJpeg(fromCamera: Boolean): ByteArray?

expect fun decodeJpegImageBitmap(bytes: ByteArray): ImageBitmap?
