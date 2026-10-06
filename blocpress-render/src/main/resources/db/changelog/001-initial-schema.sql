--liquibase formatted sql

--changeset blocpress:001-initial-schema
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'template'
--comment: Ausgangsschema; eine Datenbank, die schon Tabellen hat (Hibernate update, init-studio.sql), uebernimmt es ungeprueft

-- blocpress-render, Datenbank production: Ausgangsschema (Stand 2.7.0).
-- Datenbanken, die vor den Migrationen entstanden sind (Hibernate update oder
-- docker/studio/init-studio.sql), markiert Liquibase dieses Changeset als ausgefuehrt (MARK_RAN).

CREATE TABLE template (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    valid_from  TIMESTAMP(6) NOT NULL,
    valid_until TIMESTAMP(6),
    version     INTEGER      NOT NULL DEFAULT 1,
    content     BYTEA        NOT NULL,
    UNIQUE (name, valid_from, version)
);

CREATE INDEX idx_template_name ON template (name);
CREATE INDEX idx_template_valid_from ON template (valid_from DESC);

CREATE TABLE render_job (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    template_name VARCHAR(255),
    data          JSONB         NOT NULL,
    output_type   VARCHAR(10)   NOT NULL DEFAULT 'pdf',
    result        BYTEA,
    webhook_url   VARCHAR(1000),
    created_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT
);

CREATE INDEX idx_render_job_status ON render_job (status, created_at);
