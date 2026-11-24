package com.accesolibre.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.accesolibre.app.domain.model.AccessLogItem
import com.accesolibre.app.domain.model.Pass

@Entity(tableName = "offline_passes")
data class PassEntity(
    @PrimaryKey val passId: String,
    val qrPayload: String,
    val visitorName: String,
    val passType: String,
    val maxUses: Int,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val synced: Boolean,
    val revoked: Boolean,
    val syncError: String?,
) {
    fun toDomain() = Pass(
        passId = passId,
        qrPayload = qrPayload,
        visitorName = visitorName,
        passType = passType,
        maxUses = maxUses,
        createdAtMs = createdAtMs,
        expiresAtMs = expiresAtMs,
        synced = synced,
        revoked = revoked,
        syncError = syncError,
    )

    companion object {
        fun fromDomain(p: Pass) = PassEntity(
            passId = p.passId,
            qrPayload = p.qrPayload,
            visitorName = p.visitorName,
            passType = p.passType,
            maxUses = p.maxUses,
            createdAtMs = p.createdAtMs,
            expiresAtMs = p.expiresAtMs,
            synced = p.synced,
            revoked = p.revoked,
            syncError = p.syncError,
        )
    }
}

@Entity(tableName = "cached_access_logs")
data class AccessLogEntity(
    @PrimaryKey val id: String,
    val result: String,
    val accessMethod: String,
    val offlineValidated: Boolean,
    val occurredAtMs: Long,
) {
    fun toDomain() = AccessLogItem(
        id = id,
        result = result,
        accessMethod = accessMethod,
        offlineValidated = offlineValidated,
        occurredAtMs = occurredAtMs,
    )
}
