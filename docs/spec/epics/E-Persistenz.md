---
id: E-Persistenz
title: Template-Speicherung (workbench / production)
status: verified
derived_from: docs/product-backlog.adoc:100-125 (Epic 2)
---

## E-Persistenz — Template-Speicherung und Stufentrennung

Vorlagen werden in zwei getrennten Datenhaltungen geführt: `workbench`
(Entwicklung/Test, alles erlaubt) und `production` (nur freigegebene Vorlagen,
physisch kopiert).

**Warum.** Die physische Kopie nach `production` ist der Compliance-Nachweis,
dass nur validierte und freigegebene Vorlagen produktiv gedruckt werden. Die
ursprünglich gedachten vier Schemata (workbench, proof, production, admin) wurden
am 2026-03-01 auf zwei reduziert — eine separate Proof-/Admin-Instanz ist dafür
nicht nötig.
