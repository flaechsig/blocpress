-- ======================================================================
-- blocpress-studio: Database initialisation (idempotent)
-- Run as superuser against the 'postgres' database.
-- Creates 'workbench' and 'production' databases with full schemas.
-- ======================================================================

-- Application user (password irrelevant — trust auth in pg_hba.conf)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'blocpress') THEN
        CREATE USER blocpress WITH PASSWORD 'blocpress';
    END IF;
END$$;

-- Create databases
SELECT 'CREATE DATABASE workbench OWNER blocpress'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'workbench')\gexec

SELECT 'CREATE DATABASE production OWNER blocpress'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'production')\gexec

-- ======================================================================
-- Workbench schema  (mirrors Template + TestDataSet JPA entities)
-- ======================================================================
\connect workbench

CREATE TABLE IF NOT EXISTS template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    valid_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP,
    review_cycle_years INTEGER,
    version INTEGER NOT NULL DEFAULT 1,
    content BYTEA NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    type VARCHAR(50) NOT NULL DEFAULT 'TEMPLATE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validation_result JSONB,
    ignored_patterns JSONB,
    rejection_reason TEXT,
    rejected_at TIMESTAMP,
    UNIQUE(name, valid_from, version)
);

CREATE TABLE IF NOT EXISTS test_data_set (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES template(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    test_data JSONB NOT NULL,
    expected_pdf BYTEA,
    pdf_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    notes TEXT,
    ignored_patterns JSONB,
    UNIQUE(template_id, name)
);

CREATE INDEX IF NOT EXISTS idx_template_name ON template(name);
CREATE INDEX IF NOT EXISTS idx_template_valid_from ON template(valid_from DESC);
CREATE INDEX IF NOT EXISTS idx_template_status ON template(status);
CREATE INDEX IF NOT EXISTS idx_template_type ON template(type);
CREATE INDEX IF NOT EXISTS idx_test_data_set_template ON test_data_set(template_id);

-- Transfer ownership so the app user can ALTER tables (Hibernate update strategy)
ALTER TABLE template OWNER TO blocpress;
ALTER TABLE test_data_set OWNER TO blocpress;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO blocpress;

-- ======================================================================
-- Production schema  (mirrors ProductionTemplate JPA entity in render)
-- ======================================================================
\connect production

CREATE TABLE IF NOT EXISTS template (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,
    content BYTEA NOT NULL,
    UNIQUE(name, valid_from, version)
);

CREATE INDEX IF NOT EXISTS idx_template_name ON template(name);
CREATE INDEX IF NOT EXISTS idx_template_valid_from ON template(valid_from DESC);

CREATE TABLE IF NOT EXISTS render_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    template_name VARCHAR(255),
    data JSONB NOT NULL,
    output_type VARCHAR(10) NOT NULL DEFAULT 'pdf',
    result BYTEA,
    webhook_url VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_render_job_status ON render_job(status, created_at);

ALTER TABLE template OWNER TO blocpress;
ALTER TABLE render_job OWNER TO blocpress;
