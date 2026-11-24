package com.accesolibre.domain.entity;

import com.accesolibre.domain.enums.IntercomCallStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "intercom_calls")
public class IntercomCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "device_id", nullable = false, updatable = false)
    private UUID deviceId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "answered_by_user_id")
    private UUID answeredByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private IntercomCallStatus status = IntercomCallStatus.RINGING;

    @Column(name = "signaling_session_id", length = 64)
    private String signalingSessionId;

    @Column(name = "visitor_name", length = 150)
    private String visitorName;

    @Column(name = "visitor_note", length = 255)
    private String visitorNote;

    @Column(name = "door_unlocked", nullable = false)
    private Boolean doorUnlocked = false;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "snapshot_path", length = 512)
    private String snapshotPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.status == null) {
            this.status = IntercomCallStatus.RINGING;
        }
        if (this.doorUnlocked == null) {
            this.doorUnlocked = false;
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

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }

    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }

    public UUID getAnsweredByUserId() { return answeredByUserId; }
    public void setAnsweredByUserId(UUID answeredByUserId) { this.answeredByUserId = answeredByUserId; }

    public IntercomCallStatus getStatus() { return status; }
    public void setStatus(IntercomCallStatus status) { this.status = status; }

    public String getSignalingSessionId() { return signalingSessionId; }
    public void setSignalingSessionId(String signalingSessionId) { this.signalingSessionId = signalingSessionId; }

    public String getVisitorName() { return visitorName; }
    public void setVisitorName(String visitorName) { this.visitorName = visitorName; }

    public String getVisitorNote() { return visitorNote; }
    public void setVisitorNote(String visitorNote) { this.visitorNote = visitorNote; }

    public Boolean getDoorUnlocked() { return doorUnlocked; }
    public void setDoorUnlocked(Boolean doorUnlocked) { this.doorUnlocked = doorUnlocked; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getSnapshotPath() { return snapshotPath; }
    public void setSnapshotPath(String snapshotPath) { this.snapshotPath = snapshotPath; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
