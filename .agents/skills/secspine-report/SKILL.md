---
name: secspine-report
description: >-
  Phase 6 des Pentests (Bericht): die Befunde prüfen und security-review/report.md neu generieren,
  Risiko je Fund einschätzen (Schwere × Wahrscheinlichkeit), offene Lücken benennen. Verwenden,
  wenn ein Zwischenstand oder der Abschlussbericht ansteht, oder bei Aufruf /secspine-report.
  In Projekten mit Architektur-Doku (docspine) zusätzlich die bestätigten Funde nach arc42
  Kapitel 11 destillieren.
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

## docspine-Modus: Funde in die Architektur-Doku destillieren

Erkennst du im Projekt eine Architektur-Dokumentation (`.docspine/` bzw. `docs/11-risks.md`),
gehört die **dauerhafte** Erkenntnis dorthin, nicht nur nach `security-review/` (ADR-0007).
Dann zusätzlich — **vorschlagen, dann nach Freigabe** ausführen:

1. **Risiken nach arc42 Kapitel 11** (`docs/11-risks.md`): je **bestätigtem** Fund eine Zeile
   in einem eigenen Abschnitt „Security-Test-Befunde" — **direkt unter den Architekturrisiken,
   vor den technischen Schulden**. Eigener Präfix **`SEC-1`, `SEC-2`, …**, getrennt von der
   `R-`Reihe des Projekts. Spalten: ID, Risiko, Schwere, **Status** (`offen`/`behoben`), Story.
   Verworfene Funde nicht als Risiko führen; relevante „geprüft, kein Risiko" als Fußnote.
   Gleicht sich ein Fund mit einem schon dokumentierten Risiko (z. B. einer `R-`Zeile), dort
   ergänzen oder darauf verweisen, statt ihn doppelt zu führen.
2. **Aufträge als Stories**, gebündelt in einem Epic **`E-SECURITY`** („Sicherheit"). Jede
   `SEC-`Zeile verweist auf ihre Story, jede Story zurück auf ihr `SEC-`Risiko.
3. **`security-review/` ist dann Arbeitsstand** — in die `.gitignore` des Projekts aufnehmen
   (`logs/` und `certs/` schützt secspine bereits). Der `report.md` bleibt lokal.
4. **Erhalt**: behobene `SEC-`Einträge bleiben stehen, nur als `behoben` markiert (mit Verweis
   auf die lösende Änderung) — Nachweis und Historie bleiben sichtbar.
5. Die docspine-Prüfung laufen lassen (`render`, dann `check`), bis sie OK meldet.

Das **Beheben** der Funde ist Sache des Zielprojekts (eigene Branches), nicht von secspine.

## Regeln

- `report.md` nie von Hand ändern — der Kopf sagt es, `render` überschreibt es. Änderungen
  immer an den Befunden.
- Keine Schwere hochreden und keine herunterspielen. Das Risiko ist Schwere × Wahrscheinlichkeit
  im realen Kontext dieses Systems.
- Behobene Funde bleiben im Bericht (als `verified`) — der Weg vom Verdacht zur Behebung ist der
  Nachweis, dass getestet wurde.
