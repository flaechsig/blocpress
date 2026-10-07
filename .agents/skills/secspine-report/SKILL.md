---
name: secspine-report
description: >-
  Phase 6 des Pentests (Bericht): die Befunde prüfen und security-review/report.md neu generieren,
  Risiko je Fund einschätzen (Schwere × Wahrscheinlichkeit), offene Lücken benennen. Verwenden,
  wenn ein Zwischenstand oder der Abschlussbericht ansteht, oder bei Aufruf /secspine-report.
---

# secspine-report

Du lieferst den Bericht — die eigentliche Lieferung des Tests. Der Bericht wird **generiert**,
nicht von Hand geschrieben: die Wahrheit steht in `security-review/`.

## Vorgehen

1. `python3 .secspine/secspine.py check` — muss OK melden. Mängel (fehlende Repro/Maßnahme bei
   bestätigten Funden, tote Evidence-Pfade, doppelte IDs) zuerst in den Befunden beheben.
2. `python3 .secspine/secspine.py render` — schreibt `security-review/report.md`.
3. **Durchsehen**, nicht nachdichten: stimmt je Fund die Risiko-Einordnung (Schwere ×
   Wahrscheinlichkeit)? Ist die Maßnahme konkret und umsetzbar? Fehlt bei `verified` der
   Nachweis der Behebung?
4. **Lücken benennen**: was wurde **nicht** getestet (Scope-Grenzen, offene Phasen, z. B. ein
   noch ausstehender authentifizierter Test). Ehrlichkeit über den Deckungsgrad gehört in den
   Bericht.
5. Dem Menschen einen kurzen Stand geben + den Pfad zum Bericht. Nicht den ganzen Bericht in
   den Chat kopieren.

## Regeln

- `06_bericht.md` nie von Hand ändern — der Kopf sagt es, `render` überschreibt es. Änderungen
  immer an den Befunden.
- Keine Schwere hochreden und keine herunterspielen. Das Risiko ist Schwere × Wahrscheinlichkeit
  im realen Kontext dieses Systems.
- Behobene Funde bleiben im Bericht (als `verified`) — der Weg vom Verdacht zur Behebung ist der
  Nachweis, dass getestet wurde.
