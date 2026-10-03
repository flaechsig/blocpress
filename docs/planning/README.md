<!-- GENERIERT von blocpress-req-check aus docs/planning/ + den Requirements.
     NICHT EDITIEREN — wird bei `mvn verify` neu erzeugt. -->

# Planung — Epics → User Stories → Requirements

Bricht die [Vision](../spec/VISION.md) über Epics → Stories → [Requirements](../spec/requirements/CATALOG.md) herunter und zeigt den Stand.

> **Status ist abgeleitet, nicht gepflegt:** eine Story ist `umgesetzt`, wenn alle ihre Requirements `implemented` sind; ein Epic ist `umgesetzt`, wenn alle seine Stories es sind (sonst `in Arbeit` / `geplant`). Der Status bezieht sich auf die hier modellierten Stories. Die gewachsene Doku (`product-backlog.adoc`) wird kontrolliert überführt, siehe [ROADMAP](ROADMAP.md).

## E-Administration — Rollen und Audit (über den Identity-Provider)  ·  `geplant`

- **US-0020** Benutzer und Rollen verwalten  `ohne Requirements`
- **US-0021** Rollen bei jedem API-Aufruf prüfen  `ohne Requirements`
- **US-0022** Workflow-Änderungen im Audit-Log festhalten  `ohne Requirements`

## E-Auslieferung — Auslieferung (Docker, Quickstart, Native)  ·  `geplant`

- **US-0026** Module als Docker-Images mit Healthcheck  `ohne Requirements`
- **US-0027** In Minuten ausprobieren (Quickstart)  `ohne Requirements`
- **US-0028** Native Images für schnellen Start  `ohne Requirements`
- **US-0034** render für die eigene Last richtig bemessen  `ohne Requirements`

## E-Formate — Formatkonvertierung (ODT → PDF/RTF)  ·  `umgesetzt`

- **US-0003** Dokument als PDF/RTF ausgeben  `umgesetzt` → [REQ-0012](../spec/requirements/REQ-0012.md)

## E-Freigabe — Prüfung und Freigabe  ·  `geplant`

- **US-0016** Vorlage einreichen, freigeben oder ablehnen  `ohne Requirements`
- **US-0017** Freigabe deployt automatisch nach Produktion  `ohne Requirements`
- **US-0018** Änderungen per Regressionstest absichern  `ohne Requirements`
- **US-0019** Vorlagen periodisch überprüfen (Compliance-Review)  `ohne Requirements`

## E-Persistenz — Template-Speicherung (workbench / production)  ·  `geplant`

- **US-0008** Produktion nur aus freigegebenen Vorlagen  `ohne Requirements`

## E-Release — Build-, Test- und Release-Automatisierung  ·  `geplant`

- **US-0029** Jeder Push wird gebaut und getestet  `ohne Requirements`
- **US-0030** Release mit einem Befehl  `ohne Requirements`
- **US-0031** Zusammenspiel der Module Ende-zu-Ende prüfen  `ohne Requirements`

## E-RenderService — Render-Service (REST-API, Auth, Jobs)  ·  `in Arbeit`

- **US-0004** Dokument synchron per REST rendern  `ohne Requirements`
- **US-0005** Freigegebene Vorlage per Name rendern (versioniert)  `ohne Requirements`
- **US-0006** Asynchron rendern über eine Job-Queue  `ohne Requirements`
- **US-0007** Render-API optional per JWT absichern  `umgesetzt` → [REQ-0008](../spec/requirements/REQ-0008.md), [REQ-0009](../spec/requirements/REQ-0009.md)

## E-Rendering — Render-Pipeline (Vorlage + Daten → Dokument)  ·  `in Arbeit`

- **US-0001** Platzhalter automatisch aus JSON füllen  `umgesetzt` → [REQ-0001](../spec/requirements/REQ-0001.md)
- **US-0002** Bedingungen und Listen in einer Vorlage  `umgesetzt` → [REQ-0002](../spec/requirements/REQ-0002.md), [REQ-0003](../spec/requirements/REQ-0003.md)
- **US-0032** Gemeinsame Textbausteine einbinden  `umgesetzt` → [REQ-0011](../spec/requirements/REQ-0011.md)
- **US-0033** Zahlen und Daten im Sprachformat der Vorlage  `umgesetzt` → [REQ-0005](../spec/requirements/REQ-0005.md), [REQ-0006](../spec/requirements/REQ-0006.md), [REQ-0007](../spec/requirements/REQ-0007.md)
- **US-0035** Platzhalter in Kopf- und Fußzeilen  `umgesetzt` → [REQ-0010](../spec/requirements/REQ-0010.md)
- **US-0036** Word-Vorlagen (DOCX) als Quelle  `ohne Requirements`

## E-Studio — Portal und Micro-Frontends (Studio)  ·  `geplant`

- **US-0023** Eine Portal-Shell für alle Module  `ohne Requirements`
- **US-0024** Moduloberflächen als Web Components  `ohne Requirements`
- **US-0025** Workbench-API über das Studio erreichen  `ohne Requirements`

## E-Workbench — Template-Entwicklung (Workbench)  ·  `geplant`

- **US-0009** Vorlage hochladen und sofort Feedback erhalten  `ohne Requirements`
- **US-0010** Vorlagen im Dashboard überblicken  `ohne Requirements`
- **US-0011** Mit Testdaten ausprobieren  `ohne Requirements`
- **US-0012** Sehen, welche Fälle meine Testdaten abdecken  `ohne Requirements`
- **US-0013** Textbausteine wie Vorlagen verwalten  `ohne Requirements`
- **US-0014** Vorlagen direkt in LibreOffice öffnen und speichern  `ohne Requirements`
- **US-0015** Vorlagen und Bausteine durchsuchen  `ohne Requirements`

## Rückwärtsindex: Requirement → Stories

| Requirement | Stories |
|---|---|
| [REQ-0001](../spec/requirements/REQ-0001.md) | US-0001 |
| [REQ-0002](../spec/requirements/REQ-0002.md) | US-0002 |
| [REQ-0003](../spec/requirements/REQ-0003.md) | US-0002 |
| [REQ-0005](../spec/requirements/REQ-0005.md) | US-0033 |
| [REQ-0006](../spec/requirements/REQ-0006.md) | US-0033 |
| [REQ-0007](../spec/requirements/REQ-0007.md) | US-0033 |
| [REQ-0008](../spec/requirements/REQ-0008.md) | US-0007 |
| [REQ-0009](../spec/requirements/REQ-0009.md) | US-0007 |
| [REQ-0010](../spec/requirements/REQ-0010.md) | US-0035 |
| [REQ-0011](../spec/requirements/REQ-0011.md) | US-0032 |
| [REQ-0012](../spec/requirements/REQ-0012.md) | US-0003 |

## Requirements ohne Story

- [REQ-0004](../spec/requirements/REQ-0004.md)

