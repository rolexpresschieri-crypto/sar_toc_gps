package it.ansmi.tocsar.backend

/** Persistenza locale: sessione (riavvio) e codice ente (tablet condiviso). */
expect object OperatorSessionStore {
    fun loadSessionId(): String?
    fun saveSessionId(sessionId: String?)
    fun loadOrganizationCode(): String?
    fun saveOrganizationCode(code: String?)
}
