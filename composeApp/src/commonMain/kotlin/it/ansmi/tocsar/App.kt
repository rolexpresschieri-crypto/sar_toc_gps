package it.ansmi.tocsar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.ansmi.tocsar.backend.OperatorBackendSession
import it.ansmi.tocsar.backend.OperatorSessionStore
import it.ansmi.tocsar.backend.TocSarException
import it.ansmi.tocsar.backend.TocSarFacade
import it.ansmi.tocsar.backend.isTocAdminOperator
import it.ansmi.tocsar.backend.loadTocSarConfig
import it.ansmi.tocsar.geo.createGpsLocalStore
import it.ansmi.tocsar.geo.importGpsFileContent
import it.ansmi.tocsar.geo.OperatorGpsTracking
import it.ansmi.tocsar.ui.SendNotifyScreen
import it.ansmi.tocsar.ui.SendPhotoScreen
import it.ansmi.tocsar.ui.GpsScreen
import it.ansmi.tocsar.ui.HomeScreen
import it.ansmi.tocsar.ui.LoginScreen
import it.ansmi.tocsar.ui.OnlineOperatorsScreen
import it.ansmi.tocsar.ui.OperatorSession
import it.ansmi.tocsar.ui.SplashScreen
import it.ansmi.tocsar.ui.VegetatoBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SplashVisibleMs = 5500L

private enum class AppRoute {
    Splash,
    Home,
    Login,
    Gps,
    OnlineOperators,
    SendNotify,
    SendPhoto,
}

