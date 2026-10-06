--liquibase formatted sql

--changeset blocpress:001-initial-schema
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'template'
--comment: Ausgangsschema; eine Datenbank, die schon Tabellen hat (Hibernate update, init-studio.sql), uebernimmt es ungeprueft

-- blocpress-workbench, Datenbank workbench: Ausgangsschema (Stand 2.7.0).
-- Datenbanken, die vor den Migrationen entstanden sind (Hibernate update oder
-- docker/studio/init-studio.sql), markiert Liquibase dieses Changeset als ausgefuehrt (MARK_RAN).

CREATE TABLE template (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(255) NOT NULL,
    valid_from         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until        TIMESTAMP(6),
    review_cycle_years INTEGER,
    version            INTEGER      NOT NULL DEFAULT 1,
    content            BYTEA        NOT NULL,
    status             VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    type               VARCHAR(50)  NOT NULL DEFAULT 'TEMPLATE',
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validation_result  JSONB,
    ignored_patterns   JSONB,
    rejection_reason   TEXT,
    rejected_at        TIMESTAMP(6),
    UNIQUE (name, valid_from, version)
);

CREATE TABLE test_data_set (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id      UUID         NOT NULL REFERENCES template (id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    test_data        JSONB        NOT NULL,
    expected_pdf     BYTEA,
    pdf_hash         VARCHAR(64),
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP(6) WITH TIME ZONE,
    notes            TEXT,
    ignored_patterns JSONB,
    UNIQUE (template_id, name)
);

CREATE INDEX idx_template_name ON template (name);
CREATE INDEX idx_template_valid_from ON template (valid_from DESC);
CREATE INDEX idx_template_status ON template (status);
CREATE INDEX idx_template_type ON template (type);
CREATE INDEX idx_test_data_set_template ON test_data_set (template_id);
