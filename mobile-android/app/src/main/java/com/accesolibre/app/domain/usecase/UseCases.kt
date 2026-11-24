package com.accesolibre.app.domain.usecase

import com.accesolibre.app.domain.model.OpenOutcome
import com.accesolibre.app.domain.model.Pass
import com.accesolibre.app.domain.model.Session
import com.accesolibre.app.domain.qr.CompactQr
import com.accesolibre.app.domain.repository.AccessRepository
import com.accesolibre.app.domain.repository.AuthRepository
import com.accesolibre.app.domain.repository.LocationSource
import com.accesolibre.app.domain.repository.PassRepository
import java.time.Instant
import java.util.UUID

class LoginUseCase(private val auth: AuthRepository) {
    suspend operator fun invoke(condominiumCode: String, email: String, password: String): Result<Session> {
        if (condominiumCode.isBlank() || email.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Completa todos los campos"))
        }
        return auth.login(condominiumCode.trim(), email.trim(), password)
    }
}

/** Reads the real GPS position and sends POST /api/v1/access/open. */
class OpenAccessUseCase(
    private val location: LocationSource,
    private val access: AccessRepository,
) {
    suspend operator fun invoke(deviceId: String): Result<OpenOutcome> {
        val coordinates = location.currentLocation().getOrElse { return Result.failure(it) }
        return access.open(deviceId, coordinates)
    }
}

/**
 * Creates an offline invitation: the signed QR string is produced locally (no network),
 * stored in Room, and registered in the backend later/best-effort.
 */
class GeneratePassUseCase(
    private val auth: AuthRepository,
    private val passes: PassRepository,
) {
    suspend operator fun invoke(
        visitorName: String,
        passType: String,
        maxUses: Int,
        expiresAt: Instant,
        now: Instant = Instant.now(),
    ): Result<Pass> {
        if (visitorName.isBlank()) {
            return Result.failure(IllegalArgumentException("Ingresa el nombre del invitado"))
        }
        if (!expiresAt.isAfter(now.plusSeconds(60))) {
            return Result.failure(IllegalArgumentException("La expiración debe ser futura"))
        }
        val secret = auth.unitSecret() ?: return Result.failure(
            IllegalStateException(
                "Aún no se descargó el secreto de tu unidad. Conéctate a la red del condominio y vuelve a iniciar sesión."
            )
        )
        val passId = UUID.randomUUID()
        val payload = try {
            CompactQr.generate(
                unitSecretHex = secret.secretHex,
                condominiumId = UUID.fromString(secret.condominiumId),
                unitId = UUID.fromString(secret.unitId),
                passId = passId,
                expiresAtEpochSeconds = expiresAt.epochSecond,
            )
        } catch (e: IllegalArgumentException) {
            return Result.failure(e)
        }
        val pass = Pass(
            passId = passId.toString(),
            qrPayload = payload,
            visitorName = visitorName.trim(),
            passType = passType,
            maxUses = maxUses,
            createdAtMs = now.toEpochMilli(),
            expiresAtMs = expiresAt.toEpochMilli(),
            synced = false,
            revoked = false,
            syncError = null,
        )
        passes.saveLocal(pass)
        runCatching { passes.syncPending() }   // best effort; offline is fine
        return Result.success(passes.get(pass.passId) ?: pass)
    }
}

class SyncPendingPassesUseCase(private val passes: PassRepository) {
    suspend operator fun invoke(): Int = runCatching { passes.syncPending() }.getOrDefault(0)
}
