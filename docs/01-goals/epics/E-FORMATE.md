---
id: E-FORMATE
title: Formatkonvertierung (ODT → PDF/RTF)
derived_from: product-backlog.adoc:41-44 legacy (git history) (TI-3)
---

## E-FORMATE — Formatkonvertierung

Das gefüllte ODT wird bei Bedarf in weitere Ausgabeformate konvertiert (PDF, RTF)
— über einen headless LibreOffice-Prozess (`LibreOfficeProcessor`), gekapselt im
Modul `blocpress-render`.

**Warum.** Empfänger wollen oft PDF, nicht ODT. Die Konvertierung gehört an den
Rand (render-Modul), damit der Kern (`blocpress-core`) ohne LibreOffice-Abhängigkeit
und damit schnell testbar bleibt.

## Stories

<!-- generated:stories -->
- [US-0003](../stories/US-0003.md) — Dokument als PDF/RTF ausgeben
<!-- /generated -->
