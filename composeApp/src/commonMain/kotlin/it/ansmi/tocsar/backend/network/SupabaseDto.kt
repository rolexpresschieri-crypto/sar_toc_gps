package it.ansmi.tocsar.backend.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class EventRow(
    val id: String,
    val title: String,
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
)

@Serializable
internal data class SessionRestoreRow(
    val id: String,
    @SerialName("is_online") val isOnline: Boolean,
    @SerialName("event_id") val eventId: String,
    @SerialName("squad_id") val operatorId: String,
    @SerialName("login_at") val loginAt: String,
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
internal data class SessionAuthLogInsertBody(
    @SerialName("event_id") val eventId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("squad_id") val operatorId: String,
    @SerialName("squad_code") val operatorCode: String,
    @SerialName("squad_name") val operatorName: String,
    val action: String,
)

@Serializable
internal data class ActiveOperatorSummaryRow(
    @SerialName("session_id") val sessionId: String,
    @SerialName("squad_code") val operatorCode: String,
    @SerialName("squad_name") val operatorName: String,
    @SerialName("last_latitude") val lastLatitude: Double? = null,
    @SerialName("last_longitude") val lastLongitude: Double? = null,
    @SerialName("map_color") val mapColor: String? = null,
    @SerialName("map_icon_key") val mapIconKey: String? = null,
    @SerialName("last_accuracy") val lastAccuracy: Double? = null,
)
