package it.ansmi.tocsar

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSThread
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.coroutines.resume

private const val MaxJpegBytes = 3_000_000
private const val MaxSidePx = 1600.0

private object IosFieldPhotoRuntime {
    var cameraDelegate: CameraPickerDelegate? = null
    var galleryDelegate: GalleryPickerDelegate? = null
}

actual suspend fun pickFieldPhotoJpeg(fromCamera: Boolean): ByteArray? {
    val image = (if (fromCamera) pickFromCamera() else pickFromGallery()) ?: return null
    val jpeg = withContext(Dispatchers.Default) { compressToJpeg(image) }
    return jpeg.takeIf { it.isNotEmpty() }
}

actual fun decodeJpegImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

private suspend fun pickFromCamera(): UIImage? =
    suspendCancellableCoroutine { cont ->
        runOnMain {
            val presenter = topViewController()
            if (
                presenter == null ||
                !UIImagePickerController.isSourceTypeAvailable(
                    UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
                )
            ) {
                if (cont.isActive) cont.resume(null)
                return@runOnMain
            }
            val picker = UIImagePickerController()
            val delegate =
                CameraPickerDelegate { image ->
                    IosFieldPhotoRuntime.cameraDelegate = null
                    if (cont.isActive) cont.resume(image)
                }
            IosFieldPhotoRuntime.cameraDelegate = delegate
            picker.sourceType =
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            picker.mediaTypes = listOf("public.image")
            picker.allowsEditing = false
            picker.delegate = delegate
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }

private suspend fun pickFromGallery(): UIImage? =
    suspendCancellableCoroutine { cont ->
        runOnMain {
            val presenter = topViewController()
            if (presenter == null) {
                if (cont.isActive) cont.resume(null)
                return@runOnMain
            }
            val config = PHPickerConfiguration()
            config.selectionLimit = 1
            config.filter = PHPickerFilter.imagesFilter
            val picker = PHPickerViewController(configuration = config)
            val delegate =
                GalleryPickerDelegate { image ->
                    IosFieldPhotoRuntime.galleryDelegate = null
                    if (cont.isActive) cont.resume(image)
                }
            IosFieldPhotoRuntime.galleryDelegate = delegate
            picker.delegate = delegate
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }

private class CameraPickerDelegate(
    private val onPicked: (UIImage?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true) { onPicked(image) }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) { onPicked(null) }
    }
}

private class GalleryPickerDelegate(
    private val onPicked: (UIImage?) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onPicked(null)
            return
        }
        loadImage(result) { image -> onPicked(image) }
    }
}

private fun loadImage(result: PHPickerResult, onImage: (UIImage?) -> Unit) {
    val provider = result.itemProvider
    val typeId =
        listOf("public.image", "public.jpeg", "public.heic", "public.png")
            .firstOrNull { provider.hasItemConformingToTypeIdentifier(it) }
            ?: run {
                onImage(null)
                return
            }
    provider.loadDataRepresentationForTypeIdentifier(typeId) { data, _ ->
        val image = data?.let { UIImage.imageWithData(it) }
        runOnMain { onImage(image) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun compressToJpeg(image: UIImage): ByteArray {
    val sized = image.scaledToMaxSide(MaxSidePx)
    var quality = 0.82
    var data: NSData? = null
    while (quality >= 0.40) {
        data = UIImageJPEGRepresentation(sized, quality)
        if (data != null && data.length.toInt() <= MaxJpegBytes) break
        quality -= 0.08
    }
    return data?.toByteArray() ?: ByteArray(0)
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.scaledToMaxSide(maxSide: Double): UIImage {
    val (w, h) = size.useContents { width to height }
    val longest = maxOf(w, h)
    if (longest <= maxSide || longest <= 0.0) return this
    val scale = maxSide / longest
    val nw = w * scale
    val nh = h * scale
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(nw, nh), false, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, nw, nh))
    val out = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return out ?: this
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    if (n == 0) return ByteArray(0)
    val out = ByteArray(n)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

private fun topViewController(): UIViewController? {
    val window =
        UIApplication.sharedApplication.windows
            .mapNotNull { it as? UIWindow }
            .firstOrNull { it.isKeyWindow() }
    var vc = window?.rootViewController
    while (true) {
        val presented = vc?.presentedViewController ?: break
        vc = presented
    }
    return vc
}

private fun runOnMain(block: () -> Unit) {
    if (NSThread.isMainThread) {
        block()
    } else {
        dispatch_async(dispatch_get_main_queue(), block)
    }
}
