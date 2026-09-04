package it.ansmi.tocsar.backend.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class EventRow(
    val id: String,
    val title: String,
    @SerialName("organization_id") val organizationId: String,
)

@Serializable
internal data class OrganizationRow(
    val id: String,
    @SerialName("org_code") val orgCode: String,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
)

@Serializable
internal data class OperatorRow(
    val id: String,
    @SerialName("squad_code") val operatorCode: String,
    @SerialName("squad_name") val operatorName: String,
    @SerialName("password_hash") val passwordHash: String,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("map_color") val mapColor: String? = null,
    @SerialName("map_icon_key") val mapIconKey: String? = null,
    @SerialName("organization_id") val organizationId: String,
)

@Serializable
internal data class SessionRestoreRow(
    val id: String,
    @SerialName("is_online") val isOnline: Boolean,
    @SerialName("event_id") val eventId: String,
    @SerialName("squad_id") val operatorId: String,
    @SerialName("login_at") val loginAt: String,
    @SerialName("organization_id") val organizationId: String,
    val squads: OperatorCodeNameRow,
)

@Serializable
internal data class OperatorCodeNameRow(
    @SerialName("squad_code") val operatorCode: String,
    @SerialName("squad_name") val operatorName: String,
)

@Serializable
internal data class SessionOnlineRow(
    val id: String,
    @SerialName("is_online") val isOnline: Boolean,
)

@Serializable
internal data class SessionInsertRow(
    val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("squad_id") val operatorId: String,
    @SerialName("login_at") val loginAt: String,
)

@Serializable
internal data class SessionInsertBody(
    @SerialName("event_id") val eventId: String,
    @SerialName("squad_id") val operatorId: String,
    @SerialName("is_online") val isOnline: Boolean,
    @SerialName("login_at") val loginAt: String,
    @SerialName("peer_visible") val peerVisible: Boolean = false,
    @SerialName("organization_id") val organizationId: String,
)

@Serializable
internal data class PeerVisiblePatchBody(
    @SerialName("peer_visible") val peerVisible: Boolean,
)

@Serializable
internal data class LogoutPatchBody(
    @SerialName("is_online") val isOnline: Boolean,
    @SerialName("logout_at") val logoutAt: String,
)

@Serializable
internal data class PositionPatchBody(
    @SerialName("last_latitude") val lastLatitude: Double,
    @SerialName("last_longitude") val lastLongitude: Double,
    @SerialName("last_accuracy") val lastAccuracy: Double?,
    @SerialName("last_fix_at") val lastFixAt: String,
)

@Serializable
internal data class FieldPhotoLogInsertBody(
    @SerialName("event_id") val eventId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("squad_id") val operatorId: String,
    @SerialName("squad_code") val operatorCode: String,
    @SerialName("squad_name") val operatorName: String,
    @SerialName("organization_id") val organizationId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_m") val accuracyM: Double? = null,
    val note: String? = null,
    @SerialName("storage_path") val storagePath: String? = null,
    val status: String,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
internal data class SquadAlarmInsertBody(
    @SerialName("event_id") val eventId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("squad_id") val operatorId: String,
    @SerialName("squad_code") val operatorCode: String,
    @SerialName("squad_name") val operatorName: String,
    val message: String,
    @SerialName("organization_id") val organizationId: String,
)

@Serializable
internal data class SessionAuthLogInsertBody(
    @SerialName("event_id") val eventId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("squad_id") val operatorId: String,
    @SerialName("squad_code") val operatorCode: String,
    @SerialName("squad_name") val operatorName: String,
    val action: String,
    @SerialName("organization_id") val organizationId: String,
)

@Serializable
internal data class ActiveOperatorSummaryRow(
    @SerialName("session_id") val sessionId: String,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("squad_id") val operatorId: String? = null,
    @SerialName("organization_id") val organizationId: String? = null,
    @SerialName("squad_code") val operatorCode: String,
    @SerialName("squad_name") val operatorName: String,
    @SerialName("login_at") val loginAt: String? = null,
    @SerialName("last_latitude") val lastLatitude: Double? = null,
    @SerialName("last_longitude") val lastLongitude: Double? = null,
    @SerialName("map_color") val mapColor: String? = null,
    @SerialName("map_icon_key") val mapIconKey: String? = null,
    @SerialName("last_accuracy") val lastAccuracy: Double? = null,
    @SerialName("peer_visible") val peerVisible: Boolean = false,
)

@Serializable
internal data class MissionGpsFileRow(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("event_id") val eventId: String? = null,
    val kind: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
)

@Serializable
internal data class TrackLogInsertBody(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("squad_id") val operatorId: String,
    @SerialName("squad_code") val operatorCode: String,
    @SerialName("squad_name") val operatorName: String,
    @SerialName("track_name") val trackName: String,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("duration_s") val durationS: Double,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double? = null,
    @SerialName("elev_gain_m") val elevGainM: Double,
    @SerialName("elev_loss_m") val elevLossM: Double,
    @SerialName("n_points") val nPoints: Int,
)
