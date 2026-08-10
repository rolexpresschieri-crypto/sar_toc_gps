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

    suspend fun loadLiveOperators(): List<LiveOperatorPin> =
        repository.loadLiveOperators()
}
