package it.ansmi.tocsar.backend

/** Persistenza locale: sessione (riavvio) e codice ente (tablet condiviso). */
expect object OperatorSessionStore {
    fun loadSessionId(): String?
    fun saveSessionId(sessionId: String?)
    fun loadOrganizationCode(): String?
    fun saveOrganizationCode(code: String?)
    /** Ultimo versionCode con cui è stata aperta l'app (per logout automatico post-update). */
    fun loadLastAppVersionCode(): Int
    fun saveLastAppVersionCode(code: Int)
}
