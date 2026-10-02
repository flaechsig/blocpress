---
id: E-Administration
title: Administration (Benutzer, Rollen, Audit)
status: open
derived_from: docs/product-backlog.adoc:291-321 (Epic 5)
---

## E-Administration — Benutzer, Rollen und Audit

Benutzerverwaltung mit Rollenzuweisung, Rollenprüfung bei jedem API-Aufruf und
lückenloses Audit-Log der Workflow-Änderungen. Der Altbestand sieht dafür ein
Modul `blocpress-admin` vor.

**Warum.** Ohne eigene Benutzer- und Rollenverwaltung hängt die Freigabe an der
externen JWT-Ausstellung; ein Audit-Log macht Freigaben nachträglich
nachvollziehbar. Noch nicht begonnen.
