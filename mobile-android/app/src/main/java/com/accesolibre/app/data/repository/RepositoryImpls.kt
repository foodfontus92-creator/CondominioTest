package com.accesolibre.app.data.repository

import com.accesolibre.app.data.local.AccessLogDao
import com.accesolibre.app.data.local.AccessLogEntity
import com.accesolibre.app.data.local.PassDao
import com.accesolibre.app.data.local.PassEntity
import com.accesolibre.app.data.local.SecureStorage
import com.accesolibre.app.data.remote.ActivateUnitRequestDto
import com.accesolibre.app.data.remote.AuthResponseDto
import com.accesolibre.app.data.remote.LoginRequestDto
import com.accesolibre.app.data.remote.LogoutRequestDto
import com.accesolibre.app.data.remote.NetworkFactory
import com.accesolibre.app.data.remote.RegisterOfflinePassRequestDto
import com.accesolibre.app.data.remote.RemoteOpenRequestDto
import com.accesolibre.app.domain.model.AccessLogItem
import com.accesolibre.app.domain.model.Coordinates
import com.accesolibre.app.domain.model.Device
import com.accesolibre.app.domain.model.IntercomCall
import com.accesolibre.app.domain.model.OpenOutcome
import com.accesolibre.app.domain.model.Pass
import com.accesolibre.app.domain.model.Session
import com.accesolibre.app.domain.model.UnitSecret
import com.accesolibre.app.domain.model.communityLabelsFor
import com.accesolibre.app.domain.repository.AccessRepository
import com.accesolibre.app.domain.repository.AuthRepository
import com.accesolibre.app.domain.repository.IntercomRepository
import com.accesolibre.app.domain.repository.PassRepository
import com.accesolibre.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant

class SettingsRepositoryImpl(
    private val storage: SecureStorage,
    private val network: NetworkFactory,
) : SettingsRepository {

    private val _serverUrl = MutableStateFlow(storage.serverUrl)
    override val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    override fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        val portPart = if ((parsed.scheme == "http" && parsed.port == 80) ||
            (parsed.scheme == "https" && parsed.port == 443)
        ) "" else ":${parsed.port}"
        return "${parsed.scheme}://${parsed.host}$portPart"
    }

    override fun save(url: String) {
        storage.serverUrl = url
        _serverUrl.value = url
    }

    override suspend fun testConnection(url: String): Result<String> = withContext(Dispatchers.IO) {
        network.probeHealth(url)
    }
}

