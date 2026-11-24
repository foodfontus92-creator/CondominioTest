package com.accesolibre.domain.entity;

import com.accesolibre.domain.enums.AccessMethod;
import com.accesolibre.domain.enums.AccessResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "access_logs")
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "access_pass_id")
    private UUID accessPassId;

    @Column(name = "triggered_by_user_id")
    private UUID triggeredByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_method", nullable = false, length = 32)
    private AccessMethod accessMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 32)
    private AccessResult result;

    @Column(name = "offline_validated", nullable = false)
    private Boolean offlineValidated = false;

    @Column(name = "qr_pass_id_raw", length = 64)
    private String qrPassIdRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.occurredAt == null) {
            this.occurredAt = now;
        }
        if (this.syncedAt == null) {
            this.syncedAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.offlineValidated == null) {
            this.offlineValidated = false;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCondominiumId() { return condominiumId; }
    public void setCondominiumId(UUID condominiumId) { this.condominiumId = condominiumId; }

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }

    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }

    public UUID getAccessPassId() { return accessPassId; }
    public void setAccessPassId(UUID accessPassId) { this.accessPassId = accessPassId; }

    public UUID getTriggeredByUserId() { return triggeredByUserId; }
    public void setTriggeredByUserId(UUID triggeredByUserId) { this.triggeredByUserId = triggeredByUserId; }

    public AccessMethod getAccessMethod() { return accessMethod; }
    public void setAccessMethod(AccessMethod accessMethod) { this.accessMethod = accessMethod; }

    public AccessResult getResult() { return result; }
    public void setResult(AccessResult result) { this.result = result; }

    public Boolean getOfflineValidated() { return offlineValidated; }
    public void setOfflineValidated(Boolean offlineValidated) { this.offlineValidated = offlineValidated; }

    public String getQrPassIdRaw() { return qrPassIdRaw; }
    public void setQrPassIdRaw(String qrPassIdRaw) { this.qrPassIdRaw = qrPassIdRaw; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
