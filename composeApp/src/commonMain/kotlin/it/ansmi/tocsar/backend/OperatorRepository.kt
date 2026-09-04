package it.ansmi.tocsar.backend

import it.ansmi.tocsar.backend.network.ActiveOperatorSummaryRow
import it.ansmi.tocsar.backend.network.EventRow
import it.ansmi.tocsar.backend.network.LogoutPatchBody
import it.ansmi.tocsar.backend.network.OperatorRow
import it.ansmi.tocsar.backend.network.PeerVisiblePatchBody
import it.ansmi.tocsar.backend.network.PositionPatchBody
import it.ansmi.tocsar.backend.network.SessionAuthLogInsertBody
import it.ansmi.tocsar.backend.network.SessionInsertBody
import it.ansmi.tocsar.backend.network.SessionInsertRow
import it.ansmi.tocsar.backend.network.SessionOnlineRow
import it.ansmi.tocsar.backend.network.SessionRestoreRow
import it.ansmi.tocsar.backend.network.SquadAlarmInsertBody
import it.ansmi.tocsar.backend.network.SupabaseRestClient
import kotlinx.serialization.json.Json

/**
 * Login/sessione/posizione: stessa logica gestSQUADRE, terminologia operatore.
 * Tabelle DB: events, squads (anagrafica operatori), squad_sessions.
 */
