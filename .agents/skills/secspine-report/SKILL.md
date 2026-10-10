---
name: secspine-report
description: >-
  Phase 6 des Pentests (Bericht): die Befunde prüfen und security-review/report.md neu generieren,
  Risiko je Fund einschätzen (Schwere × Wahrscheinlichkeit), offene Lücken benennen. Verwenden,
  wenn ein Zwischenstand oder der Abschlussbericht ansteht, oder bei Aufruf /secspine-report.
  In Projekten mit Architektur-Doku (docspine) zusätzlich die bestätigten Funde nach den
  Regeln von docspine in Kapitel 11 übertragen (je Fund ein SEC-Risiko).
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

Erkennst du im Projekt eine Architektur-Dokumentation nach docspine (`.docspine/STANDARD.md`),
gehört die **dauerhafte** Erkenntnis dorthin, nicht nur nach `security-review/` (ADR-0007).
Was in `docs/` steht, regelt **docspine**, nicht secspine (ADR-0008): Format, IDs, Verweise
und Prüfung stehen in `.docspine/STANDARD.md`. Du schreibst dort nur nach diesen Regeln.

Welcher Weg gilt, entscheidet die docspine-Version in der ersten Zeile von
`.docspine/STANDARD.md` (`<!-- docspine X · … -->`):

### docspine 0.21 oder neuer: ein `SEC-` je Fund

Lies vorher `.docspine/STANDARD.md` Abschnitt 3.8 (Risiken und Schulden). Dann —
**vorschlagen, dann nach Freigabe** ausführen:

1. **Liegt Kapitel 11 noch als eine Datei `docs/11-risks.md` vor**, zuerst die Migration mit
   `docspine-update` vorschlagen und danach weitermachen. Ohne Migration den Übergangsweg
   unten nehmen; nie beides nebeneinander anlegen.
2. **Je bestätigtem Fund eine Datei** `docs/11-risks/SEC-NNNN.md` mit der nächsten freien
   Nummer, `status: open`, `severity` aus dem Befund, `source` mit Test und Datum (etwa
   „Security-Test 2026-10-07“). Text: Risiko, Folge, Maßnahme — in eigenen Worten, ohne
   Logs, Payloads oder Pfade aus `security-review/` (die sind lokal).
3. **Abgleich statt Doppelung.** Deckt ein vorhandenes Risiko den Fund schon ab: Ist es ein
   `SEC-`, dort ergänzen. Ist es ein `R-`, das sich als Security-Thema herausstellt, ein neues
   `SEC-` mit `supersedes: R-NNNN` vorschlagen; das `R-` wird `superseded`.
4. **Stories für die Behebung** schlägst du vor; angelegt werden sie nach den Regeln von
   docspine (`docspine-require`). Die Story nennt ihr Risiko in `addresses`. Keine Verweise
   von Hand in die eine oder andere Richtung, kein „behoben am …“ — den Stand zeigt docspine
   am Risiko. Ein Epic (etwa `E-SECURITY`) ist ein Vorschlag; der Name gehört dem Projekt.
5. **Verworfene Funde** werden kein Risiko. Relevantes „geprüft, kein Risiko“ gehört als Text
   in `docs/11-risks/README.md`, oberhalb des generierten Bereichs.
6. **Behoben** setzt nicht secspine: Ein `SEC-` wird `closed`, wenn seine Stories `verified`
   sind; das prüft docspine. secspine prüft in einer Nachtestrunde nur, ob der Fund wirklich
   weg ist (`secspine-verify`).

### Ältere docspine-Version: Übergang

Bis das Projekt docspine 0.21 oder neuer hat, gilt der bisherige Weg: je bestätigtem Fund eine
Zeile in einem eigenen Abschnitt „Security-Test-Befunde“ in `docs/11-risks.md`, Präfix
`SEC-1`, `SEC-2`, …, Spalten ID, Risiko, Schwere, Status (`offen`/`behoben`), Story. Schlage
dabei das Update von docspine vor.

### In beiden Fällen

- **`security-review/` ist Arbeitsstand** — in die `.gitignore` des Projekts aufnehmen
  (`logs/` und `certs/` schützt secspine bereits). Der `report.md` bleibt lokal.
- Die docspine-Prüfung laufen lassen (`render`, dann `check`), bis sie OK meldet.
- Das **Beheben** der Funde ist Sache des Zielprojekts (eigene Branches), nicht von secspine.
- **Öffentliches Repo** (ADR-0009): Die neuen `SEC-` und ihre Stories bleiben auf dem
  Security-Branch, bis sie `closed` sind; Fix-Branches zweigen von ihm ab. Weise darauf hin
  und auf die Sperre vor dem Push (`secspine.py install-hook`), falls sie fehlt.

## Regeln

- `report.md` nie von Hand ändern — der Kopf sagt es, `render` überschreibt es. Änderungen
  immer an den Befunden.
- Keine Schwere hochreden und keine herunterspielen. Das Risiko ist Schwere × Wahrscheinlichkeit
  im realen Kontext dieses Systems.
- Behobene Funde bleiben im Bericht (als `verified`) — der Weg vom Verdacht zur Behebung ist der
  Nachweis, dass getestet wurde.
