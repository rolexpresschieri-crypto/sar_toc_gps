package it.ansmi.tocsar.backend

import it.ansmi.tocsar.backend.network.ActiveOperatorSummaryRow
import it.ansmi.tocsar.backend.network.EventRow
import it.ansmi.tocsar.backend.network.LogoutPatchBody
import it.ansmi.tocsar.backend.network.OperatorRow
import it.ansmi.tocsar.backend.network.OrganizationRow
import it.ansmi.tocsar.backend.network.PeerVisiblePatchBody
import it.ansmi.tocsar.backend.network.PositionPatchBody
import it.ansmi.tocsar.backend.network.SessionAuthLogInsertBody
import it.ansmi.tocsar.backend.network.SessionInsertBody
import it.ansmi.tocsar.backend.network.SessionInsertRow
import it.ansmi.tocsar.backend.network.SessionOnlineRow
import it.ansmi.tocsar.backend.network.SessionRestoreRow
import it.ansmi.tocsar.backend.network.FieldPhotoLogInsertBody
import it.ansmi.tocsar.backend.network.MissionGpsFileRow
import it.ansmi.tocsar.backend.network.SquadAlarmInsertBody
import it.ansmi.tocsar.backend.network.TrackLogInsertBody
import it.ansmi.tocsar.backend.network.SupabaseRestClient
import it.ansmi.tocsar.geo.MapTrackOverlay
import it.ansmi.tocsar.geo.MissionGpsContent
import it.ansmi.tocsar.geo.TrackStats
import it.ansmi.tocsar.geo.WaypointItem
import it.ansmi.tocsar.geo.missionFolderFromStoragePath
import it.ansmi.tocsar.geo.parseMissionWaypoints
import it.ansmi.tocsar.geo.parseTrkFile
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

    internal suspend fun findOrganization(orgCode: String): OrganizationRow? {
        val code = normalizeOrgCode(orgCode)
        if (code.isEmpty()) return null
        return rest.getMaybeSingle(
            table = "organizations",
            select = "id,org_code,is_enabled",
            filters = listOf("org_code" to code),
        ) { body ->
            json.decodeFromString<OrganizationRow>(body)
        }
    }

    suspend fun loadActiveEvent(organizationId: String): EventInfo? {
        val row =
            rest.getMaybeSingle(
                table = "events",
                select = "id,title,organization_id",
                filters = listOf(
                    "is_active" to "true",
                    "organization_id" to organizationId,
                ),
            ) { body ->
                json.decodeFromString<EventRow>(body)
            } ?: return null
        return EventInfo(id = row.id, title = row.title, organizationId = row.organizationId)
    }

    suspend fun loginOperator(
        eventId: String,
        organizationId: String,
        organizationCode: String,
        operatorCode: String,
        password: String,
    ): OperatorBackendSession {
        val normalizedCode = operatorCode.trim().uppercase()
        val operator =
            rest.getMaybeSingle(
                table = "squads",
                select =
                    "id,squad_code,squad_name,password_hash,is_enabled," +
                        "map_color,map_icon_key,organization_id",
                filters = listOf(
                    "squad_code" to normalizedCode,
                    "organization_id" to organizationId,
                    "is_enabled" to "true",
                ),
            ) { body ->
                json.decodeFromString<OperatorRow>(body)
            } ?: throw TocSarException("Operatore non trovato o non abilitato.")

        if (operator.passwordHash != password.trim()) {
            throw TocSarException("Password non valida.")
        }

        if (operator.organizationId != organizationId) {
            throw TocSarException("Operatore e evento non appartengono allo stesso ente.")
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
                        organizationId = organizationId,
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
                organizationId = organizationId,
                organizationCode = normalizeOrgCode(organizationCode),
            )
        insertSessionAuthLogBestEffort(session, ACTION_LOGIN)
        return session
    }

    suspend fun restoreOnlineSession(sessionId: String): OperatorBackendSession? {
        val row =
            rest.getMaybeSingle(
                table = "squad_sessions",
                select =
                    "id,is_online,event_id,squad_id,login_at,organization_id," +
                        "squads(squad_code,squad_name)",
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
            organizationId = row.organizationId,
            organizationCode = loadOrganizationCode(row.organizationId),
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
                    organizationId = session.organizationId,
                ),
        )
    }

    suspend fun sendFieldPhoto(
        session: OperatorBackendSession,
        jpegBytes: ByteArray,
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        note: String?,
    ) {
        if (jpegBytes.isEmpty()) {
            throw TocSarException("Nessuna foto da inviare.")
        }
        val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        val pathSeg = session.operatorCode.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val eventSeg = session.eventId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val orgSeg = session.organizationCode.replace(Regex("[^A-Z0-9._-]"), "_")
        val objectPath = "$orgSeg/$eventSeg/$pathSeg/${nowIso().replace(":", "").replace(".", "_")}.jpg"
        try {
            rest.uploadStorageObject(
                bucket = "squad-photos",
                objectPath = objectPath,
                bytes = jpegBytes,
                contentType = "image/jpeg",
            )
            rest.insert(
                table = "squad_field_photo_logs",
                body =
                    FieldPhotoLogInsertBody(
                        eventId = session.eventId,
                        sessionId = session.sessionId,
                        operatorId = session.operatorId,
                        operatorCode = session.operatorCode,
                        operatorName = session.operatorName,
                        organizationId = session.organizationId,
                        latitude = latitude,
                        longitude = longitude,
                        accuracyM = accuracyM,
                        note = trimmedNote,
                        storagePath = objectPath,
                        status = "inviato",
                    ),
            )
        } catch (e: Exception) {
            runCatching {
                rest.insert(
                    table = "squad_field_photo_logs",
                    body =
                        FieldPhotoLogInsertBody(
                            eventId = session.eventId,
                            sessionId = session.sessionId,
                            operatorId = session.operatorId,
                            operatorCode = session.operatorCode,
                            operatorName = session.operatorName,
                            organizationId = session.organizationId,
                            latitude = latitude,
                            longitude = longitude,
                            accuracyM = accuracyM,
                            note = trimmedNote,
                            storagePath = null,
                            status = "fallito",
                            errorMessage = e.message?.take(300),
                        ),
                )
            }
            if (e is TocSarException) throw e
            throw TocSarException(e.message ?: "Invio foto non riuscito")
        }
    }

    /**
     * Pin in mappa:
     * - LUPO (admin): tutti gli online con fix GPS (anche nascosti)
     * - altri: solo sessioni con peer_visible = true (anche LUPO, se il flag è acceso)
     */
    suspend fun loadLiveOperators(
        viewerOperatorCode: String,
        organizationId: String,
    ): List<LiveOperatorPin> {
        val admin = isTocAdminOperator(viewerOperatorCode)
        return loadOnlineSummaries(organizationId).mapNotNull { row ->
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

    /** Elenco admin: sessioni online dello stesso ente (anche senza GPS). */
    suspend fun loadOnlineOperatorSessions(organizationId: String): List<OnlineOperatorSession> {
        return loadOnlineSummaries(organizationId).mapNotNull { row ->
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
                organizationId = row.organizationId?.trim().orEmpty(),
                organizationCode = "",
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
                organizationId = target.organizationId,
                organizationCode = target.organizationCode,
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

    private suspend fun loadOnlineSummaries(organizationId: String): List<ActiveOperatorSummaryRow> {
        val orgId = organizationId.trim()
        if (orgId.isEmpty()) return emptyList()
        return rest.getList(
            table = "active_squad_summaries",
            select =
                "session_id,event_id,squad_id,organization_id,squad_code,squad_name,login_at," +
                    "last_latitude,last_longitude,map_color,map_icon_key,last_accuracy,peer_visible",
            eqFilters = listOf("organization_id" to orgId),
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
                        organizationId = session.organizationId,
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

    suspend fun loadMissionGps(
        organizationId: String,
        eventId: String?,
    ): MissionGpsContent {
        val orgId = organizationId.trim()
        if (orgId.isEmpty()) {
            return MissionGpsContent(emptyList(), emptyList())
        }
        val rows =
            rest.getList(
                table = "mission_gps_files",
                select = "id,organization_id,event_id,kind,file_name,storage_path,is_enabled",
                eqFilters =
                    listOf(
                        "organization_id" to orgId,
                        "is_enabled" to "true",
                    ),
                order = "file_name.asc",
            ) { body ->
                json.decodeFromString<List<MissionGpsFileRow>>(body)
            }
        val event = eventId?.trim()?.takeIf { it.isNotEmpty() }
        val scoped =
            rows.filter { row ->
                val fileEvent = row.eventId?.trim()?.takeIf { it.isNotEmpty() }
                fileEvent == null || fileEvent == event
            }
        val waypoints = mutableListOf<WaypointItem>()
        val tracks = mutableListOf<MapTrackOverlay>()
        val colors = listOf("#1565C0", "#00838F", "#6A1B9A", "#EF6C00", "#2E7D32")
        var colorIdx = 0
        for (row in scoped) {
            val body =
                runCatching {
                    rest.downloadStorageObject("mission-gps", row.storagePath)
                }.getOrNull() ?: continue
            when (row.kind.trim().lowercase()) {
                "wpt" -> {
                    val group = missionFolderFromStoragePath(row.storagePath, row.fileName)
                    waypoints += parseMissionWaypoints(body).map { it.copy(missionGroup = group) }
                }
                "trk" -> {
                    val pts = parseTrkFile(body)
                    if (pts.size >= 2) {
                        tracks +=
                            MapTrackOverlay(
                                name = row.fileName.trim().ifBlank { "TRK" },
                                points = pts,
                                colorHex = colors[colorIdx % colors.size],
                            )
                        colorIdx++
                    }
                }
            }
        }
        return MissionGpsContent(waypoints = waypoints, tracks = tracks)
    }

    suspend fun sendTrackLog(
        session: OperatorBackendSession,
        trackName: String,
        stats: TrackStats,
    ) {
        rest.insert(
            table = "squad_track_logs",
            body =
                TrackLogInsertBody(
                    organizationId = session.organizationId,
                    eventId = session.eventId.trim().takeIf { it.isNotEmpty() },
                    sessionId = session.sessionId.trim().takeIf { it.isNotEmpty() },
                    operatorId = session.operatorId,
                    operatorCode = session.operatorCode,
                    operatorName = session.operatorName,
                    trackName = trackName,
                    distanceM = stats.distanceM,
                    durationS = stats.durationMs / 1000.0,
                    avgSpeedKmh = stats.avgSpeedKmh,
                    elevGainM = stats.elevGainM,
                    elevLossM = stats.elevLossM,
                    nPoints = stats.nPoints,
                ),
        )
    }

    private suspend fun loadOrganizationCode(organizationId: String): String {
        val id = organizationId.trim()
        if (id.isEmpty()) {
            throw TocSarException("Ente mancante. Esegui sql/organizations.sql su Supabase.")
        }
        val row =
            rest.getMaybeSingle(
                table = "organizations",
                select = "id,org_code",
                filters = listOf("id" to id),
            ) { body ->
                json.decodeFromString<OrganizationRow>(body)
            } ?: throw TocSarException("Ente non trovato. Esegui sql/organizations.sql su Supabase.")
        val code = normalizeOrgCode(row.orgCode)
        if (code.isEmpty()) {
            throw TocSarException("Codice ente vuoto.")
        }
        return code
    }

    companion object {
        private const val ACTION_LOGIN = "login"
        private const val ACTION_LOGOUT = "logout"
    }
}

internal expect fun nowIso(): String
