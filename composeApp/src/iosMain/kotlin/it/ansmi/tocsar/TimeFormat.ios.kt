package it.ansmi.tocsar

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

actual fun currentTimeHm(): String {
    val fmt = NSDateFormatter()
    fmt.dateFormat = "HH:mm"
    fmt.locale = NSLocale(localeIdentifier = "it_IT")
    return fmt.stringFromDate(NSDate())
}
