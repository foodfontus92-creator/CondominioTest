package com.accesolibre.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.accesolibre.app.data.remote.StompConnectionState
import com.accesolibre.app.data.remote.StompIntercomClient
import com.accesolibre.app.domain.model.AccessLogItem
import com.accesolibre.app.domain.model.Device
import com.accesolibre.app.domain.model.DeviceKind
import com.accesolibre.app.domain.model.IncomingCall
import com.accesolibre.app.domain.model.IntercomCall
import com.accesolibre.app.domain.model.Pass
import com.accesolibre.app.domain.model.communityLabelsFor
import com.accesolibre.app.domain.repository.AccessRepository
import com.accesolibre.app.domain.repository.AuthRepository
import com.accesolibre.app.domain.repository.IntercomRepository
import com.accesolibre.app.domain.repository.PassRepository
import com.accesolibre.app.domain.repository.SettingsRepository
import com.accesolibre.app.domain.usecase.ActivateUnitUseCase
import com.accesolibre.app.domain.usecase.GeneratePassUseCase
import com.accesolibre.app.domain.usecase.LoginUseCase
import com.accesolibre.app.domain.usecase.OpenAccessUseCase
import com.accesolibre.app.domain.usecase.SyncPendingPassesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

// ---- 1. Server Configuration ViewModel -------------------------------------
data class ServerConfigUiState(
    val urlInput: String = "",
    val testing: Boolean = false,
    val testSuccessMessage: String? = null,
    val errorMessage: String? = null,
)

class ServerConfigViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ServerConfigUiState(urlInput = settings.serverUrl.value ?: "http://192.168.1.100:8080")
    )
    val state: StateFlow<ServerConfigUiState> = _state.asStateFlow()

    fun onUrlChange(value: String) {
        _state.update { it.copy(urlInput = value, errorMessage = null, testSuccessMessage = null) }
    }

    fun testConnection() {
        val normalized = settings.normalize(_state.value.urlInput)
        if (normalized == null) {
            _state.update { it.copy(errorMessage = "URL inválida. Ejemplo: http://192.168.1.100:8080") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true, errorMessage = null, testSuccessMessage = null) }
            settings.testConnection(normalized)
                .onSuccess { status ->
                    _state.update {
                        it.copy(
                            urlInput = normalized,
                            testing = false,
                            testSuccessMessage = "Conexión exitosa ($normalized — $status)",
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            testing = false,
                            errorMessage = err.message ?: "No se pudo conectar al servidor local",
                        )
                    }
                }
        }
    }

    fun saveAndContinue(onSaved: () -> Unit) {
        val normalized = settings.normalize(_state.value.urlInput)
        if (normalized == null) {
            _state.update { it.copy(errorMessage = "URL inválida. Ejemplo: http://192.168.1.100:8080") }
            return
        }
        settings.save(normalized)
        onSaved()
    }
}

// ---- 2. Login ViewModel ----------------------------------------------------
data class LoginUiState(
    val condoCode: String = "",
    val email: String = "",
    val password: String = "",
    val communityType: String = "CONDOMINIUM",
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginUiState(communityType = authRepository.lastCommunityType.value)
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.refreshServerCommunityType()
        }
        viewModelScope.launch {
            authRepository.lastCommunityType.collect { type ->
                _state.update { it.copy(communityType = type) }
            }
        }
    }

    fun onCondoCodeChange(v: String) = _state.update { it.copy(condoCode = v, errorMessage = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, errorMessage = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, errorMessage = null) }

    fun submit(onSuccess: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            loginUseCase(s.condoCode, s.email, s.password)
                .onSuccess {
                    _state.update { it.copy(loading = false, password = "") }
                    onSuccess()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(loading = false, errorMessage = err.message ?: "Error al iniciar sesión")
                    }
                }
        }
    }
}

// ---- 2b. Activate Unit ViewModel -------------------------------------------
data class ActivateUnitUiState(
    val activationCode: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val communityType: String = "CONDOMINIUM",
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

class ActivateUnitViewModel(
    private val activateUnitUseCase: ActivateUnitUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ActivateUnitUiState(communityType = authRepository.lastCommunityType.value)
    )
    val state: StateFlow<ActivateUnitUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.refreshServerCommunityType()
        }
        viewModelScope.launch {
            authRepository.lastCommunityType.collect { type ->
                _state.update { it.copy(communityType = type) }
            }
        }
    }

    fun onCodeChange(v: String) = _state.update { it.copy(activationCode = v.uppercase(), errorMessage = null) }
    fun onFullNameChange(v: String) = _state.update { it.copy(fullName = v, errorMessage = null) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v, errorMessage = null) }
    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v, errorMessage = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, errorMessage = null) }

    fun submit(onSuccess: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            activateUnitUseCase(s.activationCode, s.fullName, s.email, s.phone, s.password)
                .onSuccess {
                    _state.update { it.copy(loading = false, password = "") }
                    onSuccess()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            loading = false,
                            errorMessage = err.message ?: communityLabelsFor(s.communityType).activateErrorFallback,
                        )
                    }
                }
        }
    }
}

