---
id: E-RENDERING
title: Render-Pipeline (Vorlage + Daten → Dokument)
derived_from: product-backlog.adoc:36-39 legacy (git history) (TF-5)
---

## E-RENDERING — Render-Pipeline

Das Herzstück: `RenderEngine.mergeTemplate(URL, JsonNode)` führt vier
sequentielle Schritte aus — Textblock-Expansion, Bedingungsauswertung,
Schleifen-Behandlung, Feld-Ersetzung — und macht aus einer ODT-Vorlage plus JSON
ein fertiges Dokument.

**Warum.** Ohne eine verlässliche, getestete Pipeline ist jede Vorlage ein
Sonderfall. Dieses Thema bündelt die Regeln, nach denen *jede* Vorlage gefüllt
wird.

## Stories

<!-- generated:stories -->
- [US-0001](../stories/US-0001.md) — Platzhalter automatisch aus JSON füllen
- [US-0002](../stories/US-0002.md) — Bedingungen und Listen in einer Vorlage
- [US-0032](../stories/US-0032.md) — Gemeinsame Textbausteine einbinden
- [US-0033](../stories/US-0033.md) — Zahlen und Daten im Sprachformat der Vorlage
- [US-0035](../stories/US-0035.md) — Platzhalter in Kopf- und Fußzeilen
- [US-0036](../stories/US-0036.md) — Word-Vorlagen (DOCX) als Quelle
- [US-0071](../stories/US-0071.md) — Große Tabellen in linearer Zeit mischen
<!-- /generated -->
