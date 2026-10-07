<!-- secspine 0.3.1 · source: .secspine/STANDARD.md · do not edit in projects -->

# secspine — Standard

> Das Rückgrat eines Security-Tests ist die **Befund-Kette**: von einem Verdacht über
> den reproduzierbaren Nachweis und die Auswirkung bis zur Maßnahme, verbunden über eine
> feste ID und vom Prüfwerkzeug geprüft. secspine ist für docspine gedacht, was ein
> Security-Test für Entwicklung ist: dieselbe Arbeitsweise, anderes Rückgrat.
>
> secspine testet **eigene, autorisierte** Systeme. Scope und Freigabe (Phase 0 im
> Drehbuch) stehen vor jedem Paket.

## 1. Idee

secspine arbeitet in **Phasen** (Abschnitt 6). Es liefert die **Werkzeuge**, die in den
Phasen greifen, und hält die Ergebnisse zusammen:

- `security-review/` — alle Artefakte des Tests in einem Ordner: ein Befund je Datei
  (`F-NN.md`, `BB-NN.md`; das Werkzeug erkennt Befunde am ID-Muster), dazu die von Hand
  gepflegten Notizen `scope.md` und `enumeration.md` sowie der generierte `report.md`.
- `logs/` — rohe Tool-Ausgaben, unverändert. Jeder Befund verweist auf seinen Log.
- `screenshots/` — Bildnachweise.

## 2. Der Befund

Eine Datei `security-review/<ID>.md`. Frontmatter (englische Schlüssel) + Fließtext
(deutsch). Vorlage: `.secspine/finding-template.md`.

### 2.1 Pflicht-Frontmatter

| Schlüssel | Werte |
|---|---|
| `id` | unveränderlich, Muster `F-NN`, `BB-NN` oder `F-NNNN`. Einmal vergeben, bleibt. |
| `title` | eine Zeile |
| `status` | `candidate` → `confirmed`/`dismissed` → `fixed` → `verified` |
| `severity` | `info` \| `low` \| `medium` \| `high` \| `critical` |
| `target` | was getestet wurde, z. B. `registry:5001` |
| `source` | wer/was den Verdacht fand: `manual`, `semgrep`, `trivy`, `gitleaks`, `zap`, `nuclei`, `naabu`, `nmap` |
| `discovered` | `YYYY-MM-DD` |

Optional: `cwe` (z. B. `CWE-306`), `owasp` (z. B. `A05:2021`), `evidence` (Liste von
Pfaden zu `logs/`/`screenshots/`), `story` (Verweis auf eine ausgelöste Entwicklungs-Story).

### 2.2 Pflicht-Abschnitte (Markdown `##`)

- `## Was` — der Verdacht in zwei, drei Sätzen.
- `## Reproduktion` — die kleinsten Schritte, die es auslösen. **Pflicht ab `confirmed`.**
- `## Auswirkung` — was ein Angreifer real erreicht. **Pflicht ab `confirmed`.**
- `## Maßnahme` — die konkrete Gegenmaßnahme. **Pflicht ab `confirmed`.**

Bei `verified` kommt dazu, **wie** die Behebung nachgeprüft wurde (ein Satz + Log).

## 3. Lebenszyklus (die Gates)

```
candidate ──bestätigt──> confirmed ──gefixt──> fixed ──nachgeprüft──> verified
    └────────verworfen──> dismissed
```

- Ein `candidate` darf frei geändert werden. Ab `confirmed` ändert sich der **Befund als
  Tatsache** nicht mehr rückwirkend — Korrekturen kommen als Statuswechsel oder neuer Befund.
- `dismissed` wird nicht gelöscht: der verworfene Verdacht mit Begründung ist Teil des
  Nachweises (kein Lärm mehr, aber dokumentiert).
- Kein Schritt ohne Verständnis (Drehbuch-Regel 4). Kein Nachweis, der Daten gefährdet.

## 4. IDs

Unveränderlich, wie in docspine. Bestehende `F-`/`BB-`-IDs bleiben. Neue Befunde: nächste
freie `F-`Nummer. Das Prüfwerkzeug meldet Doppelungen.

## 5. Das Prüfwerkzeug

`python3 .secspine/secspine.py <befehl>` (nur Standardbibliothek):

- `check` — jeder Befund formal gültig; ab `confirmed` sind Repro/Auswirkung/Maßnahme da;
  referenzierte Evidence-Dateien existieren; IDs eindeutig. Meldet `OK` oder listet Mängel.
- `render` — baut `security-review/report.md` neu aus den Befunden.
- `list` — Überblick: ID, Schwere, Status, Titel.
- `new <ID> "<Titel>"` — legt einen Befund aus der Vorlage an.

Vor jedem Abschluss einer Phase: `check` muss OK melden, dann `render`.

## 6. Werkzeuge je Phase (Skills)

| Phase (Drehbuch) | Skill | Rolle |
|---|---|---|
| 2 Enumeration | `secspine-enum` | Angriffsfläche aufnehmen → `logs/` + `security-review/enumeration.md` |
| 3 Schwachstellen | `secspine-scan` | Scanner-Flotte, Roh-Output → `logs/`, Kandidaten anlegen |
| 4 Verifikation | `secspine-verify` | einen Verdacht bestätigen/verwerfen, minimaler PoC |
| (Nachtlauf) | `secspine-triage` | was lief rein, was ist neu, was ist Lärm |
| 6 Bericht | `secspine-report` | `check` + `render`, Risiko einschätzen, Lücken benennen |

Grundsatz aller Skills: **frag das System, nicht den Menschen.** Stack,
offene Ports, ob Docker läuft, welche Scanner da sind — selbst erkennen. Den Menschen nur
fragen, wo Scope, Freigabe oder ein echtes Urteil ansteht.

## 7. Scanner-Flotte

secspine schreibt die Scanner nicht neu; es dirigiert fertige. Fehlt einer lokal, aber
Docker ist da, läuft er als Container. Zuordnung in `secspine-scan`:

| Ebene | Werkzeug | fällt in Phase |
|---|---|---|
| Quellcode (SAST) | Semgrep | 3 |
| Abhängigkeiten/Container/IaC (SCA) | Trivy | 3 |
| Secrets in Git-Historie | gitleaks | 3 |
| Laufende App/API (DAST) | OWASP ZAP | 3 |
| bekannte Lücken (Templates) | Nuclei | 3 |
| Ports | naabu (optional nmap für Dienstversion) | 2 |

Scanner liefern **Kandidaten** (`status: candidate`), keine Befunde. Erst `secspine-verify` (der
Mensch mit dem Werkzeug) macht daraus `confirmed` oder `dismissed`. Scanner-Lärm bleibt Lärm,
bis er bestätigt ist.
