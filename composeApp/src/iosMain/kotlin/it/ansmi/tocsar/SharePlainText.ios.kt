package it.ansmi.tocsar

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
actual fun sharePlainText(subject: String, text: String, fileNameHint: String) {
    runOnMain {
        val presenter = topViewController() ?: return@runOnMain
        val safe = fileNameHint.replace(Regex("[^\\w.\\-]+"), "_").ifBlank { "gps.txt" }
        val path = NSTemporaryDirectory() + safe
        @Suppress("CAST_NEVER_SUCCEEDS")
        val written =
            (text as NSString).writeToFile(
                path,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            )
        val item: Any =
            if (written) NSURL.fileURLWithPath(path) else text
        val activity =
            UIActivityViewController(activityItems = listOf(item), applicationActivities = null)
        presenter.presentViewController(activity, animated = true, completion = null)
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
