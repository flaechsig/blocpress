-- Initialize production database for blocpress
-- This is executed when the production database is created

-- Production template table (deployed/approved templates only)
-- Uniqueness: (name, valid_from, version)
-- The active template for rendering is: SELECT * FROM template
--   WHERE name = ? AND valid_from <= NOW()
--   ORDER BY valid_from DESC, version DESC LIMIT 1
CREATE TABLE IF NOT EXISTS template (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    content BYTEA NOT NULL,
    UNIQUE(name, valid_from, version)
);

-- Performance indices
CREATE INDEX IF NOT EXISTS idx_template_name ON template(name);
CREATE INDEX IF NOT EXISTS idx_template_valid_from ON template(valid_from DESC);
