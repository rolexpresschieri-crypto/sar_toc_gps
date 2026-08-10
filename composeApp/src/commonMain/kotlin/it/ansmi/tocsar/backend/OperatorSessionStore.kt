package it.ansmi.tocsar.backend

/** Persistenza locale dell'id sessione per ripristino dopo riavvio app. */
expect object OperatorSessionStore {
    fun loadSessionId(): String?
    fun saveSessionId(sessionId: String?)
}
