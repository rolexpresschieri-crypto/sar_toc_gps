package it.ansmi.tocsar

import platform.UIKit.UIDevice
import kotlin.math.roundToInt

actual fun readBatteryPercent(): Int? {
    val device = UIDevice.currentDevice
    device.batteryMonitoringEnabled = true
    val level = device.batteryLevel
    if (level < 0) return null
    return (level * 100).roundToInt().coerceIn(0, 100)
}
