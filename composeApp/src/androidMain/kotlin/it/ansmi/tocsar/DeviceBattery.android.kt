package it.ansmi.tocsar

import android.content.Context
import android.os.BatteryManager

actual fun readBatteryPercent(): Int? {
    val manager = AndroidAppContext.require()
        .getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        ?: return null
    val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    return percent.takeIf { it in 0..100 }
}
