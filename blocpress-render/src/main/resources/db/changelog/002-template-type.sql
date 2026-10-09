--liquibase formatted sql

--changeset blocpress:002-template-type
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'template' AND column_name = 'type'
--comment: Vorlage oder Baustein (ADR-0018, REQ-0036); bestehende Eintraege gelten als Vorlage

-- Rendern per Namen findet nur Vorlagen, eingebundene Abschnitte nur Bausteine. Bausteine, die
-- vor diesem Changeset importiert wurden, gelten als Vorlage, bis die Workbench sie erneut
-- importiert.

ALTER TABLE template ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'TEMPLATE';

CREATE INDEX idx_template_name_type ON template (name, type);
