package com.accesolibre.app.domain.model

data class Session(
    val userId: String,
    val condominiumId: String,
    val unitId: String?,
    val email: String,
    val fullName: String,
    val role: String,
)

/** Derived per-unit HMAC key delivered by GET /api/v1/access-passes/unit-secret. */
data class UnitSecret(
    val condominiumId: String,
    val unitId: String,
    val unitNumber: String,
    val secretHex: String,
    val hmacKeyVersion: Int,
)

enum class DeviceKind { GATE, PEDESTRIAN }

data class Device(
    val id: String,
    val name: String,
    val type: String,
    val location: String?,
    val status: String,
    val active: Boolean,
) {
    fun matches(kind: DeviceKind): Boolean = when (kind) {
        DeviceKind.GATE -> type == "VEHICLE_BARRIER"
        DeviceKind.PEDESTRIAN -> type == "PEDESTRIAN_GATE" || type == "LOBBY_DOOR"
    }
}

data class Coordinates(val latitude: Double, val longitude: Double, val accuracyMeters: Float?)

data class OpenOutcome(
    val granted: Boolean,
    val deviceName: String,
    val distanceMeters: Double,
    val allowedRadiusMeters: Int,
)

data class Pass(
    val passId: String,
    val qrPayload: String,
    val visitorName: String,
    val passType: String,
    val maxUses: Int,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val synced: Boolean,
    val revoked: Boolean,
    val syncError: String?,
)

data class AccessLogItem(
    val id: String,
    val result: String,
    val accessMethod: String,
    val offlineValidated: Boolean,
    val occurredAtMs: Long,
)

data class IntercomCall(
    val id: String,
    val status: String,
    val visitorName: String?,
    val visitorNote: String?,
    val doorUnlocked: Boolean,
    val startedAtMs: Long,
)

data class IncomingCall(
    val callId: String,
    val visitorName: String,
    val visitorNote: String?,
    val deviceName: String?,
    val unitNumber: String?,
    val receivedAtMs: Long,
)
