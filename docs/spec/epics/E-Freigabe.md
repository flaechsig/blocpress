---
id: E-Freigabe
title: Prüfung und Freigabe
status: verified
derived_from: docs/product-backlog.adoc:229-288 (Epic 4) und :156-157, :176-187 (Epic 3, Workflow/Regression)
---

## E-Freigabe — Prüfung, Freigabe und Compliance-Review

Vorlagen durchlaufen einen Workflow (DRAFT → SUBMITTED → APPROVED, Ablehnung
zurück nach DRAFT), werden über Regressionstests gegen Baseline-PDFs abgesichert,
bei Freigabe automatisch nach `production` übergeben und nach Ablauf ihres
Gültigkeitszeitraums gesperrt.

**Warum.** Dokumente mit Außenwirkung (Verträge, Rechnungen) dürfen nur in
geprüfter Form produktiv gehen und müssen periodisch überprüft werden.
Das Vier-Augen-Prinzip ist bewusst **organisatorisch**, nicht technisch
erzwungen (Entscheidung 2026-03-01) — eine schlanke Umsetzung mit Option auf
späteres Verschärfen.

> **Abweichung zum Altbestand:** Der Altbestand verortet dieses Thema im Modul
> `blocpress-proof`. Das Modul existiert nicht; die Freigabe läuft in
> `blocpress-workbench` (dort als „Interim" bezeichnet). Ob ein eigenes Modul
> noch Ziel ist, ist offen (siehe ROADMAP).