class AuthRepositoryImpl(
    private val storage: SecureStorage,
    private val network: NetworkFactory,
    private val passDao: PassDao,
    private val logDao: AccessLogDao,
) : AuthRepository {

    private val _session = MutableStateFlow(storage.readSession())
    override val session: StateFlow<Session?> = _session.asStateFlow()

    private val _lastCommunityType = MutableStateFlow(
        storage.readSession()?.communityType ?: storage.lastCommunityType ?: "CONDOMINIUM"
    )
    override val lastCommunityType: StateFlow<String> = _lastCommunityType.asStateFlow()

    override suspend fun refreshServerCommunityType() {
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = network.api.setupStatus()
                val type = resp.body()?.defaultCommunityType
                if (resp.isSuccessful && !type.isNullOrBlank()) {
                    storage.lastCommunityType = type
                    _lastCommunityType.value = type
                }
            }
        }
    }

    override suspend fun login(condominiumCode: String, email: String, password: String): Result<Session> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = network.api.login(LoginRequestDto(condominiumCode, email, password))
                if (!resp.isSuccessful || resp.body() == null) {
                    error(network.extractError(resp, "Credenciales inválidas"))
                }
                persistAuthResponse(resp.body()!!)
            }
        }

    override suspend fun activateUnit(
        activationCode: String,
        fullName: String,
        email: String,
        phone: String,
        password: String,
    ): Result<Session> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = network.api.activateUnit(
                ActivateUnitRequestDto(
                    activationCode = activationCode,
                    fullName = fullName,
                    email = email,
                    phone = phone.takeIf { it.isNotBlank() },
                    password = password,
                )
            )
            if (!resp.isSuccessful || resp.body() == null) {
                error(network.extractError(resp, communityLabelsFor(_lastCommunityType.value).activateErrorFallback))
            }
            persistAuthResponse(resp.body()!!)
        }
    }

    private suspend fun persistAuthResponse(body: AuthResponseDto): Session {
        storage.accessToken = body.accessToken
        storage.refreshToken = body.refreshToken

        val resolvedCommunityType = body.user.communityType
            ?: body.communityType
            ?: storage.lastCommunityType
            ?: "CONDOMINIUM"

        val userSession = Session(
            userId = body.user.id,
            condominiumId = body.user.condominiumId,
            unitId = body.user.unitId,
            email = body.user.email,
            fullName = body.user.fullName,
            role = body.user.role,
            communityType = resolvedCommunityType,
            blockName = body.user.blockName,
            unitNumber = body.user.unitNumber,
        )
        storage.saveSession(userSession)
        _lastCommunityType.value = resolvedCommunityType
        _session.value = userSession

        // Immediately fetch & encrypt unit_secret so offline QR generation works right away
        if (!userSession.unitId.isNullOrBlank()) {
            refreshUnitSecret()
        }
        return userSession
    }

    override suspend fun refreshUnitSecret(): Result<UnitSecret> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = network.api.getUnitSecret()
            if (!resp.isSuccessful || resp.body() == null) {
                error(network.extractError(resp, "No se pudo obtener el secreto de la unidad"))
            }
            val dto = resp.body()!!
            val secret = UnitSecret(
                condominiumId = dto.condominiumId,
                unitId = dto.unitId,
                unitNumber = dto.unitNumber,
                blockName = dto.blockName,
                secretHex = dto.unitSecret,
                hmacKeyVersion = dto.hmacKeyVersion,
            )
            storage.saveUnitSecret(secret)
            storage.readSession()?.let { current ->
                val updated = current.copy(
                    blockName = dto.blockName ?: current.blockName,
                    unitNumber = dto.unitNumber.ifBlank { current.unitNumber },
                )
                storage.saveSession(updated)
                _session.value = updated
            }
            secret
        }
    }

    override fun unitSecret(): UnitSecret? = storage.readUnitSecret()

    override suspend fun logout() = withContext(Dispatchers.IO) {
        val refresh = storage.refreshToken
        if (!refresh.isNullOrBlank()) {
            runCatching { network.api.logout(LogoutRequestDto(refresh)) }
        }
        storage.clearAuth()
        passDao.clear()
        logDao.clear()
        _session.value = null
    }
}

class AccessRepositoryImpl(
    private val storage: SecureStorage,
    private val network: NetworkFactory,
    private val logDao: AccessLogDao,
) : AccessRepository {

    override suspend fun devices(): Result<List<Device>> = withContext(Dispatchers.IO) {
        runCatching {
            val condoId = storage.readSession()?.condominiumId ?: error("Sesión no iniciada")
            val resp = network.api.listDevices(condoId)
            if (!resp.isSuccessful || resp.body() == null) {
                error(network.extractError(resp, "Error cargando dispositivos"))
            }
            resp.body()!!.map { d ->
                Device(
                    id = d.id,
                    name = d.name,
                    type = d.deviceType,
                    location = d.locationDescription,
                    status = d.status,
                    active = d.active,
                )
            }
        }
    }

    override suspend fun open(deviceId: String, coordinates: Coordinates): Result<OpenOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = network.api.remoteOpen(
                    RemoteOpenRequestDto(
                        deviceId = deviceId,
                        latitude = coordinates.latitude,
                        longitude = coordinates.longitude,
                    )
                )
                if (!resp.isSuccessful || resp.body() == null) {
                    error(network.extractError(resp, "Acceso denegado"))
                }
                val dto = resp.body()!!
                OpenOutcome(
                    granted = dto.granted,
                    deviceName = dto.deviceName,
                    distanceMeters = dto.distanceMeters,
                    allowedRadiusMeters = dto.allowedRadiusMeters,
                )
            }
        }

    override fun observeLogs(): Flow<List<AccessLogItem>> =
        logDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun refreshLogs(page: Int, size: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val condoId = storage.readSession()?.condominiumId ?: error("Sesión no iniciada")
            val resp = network.api.listAccessLogs(condoId, page, size)
            if (!resp.isSuccessful || resp.body() == null) {
                error(network.extractError(resp, "No se pudo actualizar la bitácora"))
            }
            val body = resp.body()!!
            val entities = body.content.map { item ->
                val ms = runCatching { Instant.parse(item.occurredAt).toEpochMilli() }
                    .getOrDefault(System.currentTimeMillis())
                AccessLogEntity(
                    id = item.id,
                    result = item.result,
                    accessMethod = item.accessMethod,
                    offlineValidated = item.offlineValidated,
                    occurredAtMs = ms,
                )
            }
            if (page == 0) {
                logDao.clear()
            }
            logDao.upsertAll(entities)
            body.last
        }
    }
}

