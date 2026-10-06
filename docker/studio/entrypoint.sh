#!/bin/bash
set -e

PG_BIN=/usr/lib/postgresql/18/bin
PG_CONF=/etc/postgresql/18/main/postgresql.conf
PG_DATA=/data/postgresql
ES_DATA=/data/elasticsearch

mkdir -p /var/log/postgresql /var/log/supervisor /var/run/postgresql
chown postgres:postgres /var/log/postgresql /var/run/postgresql

# -----------------------------------------------------------------------
# 1. Data volume /data: create what is missing (empty volume or none)
# -----------------------------------------------------------------------
mkdir -p "$PG_DATA" "$ES_DATA"
chown postgres:postgres "$PG_DATA"
chmod 700 "$PG_DATA"
chown elasticsearch:elasticsearch "$ES_DATA"
if [ ! -s "$PG_DATA/PG_VERSION" ]; then
    echo "[studio] Creating PostgreSQL cluster in $PG_DATA..."
    su postgres -s /bin/bash -c "$PG_BIN/initdb -D $PG_DATA --auth=trust --encoding=UTF8 --locale=C.UTF-8" > /dev/null
fi

# -----------------------------------------------------------------------
# 2. Create user and databases (idempotent); the services create their
#    tables themselves on start (Liquibase). PostgreSQL runs only briefly
#    here; supervisord starts it for good and restarts it if it stops.
# -----------------------------------------------------------------------
echo "[studio] Initialising databases..."
su postgres -s /bin/bash -c "$PG_BIN/pg_ctl -D $PG_DATA -o '-c config_file=$PG_CONF' -w start" > /dev/null
su postgres -s /bin/bash -c "psql -q -d postgres -f /docker-entrypoint-initdb.d/init-studio.sql"
su postgres -s /bin/bash -c "$PG_BIN/pg_ctl -D $PG_DATA -m fast -w stop" > /dev/null
echo "[studio] Database init complete."

# -----------------------------------------------------------------------
# 3. Hand over to supervisord (postgresql, elasticsearch, studio, workbench, render)
# -----------------------------------------------------------------------
# Defaults für konfigurierbare Laufzeit-Parameter (überschreibbar via -e)
export BLOCPRESS_LO_WORKERS=${BLOCPRESS_LO_WORKERS:-1}

echo "[studio] Starting services via supervisord..."
exec /usr/bin/supervisord -n -c /etc/supervisor/conf.d/blocpress.conf
