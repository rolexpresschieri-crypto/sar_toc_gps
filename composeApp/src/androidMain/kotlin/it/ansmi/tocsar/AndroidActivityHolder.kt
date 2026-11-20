package it.ansmi.tocsar

import android.app.Activity
import java.lang.ref.WeakReference

/** Activity foreground: su Samsung startActivity dall'Application spesso non fa nulla. */
object AndroidActivityHolder {
    @Volatile
    private var ref: WeakReference<Activity>? = null

    fun set(activity: Activity?) {
        ref = activity?.let { WeakReference(it) }
    }

    fun get(): Activity? = ref?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
}