class PassRepositoryImpl(
    private val storage: SecureStorage,
    private val network: NetworkFactory,
    private val passDao: PassDao,
) : PassRepository {

    override fun observe(): Flow<List<Pass>> =
        passDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(passId: String): Pass? = withContext(Dispatchers.IO) {
        passDao.getById(passId)?.toDomain()
    }

    override suspend fun saveLocal(pass: Pass) = withContext(Dispatchers.IO) {
        passDao.upsert(PassEntity.fromDomain(pass))
    }

    override suspend fun syncPending(): Int = withContext(Dispatchers.IO) {
        val condoId = storage.readSession()?.condominiumId ?: return@withContext 0
        var syncedCount = 0
        for (pending in passDao.listPendingSync()) {
            val result = runCatching {
                val resp = network.api.registerOfflinePass(
                    condoId = condoId,
                    body = RegisterOfflinePassRequestDto(
                        qrPayload = pending.qrPayload,
                        visitorName = pending.visitorName,
                        passType = pending.passType,
                        maxUses = pending.maxUses,
                    ),
                )
                if (resp.isSuccessful) {
                    passDao.markSynced(pending.passId)
                    syncedCount++
                } else {
                    passDao.markSyncError(pending.passId, network.extractError(resp, "Error al sincronizar"))
                }
            }
            if (result.isFailure) {
                // Offline or unreachable server: keep pending cleanly without blocking
                break
            }
        }
        syncedCount
    }

    override suspend fun revoke(passId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val condoId = storage.readSession()?.condominiumId ?: error("Sesión no iniciada")
            val resp = network.api.revokePass(condoId, passId)
            if (!resp.isSuccessful) {
                error(network.extractError(resp, "No se pudo revocar el pase"))
            }
            passDao.markRevoked(passId)
        }
    }
}

class IntercomRepositoryImpl(
    private val storage: SecureStorage,
    private val network: NetworkFactory,
) : IntercomRepository {

    override suspend fun calls(page: Int, size: Int): Result<List<IntercomCall>> = withContext(Dispatchers.IO) {
        runCatching {
            val condoId = storage.readSession()?.condominiumId ?: error("Sesión no iniciada")
            val resp = network.api.listIntercomCalls(condoId, page, size)
            if (!resp.isSuccessful || resp.body() == null) {
                error(network.extractError(resp, "No se pudo cargar el historial de citofonía"))
            }
            resp.body()!!.content.map { c ->
                val ms = runCatching { Instant.parse(c.startedAt).toEpochMilli() }
                    .getOrDefault(System.currentTimeMillis())
                IntercomCall(
                    id = c.id,
                    status = c.status,
                    visitorName = c.visitorName,
                    visitorNote = c.visitorNote,
                    doorUnlocked = c.doorUnlocked,
                    startedAtMs = ms,
                )
            }
        }
    }

    override suspend fun unlock(callId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val condoId = storage.readSession()?.condominiumId ?: error("Sesión no iniciada")
            val resp = network.api.unlockIntercomCall(condoId, callId)
            if (!resp.isSuccessful) {
                error(network.extractError(resp, "No se pudo autorizar el ingreso"))
            }
        }
    }

    override suspend fun reject(callId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val condoId = storage.readSession()?.condominiumId ?: error("Sesión no iniciada")
            val resp = network.api.rejectIntercomCall(condoId, callId)
            if (!resp.isSuccessful) {
                error(network.extractError(resp, "No se pudo rechazar la llamada"))
            }
        }
    }
}