// ---- 3. Home (Quick Open + GPS Geofencing) ViewModel -----------------------
data class HomeUiState(
    val devices: List<Device> = emptyList(),
    val loadingDevices: Boolean = false,
    val openingKind: DeviceKind? = null,
    val feedbackSuccess: String? = null,
    val feedbackError: String? = null,
    val blockName: String? = null,
    val unitNumber: String? = null,
    val communityType: String = "CONDOMINIUM",
)

class HomeViewModel(
    private val accessRepository: AccessRepository,
    private val authRepository: AuthRepository,
    private val openAccessUseCase: OpenAccessUseCase,
    private val syncPendingPassesUseCase: SyncPendingPassesUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(
        HomeUiState(
            blockName = authRepository.session.value?.blockName ?: authRepository.unitSecret()?.blockName,
            unitNumber = authRepository.session.value?.unitNumber ?: authRepository.unitSecret()?.unitNumber,
            communityType = authRepository.session.value?.communityType ?: "CONDOMINIUM",
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loadingDevices = true,
                    blockName = authRepository.session.value?.blockName ?: authRepository.unitSecret()?.blockName,
                    unitNumber = authRepository.session.value?.unitNumber ?: authRepository.unitSecret()?.unitNumber,
                    communityType = authRepository.session.value?.communityType ?: it.communityType,
                )
            }
            if (authRepository.unitSecret() == null) {
                authRepository.refreshUnitSecret()
            }
            syncPendingPassesUseCase()
            accessRepository.devices()
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            devices = list.filter { d -> d.active },
                            loadingDevices = false,
                            blockName = authRepository.session.value?.blockName ?: authRepository.unitSecret()?.blockName,
                            unitNumber = authRepository.session.value?.unitNumber ?: authRepository.unitSecret()?.unitNumber,
                            communityType = authRepository.session.value?.communityType ?: it.communityType,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            loadingDevices = false,
                            feedbackError = err.message ?: "Sin conexión al servidor local",
                        )
                    }
                }
        }
    }

    fun openByKind(kind: DeviceKind) {
        val target = _state.value.devices.firstOrNull { it.matches(kind) }
        if (target == null) {
            val label = if (kind == DeviceKind.GATE) "portón vehicular" else "puerta peatonal"
            val labels = communityLabelsFor(_state.value.communityType)
            _state.update {
                it.copy(
                    feedbackSuccess = null,
                    feedbackError = "No hay ningún dispositivo de $label configurado ${labels.inYourCommunity}.",
                )
            }
            return
        }
        openDevice(target, kind)
    }

    fun openDevice(device: Device, kind: DeviceKind) {
        viewModelScope.launch {
            _state.update { it.copy(openingKind = kind, feedbackSuccess = null, feedbackError = null) }
            openAccessUseCase(device.id)
                .onSuccess { outcome ->
                    val dist = "%.0f".format(outcome.distanceMeters)
                    val labels = communityLabelsFor(authRepository.session.value?.communityType ?: _state.value.communityType)
                    _state.update {
                        it.copy(
                            openingKind = null,
                            feedbackSuccess = "${outcome.deviceName} abierto (${dist} m ${labels.ofCommunity})",
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            openingKind = null,
                            feedbackError = err.message ?: "No se pudo abrir el acceso",
                        )
                    }
                }
        }
    }

    fun clearFeedback() = _state.update { it.copy(feedbackSuccess = null, feedbackError = null) }
}

// ---- 4. Offline QR Generator ViewModel -------------------------------------
data class QrGeneratorUiState(
    val visitorName: String = "",
    val passType: String = "VISITOR",
    val maxUses: Int = 1,
    val selectedPass: Pass? = null,
    val generating: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val hasUnitSecret: Boolean = false,
)

