-- =============================================================================
-- AccesoLibre - Flyway Migration V4: Community Types & Block Grouping
-- =============================================================================

ALTER TABLE condominiums
    ADD COLUMN IF NOT EXISTS community_type VARCHAR(32) NOT NULL DEFAULT 'CONDOMINIUM';

ALTER TABLE condominiums
    DROP CONSTRAINT IF EXISTS ck_condominiums_community_type;

ALTER TABLE condominiums
    ADD CONSTRAINT ck_condominiums_community_type
        CHECK (community_type IN ('CONDOMINIUM', 'POBLACION', 'VILLA', 'PASAJE'));

ALTER TABLE units
    ADD COLUMN IF NOT EXISTS block_name VARCHAR(64);

UPDATE units
SET block_name = tower_or_block
WHERE block_name IS NULL AND tower_or_block IS NOT NULL;

-- Allow identical unit numbers (e.g. 101, 102, 201, 202) across different Blocks
-- within the same enclosure while keeping (condominium_id, block_name, unit_number) unique.
ALTER TABLE units
    DROP CONSTRAINT IF EXISTS uq_units_condo_number;

CREATE UNIQUE INDEX IF NOT EXISTS uq_units_condo_block_number
    ON units (condominium_id, COALESCE(LOWER(TRIM(block_name)), ''), LOWER(TRIM(unit_number)));

CREATE INDEX IF NOT EXISTS idx_units_condo_block
    ON units (condominium_id, block_name);
