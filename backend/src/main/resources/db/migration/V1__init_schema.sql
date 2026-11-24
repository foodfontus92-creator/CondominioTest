-- =============================================================================
-- AccesoLibre - Flyway Migration V1: Initial Multi-Tenant Schema (PostgreSQL 16)
-- 100% On-Premise / Offline-First Residential Access Control Platform
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Shared trigger function to automatically maintain updated_at timestamps
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 1. CONDOMINIUMS (Tenant Root)
-- =============================================================================
CREATE TABLE condominiums (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(32)  NOT NULL,
    name              VARCHAR(150) NOT NULL,
    address           VARCHAR(255),
    timezone          VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    hmac_secret       VARCHAR(128) NOT NULL,
    hmac_key_version  INTEGER      NOT NULL DEFAULT 1,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_condominiums_code             UNIQUE (code),
    CONSTRAINT ck_condominiums_hmac_secret_len  CHECK (char_length(hmac_secret) >= 64),
    CONSTRAINT ck_condominiums_hmac_key_version CHECK (hmac_key_version >= 1)
);

CREATE INDEX idx_condominiums_active
    ON condominiums (id)
    WHERE active = TRUE;

CREATE TRIGGER trg_condominiums_set_updated_at
    BEFORE UPDATE ON condominiums
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- =============================================================================
-- 2. UNITS (Apartments / Lots per Condominium)
-- =============================================================================
CREATE TABLE units (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id     UUID         NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
    unit_number        VARCHAR(32)  NOT NULL,
    tower_or_block     VARCHAR(64),
    floor              VARCHAR(16),
    intercom_extension VARCHAR(32),
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_units_id_condo       UNIQUE (id, condominium_id),
    CONSTRAINT uq_units_condo_number   UNIQUE (condominium_id, unit_number)
);

CREATE UNIQUE INDEX uq_units_condo_intercom_ext
    ON units (condominium_id, intercom_extension)
    WHERE intercom_extension IS NOT NULL;

CREATE INDEX idx_units_condo_active
    ON units (condominium_id, active);

CREATE TRIGGER trg_units_set_updated_at
    BEFORE UPDATE ON units
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- =============================================================================
-- 3. USERS (Admins & Residents scoped to a Condominium)
-- =============================================================================
CREATE TABLE users (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id UUID         NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
    unit_id        UUID,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(150) NOT NULL,
    phone          VARCHAR(32),
    role           VARCHAR(32)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_id_condo   UNIQUE (id, condominium_id),
    CONSTRAINT ck_users_role       CHECK (role IN ('SUPER_ADMIN', 'CONDO_ADMIN', 'RESIDENT')),
    CONSTRAINT fk_users_unit_tenant
        FOREIGN KEY (unit_id, condominium_id)
        REFERENCES units(id, condominium_id)
        ON DELETE SET NULL (unit_id)
);

CREATE UNIQUE INDEX uq_users_condo_email_lower
    ON users (condominium_id, LOWER(email));

CREATE INDEX idx_users_condo_unit
    ON users (condominium_id, unit_id)
    WHERE unit_id IS NOT NULL;

CREATE INDEX idx_users_condo_role_active
    ON users (condominium_id, role, active);

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- =============================================================================
-- 4. IOT_DEVICES (ESP32 Gate Controllers, QR Readers & Intercom Panels)
-- =============================================================================
CREATE TABLE iot_devices (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id          UUID         NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
    hardware_uid            VARCHAR(64)  NOT NULL,
    name                    VARCHAR(120) NOT NULL,
    device_type             VARCHAR(32)  NOT NULL,
    location_description    VARCHAR(255),
    mqtt_username           VARCHAR(128) NOT NULL,
    firmware_version        VARCHAR(32),
    ip_address              INET,
    status                  VARCHAR(24)  NOT NULL DEFAULT 'OFFLINE',
    relay_pulse_ms          INTEGER      NOT NULL DEFAULT 3000,
    hmac_key_version_synced INTEGER      NOT NULL DEFAULT 0,
    last_seen_at            TIMESTAMPTZ,
    last_telemetry          JSONB,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_iot_devices_id_condo      UNIQUE (id, condominium_id),
    CONSTRAINT uq_iot_devices_hardware_uid  UNIQUE (hardware_uid),
    CONSTRAINT uq_iot_devices_mqtt_username UNIQUE (mqtt_username),
    CONSTRAINT ck_iot_devices_type
        CHECK (device_type IN ('PEDESTRIAN_GATE', 'VEHICLE_BARRIER', 'LOBBY_DOOR', 'INTERCOM_PANEL', 'ELEVATOR_CONTROLLER')),
    CONSTRAINT ck_iot_devices_status
        CHECK (status IN ('ONLINE', 'OFFLINE', 'MAINTENANCE', 'FAULT')),
    CONSTRAINT ck_iot_devices_relay_pulse
        CHECK (relay_pulse_ms BETWEEN 200 AND 30000),
    CONSTRAINT ck_iot_devices_hmac_version
        CHECK (hmac_key_version_synced >= 0)
);

