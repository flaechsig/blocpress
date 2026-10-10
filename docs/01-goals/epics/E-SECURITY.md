---
id: E-SECURITY
title: Sicherheit
---

## E-SECURITY — Sicherheit

Maßnahmen aus Security-Tests (secspine): Schwachstellen in Konfiguration, Abhängigkeiten,
Images und Pipeline, die ein Test bestätigt hat. Jede Story nennt in `addresses` das Risiko
(`SEC-NNNN` oder `R-NNNN`) in [Kapitel 11](../../11-risks/README.md), an dem sie arbeitet.

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
- [US-0060](../stories/US-0060.md) — Bausteine nur aus der eigenen Bibliothek
- [US-0064](../stories/US-0064.md) — Security-Header in Workbench, Studio und render
- [US-0066](../stories/US-0066.md) — Engine nur mit API-Schlüssel
- [US-0067](../stories/US-0067.md) — Voller Modus nur mit gültigem Token
<!-- /generated -->
