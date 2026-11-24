package com.accesolibre.domain.entity;

import com.accesolibre.domain.enums.PassStatus;
import com.accesolibre.domain.enums.PassType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_passes")
public class AccessPass {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "visitor_name", nullable = false, length = 150)
    private String visitorName;

    @Column(name = "visitor_document", length = 64)
    private String visitorDocument;

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_type", nullable = false, length = 24)
    private PassType passType = PassType.VISITOR;

    @Column(name = "qr_payload", nullable = false, length = 119)
    private String qrPayload;

    @Column(name = "hmac_key_version", nullable = false)
    private Integer hmacKeyVersion = 1;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "max_uses", nullable = false)
    private Integer maxUses = 1;

    @Column(name = "current_uses", nullable = false)
    private Integer currentUses = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PassStatus status = PassStatus.ACTIVE;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by_user_id")
    private UUID revokedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.validFrom == null) {
            this.validFrom = now;
        }
        if (this.passType == null) {
            this.passType = PassType.VISITOR;
        }
        if (this.hmacKeyVersion == null) {
            this.hmacKeyVersion = 1;
        }
        if (this.maxUses == null) {
            this.maxUses = 1;
        }
        if (this.currentUses == null) {
            this.currentUses = 0;
        }
        if (this.status == null) {
            this.status = PassStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCondominiumId() { return condominiumId; }
    public void setCondominiumId(UUID condominiumId) { this.condominiumId = condominiumId; }

    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }

    public String getVisitorName() { return visitorName; }
    public void setVisitorName(String visitorName) { this.visitorName = visitorName; }

    public String getVisitorDocument() { return visitorDocument; }
    public void setVisitorDocument(String visitorDocument) { this.visitorDocument = visitorDocument; }

    public PassType getPassType() { return passType; }
    public void setPassType(PassType passType) { this.passType = passType; }

    public String getQrPayload() { return qrPayload; }
    public void setQrPayload(String qrPayload) { this.qrPayload = qrPayload; }

    public Integer getHmacKeyVersion() { return hmacKeyVersion; }
    public void setHmacKeyVersion(Integer hmacKeyVersion) { this.hmacKeyVersion = hmacKeyVersion; }

    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

    public Integer getCurrentUses() { return currentUses; }
    public void setCurrentUses(Integer currentUses) { this.currentUses = currentUses; }

    public PassStatus getStatus() { return status; }
    public void setStatus(PassStatus status) { this.status = status; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public UUID getRevokedByUserId() { return revokedByUserId; }
    public void setRevokedByUserId(UUID revokedByUserId) { this.revokedByUserId = revokedByUserId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
