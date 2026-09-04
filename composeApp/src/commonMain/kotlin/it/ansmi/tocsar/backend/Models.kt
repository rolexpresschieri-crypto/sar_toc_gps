package it.ansmi.tocsar.backend

/**
 * Sessione operatore sul TOC (tabella DB: squad_sessions / squads).
 * Un operatore = una riga anagrafica, come una "squadra" in gestSQUADRE.
 */
data class OperatorBackendSession(
    val sessionId: String,
    val eventId: String,
    val operatorId: String,
    val operatorCode: String,
    val operatorName: String,
    val loginAtIso: String,
    val organizationId: String,
    val organizationCode: String,
)

data class EventInfo(
    val id: String,
    val title: String,
    val organizationId: String,
)

data class GpsPosition(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
)

/** Pin operatore online sulla mappa (vista DB active_squad_summaries). */
data class LiveOperatorPin(
    val sessionId: String,
    val operatorCode: String,
    val operatorName: String,
    val latitude: Double,
    val longitude: Double,
    val mapColorHex: String,
    val mapIconKey: String,
    val accuracyM: Double?,
)

/** Riga elenco admin: operatori con sessione online (anche senza fix GPS). */
data class OnlineOperatorSession(
    val sessionId: String,
    val eventId: String,
    val operatorId: String,
    val operatorCode: String,
    val operatorName: String,
    val loginAtIso: String?,
    val hasGpsFix: Boolean,
    /** Se true, gli altri operatori lo vedono in mappa. LUPO (admin) vede tutti indipendentemente dal flag. */
    val peerVisible: Boolean,
    val organizationId: String,
    val organizationCode: String,
)

/** Solo questo codice vede/gestisce l'elenco e il force log-out. */
const val TocAdminOperatorCode = "LUPO"

fun isTocAdminOperator(operatorCode: String): Boolean =
    operatorCode.trim().equals(TocAdminOperatorCode, ignoreCase = true)

fun normalizeOrgCode(code: String): String = code.trim().uppercase()
