-- =============================================================================
-- AccesoLibre - Flyway Migration V5: SaaS Licensing & Admin Activation Codes
-- =============================================================================

ALTER TABLE condominiums
    ADD COLUMN subscription_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN plan_name           VARCHAR(64) NOT NULL DEFAULT 'Mensual',
    ADD COLUMN license_expires_at  TIMESTAMPTZ NULL,
    ADD COLUMN max_units           INTEGER     NOT NULL DEFAULT 200;

ALTER TABLE condominiums
    ADD CONSTRAINT ck_condominiums_subscription_status
        CHECK (subscription_status IN ('TRIAL', 'ACTIVE', 'EXPIRED', 'SUSPENDED')),
    ADD CONSTRAINT ck_condominiums_max_units
        CHECK (max_units >= 1);

CREATE TABLE admin_activation_codes (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    condominium_id  UUID        NOT NULL REFERENCES condominiums(id) ON DELETE CASCADE,
    code            VARCHAR(16) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_by_user_id UUID        NULL,
    used_at         TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_admin_activation_codes_code UNIQUE (code),
    CONSTRAINT fk_admin_activation_codes_used_by_tenant
        FOREIGN KEY (used_by_user_id, condominium_id)
        REFERENCES users(id, condominium_id)
        ON DELETE SET NULL (used_by_user_id)
);

CREATE INDEX idx_admin_activation_codes_condo_open
    ON admin_activation_codes (condominium_id, expires_at)
    WHERE used_at IS NULL;
