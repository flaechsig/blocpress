-- Migration V003: Fix template constraints for UC-10.1 versioning
-- This migration handles upgrading existing databases to the new versioning schema

-- 1. Add version and validFrom columns if they don't exist
ALTER TABLE template
  ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;

ALTER TABLE template
  ADD COLUMN IF NOT EXISTS valid_from TIMESTAMP NULL;

-- 2. Handle existing data: ensure all templates have a version
UPDATE template SET version = 1 WHERE version IS NULL;

-- 3. Make columns NOT NULL
ALTER TABLE template
  ALTER COLUMN version SET NOT NULL,
  ALTER COLUMN name SET NOT NULL;

-- 4. Drop old unique constraint on name only
-- Find and drop constraints that are unique on name only
DO $$
DECLARE
  constraint_to_drop text;
BEGIN
  -- Find the unique constraint on name column (not including version)
  SELECT constraint_name INTO constraint_to_drop
  FROM information_schema.key_column_usage
  WHERE table_name = 'template'
    AND constraint_type = 'UNIQUE'
    AND column_name = 'name'
    AND constraint_name NOT LIKE '%version%'
  LIMIT 1;
  
  IF constraint_to_drop IS NOT NULL THEN
    EXECUTE 'ALTER TABLE template DROP CONSTRAINT ' || constraint_to_drop;
  END IF;
END $$;

-- 5. Add new composite unique constraint on (name, version)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_name = 'template'
      AND constraint_type = 'UNIQUE'
      AND constraint_name = 'uk_template_name_version'
  ) THEN
    ALTER TABLE template
      ADD CONSTRAINT uk_template_name_version UNIQUE (name, version);
  END IF;
END $$;

-- 6. Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_template_name_version 
  ON template(name, version DESC);

CREATE INDEX IF NOT EXISTS idx_template_status_valid_from 
  ON template(status, valid_from DESC NULLS FIRST);

-- 7. Migration complete
COMMENT ON TABLE template IS 'Templates mit Versionierung (UC-10.1): Eindeutig auf (name, version), validFrom für zeitgesteuerte Aktivierung';
