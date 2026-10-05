# Ziele und Anforderungen

Kapitel 1 des Dokuments: warum es blocpress gibt ([Vision](vision.md)) und was es leisten
soll, heruntergebrochen in Epics, Stories und Requirements.

<!-- generated:status -->
| | Anzahl |
|---|---|
| Stories | ⚪ offen 6 · ✅ verifiziert 32 · ⛔ abgelöst 1 |
| Requirements | umgesetzt 11 · abgelöst 1 |
| [Entscheidungen](../09-decisions/) | vorgeschlagen 1 · angenommen 12 |

## [E-ADMINISTRATION](epics/E-ADMINISTRATION.md) — Rollen und Audit (über den Identity-Provider)

Status: ⚪ offen

| Story | Titel | Status |
|---|---|---|
| [US-0020](stories/US-0020.md) | Benutzer und Rollen verwalten | ⛔ abgelöst |
| [US-0021](stories/US-0021.md) | Rollen bei jedem API-Aufruf prüfen | ⚪ offen |
| [US-0022](stories/US-0022.md) | Workflow-Änderungen im Audit-Log festhalten | ⚪ offen |

## [E-AUSLIEFERUNG](epics/E-AUSLIEFERUNG.md) — Auslieferung (Docker, Quickstart, Native)

Status: 🟡 in Arbeit

| Story | Titel | Status |
|---|---|---|
| [US-0026](stories/US-0026.md) | Module als Docker-Images mit Healthcheck | ✅ verifiziert |
| [US-0027](stories/US-0027.md) | In Minuten ausprobieren (Quickstart) | ✅ verifiziert |
| [US-0028](stories/US-0028.md) | Native Images für schnellen Start | ✅ verifiziert |
| [US-0034](stories/US-0034.md) | render für die eigene Last richtig bemessen | ✅ verifiziert |
| [US-0037](stories/US-0037.md) | Worker-Zahl aus dem CPU-Kontingent ableiten | ⚪ offen |
| [US-0039](stories/US-0039.md) | blocpress mit docker-compose vollständig starten | ⚪ offen |

## [E-FORMATE](epics/E-FORMATE.md) — Formatkonvertierung (ODT → PDF/RTF)

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0003](stories/US-0003.md) | Dokument als PDF/RTF ausgeben | ✅ verifiziert |

## [E-FREIGABE](epics/E-FREIGABE.md) — Prüfung und Freigabe

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0016](stories/US-0016.md) | Vorlage einreichen, freigeben oder ablehnen | ✅ verifiziert |
| [US-0017](stories/US-0017.md) | Freigabe deployt automatisch nach Produktion | ✅ verifiziert |
| [US-0018](stories/US-0018.md) | Änderungen per Regressionstest absichern | ✅ verifiziert |
| [US-0019](stories/US-0019.md) | Vorlagen periodisch überprüfen (Compliance-Review) | ✅ verifiziert |

## [E-PERSISTENZ](epics/E-PERSISTENZ.md) — Template-Speicherung (workbench / production)

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0008](stories/US-0008.md) | Produktion nur aus freigegebenen Vorlagen | ✅ verifiziert |

## [E-RELEASE](epics/E-RELEASE.md) — Build-, Test- und Release-Automatisierung

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0029](stories/US-0029.md) | Jeder Push wird gebaut und getestet | ✅ verifiziert |
| [US-0030](stories/US-0030.md) | Release mit einem Befehl | ✅ verifiziert |
| [US-0031](stories/US-0031.md) | Zusammenspiel der Module Ende-zu-Ende prüfen | ✅ verifiziert |

## [E-RENDER-SERVICE](epics/E-RENDER-SERVICE.md) — Render-Service (REST-API, Auth, Jobs)

Status: 🟡 in Arbeit

| Story | Titel | Status |
|---|---|---|
| [US-0004](stories/US-0004.md) | Dokument synchron per REST rendern | ✅ verifiziert |
| [US-0005](stories/US-0005.md) | Freigegebene Vorlage per Name rendern (versioniert) | ✅ verifiziert |
| [US-0006](stories/US-0006.md) | Asynchron rendern über eine Job-Queue | ✅ verifiziert |
| [US-0007](stories/US-0007.md) | Render-API optional per JWT absichern | ✅ verifiziert |
| [US-0038](stories/US-0038.md) | Fehlerpfade und Dashboard von render testen | ⚪ offen |

## [E-RENDERING](epics/E-RENDERING.md) — Render-Pipeline (Vorlage + Daten → Dokument)

Status: 🟡 in Arbeit

