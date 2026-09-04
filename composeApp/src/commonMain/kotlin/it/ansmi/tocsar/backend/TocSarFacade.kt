package it.ansmi.tocsar.backend

class TocSarFacade(
    config: TocSarConfig,
) {
    private val repository = OperatorRepository(config)

    suspend fun loadActiveEvent(organizationId: String): EventInfo? =
        repository.loadActiveEvent(organizationId)

    suspend fun loginOperator(
        organizationCode: String,
        operatorCode: String,
        password: String,
    ): OperatorBackendSession {
        val orgCode = normalizeOrgCode(organizationCode)
        if (orgCode.isEmpty()) {
            throw TocSarException("Inserisci il codice ente.")
        }
        val org =
            repository.findOrganization(orgCode)
                ?: throw TocSarException("Ente non trovato.")
        val event =
            repository.loadActiveEvent(org.id)
                ?: throw TocSarException("Nessun evento attivo per questo ente.")
        return repository.loginOperator(
            eventId = event.id,
            organizationId = org.id,
            organizationCode = normalizeOrgCode(org.orgCode),
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

    suspend fun sendFieldPhoto(
        session: OperatorBackendSession,
        jpegBytes: ByteArray,
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        note: String?,
    ) = repository.sendFieldPhoto(session, jpegBytes, latitude, longitude, accuracyM, note)

    suspend fun loadLiveOperators(
        viewerOperatorCode: String,
        organizationId: String,
    ): List<LiveOperatorPin> =
        repository.loadLiveOperators(viewerOperatorCode, organizationId)

    suspend fun loadOnlineOperatorSessions(organizationId: String): List<OnlineOperatorSession> =
        repository.loadOnlineOperatorSessions(organizationId)

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
