package it.ansmi.tocsar

/** Apre le impostazioni dell'app sul dispositivo. */
expect fun openAppSystemSettings()

/** Testo home: come tenere GPS e notifiche TOC attivi sul telefono. */
expect fun unusedAppPermissionHint(): String