| Story | Titel | Status |
|---|---|---|
| [US-0001](stories/US-0001.md) | Platzhalter automatisch aus JSON füllen | ✅ verifiziert |
| [US-0002](stories/US-0002.md) | Bedingungen und Listen in einer Vorlage | ✅ verifiziert |
| [US-0032](stories/US-0032.md) | Gemeinsame Textbausteine einbinden | ✅ verifiziert |
| [US-0033](stories/US-0033.md) | Zahlen und Daten im Sprachformat der Vorlage | ✅ verifiziert |
| [US-0035](stories/US-0035.md) | Platzhalter in Kopf- und Fußzeilen | ✅ verifiziert |
| [US-0036](stories/US-0036.md) | Word-Vorlagen (DOCX) als Quelle | ⚪ offen |

## [E-STUDIO](epics/E-STUDIO.md) — Portal und Micro-Frontends (Studio)

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0023](stories/US-0023.md) | Eine Portal-Shell für alle Module | ✅ verifiziert |
| [US-0024](stories/US-0024.md) | Moduloberflächen als Web Components | ✅ verifiziert |
| [US-0025](stories/US-0025.md) | Workbench-API über das Studio erreichen | ✅ verifiziert |

## [E-WORKBENCH](epics/E-WORKBENCH.md) — Template-Entwicklung (Workbench)

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0009](stories/US-0009.md) | Vorlage hochladen und sofort Feedback erhalten | ✅ verifiziert |
| [US-0010](stories/US-0010.md) | Vorlagen im Dashboard überblicken | ✅ verifiziert |
| [US-0011](stories/US-0011.md) | Mit Testdaten ausprobieren | ✅ verifiziert |
| [US-0012](stories/US-0012.md) | Sehen, welche Fälle meine Testdaten abdecken | ✅ verifiziert |
| [US-0013](stories/US-0013.md) | Textbausteine wie Vorlagen verwalten | ✅ verifiziert |
| [US-0014](stories/US-0014.md) | Vorlagen direkt in LibreOffice öffnen und speichern | ✅ verifiziert |
| [US-0015](stories/US-0015.md) | Vorlagen und Bausteine durchsuchen | ✅ verifiziert |

## Offene Fragen

- [03-context.md](../03-context.md): UNKNOWN — offene Frage: Lädt render zur Laufzeit Textbausteine über die WebDAV-Adresse der Workbench nach, wenn eine freigegebene Vorlage auf sie verweist? Der Altbestand sagt ja; im Code löst blocpress-core ohne gesetzte System-Property blocpress.mode den Verweis (xlink:href) aus der Vorlage selbst auf, die Abhängigkeit render → workbench entstünde also nur über den Inhalt der Vorlage.
- [03-context.md](../03-context.md): UNKNOWN — offene Frage: Wie kommt ein Benutzer in einer produktiven Installation an sein Token? Das Studio kennt nur das Eingabefeld, keinen Anmeldeablauf gegen den Identity-Provider.
- [08-concepts/domaenenmodell.md](../08-concepts/domaenenmodell.md): UNKNOWN — offene Frage: Wer legt die Tabellen in einer produktiven Installation an? Beide Dienste prüfen das Schema nur (Hibernate validate, die Workbench ergänzt im Profil dev), und die SQL-Skripte unter docker/ für docker-compose.yml enthalten weder valid_until, review_cycle_years, ignored_patterns noch die Tabelle render_job; vollständig ist nur docker/studio/init-studio.sql.
- [08-concepts/versionierung.md](../08-concepts/versionierung.md): UNKNOWN — offene Frage: Ist die unterschiedliche Auswahl in Workbench und render gewollt, und soll das Zurückziehen einer Version wirklich alle Versionen dieses Namens aus production entfernen?
- [09-decisions/ADR-0006.md](../09-decisions/ADR-0006.md): UNKNOWN — offene Frage: Gilt die Grenze von 5.000 Dokumenten noch, und wurde sie je gemessen?
- [09-decisions/ADR-0007.md](../09-decisions/ADR-0007.md): UNKNOWN — offene Frage: Die Startzeiten aus dem Altbestand (unter 1 s gegenüber 5–10 s) sind nicht gemessen; gibt es Messwerte?
- [09-decisions/ADR-0008.md](../09-decisions/ADR-0008.md): UNKNOWN — offene Frage: Soll der Render-Service selbst ein Zeitlimit für synchrone Aufrufe bzw. für soffice erhalten?
- [09-decisions/ADR-0012.md](../09-decisions/ADR-0012.md): UNKNOWN — offene Frage: Die Init-Skripte für docker-compose legen render_job nicht an (nur das Quickstart-Skript docker/studio/init-studio.sql); wie entsteht die Tabelle dort, wenn Hibernate nur validiert?

## Offene Entscheidungen

- [ADR-0013](../09-decisions/ADR-0013.md) — REST-API von render API-first aus openapi.yml

## Widersprüche

_keine_

## Kapitel ohne Inhalt

- 04-strategy
- 06-runtime
- 07-deployment
- 10-quality
- 11-risks
- 12-glossary

## Teilweise gefüllte Kapitel

_keine_
<!-- /generated -->
