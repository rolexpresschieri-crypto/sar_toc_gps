package it.ansmi.tocsar.stationary

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

private val mainHandler = Handler(Looper.getMainLooper())
private var tone: ToneGenerator? = null
private var soundGen = 0
private val bipRunnable = object : Runnable {
    var gen = 0
    var remaining = 0
    override fun run() {
        if (gen != soundGen || remaining <= 0) return
        try {
            val t = tone ?: ToneGenerator(AudioManager.STREAM_ALARM, 80).also { tone = it }
            t.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 110)
        } catch (_: Exception) {
            /* device senza audio */
        }
        remaining -= 420
        if (remaining > 0 && gen == soundGen) {
            mainHandler.postDelayed(this, 420L)
        }
    }
}

actual fun playStationaryBips(durationMs: Long) {
    stopStationaryBips()
    soundGen += 1
    bipRunnable.gen = soundGen
    bipRunnable.remaining = durationMs.toInt().coerceAtLeast(420)
    mainHandler.post(bipRunnable)
}

actual fun stopStationaryBips() {
    soundGen += 1
    mainHandler.removeCallbacks(bipRunnable)
    try {
        tone?.stopTone()
    } catch (_: Exception) {
    }
}
