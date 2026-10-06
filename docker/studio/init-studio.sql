-- ======================================================================
-- blocpress-studio: Database initialisation (idempotent)
-- Run as superuser against the 'postgres' database.
-- Creates the user and the 'workbench' and 'production' databases. The tables are
-- created by the services themselves on start (Liquibase, db/changeLog.xml, US-0052).
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
