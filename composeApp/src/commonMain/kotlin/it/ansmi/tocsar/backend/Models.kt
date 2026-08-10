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
)

data class EventInfo(
    val id: String,
    val title: String,
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
