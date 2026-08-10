package it.ansmi.tocsar.backend

actual object OperatorSessionStore {
    private var cached: String? = null

    actual fun loadSessionId(): String? = cached

    actual fun saveSessionId(sessionId: String?) {
        cached = sessionId?.trim()?.takeIf { it.isNotEmpty() }
    }
}
