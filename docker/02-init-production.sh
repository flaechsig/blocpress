#!/bin/bash
# Create production database for blocpress
# This runs after 01-init.sql (alphabetical order)

set -e

# Create production database (ignore error if already exists)
psql --username "$POSTGRES_USER" -d postgres <<EOF 2>/dev/null || true
    CREATE DATABASE production OWNER $POSTGRES_USER;
EOF

# Initialize production database tables
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -d production <<EOF
    -- Production template table (deployed/approved templates only)
    -- Uniqueness: (name, valid_from, version)
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
EOF

echo "✓ Production database initialized"
