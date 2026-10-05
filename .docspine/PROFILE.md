---
language: de
statement_language: en
sources:
  - MicroProfile JWT Auth; RFC 6750 (Bearer Token Usage)
  - OpenDocument (ODF) 1.3 — number:language / number:country
  - OpenDocument (ODF) 1.3 — style:master-page (style:header, style:footer, -left, -first)
  - OpenDocument (ODF) 1.3 — text:condition
  - OpenDocument (ODF) 1.3 — text:section-source
test_reports:
  - blocpress-core/target/surefire-reports
  - blocpress-render/target/surefire-reports
  - blocpress-workbench/target/surefire-reports
---

# Profil

## Abweichungen vom Standard

Keine. Unter `docs/legacy/` liegt Altbestand, der nach Abschnitt 10 des Standards
abgebaut wird. `docs/guides/` und `docs/dockerhub.md` sind projekteigene Dateien.
