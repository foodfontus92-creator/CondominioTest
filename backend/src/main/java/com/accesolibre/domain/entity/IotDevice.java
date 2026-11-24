package com.accesolibre.domain.entity;

import com.accesolibre.domain.enums.DeviceStatus;
import com.accesolibre.domain.enums.DeviceType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "iot_devices")
public class IotDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "condominium_id", nullable = false, updatable = false)
    private UUID condominiumId;

    @Column(name = "hardware_uid", nullable = false, unique = true, length = 64)
    private String hardwareUid;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 32)
    private DeviceType deviceType;

    @Column(name = "location_description", length = 255)
    private String locationDescription;

    @Column(name = "mqtt_username", nullable = false, unique = true, length = 128)
    private String mqttUsername;

    @Column(name = "public_qr_token", nullable = false, unique = true, length = 64)
    private String publicQrToken;

    @Column(name = "firmware_version", length = 32)
    private String firmwareVersion;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", columnDefinition = "inet")
    private InetAddress ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private DeviceStatus status = DeviceStatus.OFFLINE;

    @Column(name = "relay_pulse_ms", nullable = false)
    private Integer relayPulseMs = 3000;

    @Column(name = "hmac_key_version_synced", nullable = false)
    private Integer hmacKeyVersionSynced = 0;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_telemetry", columnDefinition = "jsonb")
    private Map<String, Object> lastTelemetry;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.status == null) {
            this.status = DeviceStatus.OFFLINE;
        }
        if (this.relayPulseMs == null) {
            this.relayPulseMs = 3000;
        }
        if (this.hmacKeyVersionSynced == null) {
            this.hmacKeyVersionSynced = 0;
        }
        if (this.active == null) {
            this.active = true;
        }
        if (this.publicQrToken == null || this.publicQrToken.isBlank()) {
            this.publicQrToken = UUID.randomUUID().toString().replace("-", "");
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

    public String getHardwareUid() { return hardwareUid; }
    public void setHardwareUid(String hardwareUid) { this.hardwareUid = hardwareUid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DeviceType getDeviceType() { return deviceType; }
    public void setDeviceType(DeviceType deviceType) { this.deviceType = deviceType; }

    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }

    public String getMqttUsername() { return mqttUsername; }
    public void setMqttUsername(String mqttUsername) { this.mqttUsername = mqttUsername; }

    public String getPublicQrToken() { return publicQrToken; }
    public void setPublicQrToken(String publicQrToken) { this.publicQrToken = publicQrToken; }

    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }

    public InetAddress getIpAddress() { return ipAddress; }
    public void setIpAddress(InetAddress ipAddress) { this.ipAddress = ipAddress; }

    public DeviceStatus getStatus() { return status; }
    public void setStatus(DeviceStatus status) { this.status = status; }

    public Integer getRelayPulseMs() { return relayPulseMs; }
    public void setRelayPulseMs(Integer relayPulseMs) { this.relayPulseMs = relayPulseMs; }

    public Integer getHmacKeyVersionSynced() { return hmacKeyVersionSynced; }
    public void setHmacKeyVersionSynced(Integer hmacKeyVersionSynced) { this.hmacKeyVersionSynced = hmacKeyVersionSynced; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Map<String, Object> getLastTelemetry() { return lastTelemetry; }
    public void setLastTelemetry(Map<String, Object> lastTelemetry) { this.lastTelemetry = lastTelemetry; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
