---
name: secspine-verify
description: >-
  Phase 4 des Pentests (Verifikation): einen Befund-Kandidaten bestätigen oder verwerfen —
  mit dem kleinstmöglichen, schadensfreien Nachweis — und auf confirmed oder dismissed setzen,
  inkl. Reproduktion, Auswirkung und Maßnahme. Verwenden für einen einzelnen Verdacht aus
  security-review/, oder bei Aufruf /secspine-verify (optional mit einer Befund-ID).
---

# secspine-verify

Du beantwortest für **einen** Verdacht genau eine Frage: lässt er sich reproduzierbar und
**schadensfrei** auslösen? Das Ergebnis ist ein Statuswechsel, kein Scan. Arbeite an einem
eigenen, autorisierten Ziel im Scope.

## Argument

Eine Befund-ID, z. B. `/secspine-verify F-05`. Ohne ID: `python3 .secspine/secspine.py list`,
die `candidate`-Einträge zeigen, einen vorschlagen.

## Vorgehen

1. Den Kandidaten `security-review/<ID>.md` lesen (`## Was`).
2. **Kleinsten Nachweis planen**: der minimale Proof of Concept, der den Verdacht zeigt,
   ohne Daten zu verändern, zu löschen oder die Verfügbarkeit zu gefährden. Schreib-/
   Lösch-Tests nur read-only nachbilden oder sofort sauber zurücknehmen (wie bei F-01:
   Upload-Session eröffnet → sofort abgebrochen, nichts geschrieben).
3. **Ausführen**, Roh-Output nach `logs/04_<kurz>_<datum>.txt`, Bildnachweis nach
   `screenshots/`.
4. **Urteil**:
   - Bestätigt → `status: confirmed`. Jetzt sind `## Reproduktion`, `## Auswirkung`,
     `## Maßnahme` Pflicht (sonst meckert `check`). Reale Auswirkung einschätzen, nicht die
     theoretische.
   - Verworfen → `status: dismissed`. Unter `## Was` **warum** verworfen. Nicht löschen.
5. `evidence:` um die neuen Logs/Screenshots ergänzen.
6. `python3 .secspine/secspine.py check` — muss OK melden.

## Regeln

- Kein Datenverlust, keine Verfügbarkeit gefährdet. Im Zweifel stoppen und nachdenken
  (Drehbuch Phase 4).
- Ein Befund je Durchlauf. Taucht dabei ein neuer, eigenständiger Verdacht auf, bekommt er
  einen eigenen Kandidaten (`secspine.py new`), wird aber separat verifiziert.
- Führt der Fund zu einer Code-Änderung im getesteten Projekt, verweise mit `story:` darauf
  (z. B. eine docspine-US), statt den Fix hier zu beschreiben.
- Im **Standardlauf** (`secspine-run`) werden Kandidaten automatisch verifiziert, soweit das
  **schadensfrei aus der Analyse** geht; ein aktiver/eingreifender Nachweis wird nicht blind
  ausgeführt, sondern als „braucht manuelle Prüfung" markiert und gemeldet.