@Composable
fun App() {
    var route by remember { mutableStateOf(AppRoute.Splash) }
    var session by remember { mutableStateOf<OperatorSession?>(null) }
    var tocMessage by remember { mutableStateOf<String?>(null) }
    var gpsStatusLabel by remember { mutableStateOf<String?>(null) }
    var loginBusy by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var logoutBusy by remember { mutableStateOf(false) }
    var notifyBusy by remember { mutableStateOf(false) }
    var notifyError by remember { mutableStateOf<String?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var rememberedOrgCode by remember {
        mutableStateOf(OperatorSessionStore.loadOrganizationCode())
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val facade = remember {
        loadTocSarConfig()?.let { TocSarFacade(it) }
    }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun clearSessionLocal() {
        OperatorGpsTracking.stop()
        session = null
        gpsStatusLabel = null
        OperatorSessionStore.saveSessionId(null)
    }

    fun applyBackendSession(backend: OperatorBackendSession) {
        session = backend.toUiSession()
        OperatorSessionStore.saveSessionId(backend.sessionId)
        OperatorSessionStore.saveOrganizationCode(backend.organizationCode)
        rememberedOrgCode = backend.organizationCode
        val started = OperatorGpsTracking.start(backend.sessionId)
        gpsStatusLabel =
            if (started) {
                OperatorGpsTracking.statusLabel()
                    ?: "GPS attivo verso TOC (anche in tasca)"
            } else {
                "Login ok · concedi permesso GPS per tracking TOC"
            }
    }

    LaunchedEffect(Unit) {
        delay(SplashVisibleMs)
        if (route == AppRoute.Splash) {
            route = AppRoute.Home
        }
    }

    LaunchedEffect(facade) {
        val api = facade ?: return@LaunchedEffect
        val savedId = OperatorSessionStore.loadSessionId() ?: return@LaunchedEffect
        runCatching { api.restoreOnlineSession(savedId) }
            .onSuccess { restored ->
                if (restored != null) {
                    applyBackendSession(restored)
                } else {
                    OperatorSessionStore.saveSessionId(null)
                }
            }
            .onFailure {
                OperatorSessionStore.saveSessionId(null)
            }
    }

    LaunchedEffect(session?.sessionId) {
        if (session == null) return@LaunchedEffect
        while (isActive) {
            OperatorGpsTracking.statusLabel()?.let { gpsStatusLabel = it }
            delay(2_000L)
        }
    }

    // File aperti/condivisi verso TOC SAR (WhatsApp, Drive, file manager, …)
    LaunchedEffect(Unit) {
        val store = createGpsLocalStore()
        while (isActive) {
            val payload = PendingGpsImport.take()
            if (payload != null) {
                val msg = withContext(Dispatchers.Default) {
                    importGpsFileContent(store, payload.fileName, payload.body)
                }
                toast(msg)
            }
            delay(700L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            AppRoute.Splash -> {
                VegetatoBackground { SplashScreen() }
            }
            AppRoute.Home -> {
                VegetatoBackground {
                    HomeScreen(
                        session = session,
                        tocMessage = tocMessage,
                        gpsStatusLabel = gpsStatusLabel,
                        onResetNotification = {
                            tocMessage = null
                            toast("Notifica resettata sul telefono")
                        },
                        onLogin = {
                            loginError = null
                            route = AppRoute.Login
                        },
                        onLogout = {
                            if (logoutBusy) return@HomeScreen
                            val current = session
                            val api = facade
                            if (current == null) return@HomeScreen
                            if (api == null) {
                                clearSessionLocal()
                                toast("Log-out locale")
                                return@HomeScreen
                            }
                            scope.launch {
                                logoutBusy = true
                                try {
                                    api.logoutOperator(current.toBackend())
                                    clearSessionLocal()
                                    toast("Log-out effettuato")
                                } catch (e: Exception) {
                                    toast(e.message ?: "Errore log-out")
                                } finally {
                                    logoutBusy = false
                                }
                            }
                        },
                        onSendNotify = {
                            notifyError = null
                            route = AppRoute.SendNotify
                        },
                        onSendPhoto = {
                            photoError = null
                            route = AppRoute.SendPhoto
                        },
                        onOpenGps = { route = AppRoute.Gps },
                        onOpenOnlineOperators =
                            if (session != null && isTocAdminOperator(session!!.operatorCode)) {
                                { route = AppRoute.OnlineOperators }
                            } else {
                                null
                            },
                    )
                }
            }
            AppRoute.OnlineOperators -> {
                val current = session
                val api = facade
                if (current == null || api == null || !isTocAdminOperator(current.operatorCode)) {
                    route = AppRoute.Home
                } else {
                    OnlineOperatorsScreen(
                        facade = api,
                        actorCode = current.operatorCode,
                        organizationId = current.organizationId,
                        selfSessionId = current.sessionId,
                        onBack = { route = AppRoute.Home },
                        onForcedSelfLogout = {
                            clearSessionLocal()
                            route = AppRoute.Home
                            toast("Sei stato disconnesso")
                        },
                        toast = ::toast,
                    )
                }
            }
            AppRoute.SendNotify -> {
                val current = session
                val api = facade
                if (current == null) {
                    route = AppRoute.Home
                } else {
                    VegetatoBackground {
                        SendNotifyScreen(
                            operatorCode = current.operatorCode,
                            isSending = notifyBusy,
                            errorMessage = notifyError,
                            onBack = {
                                if (!notifyBusy) {
                                    notifyError = null
                                    route = AppRoute.Home
                                }
                            },
                            onSend = { message ->
                                if (notifyBusy) return@SendNotifyScreen
                                if (api == null) {
                                    notifyError =
                                        "Config Supabase mancante. Controlla supabase-config.local.json."
                                    return@SendNotifyScreen
                                }
                                scope.launch {
                                    notifyBusy = true
                                    notifyError = null
                                    try {
                                        api.sendOperatorAlarm(
                                            current.toBackend(),
                                            message,
                                        )
                                        toast("Notifica inviata al TOC")
                                        route = AppRoute.Home
                                    } catch (e: TocSarException) {
                                        notifyError = e.message
                                    } catch (e: Exception) {
                                        notifyError = e.message ?: "Invio non riuscito"
                                    } finally {
                                        notifyBusy = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
            AppRoute.SendPhoto -> {
                val current = session
                val api = facade
                if (current == null) {
                    route = AppRoute.Home
                } else {
                    VegetatoBackground {
                        SendPhotoScreen(
                            operatorCode = current.operatorCode,
                            isSending = photoBusy,
                            errorMessage = photoError,
                            onBack = {
                                if (!photoBusy) {
                                    photoError = null
                                    route = AppRoute.Home
                                }
                            },
                            onSend = { jpeg, fix, note ->
                                if (photoBusy) return@SendPhotoScreen
                                if (api == null) {
                                    photoError =
                                        "Config Supabase mancante. Controlla supabase-config.local.json."
                                    return@SendPhotoScreen
                                }
                                scope.launch {
                                    photoBusy = true
                                    photoError = null
                                    try {
                                        api.sendFieldPhoto(
                                            current.toBackend(),
                                            jpegBytes = jpeg,
                                            latitude = fix.latitude,
                                            longitude = fix.longitude,
                                            accuracyM = fix.accuracyM.toDouble(),
                                            note = note,
                                        )
                                        toast("Foto inviata al TOC")
                                        route = AppRoute.Home
                                    } catch (e: TocSarException) {
                                        photoError = e.message
                                    } catch (e: Exception) {
                                        photoError = e.message ?: "Invio foto non riuscito"
                                    } finally {
                                        photoBusy = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
            AppRoute.Login -> {
                VegetatoBackground {
                    LoginScreen(
                        rememberedOrgCode = rememberedOrgCode,
                        isLoading = loginBusy,
                        errorMessage = loginError,
                        onBack = {
                            if (!loginBusy) {
                                loginError = null
                                route = AppRoute.Home
                            }
                        },
                        onChangeOrganization = {
                            if (loginBusy) return@LoginScreen
                            OperatorSessionStore.saveOrganizationCode(null)
                            rememberedOrgCode = null
                            loginError = null
                        },
                        onLogin = { orgCode, code, password ->
                            val api = facade
                            if (api == null) {
                                loginError =
                                    "Config Supabase mancante. Controlla supabase-config.local.json e rebuild."
                                return@LoginScreen
                            }
                            scope.launch {
                                loginBusy = true
                                loginError = null
                                try {
                                    val backend = api.loginOperator(orgCode, code, password)
                                    applyBackendSession(backend)
                                    route = AppRoute.Home
                                    toast("Login: ${backend.organizationCode} · ${backend.operatorCode}")
                                } catch (e: TocSarException) {
                                    loginError = e.message
                                } catch (e: Exception) {
                                    loginError = e.message ?: "Errore di rete"
                                } finally {
                                    loginBusy = false
                                }
                            }
                        },
                    )
                }
            }
            AppRoute.Gps -> {
                if (session == null) {
                    route = AppRoute.Home
                } else {
                    GpsScreen(
                        onBack = { route = AppRoute.Home },
                        navigatorLabel = session?.operatorCode,
                        organizationId = session?.organizationId,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

private fun OperatorBackendSession.toUiSession(): OperatorSession =
    OperatorSession(
        sessionId = sessionId,
        eventId = eventId,
        operatorId = operatorId,
        operatorCode = operatorCode,
        displayName = operatorName,
        loginLabel = currentTimeHm(),
        organizationId = organizationId,
        organizationCode = organizationCode,
    )

private fun OperatorSession.toBackend(): OperatorBackendSession =
    OperatorBackendSession(
        sessionId = sessionId,
        eventId = eventId,
        operatorId = operatorId,
        operatorCode = operatorCode,
        operatorName = displayName,
        loginAtIso = "",
        organizationId = organizationId,
        organizationCode = organizationCode,
    )
