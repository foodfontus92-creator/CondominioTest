-- =============================================================================
-- AccesoLibre - Flyway Migration V2: Geofencing, Unit Secrets, Blocking,
-- Public Door Intercom QR & Persistent Refresh Tokens
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Condominiums: GPS coordinates and geofence radius (meters)
-- -----------------------------------------------------------------------------
ALTER TABLE condominiums
    ADD COLUMN latitude               DOUBLE PRECISION,
    ADD COLUMN longitude              DOUBLE PRECISION,
    ADD COLUMN geofence_radius_meters INTEGER NOT NULL DEFAULT 150;

ALTER TABLE condominiums
    ADD CONSTRAINT ck_condominiums_latitude
        CHECK (latitude IS NULL OR (latitude >= -90.0 AND latitude <= 90.0)),
    ADD CONSTRAINT ck_condominiums_longitude
        CHECK (longitude IS NULL OR (longitude >= -180.0 AND longitude <= 180.0)),
    ADD CONSTRAINT ck_condominiums_geofence_radius
        CHECK (geofence_radius_meters BETWEEN 10 AND 5000);

-- -----------------------------------------------------------------------------
-- 2. Units: per-unit derived HMAC secret (unit_secret) & explicit blocking
-- -----------------------------------------------------------------------------
ALTER TABLE units
    ADD COLUMN unit_secret  VARCHAR(128) NOT NULL DEFAULT '',
    ADD COLUMN blocked      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN block_reason VARCHAR(255);

-- Backfill any pre-existing units (if any) with a 64-char hex placeholder;
-- application service always derives HMAC-SHA256(condo.hmac_secret, unitIdB64).
UPDATE units
SET unit_secret = repeat(md5(id::text || condominium_id::text), 2)
WHERE unit_secret = '';

ALTER TABLE units
    ALTER COLUMN unit_secret DROP DEFAULT,
    ADD CONSTRAINT ck_units_unit_secret_len CHECK (char_length(unit_secret) >= 64);

CREATE INDEX idx_units_condo_blocked
    ON units (condominium_id, blocked)
    WHERE blocked = TRUE;

-- -----------------------------------------------------------------------------
-- 3. Users: explicit blocking flag & reason
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN blocked      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN block_reason VARCHAR(255);

CREATE INDEX idx_users_condo_blocked
    ON users (condominium_id, blocked)
    WHERE blocked = TRUE;

-- -----------------------------------------------------------------------------
-- 4. IoT Devices: static public QR token for visitor virtual intercom at door
-- -----------------------------------------------------------------------------
ALTER TABLE iot_devices
    ADD COLUMN public_qr_token VARCHAR(64);

UPDATE iot_devices
SET public_qr_token = replace(gen_random_uuid()::text, '-', '')
WHERE public_qr_token IS NULL;

ALTER TABLE iot_devices
    ALTER COLUMN public_qr_token SET NOT NULL,
    ADD CONSTRAINT uq_iot_devices_public_qr_token UNIQUE (public_qr_token);

-- -----------------------------------------------------------------------------
-- 5. Access Logs: expand result check constraint for geofencing & block checks
-- -----------------------------------------------------------------------------
ALTER TABLE access_logs
    DROP CONSTRAINT ck_access_logs_result;

ALTER TABLE access_logs
    ADD CONSTRAINT ck_access_logs_result
        CHECK (result IN (
            'GRANTED',
            'DENIED_INVALID_HMAC',
            'DENIED_EXPIRED',
            'DENIED_REVOKED',
            'DENIED_WRONG_CONDO',
            'DENIED_MAX_USES',
            'DENIED_DEVICE_OFFLINE',
            'DENIED_OUTSIDE_GEOFENCE',
            'DENIED_USER_BLOCKED',
            'DENIED_UNIT_BLOCKED',
            'DENIED_UNAUTHORIZED',
            'ERROR'
        ));

-- -----------------------------------------------------------------------------
-- 6. Intercom Calls: optional visitor name & note from public door QR flow
-- -----------------------------------------------------------------------------
ALTER TABLE intercom_calls
    ADD COLUMN visitor_name VARCHAR(150),
    ADD COLUMN visitor_note VARCHAR(255);

-- -----------------------------------------------------------------------------
-- 7. Refresh Tokens: DB-backed revocable refresh tokens (multi-tenant safe)
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id UUID         NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
    user_id        UUID         NOT NULL,
    token_hash     VARCHAR(128) NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked        BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user_tenant
        FOREIGN KEY (user_id, condominium_id)
        REFERENCES users(id, condominium_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens (condominium_id, user_id, expires_at)
    WHERE revoked = FALSE;
