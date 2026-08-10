package it.ansmi.tocsar.backend

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter

internal actual fun nowIso(): String =
    NSISO8601DateFormatter().stringFromDate(NSDate())
