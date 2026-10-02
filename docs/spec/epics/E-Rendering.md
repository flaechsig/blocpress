---
id: E-Rendering
title: Render-Pipeline (Vorlage + Daten → Dokument)
status: in-progress
derived_from: docs/product-backlog.adoc:36-39 (TF-5)
---

## E-Rendering — Render-Pipeline

Das Herzstück: `RenderEngine.mergeTemplate(URL, JsonNode)` führt vier
sequentielle Schritte aus — Textblock-Expansion, Bedingungsauswertung,
Schleifen-Behandlung, Feld-Ersetzung — und macht aus einer ODT-Vorlage plus JSON
ein fertiges Dokument.

**Warum.** Ohne eine verlässliche, getestete Pipeline ist jede Vorlage ein
Sonderfall. Dieses Thema bündelt die Regeln, nach denen *jede* Vorlage gefüllt
wird.
