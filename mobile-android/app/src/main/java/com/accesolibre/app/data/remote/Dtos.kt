package com.accesolibre.app.data.remote

data class HealthDto(val status: String?)

data class ApiErrorDto(
    val status: Int?,
    val error: String?,
    val code: String?,
    val message: String?,
)

data class PageDto<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val last: Boolean = true,
)

// ---- Auth (/api/v1/auth/**) ------------------------------------------------
data class LoginRequestDto(
    val condominiumCode: String,
    val email: String,
    val password: String,
)

data class RefreshTokenRequestDto(val refreshToken: String)
data class LogoutRequestDto(val refreshToken: String)

data class AuthResponseDto(
    val tokenType: String,
    val accessToken: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshTokenExpiresInSeconds: Long,
    val user: UserProfileDto,
)

data class UserProfileDto(
    val id: String,
    val condominiumId: String,
    val unitId: String?,
    val email: String,
    val fullName: String,
    val role: String,
)

// ---- Unit secret & Offline QR passes ---------------------------------------
data class UnitSecretResponseDto(
    val condominiumId: String,
    val condoIdB64: String,
    val unitId: String,
    val unitIdB64: String,
    val unitNumber: String,
    val unitSecret: String,
    val hmacKeyVersion: Int,
    val qrFormat: String,
    val hmacTruncatedHexLength: Int,
)

data class RegisterOfflinePassRequestDto(
    val qrPayload: String,
    val visitorName: String,
    val visitorDocument: String? = null,
    val passType: String = "VISITOR",
    val maxUses: Int = 1,
)

data class AccessPassResponseDto(
    val id: String,
    val condominiumId: String,
    val unitId: String,
    val visitorName: String,
    val passType: String,
    val qrPayload: String,
    val expiresAt: String,
    val maxUses: Int,
    val currentUses: Int,
    val status: String,
)

// ---- Devices & Remote Open with Geofencing ---------------------------------
data class DeviceResponseDto(
    val id: String,
    val condominiumId: String,
    val name: String,
    val deviceType: String,
    val locationDescription: String?,
    val status: String,
    val active: Boolean,
)

data class RemoteOpenRequestDto(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val pulseMs: Int? = null,
)

data class RemoteOpenResponseDto(
    val granted: Boolean,
    val result: String,
    val accessLogId: String,
    val mqttCommandId: String,
    val deviceId: String,
    val deviceName: String,
    val distanceMeters: Double,
    val allowedRadiusMeters: Int,
    val pulseMs: Int,
    val occurredAt: String,
)

data class AccessLogResponseDto(
    val id: String,
    val condominiumId: String,
    val deviceId: String?,
    val unitId: String?,
    val accessMethod: String,
    val result: String,
    val offlineValidated: Boolean,
    val occurredAt: String,
)

// ---- Intercom (REST + STOMP) -----------------------------------------------
data class IntercomCallResponseDto(
    val id: String,
    val condominiumId: String,
    val deviceId: String,
    val unitId: String,
    val status: String,
    val visitorName: String?,
    val visitorNote: String?,
    val doorUnlocked: Boolean,
    val startedAt: String,
)

data class IntercomSignalEventDto(
    val eventType: String?,
    val callId: String?,
    val condominiumId: String?,
    val deviceId: String?,
    val deviceName: String?,
    val unitId: String?,
    val unitNumber: String?,
    val status: String?,
    val visitorName: String?,
    val visitorNote: String?,
    val doorUnlocked: Boolean?,
    val timestamp: String?,
)
