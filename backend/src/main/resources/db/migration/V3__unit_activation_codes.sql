-- =============================================================================
-- AccesoLibre - Flyway Migration V3: Unit Family Activation Codes
-- =============================================================================

CREATE TABLE unit_activation_codes (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id UUID        NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
    unit_id        UUID        NOT NULL,
    code           VARCHAR(80) NOT NULL,
    max_uses       INTEGER     NOT NULL,
    used_count     INTEGER     NOT NULL DEFAULT 0,
    expires_at     TIMESTAMPTZ NOT NULL,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_unit_activation_codes_code UNIQUE (code),
    CONSTRAINT ck_unit_activation_codes_max_uses CHECK (max_uses BETWEEN 1 AND 10),
    CONSTRAINT ck_unit_activation_codes_used_count CHECK (used_count >= 0 AND used_count <= max_uses),
    CONSTRAINT fk_unit_activation_codes_unit_tenant
        FOREIGN KEY (unit_id, condominium_id)
        REFERENCES units(id, condominium_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_unit_activation_codes_unit_active
    ON unit_activation_codes (condominium_id, unit_id)
    WHERE active = TRUE;
