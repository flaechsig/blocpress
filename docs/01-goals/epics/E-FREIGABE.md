---
id: E-FREIGABE
title: Prüfung und Freigabe
derived_from: product-backlog.adoc:229-288 legacy (git history) (Epic 4) und :156-157, :176-187 (Epic 3, Workflow/Regression)
---

## E-FREIGABE — Prüfung, Freigabe und Compliance-Review

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
> `blocpress-proof`. Entschieden 2026-10-03: kein eigenes Modul, die Freigabe
> bleibt in `blocpress-workbench` ([ADR-0003](../../09-decisions/ADR-0003.md)).

## Stories

<!-- generated:stories -->
- [US-0016](../stories/US-0016.md) — Vorlage einreichen, freigeben oder ablehnen
- [US-0017](../stories/US-0017.md) — Freigabe deployt automatisch nach Produktion
- [US-0018](../stories/US-0018.md) — Änderungen per Regressionstest absichern
- [US-0019](../stories/US-0019.md) — Vorlagen periodisch überprüfen (Compliance-Review)
- [US-0040](../stories/US-0040.md) — Fällige Reviews aktiv melden
- [US-0042](../stories/US-0042.md) — Ausmustern und Zurückziehen zuverlässig machen
- [US-0050](../stories/US-0050.md) — Freigabe nur mit bestandenen Regressionstests
- [US-0053](../stories/US-0053.md) — Diff-PDF aus der Oberfläche abrufen
<!-- /generated -->