class OperatorRepository(
    config: TocSarConfig,
) {
    private val rest = SupabaseRestClient(config)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadActiveEvent(): EventInfo? {
        val row =
            rest.getMaybeSingle(
                table = "events",
                select = "id,title",
                filters = listOf("is_active" to "true"),
            ) { body ->
                json.decodeFromString<EventRow>(body)
            } ?: return null
        return EventInfo(id = row.id, title = row.title)
    }

    suspend fun loginOperator(
        eventId: String,
        operatorCode: String,
        password: String,
    ): OperatorBackendSession {
        val normalizedCode = operatorCode.trim().uppercase()
        val operator =
            rest.getMaybeSingle(
                table = "squads",
                select = "id,squad_code,squad_name,password_hash,is_enabled,map_color,map_icon_key",
                filters = listOf(
                    "squad_code" to normalizedCode,
                    "is_enabled" to "true",
                ),
            ) { body ->
                json.decodeFromString<OperatorRow>(body)
            } ?: throw TocSarException("Operatore non trovato o non abilitato.")

        if (operator.passwordHash != password.trim()) {
            throw TocSarException("Password non valida.")
        }

        if (hasActiveSession(eventId = eventId, operatorId = operator.id)) {
            throw TocSarException("Operatore già online su un altro dispositivo.")
        }

        val now = nowIso()
        val inserted =
            rest.insertReturning(
                table = "squad_sessions",
                body =
                    SessionInsertBody(
                        eventId = eventId,
                        operatorId = operator.id,
                        isOnline = true,
                        loginAt = now,
                        peerVisible = false,
                    ),
            ) { body ->
                json.decodeFromString<SessionInsertRow>(body)
            }

        val session =
            OperatorBackendSession(
                sessionId = inserted.id,
                eventId = inserted.eventId,
                operatorId = inserted.operatorId,
                operatorCode = operator.operatorCode.uppercase(),
                operatorName = operator.operatorName,
                loginAtIso = inserted.loginAt,
            )
        insertSessionAuthLogBestEffort(session, ACTION_LOGIN)
        return session
    }

    suspend fun restoreOnlineSession(sessionId: String): OperatorBackendSession? {
        val row =
            rest.getMaybeSingle(
                table = "squad_sessions",
                select = "id,is_online,event_id,squad_id,login_at,squads(squad_code,squad_name)",
                filters = listOf("id" to sessionId),
            ) { body ->
                json.decodeFromString<SessionRestoreRow>(body)
            } ?: return null

        if (!row.isOnline) {
            return null
        }

        return OperatorBackendSession(
            sessionId = row.id,
            eventId = row.eventId,
            operatorId = row.operatorId,
            operatorCode = row.squads.operatorCode.uppercase(),
            operatorName = row.squads.operatorName,
            loginAtIso = row.loginAt,
        )
    }

    suspend fun logoutOperator(session: OperatorBackendSession) {
        val now = nowIso()
        rest.patch(
            table = "squad_sessions",
            filters = listOf("id" to session.sessionId),
            body = LogoutPatchBody(isOnline = false, logoutAt = now),
        )
        insertSessionAuthLogBestEffort(session, ACTION_LOGOUT)
    }

    suspend fun updatePosition(
        sessionId: String,
        position: GpsPosition,
    ) {
        rest.patch(
            table = "squad_sessions",
            filters = listOf("id" to sessionId),
            body =
                PositionPatchBody(
                    lastLatitude = position.latitude,
                    lastLongitude = position.longitude,
                    lastAccuracy = position.accuracyMeters,
                    lastFixAt = nowIso(),
                ),
        )
    }

    suspend fun sendOperatorAlarm(
        session: OperatorBackendSession,
        message: String,
    ) {
        val text = message.trim()
        if (text.isEmpty()) {
            throw TocSarException("Scrivi un messaggio o scegli un tipo di notifica.")
        }
        rest.insert(
            table = "squad_alarms",
            body =
                SquadAlarmInsertBody(
                    eventId = session.eventId,
                    sessionId = session.sessionId,
                    operatorId = session.operatorId,
                    operatorCode = session.operatorCode,
                    operatorName = session.operatorName,
                    message = text.take(500),
                ),
        )
    }

    /**
     * Pin in mappa:
     * - LUPO (admin): tutti gli online con fix GPS (anche nascosti)
     * - altri: solo sessioni con peer_visible = true (anche LUPO, se il flag è acceso)
     */
    suspend fun loadLiveOperators(viewerOperatorCode: String): List<LiveOperatorPin> {
        val admin = isTocAdminOperator(viewerOperatorCode)
        return loadOnlineSummaries().mapNotNull { row ->
            if (!admin && !row.peerVisible) return@mapNotNull null
            val lat = row.lastLatitude ?: return@mapNotNull null
            val lon = row.lastLongitude ?: return@mapNotNull null
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            LiveOperatorPin(
                sessionId = row.sessionId,
                operatorCode = row.operatorCode.uppercase(),
                operatorName = row.operatorName,
                latitude = lat,
                longitude = lon,
                mapColorHex = row.mapColor?.trim()?.takeIf { it.isNotEmpty() } ?: "#079B42",
                mapIconKey = row.mapIconKey?.trim()?.ifEmpty { "squadre_a_piedi" } ?: "squadre_a_piedi",
                accuracyM = row.lastAccuracy,
            )
        }
    }

    /** Elenco admin: tutte le sessioni online (anche senza GPS). */
    suspend fun loadOnlineOperatorSessions(): List<OnlineOperatorSession> {
        return loadOnlineSummaries().mapNotNull { row ->
            val eventId = row.eventId?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val operatorId = row.operatorId?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val lat = row.lastLatitude
            val lon = row.lastLongitude
            val hasGps = lat != null && lon != null && lat.isFinite() && lon.isFinite()
            OnlineOperatorSession(
                sessionId = row.sessionId,
                eventId = eventId,
                operatorId = operatorId,
                operatorCode = row.operatorCode.uppercase(),
                operatorName = row.operatorName,
                loginAtIso = row.loginAt,
                hasGpsFix = hasGps,
                peerVisible = row.peerVisible,
            )
        }
    }

    /**
     * Force log-out da admin (LUPO). Solo log-out esplicito / forzato — mai automatico.
     */
    suspend fun forceLogoutOperatorSession(
        target: OnlineOperatorSession,
        actorCode: String,
    ) {
        if (!isTocAdminOperator(actorCode)) {
            throw TocSarException("Solo $TocAdminOperatorCode può forzare il log-out.")
        }
        logoutOperator(
            OperatorBackendSession(
                sessionId = target.sessionId,
                eventId = target.eventId,
                operatorId = target.operatorId,
                operatorCode = target.operatorCode,
                operatorName = target.operatorName,
                loginAtIso = target.loginAtIso.orEmpty(),
            ),
        )
    }

        /** Flag visibilità in mappa verso gli altri operatori (vale anche per LUPO). */
    suspend fun setPeerVisible(
        sessionId: String,
        visible: Boolean,
        actorCode: String,
    ) {
        if (!isTocAdminOperator(actorCode)) {
            throw TocSarException("Solo $TocAdminOperatorCode può impostare la visibilità.")
        }
        rest.patch(
            table = "squad_sessions",
            filters = listOf("id" to sessionId),
            body = PeerVisiblePatchBody(peerVisible = visible),
        )
    }

    private suspend fun loadOnlineSummaries(): List<ActiveOperatorSummaryRow> {
        return rest.getList(
            table = "active_squad_summaries",
            select =
                "session_id,event_id,squad_id,squad_code,squad_name,login_at," +
                    "last_latitude,last_longitude,map_color,map_icon_key,last_accuracy,peer_visible",
            order = "squad_code.asc",
        ) { body ->
            json.decodeFromString<List<ActiveOperatorSummaryRow>>(body)
        }
    }

    private suspend fun insertSessionAuthLogBestEffort(
        session: OperatorBackendSession,
        action: String,
    ) {
        runCatching {
            rest.insert(
                table = "squad_session_auth_logs",
                body =
                    SessionAuthLogInsertBody(
                        eventId = session.eventId,
                        sessionId = session.sessionId,
                        operatorId = session.operatorId,
                        operatorCode = session.operatorCode,
                        operatorName = session.operatorName,
                        action = action,
                    ),
            )
        }
    }

    private suspend fun hasActiveSession(
        eventId: String,
        operatorId: String,
    ): Boolean {
        val rows =
            rest.getList(
                table = "squad_sessions",
                select = "id,is_online",
                eqFilters =
                    listOf(
                        "event_id" to eventId,
                        "squad_id" to operatorId,
                        "is_online" to "true",
                    ),
                limit = 1,
            ) { body ->
                json.decodeFromString<List<SessionOnlineRow>>(body)
            }
        return rows.isNotEmpty()
    }

    companion object {
        private const val ACTION_LOGIN = "login"
        private const val ACTION_LOGOUT = "logout"
    }
}

internal expect fun nowIso(): String
