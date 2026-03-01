#!/bin/bash
# Initialize both workbench and production databases for blocpress

set -e

# Use the POSTGRES_USER and POSTGRES_PASSWORD passed by PostgreSQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    -- Create production database if not exists
    CREATE DATABASE production OWNER $POSTGRES_USER;
EOSQL

# Initialize workbench database (default)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "workbench" <<-EOSQL
    $(cat /docker-entrypoint-initdb.d/01-init-schemas.sql)
EOSQL

# Initialize production database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "production" <<-EOSQL
    $(cat /docker-entrypoint-initdb.d/02-init-production.sql)
EOSQL

echo "✓ Both blocpress databases initialized"
