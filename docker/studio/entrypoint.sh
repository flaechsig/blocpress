#!/bin/bash
set -e

LOG_DIR=/var/log/postgresql
mkdir -p "$LOG_DIR"
chown postgres:postgres "$LOG_DIR"

mkdir -p /var/log/supervisor

# -----------------------------------------------------------------------
# 1. Start PostgreSQL (stays running in the background)
# -----------------------------------------------------------------------
echo "[studio] Starting PostgreSQL..."
pg_ctlcluster 16 main start

# -----------------------------------------------------------------------
# 2. Initialise databases + schemas (idempotent)
# -----------------------------------------------------------------------
echo "[studio] Initialising databases..."
su - postgres -c "psql -d postgres -f /docker-entrypoint-initdb.d/init-studio.sql"
echo "[studio] Database init complete."

# -----------------------------------------------------------------------
# 3. Hand over to supervisord (manages workbench + render)
# -----------------------------------------------------------------------
echo "[studio] Starting services via supervisord..."
exec /usr/bin/supervisord -n -c /etc/supervisor/conf.d/blocpress.conf
