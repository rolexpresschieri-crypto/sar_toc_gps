package it.ansmi.tocsar.stationary

/** Serie di bip (~durationMs). Solo Android ha implementazione sonora per ora. */
expect fun playStationaryBips(durationMs: Long = STATIONARY_SOUND_MS)

expect fun stopStationaryBips()
