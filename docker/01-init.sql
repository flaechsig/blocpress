-- Initialize blocpress PostgreSQL databases
-- PostgreSQL automatically runs all .sql files in /docker-entrypoint-initdb.d/ in alphabetical order
-- This single file handles both database initialization

-- ==================== WORKBENCH DATABASE ====================
-- Already created via POSTGRES_DB env var, but we initialize its tables

-- Workbench development template table
-- Uniqueness: (name, valid_from, version)
CREATE TABLE IF NOT EXISTS template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    valid_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,
    content BYTEA NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validation_result JSONB,
    UNIQUE(name, valid_from, version)
);

-- Workbench test data sets
CREATE TABLE IF NOT EXISTS test_data_set (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES template(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    test_data JSONB NOT NULL,
    expected_pdf BYTEA,
    pdf_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE(template_id, name)
);

-- Performance indices
CREATE INDEX IF NOT EXISTS idx_template_name ON template(name);
CREATE INDEX IF NOT EXISTS idx_template_valid_from ON template(valid_from DESC);
CREATE INDEX IF NOT EXISTS idx_template_status ON template(status);
CREATE INDEX IF NOT EXISTS idx_test_data_set_template ON test_data_set(template_id);
