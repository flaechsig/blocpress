#!/bin/bash
# Initialize both workbench and production databases for blocpress
# This script is called by PostgreSQL entrypoint

set -e

# Create production database if not exists
# Note: workbench DB is created automatically via POSTGRES_DB env var
PGPASSWORD="$POSTGRES_PASSWORD" psql -v ON_ERROR_STOP=1 \
    -h localhost \
    -U "$POSTGRES_USER" \
    -d postgres \
    -c "CREATE DATABASE production OWNER $POSTGRES_USER;"

echo "✓ Both blocpress databases created"
