package it.ansmi.tocsar

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun currentTimeHm(): String =
    SimpleDateFormat("HH:mm", Locale.ITALY).format(Date())
