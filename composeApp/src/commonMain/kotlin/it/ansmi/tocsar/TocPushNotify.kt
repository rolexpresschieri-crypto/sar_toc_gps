package it.ansmi.tocsar

/** Avviso di sistema per notifica arrivata dal TOC (anche con l'app in tasca). */
expect fun showTocPushNotification(title: String, body: String)

expect fun clearTocPushNotification()
