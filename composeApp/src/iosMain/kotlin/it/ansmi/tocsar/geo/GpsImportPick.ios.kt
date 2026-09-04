package it.ansmi.tocsar.geo

import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private object IosGpsImportRuntime {
    var delegate: GpsImportPickerDelegate? = null
}

actual suspend fun pickGpsImportFile(): Pair<String, String>? =
    suspendCancellableCoroutine { cont ->
        runOnMain {
            val presenter = topViewController()
            val types =
                listOf("public.item", "public.data", "public.text", "public.content")
                    .mapNotNull { UTType.typeWithIdentifier(it) }
            if (presenter == null || types.isEmpty()) {
                if (cont.isActive) cont.resume(null)
                return@runOnMain
            }
            val picker =
                UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
            picker.allowsMultipleSelection = false
            val delegate =
                GpsImportPickerDelegate { picked ->
                    IosGpsImportRuntime.delegate = null
                    if (cont.isActive) cont.resume(picked)
                }
            IosGpsImportRuntime.delegate = delegate
            picker.delegate = delegate
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }

private class GpsImportPickerDelegate(
    private val onPicked: (Pair<String, String>?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onPicked(url?.let { readImportedFile(it) })
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

@OptIn(BetaInteropApi::class)
private fun readImportedFile(url: NSURL): Pair<String, String>? {
    val accessed = url.startAccessingSecurityScopedResource()
    try {
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        val text =
            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                ?: return null
        val name = url.lastPathComponent?.trim().orEmpty().ifBlank { "IMPORT.trk" }
        return name to text
    } catch (_: Exception) {
        return null
    } finally {
        if (accessed) url.stopAccessingSecurityScopedResource()
    }
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
    dispatch_async(dispatch_get_main_queue(), block)
}
