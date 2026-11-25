package com.accesolibre.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.accesolibre.app.di.AppContainer
import com.accesolibre.app.ui.screens.ActivateUnitScreen
import com.accesolibre.app.ui.screens.HistoryScreen
import com.accesolibre.app.ui.screens.HomeScreen
import com.accesolibre.app.ui.screens.IntercomScreen
import com.accesolibre.app.ui.screens.LoginScreen
import com.accesolibre.app.ui.screens.QrGeneratorScreen
import com.accesolibre.app.ui.screens.ServerConfigScreen
import com.accesolibre.app.ui.theme.AccesoLibreTheme
import com.accesolibre.app.ui.viewmodel.ActivateUnitViewModel
import com.accesolibre.app.ui.viewmodel.HistoryViewModel
import com.accesolibre.app.ui.viewmodel.HomeViewModel
import com.accesolibre.app.ui.viewmodel.IntercomViewModel
import com.accesolibre.app.ui.viewmodel.LoginViewModel
import com.accesolibre.app.ui.viewmodel.QrGeneratorViewModel
import com.accesolibre.app.ui.viewmodel.ServerConfigViewModel
import com.accesolibre.app.ui.viewmodel.simpleFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AccesoLibreApp).container
        setContent {
            AccesoLibreTheme {
                AccesoLibreRoot(container)
            }
        }
    }
}

private enum class MainTab(val label: String) {
    HOME("Acceso"),
    QR("QR Offline"),
    INTERCOM("Citofonía"),
    HISTORY("Historial"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccesoLibreRoot(container: AppContainer) {
    val serverUrl by container.settingsRepository.serverUrl.collectAsStateWithLifecycle()
    val session by container.authRepository.session.collectAsStateWithLifecycle()
    var editingServer by remember { mutableStateOf(false) }
    var activatingUnit by remember { mutableStateOf(false) }

    when {
        serverUrl.isNullOrBlank() || editingServer -> {
            val vm: ServerConfigViewModel = viewModel(
                factory = simpleFactory { ServerConfigViewModel(container.settingsRepository) }
            )
            ServerConfigScreen(
                viewModel = vm,
                onSaved = { editingServer = false },
            )
        }
        session == null && activatingUnit -> {
            val vm: ActivateUnitViewModel = viewModel(
                factory = simpleFactory { ActivateUnitViewModel(container.activateUnitUseCase) }
            )
            ActivateUnitScreen(
                serverUrl = serverUrl.orEmpty(),
                viewModel = vm,
                onActivated = { activatingUnit = false },
                onBackToLogin = { activatingUnit = false },
            )
        }
        session == null -> {
            val vm: LoginViewModel = viewModel(
                factory = simpleFactory { LoginViewModel(container.loginUseCase) }
            )
            LoginScreen(
                serverUrl = serverUrl.orEmpty(),
                viewModel = vm,
                onLoginSuccess = { /* session StateFlow transitions automatically */ },
                onActivateUnit = { activatingUnit = true },
                onChangeServer = { editingServer = true },
            )
        }
        else -> {
            val currentSession = session!!
            val scope = rememberCoroutineScope()
            var selectedTab by remember { mutableStateOf(MainTab.HOME) }

            val homeVm: HomeViewModel = viewModel(
                key = "home-${currentSession.userId}",
                factory = simpleFactory {
                    HomeViewModel(
                        container.accessRepository,
                        container.authRepository,
                        container.openAccessUseCase,
                        container.syncPendingPassesUseCase,
                    )
                },
            )
            val qrVm: QrGeneratorViewModel = viewModel(
                key = "qr-${currentSession.userId}",
                factory = simpleFactory {
                    QrGeneratorViewModel(
                        container.authRepository,
                        container.passRepository,
                        container.generatePassUseCase,
                    )
                },
            )
            val intercomVm: IntercomViewModel = viewModel(
                key = "intercom-${currentSession.userId}",
                factory = simpleFactory {
                    IntercomViewModel(container.intercomRepository, container.stompClient)
                },
            )
            val historyVm: HistoryViewModel = viewModel(
                key = "history-${currentSession.userId}",
                factory = simpleFactory { HistoryViewModel(container.accessRepository) },
            )

            DisposableEffect(currentSession.userId, serverUrl) {
                container.stompClient.connect()
                onDispose { container.stompClient.disconnect() }
            }

            val incomingCall by container.stompClient.incomingCall.collectAsStateWithLifecycle()

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("AccesoLibre") },
                        actions = {
                            IconButton(onClick = { editingServer = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Servidor local")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    container.stompClient.disconnect()
                                    container.authRepository.logout()
                                }
                            }) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == MainTab.HOME,
                            onClick = { selectedTab = MainTab.HOME },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text(MainTab.HOME.label) },
                        )
                        NavigationBarItem(
                            selected = selectedTab == MainTab.QR,
                            onClick = { selectedTab = MainTab.QR },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            label = { Text(MainTab.QR.label) },
                        )
                        NavigationBarItem(
                            selected = selectedTab == MainTab.INTERCOM,
                            onClick = { selectedTab = MainTab.INTERCOM },
                            icon = { Icon(Icons.Default.Call, contentDescription = null) },
                            label = { Text(MainTab.INTERCOM.label) },
                        )
                        NavigationBarItem(
                            selected = selectedTab == MainTab.HISTORY,
                            onClick = { selectedTab = MainTab.HISTORY },
                            icon = { Icon(Icons.Default.List, contentDescription = null) },
                            label = { Text(MainTab.HISTORY.label) },
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (selectedTab) {
                        MainTab.HOME -> HomeScreen(residentName = currentSession.fullName, viewModel = homeVm)
                        MainTab.QR -> QrGeneratorScreen(viewModel = qrVm)
                        MainTab.INTERCOM -> IntercomScreen(viewModel = intercomVm)
                        MainTab.HISTORY -> HistoryScreen(viewModel = historyVm)
                    }
                }
            }

            // Global real-time alert when a visitor rings the unit while resident is on any tab
            incomingCall?.let { call ->
                if (selectedTab != MainTab.INTERCOM) {
                    AlertDialog(
                        onDismissRequest = { container.stompClient.dismissIncomingCall() },
                        title = { Text("Visita en ${call.deviceName ?: "Puerta"}") },
                        text = {
                            Text(
                                buildString {
                                    append("${call.visitorName} está llamando a tu unidad.")
                                    call.visitorNote?.takeIf { it.isNotBlank() }?.let { append("\nNota: $it") }
                                }
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { intercomVm.allowEntry(call.callId) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            ) {
                                Text("Permitir Ingreso")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { intercomVm.rejectCall(call.callId) }) {
                                Text("Rechazar")
                            }
                        },
                    )
                }
            }
        }
    }
}
