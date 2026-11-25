package com.accesolibre.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "units")
public class Unit {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "unit_number", nullable = false, length = 32)
    private String unitNumber;

    @Column(name = "block_name", length = 64)
    private String blockName;

    @Column(name = "tower_or_block", length = 64)
    private String towerOrBlock;

    @Column(name = "floor", length = 16)
    private String floor;

    @Column(name = "intercom_extension", length = 32)
    private String intercomExtension;

    @Column(name = "unit_secret", nullable = false, length = 128)
    private String unitSecret;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "blocked", nullable = false)
    private Boolean blocked = false;

    @Column(name = "block_reason", length = 255)
    private String blockReason;

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
        if (this.active == null) {
            this.active = true;
        }
        if (this.blocked == null) {
            this.blocked = false;
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

    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }

    public String getBlockName() { return blockName; }
    public void setBlockName(String blockName) { this.blockName = blockName; }

    public String getTowerOrBlock() { return towerOrBlock; }
    public void setTowerOrBlock(String towerOrBlock) { this.towerOrBlock = towerOrBlock; }

    public String effectiveBlockName() {
        if (blockName != null && !blockName.isBlank()) {
            return blockName.trim();
        }
        if (towerOrBlock != null && !towerOrBlock.isBlank()) {
            return towerOrBlock.trim();
        }
        return null;
    }

    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }

    public String getIntercomExtension() { return intercomExtension; }
    public void setIntercomExtension(String intercomExtension) { this.intercomExtension = intercomExtension; }

    public String getUnitSecret() { return unitSecret; }
    public void setUnitSecret(String unitSecret) { this.unitSecret = unitSecret; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getBlocked() { return blocked; }
    public void setBlocked(Boolean blocked) { this.blocked = blocked; }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
