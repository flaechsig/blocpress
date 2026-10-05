---
id: E-PERSISTENZ
title: Template-Speicherung (workbench / production)
derived_from: product-backlog.adoc:100-125 legacy (git history) (Epic 2)
---

## E-PERSISTENZ — Template-Speicherung und Stufentrennung

Vorlagen werden in zwei getrennten Datenhaltungen geführt: `workbench`
(Entwicklung/Test, alles erlaubt) und `production` (nur freigegebene Vorlagen,
physisch kopiert).

**Warum.** Die physische Kopie nach `production` ist der Compliance-Nachweis,
dass nur validierte und freigegebene Vorlagen produktiv gedruckt werden. Die
ursprünglich gedachten vier Schemata (workbench, proof, production, admin) wurden
am 2026-03-01 auf zwei reduziert — eine separate Proof-/Admin-Instanz ist dafür
nicht nötig.

## Stories

<!-- generated:stories -->
- [US-0008](../stories/US-0008.md) — Produktion nur aus freigegebenen Vorlagen
<!-- /generated -->