class QrGeneratorViewModel(
    private val authRepository: AuthRepository,
    private val passRepository: PassRepository,
    private val generatePassUseCase: GeneratePassUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(QrGeneratorUiState(hasUnitSecret = authRepository.unitSecret() != null))
    val state: StateFlow<QrGeneratorUiState> = _state.asStateFlow()

    val passes: StateFlow<List<Pass>> = passRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onVisitorNameChange(v: String) = _state.update { it.copy(visitorName = v, errorMessage = null) }
    fun onPassTypeChange(v: String) = _state.update { it.copy(passType = v) }
    fun onMaxUsesChange(v: Int) = _state.update { it.copy(maxUses = v.coerceIn(1, 20)) }
    fun selectPass(pass: Pass?) = _state.update { it.copy(selectedPass = pass) }

    fun generate(expiresAt: Instant) {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(generating = true, errorMessage = null, infoMessage = null) }
            generatePassUseCase(
                visitorName = s.visitorName,
                passType = s.passType,
                maxUses = s.maxUses,
                expiresAt = expiresAt,
            ).onSuccess { created ->
                _state.update {
                    it.copy(
                        generating = false,
                        visitorName = "",
                        selectedPass = created,
                        hasUnitSecret = true,
                        infoMessage = if (created.synced) {
                            "QR generado offline y registrado en el servidor."
                        } else {
                            "QR generado 100% offline. Se sincronizará cuando haya red local."
                        },
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(generating = false, errorMessage = err.message ?: "Error al generar QR")
                }
            }
        }
    }

    fun revoke(passId: String) {
        viewModelScope.launch {
            passRepository.revoke(passId)
                .onSuccess {
                    _state.update {
                        it.copy(
                            infoMessage = "Pase revocado y difundido a los lectores.",
                            selectedPass = if (it.selectedPass?.passId == passId) null else it.selectedPass,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(errorMessage = err.message ?: "Error al revocar pase") }
                }
        }
    }
}

// ---- 5. Intercom (STOMP + Call History) ViewModel --------------------------
data class IntercomUiState(
    val calls: List<IntercomCall> = emptyList(),
    val loading: Boolean = false,
    val processingCallId: String? = null,
    val feedback: String? = null,
    val errorMessage: String? = null,
)

class IntercomViewModel(
    private val intercomRepository: IntercomRepository,
    private val stompClient: StompIntercomClient,
) : ViewModel() {

    private val _state = MutableStateFlow(IntercomUiState())
    val state: StateFlow<IntercomUiState> = _state.asStateFlow()

    val connectionState: StateFlow<StompConnectionState> = stompClient.connectionState
    val incomingCall: StateFlow<IncomingCall?> = stompClient.incomingCall

    init {
        refreshCalls()
        viewModelScope.launch {
            stompClient.lastEventAtMs.collect { ts ->
                if (ts > 0L) refreshCalls()
            }
        }
    }

    fun refreshCalls() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            intercomRepository.calls(page = 0, size = 30)
                .onSuccess { list -> _state.update { it.copy(calls = list, loading = false) } }
                .onFailure { err ->
                    _state.update { it.copy(loading = false, errorMessage = err.message) }
                }
        }
    }

    fun allowEntry(callId: String) {
        viewModelScope.launch {
            _state.update { it.copy(processingCallId = callId, errorMessage = null, feedback = null) }
            intercomRepository.unlock(callId)
                .onSuccess {
                    stompClient.dismissIncomingCall()
                    _state.update {
                        it.copy(processingCallId = null, feedback = "Puerta abierta para la visita.")
                    }
                    refreshCalls()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            processingCallId = null,
                            errorMessage = err.message ?: "No se pudo autorizar el ingreso",
                        )
                    }
                }
        }
    }

    fun rejectCall(callId: String) {
        viewModelScope.launch {
            _state.update { it.copy(processingCallId = callId, errorMessage = null, feedback = null) }
            intercomRepository.reject(callId)
                .onSuccess {
                    stompClient.dismissIncomingCall()
                    _state.update { it.copy(processingCallId = null, feedback = "Llamada rechazada.") }
                    refreshCalls()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            processingCallId = null,
                            errorMessage = err.message ?: "No se pudo rechazar la llamada",
                        )
                    }
                }
        }
    }

    fun reconnectStomp() = stompClient.connect()
}

// ---- 6. Access History ViewModel -------------------------------------------
data class HistoryUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 0,
    val isLastPage: Boolean = true,
)

class HistoryViewModel(
    private val accessRepository: AccessRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    val logs: StateFlow<List<AccessLogItem>> = accessRepository.observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        loadPage(0)
    }

    fun loadNextPage() {
        val s = _state.value
        if (s.loading || s.isLastPage) return
        loadPage(s.currentPage + 1)
    }

    private fun loadPage(page: Int) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            accessRepository.refreshLogs(page = page, size = 25)
                .onSuccess { last ->
                    _state.update { it.copy(loading = false, currentPage = page, isLastPage = last) }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(loading = false, errorMessage = err.message ?: "Error al cargar bitácora")
                    }
                }
        }
    }
}

fun <T : ViewModel> simpleFactory(creator: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
    }
