package it.ansmi.tocsar.backend

class TocSarException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
