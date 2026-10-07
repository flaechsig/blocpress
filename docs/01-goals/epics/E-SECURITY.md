---
id: E-SECURITY
title: Sicherheit
---

## E-SECURITY — Sicherheit

Maßnahmen aus Security-Tests (secspine): Schwachstellen in Konfiguration, Abhängigkeiten,
Images und Pipeline, die ein Test bestätigt hat. Jede Story verweist auf ihr Risiko
(`SEC-n` oder `R-n`) in [Kapitel 11](../../11-risks.md).

**Warum.** blocpress verarbeitet Vorlagen, aus denen Dokumente an Kunden entstehen. Wer
Vorlagen in Produktion ändern oder die Dienste lahmlegen kann, trifft genau das; Befunde
eines Tests sollen deshalb nachvollziehbar zu einer Behebung führen.

## Stories

<!-- generated:stories -->
- [US-0054](../stories/US-0054.md) — Vorlagen-Import in render nur authentifiziert annehmen
- [US-0055](../stories/US-0055.md) — CORS in Workbench und render auf bekannte Origins beschränken
- [US-0056](../stories/US-0056.md) — Quarkus-Plattform auf einen Stand ohne bekannte Lücken heben
- [US-0057](../stories/US-0057.md) — Quickstart ohne öffentlich bekannten JWT-Schlüssel
- [US-0058](../stories/US-0058.md) — Container ohne root-Rechte betreiben
- [US-0059](../stories/US-0059.md) — Actions im Release-Workflow auf Commit-SHA pinnen
<!-- /generated -->
