---
id: E-ADMINISTRATION
title: Rollen und Audit (über den Identity-Provider)
derived_from: docs/legacy/product-backlog.adoc:291-321 (Epic 5)
---

## E-ADMINISTRATION — Rollen und Audit

Rollenprüfung bei jedem API-Aufruf und lückenloses Audit-Log der
Workflow-Änderungen. Benutzer und Rollen verwaltet **nicht** blocpress, sondern
ein externer Identity-Provider; die Rollen stehen als Claim im JWT. Umsetzung in
den bestehenden Modulen — kein eigenes Modul `blocpress-admin`
([ADR-0003](../../09-decisions/ADR-0003.md)).

**Warum.** Freigaben und Produktionsdeploys sollen nur berechtigte Rollen
auslösen dürfen, und ein Audit-Log macht Freigaben nachträglich nachvollziehbar.
Betreiber haben einen Identity-Provider; eine zweite Benutzerverwaltung in
blocpress brächte keinen Mehrwert. Noch nicht begonnen.

## Stories

<!-- generated:stories -->
- [US-0020](../stories/US-0020.md) — Benutzer und Rollen verwalten
- [US-0021](../stories/US-0021.md) — Rollen bei jedem API-Aufruf prüfen
- [US-0022](../stories/US-0022.md) — Workflow-Änderungen im Audit-Log festhalten
<!-- /generated -->
