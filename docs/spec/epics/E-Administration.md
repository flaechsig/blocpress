---
id: E-Administration
title: Rollen und Audit (über den Identity-Provider)
status: open
derived_from: docs/product-backlog.adoc:291-321 (Epic 5)
---

## E-Administration — Rollen und Audit

Rollenprüfung bei jedem API-Aufruf und lückenloses Audit-Log der
Workflow-Änderungen. Benutzer und Rollen verwaltet **nicht** blocpress, sondern
ein externer Identity-Provider; die Rollen stehen als Claim im JWT. Umsetzung in
den bestehenden Modulen — kein eigenes Modul `blocpress-admin`
([ADR-003](../../architecture/decisions/ADR-003.adoc)).

**Warum.** Freigaben und Produktionsdeploys sollen nur berechtigte Rollen
auslösen dürfen, und ein Audit-Log macht Freigaben nachträglich nachvollziehbar.
Betreiber haben einen Identity-Provider; eine zweite Benutzerverwaltung in
blocpress brächte keinen Mehrwert. Noch nicht begonnen.
