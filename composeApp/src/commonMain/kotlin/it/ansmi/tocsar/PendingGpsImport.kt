package it.ansmi.tocsar

/**
 * File WP/TRK in arrivo da intent Android (Apri con / Condividi).
 */
object PendingGpsImport {
    data class Payload(val fileName: String, val body: String)

    @Volatile
    private var pending: Payload? = null

    fun offer(fileName: String, body: String) {
        val name = fileName.trim().ifBlank { "IMPORT.trk" }
        val text = body.trim()
        if (text.isEmpty()) return
        pending = Payload(name, text)
    }

    fun take(): Payload? {
        val cur = pending
        pending = null
        return cur
    }

    fun peek(): Payload? = pending
}
