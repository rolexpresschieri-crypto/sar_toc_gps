package it.ansmi.tocsar

import android.app.Application
import android.content.Context

object AndroidAppContext {
    @Volatile
    private var app: Application? = null

    fun init(application: Application) {
        app = application
    }

    fun require(): Context = app
        ?: error("AndroidAppContext non inizializzato. Chiama init in Application/MainActivity.")
}
