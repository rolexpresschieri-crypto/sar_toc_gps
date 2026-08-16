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
                                    api.logoutOperator(
                                        OperatorBackendSession(
                                            sessionId = current.sessionId,
                                            eventId = current.eventId,
                                            operatorId = current.operatorId,
                                            operatorCode = current.operatorCode,
                                            operatorName = current.displayName,
                                            loginAtIso = "",
                                        ),
                                    )
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
                            toast("INVIA NOTIFICA: collegamento TOC nel prossimo step")
                        },
                        onSendPhoto = {
                            toast("INVIA FOTO: collegamento TOC nel prossimo step")
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
            AppRoute.Login -> {
                VegetatoBackground {
                    LoginScreen(
                        isLoading = loginBusy,
                        errorMessage = loginError,
                        onBack = {
                            if (!loginBusy) {
                                loginError = null
                                route = AppRoute.Home
                            }
                        },
                        onLogin = { code, password ->
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
                                    val backend = api.loginOperator(code, password)
                                    applyBackendSession(backend)
                                    route = AppRoute.Home
                                    toast("Login: ${backend.operatorCode}")
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
    )