CREATE INDEX idx_iot_devices_condo_status
    ON iot_devices (condominium_id, status);

CREATE INDEX idx_iot_devices_condo_type
    ON iot_devices (condominium_id, device_type);

CREATE INDEX idx_iot_devices_condo_last_seen
    ON iot_devices (condominium_id, last_seen_at DESC);

CREATE TRIGGER trg_iot_devices_set_updated_at
    BEFORE UPDATE ON iot_devices
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- =============================================================================
-- 5. ACCESS_PASSES (Compact Offline QR Passes - Strictly < 120 chars, No JWT)
-- =============================================================================
CREATE TABLE access_passes (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id      UUID         NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
    unit_id             UUID         NOT NULL,
    created_by_user_id  UUID         NOT NULL,
    visitor_name        VARCHAR(150) NOT NULL,
    visitor_document    VARCHAR(64),
    pass_type           VARCHAR(24)  NOT NULL DEFAULT 'VISITOR',
    qr_payload          VARCHAR(119) NOT NULL,
    hmac_key_version    INTEGER      NOT NULL DEFAULT 1,
    valid_from          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    max_uses            INTEGER      NOT NULL DEFAULT 1,
    current_uses        INTEGER      NOT NULL DEFAULT 0,
    status              VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    revoked_at          TIMESTAMPTZ,
    revoked_by_user_id  UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_access_passes_id_condo        UNIQUE (id, condominium_id),
    CONSTRAINT ck_access_passes_type
        CHECK (pass_type IN ('VISITOR', 'DELIVERY', 'SERVICE', 'RESIDENT_TEMP')),
    CONSTRAINT ck_access_passes_status
        CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_access_passes_qr_payload_len
        CHECK (char_length(qr_payload) > 0 AND char_length(qr_payload) < 120),
    CONSTRAINT ck_access_passes_window
        CHECK (expires_at > valid_from),
    CONSTRAINT ck_access_passes_uses
        CHECK (max_uses >= 1 AND current_uses >= 0 AND current_uses <= max_uses),
    CONSTRAINT ck_access_passes_hmac_version
        CHECK (hmac_key_version >= 1),
    CONSTRAINT fk_access_passes_unit_tenant
        FOREIGN KEY (unit_id, condominium_id)
        REFERENCES units(id, condominium_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_passes_creator_tenant
        FOREIGN KEY (created_by_user_id, condominium_id)
        REFERENCES users(id, condominium_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_access_passes_revoker_tenant
        FOREIGN KEY (revoked_by_user_id, condominium_id)
        REFERENCES users(id, condominium_id)
        ON DELETE SET NULL (revoked_by_user_id)
);

CREATE INDEX idx_access_passes_condo_unit_status
    ON access_passes (condominium_id, unit_id, status);

CREATE INDEX idx_access_passes_condo_active_expires
    ON access_passes (condominium_id, expires_at)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_access_passes_condo_creator
    ON access_passes (condominium_id, created_by_user_id, created_at DESC);

CREATE TRIGGER trg_access_passes_set_updated_at
    BEFORE UPDATE ON access_passes
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- =============================================================================
-- 6. ACCESS_LOGS (Immutable Audit Trail for Online & Offline Edge Validations)
-- =============================================================================
CREATE TABLE access_logs (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id       UUID        NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
    device_id            UUID,
    unit_id              UUID,
    access_pass_id       UUID,
    triggered_by_user_id UUID,
    access_method        VARCHAR(32) NOT NULL,
    result               VARCHAR(32) NOT NULL,
    offline_validated    BOOLEAN     NOT NULL DEFAULT FALSE,
    qr_pass_id_raw       VARCHAR(64),
    details              JSONB,
    occurred_at          TIMESTAMPTZ NOT NULL,
    synced_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_access_logs_method
        CHECK (access_method IN ('QR_OFFLINE', 'QR_ONLINE', 'REMOTE_APP', 'INTERCOM_UNLOCK', 'ADMIN_OVERRIDE', 'MANUAL_EXIT')),
    CONSTRAINT ck_access_logs_result
        CHECK (result IN (
            'GRANTED',
            'DENIED_INVALID_HMAC',
            'DENIED_EXPIRED',
            'DENIED_REVOKED',
            'DENIED_WRONG_CONDO',
            'DENIED_MAX_USES',
            'DENIED_DEVICE_OFFLINE',
            'ERROR'
        )),
    CONSTRAINT fk_access_logs_device_tenant
        FOREIGN KEY (device_id, condominium_id)
        REFERENCES iot_devices(id, condominium_id)
        ON DELETE SET NULL (device_id),
    CONSTRAINT fk_access_logs_unit_tenant
        FOREIGN KEY (unit_id, condominium_id)
        REFERENCES units(id, condominium_id)
        ON DELETE SET NULL (unit_id),
    CONSTRAINT fk_access_logs_pass_tenant
        FOREIGN KEY (access_pass_id, condominium_id)
        REFERENCES access_passes(id, condominium_id)
        ON DELETE SET NULL (access_pass_id),
    CONSTRAINT fk_access_logs_user_tenant
        FOREIGN KEY (triggered_by_user_id, condominium_id)
        REFERENCES users(id, condominium_id)
        ON DELETE SET NULL (triggered_by_user_id)
);

CREATE INDEX idx_access_logs_condo_occurred
    ON access_logs (condominium_id, occurred_at DESC);

CREATE INDEX idx_access_logs_condo_device_occurred
    ON access_logs (condominium_id, device_id, occurred_at DESC);

CREATE INDEX idx_access_logs_condo_unit_occurred
    ON access_logs (condominium_id, unit_id, occurred_at DESC);

CREATE INDEX idx_access_logs_condo_result_occurred
    ON access_logs (condominium_id, result, occurred_at DESC);

CREATE INDEX idx_access_logs_pass_id
    ON access_logs (access_pass_id)
    WHERE access_pass_id IS NOT NULL;

-- =============================================================================
-- 7. INTERCOM_CALLS (Local LAN Intercom Calls from Entrance Panel to Units)
-- =============================================================================
CREATE TABLE intercom_calls (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id       UUID         NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
    device_id            UUID         NOT NULL,
    unit_id              UUID         NOT NULL,
    answered_by_user_id  UUID,
    status               VARCHAR(24)  NOT NULL DEFAULT 'RINGING',
    signaling_session_id VARCHAR(64),
    door_unlocked        BOOLEAN      NOT NULL DEFAULT FALSE,
    started_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    answered_at          TIMESTAMPTZ,
    ended_at             TIMESTAMPTZ,
    duration_seconds     INTEGER,
    snapshot_path        VARCHAR(512),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_intercom_calls_status
        CHECK (status IN ('RINGING', 'ANSWERED', 'UNLOCKED', 'MISSED', 'REJECTED', 'ENDED', 'FAILED')),
    CONSTRAINT ck_intercom_calls_duration
        CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT ck_intercom_calls_timestamps
        CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT fk_intercom_calls_device_tenant
        FOREIGN KEY (device_id, condominium_id)
        REFERENCES iot_devices(id, condominium_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_intercom_calls_unit_tenant
        FOREIGN KEY (unit_id, condominium_id)
        REFERENCES units(id, condominium_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_intercom_calls_answerer_tenant
        FOREIGN KEY (answered_by_user_id, condominium_id)
        REFERENCES users(id, condominium_id)
        ON DELETE SET NULL (answered_by_user_id)
);

CREATE INDEX idx_intercom_calls_condo_started
    ON intercom_calls (condominium_id, started_at DESC);

CREATE INDEX idx_intercom_calls_condo_unit_status
    ON intercom_calls (condominium_id, unit_id, status);

CREATE INDEX idx_intercom_calls_condo_device_started
    ON intercom_calls (condominium_id, device_id, started_at DESC);

CREATE INDEX idx_intercom_calls_active_sessions
    ON intercom_calls (condominium_id, unit_id)
    WHERE status IN ('RINGING', 'ANSWERED');

CREATE TRIGGER trg_intercom_calls_set_updated_at
    BEFORE UPDATE ON intercom_calls
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
