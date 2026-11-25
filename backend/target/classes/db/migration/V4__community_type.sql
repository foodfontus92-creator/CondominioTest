-- =============================================================================
-- AccesoLibre - Flyway Migration V4: Residential Community Type
-- Supports: CONDOMINIUM, POBLACION, VILLA, PASAJE
-- =============================================================================

ALTER TABLE condominiums
    ADD COLUMN community_type VARCHAR(32) NOT NULL DEFAULT 'CONDOMINIUM';

ALTER TABLE condominiums
    ADD CONSTRAINT ck_condominiums_community_type
        CHECK (community_type IN ('CONDOMINIUM', 'POBLACION', 'VILLA', 'PASAJE'));
