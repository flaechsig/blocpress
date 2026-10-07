---
name: secspine-scan
description: >-
  Phase 3 des Pentests (Schwachstellenanalyse): die Scanner-Flotte gegen ein eigenes,
  autorisiertes Ziel fahren — SAST (Semgrep), Abhängigkeiten/Container/IaC (Trivy), Secrets
  (gitleaks), laufende App/API (OWASP ZAP), bekannte Lücken (Nuclei) — Roh-Output nach logs/,
  je echtem Verdacht einen Befund-Kandidaten in security-review/ anlegen. Wählt die Scanner
  passend zum Stack selbst und fährt fehlende als Docker-Container. Verwenden nach secspine-enum
  oder bei Aufruf /secspine-scan.
---

# secspine-scan

Du dirigierst fertige Scanner gegen ein **eigenes, autorisiertes** Ziel und machst aus ihren
Treffern **Kandidaten** (`status: candidate`), keine fertigen Befunde. Scope in
`security-review/scope.md` zuerst lesen; nur im Scope scannen.

## Config selbst finden (nicht fragen)

- **Stack erkennen**: nach Quellcode, `Dockerfile`, `package.json`/`pom.xml`/`requirements.txt`,
  IaC (`*.tf`, k8s-YAML) sehen → welche Scanner sinnvoll sind.
- **Läuft das Ziel?** Offene Ports aus `security-review/enumeration.md` → wenn eine Web-App/API
  erreichbar ist, kommt DAST (ZAP) dazu.
- **Was ist lokal da?** `command -v` für `semgrep trivy gitleaks nuclei`. Fehlt einer und
  `docker` ist da, fahr ihn als Container (z. B. `semgrep/semgrep`, `aquasec/trivy`,
  `ghcr.io/gitleaks/gitleaks`, `projectdiscovery/nuclei`, `ghcr.io/zaproxy/zaproxy`).

## Flotte

| Scanner | Wogegen | Log |
|---|---|---|
| Semgrep | Quellcode (SAST) | `logs/03_semgrep_<datum>.txt` |
| Trivy | Abhängigkeiten, Images, IaC | `logs/03_trivy_<datum>.txt` |
| gitleaks | Secrets in der Git-Historie | `logs/03_gitleaks_<datum>.txt` |
| ZAP Baseline | laufende App/API (DAST) | `logs/03_zap_<datum>.txt` |
| Nuclei | bekannte Lücken (Templates) | `logs/03_nuclei_<datum>.txt` |

Lange Läufe (ZAP Full, Nuclei komplett) sind Nachtlauf-Kandidaten — hier die schnellen
Varianten (Baseline), die Vollläufe später automatisiert.

### Bewährte Aufrufe

Roh-Output immer nach `logs/` umleiten. Bei Docker das Ziel **read-only** mounten.

- **Trivy** (Deps + Secrets + IaC in einem Lauf):
  `trivy fs --scanners vuln,secret,misconfig --no-progress <ziel>`
- **gitleaks** (Git-Historie):
  `docker run --rm -v "$PWD":/repo:ro ghcr.io/gitleaks/gitleaks:latest detect --source=/repo --no-banner -v`
- **Semgrep** (SAST): eine **konkrete Regelmenge** angeben, z. B. `--config p/default`.
  **Nicht** `--config auto` mit `--metrics=off` kombinieren — `auto` verlangt aktive
  Metrik und bricht sonst ab (`Cannot create auto config when metrics are off`):
  `docker run --rm -v "$PWD":/src:ro semgrep/semgrep semgrep scan /src --config p/default --metrics=off`

Semgrep meldet oft viele Treffer in Test-, Dev- und generiertem Code (Codegen-Templates,
Dev-TLS-Helfer). Das ist in der Triage Kontext, kein eigener Befund je Treffer — echte
Produktionspfade von Test/Dev/Codegen trennen.

## Aus Treffern Kandidaten machen

1. Roh-Output **immer** nach `logs/`, unverändert.
2. Scanner-Treffer sichten: Dubletten und offensichtliche False Positives zusammenfassen.
   Nicht jeden Scanner-Eintrag zum Befund machen — das ist Lärm.
3. Je echtem, eigenständigem Verdacht:
   `python3 .secspine/secspine.py new F-NN "<Titel>"`, dann Frontmatter füllen
   (`status: candidate`, `severity`, `target`, `source: <scanner>`, `evidence: [logs/…]`,
   wenn bekannt `cwe`/`owasp`) und den Abschnitt `## Was` schreiben. Repro/Auswirkung/Maßnahme
   bleiben für `secspine-verify`.
4. Am Ende `python3 .secspine/secspine.py check` — muss OK melden.

## Regeln

- Scanner gegen Produktion nur mit Freigabe und schonend (Baseline, kein aktiver Angriffs-Scan).
- Treffer sind Verdacht, nicht Wahrheit. Bestätigt wird in Phase 4 (`secspine-verify`).
- Nächste freie `F-`Nummer nehmen; `check` meldet Doppelungen.
