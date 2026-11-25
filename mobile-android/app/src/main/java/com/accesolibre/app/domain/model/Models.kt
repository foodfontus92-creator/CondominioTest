package com.accesolibre.app.domain.model

data class Session(
    val userId: String,
    val condominiumId: String,
    val unitId: String?,
    val email: String,
    val fullName: String,
    val role: String,
    val communityType: String = "CONDOMINIUM",
    val blockName: String? = null,
    val unitNumber: String? = null,
)

fun formatUnitDisplay(communityType: String?, blockName: String?, unitNumber: String?): String? {
    val block = blockName?.trim()?.takeIf { it.isNotEmpty() }
    val unit = unitNumber?.trim()?.takeIf { it.isNotEmpty() }
    if (block == null && unit == null) return null
    val isApartmentStyle = communityType.equals("CONDOMINIUM", ignoreCase = true) ||
        block?.startsWith("block", ignoreCase = true) == true ||
        block?.startsWith("torre", ignoreCase = true) == true
    val dwelling = if (isApartmentStyle) "Depto" else "Casa"
    return when {
        block != null && unit != null -> "$block · $dwelling $unit"
        unit != null -> "$dwelling $unit"
        else -> block
    }
}

data class CommunityLabels(
    val communityType: String,
    val communityName: String,
    val codeFieldLabel: String,
    val ofCommunity: String,
    val inYourCommunity: String,
    val unitLabel: String,
    val activateButtonLabel: String,
    val activateScreenTitle: String,
    val activateErrorFallback: String,
    val incomingCallSuffix: String,
)

fun communityLabelsFor(communityType: String?): CommunityLabels = when (communityType?.uppercase()) {
    "POBLACION" -> CommunityLabels(
        communityType = "POBLACION",
        communityName = "Población",
        codeFieldLabel = "Código de la población",
        ofCommunity = "de la población",
        inYourCommunity = "en tu población",
        unitLabel = "Casa / Sitio",
        activateButtonLabel = "Activar vivienda con código",
        activateScreenTitle = "Activar vivienda",
        activateErrorFallback = "No se pudo activar la vivienda",
        incomingCallSuffix = "está llamando a tu vivienda.",
    )
    "VILLA" -> CommunityLabels(
        communityType = "VILLA",
        communityName = "Villa",
        codeFieldLabel = "Código de la villa",
        ofCommunity = "de la villa",
        inYourCommunity = "en tu villa",
        unitLabel = "Casa / Sitio",
        activateButtonLabel = "Activar vivienda con código",
        activateScreenTitle = "Activar vivienda",
        activateErrorFallback = "No se pudo activar la vivienda",
        incomingCallSuffix = "está llamando a tu vivienda.",
    )
    "PASAJE" -> CommunityLabels(
        communityType = "PASAJE",
        communityName = "Pasaje",
        codeFieldLabel = "Código del pasaje",
        ofCommunity = "del pasaje",
        inYourCommunity = "en tu pasaje",
        unitLabel = "Casa / Sitio",
        activateButtonLabel = "Activar vivienda con código",
        activateScreenTitle = "Activar vivienda",
        activateErrorFallback = "No se pudo activar la vivienda",
        incomingCallSuffix = "está llamando a tu vivienda.",
    )
    else -> CommunityLabels(
        communityType = "CONDOMINIUM",
        communityName = "Condominio",
        codeFieldLabel = "Código del condominio",
        ofCommunity = "del condominio",
        inYourCommunity = "en tu condominio",
        unitLabel = "Unidad",
        activateButtonLabel = "Activar departamento con código",
        activateScreenTitle = "Activar departamento",
        activateErrorFallback = "No se pudo activar el departamento",
        incomingCallSuffix = "está llamando a tu unidad.",
    )
}

/** Derived per-unit HMAC key delivered by GET /api/v1/access-passes/unit-secret. */
data class UnitSecret(
    val condominiumId: String,
    val unitId: String,
    val unitNumber: String,
    val blockName: String? = null,
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
