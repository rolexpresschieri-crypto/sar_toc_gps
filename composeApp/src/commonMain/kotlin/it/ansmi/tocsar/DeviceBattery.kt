package it.ansmi.tocsar

/** Percentuale batteria 0–100, oppure null se il sistema non la espone. */
expect fun readBatteryPercent(): Int?
