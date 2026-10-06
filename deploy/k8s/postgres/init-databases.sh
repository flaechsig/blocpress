#!/bin/bash
# Laeuft einmal beim ersten Start mit leerem Datenverzeichnis (docker-entrypoint-initdb.d).
# Legt die beiden Datenbanken an; die Tabellen legen workbench und render selbst an.
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE DATABASE workbench OWNER "$POSTGRES_USER";
CREATE DATABASE production OWNER "$POSTGRES_USER";
SQL
