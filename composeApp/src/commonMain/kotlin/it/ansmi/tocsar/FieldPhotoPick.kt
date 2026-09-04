package it.ansmi.tocsar

import androidx.compose.ui.graphics.ImageBitmap

/** JPEG da fotocamera o galleria (Android Activity Result / iOS UIImagePicker + PHPicker). */
expect suspend fun pickFieldPhotoJpeg(fromCamera: Boolean): ByteArray?

expect fun decodeJpegImageBitmap(bytes: ByteArray): ImageBitmap?
