package it.ansmi.tocsar.backend

class TocSarFacade(
    config: TocSarConfig,
) {
    private val repository = OperatorRepository(config)

    suspend fun loadActiveEvent(): EventInfo? = repository.loadActiveEvent()

    suspend fun loginOperator(
        operatorCode: String,
        password: String,
    ): OperatorBackendSession {
        val event =
            repository.loadActiveEvent()
                ?: throw TocSarException("Nessun evento attivo su Supabase.")
        return repository.loginOperator(
            eventId = event.id,
            operatorCode = operatorCode,
            password = password,
        )
    }

    suspend fun logoutOperator(session: OperatorBackendSession) =
        repository.logoutOperator(session)

    suspend fun restoreOnlineSession(sessionId: String): OperatorBackendSession? =
        repository.restoreOnlineSession(sessionId)

    suspend fun updatePosition(
        sessionId: String,
        position: GpsPosition,
    ) = repository.updatePosition(sessionId, position)

    suspend fun sendOperatorAlarm(
        session: OperatorBackendSession,
        message: String,
    ) = repository.sendOperatorAlarm(session, message)

    suspend fun loadLiveOperators(viewerOperatorCode: String): List<LiveOperatorPin> =
        repository.loadLiveOperators(viewerOperatorCode)

    suspend fun loadOnlineOperatorSessions(): List<OnlineOperatorSession> =
        repository.loadOnlineOperatorSessions()

    suspend fun forceLogoutOperatorSession(
        target: OnlineOperatorSession,
        actorCode: String,
    ) = repository.forceLogoutOperatorSession(target, actorCode)

    suspend fun setPeerVisible(
        sessionId: String,
        visible: Boolean,
        actorCode: String,
    ) = repository.setPeerVisible(sessionId, visible, actorCode)
}
