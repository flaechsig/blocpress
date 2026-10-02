---
id: E-Formate
title: Formatkonvertierung (ODT → PDF/RTF)
status: verified
derived_from: docs/product-backlog.adoc:41-44 (TI-3)
---

## E-Formate — Formatkonvertierung

Das gefüllte ODT wird bei Bedarf in weitere Ausgabeformate konvertiert (PDF, RTF)
— über einen headless LibreOffice-Prozess (`LibreOfficeProcessor`), gekapselt im
Modul `blocpress-render`.

**Warum.** Empfänger wollen oft PDF, nicht ODT. Die Konvertierung gehört an den
Rand (render-Modul), damit der Kern (`blocpress-core`) ohne LibreOffice-Abhängigkeit
und damit schnell testbar bleibt.
