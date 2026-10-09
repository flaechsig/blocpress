
Kapitel 1 des Dokuments: warum es blocpress gibt ([Vision](vision.md)) und was es leisten
soll, heruntergebrochen in Epics, Stories und Requirements.

<!-- generated:status -->
| | Anzahl |
|---|---|
| Stories | ⚪ offen 15 · 🟡 in Arbeit 1 · ✅ verifiziert 45 · ⛔ abgelöst 2 |
| Requirements | vorgeschlagen 12 · geplant 4 · umgesetzt 40 · abgelöst 1 |
| [Entscheidungen](../09-decisions/) | vorgeschlagen 4 · angenommen 14 · abgelöst 1 |

## [E-ADMINISTRATION](epics/E-ADMINISTRATION.md) — Rollen und Audit (über den Identity-Provider)

Status: ⚪ offen

| Story | Titel | Status |
|---|---|---|
| [US-0020](stories/US-0020.md) | Benutzer und Rollen verwalten | ⛔ abgelöst |
| [US-0021](stories/US-0021.md) | Rollen bei jedem API-Aufruf prüfen | ⚪ offen |
| [US-0022](stories/US-0022.md) | Workflow-Änderungen im Audit-Log festhalten | ⚪ offen |
| [US-0044](stories/US-0044.md) | Absicherung durchgängig | ⚪ offen |

## [E-AUSLIEFERUNG](epics/E-AUSLIEFERUNG.md) — Auslieferung (Docker, Quickstart, Native)

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0026](stories/US-0026.md) | Module als Docker-Images mit Healthcheck | ✅ verifiziert |
| [US-0027](stories/US-0027.md) | In Minuten ausprobieren (Quickstart) | ✅ verifiziert |
| [US-0028](stories/US-0028.md) | Native Images für schnellen Start | ✅ verifiziert |
| [US-0034](stories/US-0034.md) | render für die eigene Last richtig bemessen | ✅ verifiziert |
| [US-0037](stories/US-0037.md) | Worker-Zahl aus dem CPU-Kontingent ableiten | ✅ verifiziert |
| [US-0039](stories/US-0039.md) | blocpress mit docker-compose vollständig starten | ⛔ abgelöst |
| [US-0046](stories/US-0046.md) | Quickstart-Image betriebstauglich machen | ✅ verifiziert |
| [US-0051](stories/US-0051.md) | blocpress in Kubernetes betreiben (Tutorial und Manifeste) | ✅ verifiziert |

## [E-FORMATE](epics/E-FORMATE.md) — Formatkonvertierung (ODT → PDF/RTF)

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0003](stories/US-0003.md) | Dokument als PDF/RTF ausgeben | ✅ verifiziert |

## [E-FREIGABE](epics/E-FREIGABE.md) — Prüfung und Freigabe

Status: 🟡 in Arbeit

| Story | Titel | Status |
|---|---|---|
| [US-0016](stories/US-0016.md) | Vorlage einreichen, freigeben oder ablehnen | ✅ verifiziert |
| [US-0017](stories/US-0017.md) | Freigabe deployt automatisch nach Produktion | ✅ verifiziert |
| [US-0018](stories/US-0018.md) | Änderungen per Regressionstest absichern | ✅ verifiziert |
| [US-0019](stories/US-0019.md) | Vorlagen periodisch überprüfen (Compliance-Review) | ✅ verifiziert |
| [US-0040](stories/US-0040.md) | Fällige Reviews aktiv melden | ⚪ offen |
| [US-0042](stories/US-0042.md) | Ausmustern und Zurückziehen zuverlässig machen | ⚪ offen |
| [US-0050](stories/US-0050.md) | Freigabe nur mit bestandenen Regressionstests | ⚪ offen |
| [US-0053](stories/US-0053.md) | Diff-PDF aus der Oberfläche abrufen | ⚪ offen |
| [US-0063](stories/US-0063.md) | Freigabe berücksichtigt Bausteine | ⚪ offen |

## [E-PERSISTENZ](epics/E-PERSISTENZ.md) — Template-Speicherung (workbench / production)

Status: 🟡 in Arbeit

| Story | Titel | Status |
|---|---|---|
| [US-0008](stories/US-0008.md) | Produktion nur aus freigegebenen Vorlagen | ✅ verifiziert |
| [US-0052](stories/US-0052.md) | Dienste legen ihr Datenbankschema selbst an | ✅ verifiziert |
| [US-0061](stories/US-0061.md) | Ein Name, eine Zeitachse | ⚪ offen |

## [E-RELEASE](epics/E-RELEASE.md) — Build-, Test- und Release-Automatisierung

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0029](stories/US-0029.md) | Jeder Push wird gebaut und getestet | ✅ verifiziert |
| [US-0030](stories/US-0030.md) | Release mit einem Befehl | ✅ verifiziert |
| [US-0031](stories/US-0031.md) | Zusammenspiel der Module Ende-zu-Ende prüfen | ✅ verifiziert |
| [US-0047](stories/US-0047.md) | Release-Reihenfolge und Veröffentlichung absichern | ✅ verifiziert |
| [US-0049](stories/US-0049.md) | Ungenutzten Code und Dateien entfernen | ✅ verifiziert |

## [E-RENDER-SERVICE](epics/E-RENDER-SERVICE.md) — Render-Service (REST-API, Auth, Jobs)

Status: 🟡 in Arbeit

| Story | Titel | Status |
|---|---|---|
| [US-0004](stories/US-0004.md) | Dokument synchron per REST rendern | ✅ verifiziert |
| [US-0005](stories/US-0005.md) | Freigegebene Vorlage per Name rendern (versioniert) | ✅ verifiziert |
| [US-0006](stories/US-0006.md) | Asynchron rendern über eine Job-Queue | ✅ verifiziert |
| [US-0007](stories/US-0007.md) | Render-API optional per JWT absichern | ✅ verifiziert |
| [US-0038](stories/US-0038.md) | Fehlerpfade und Dashboard von render testen | ⚪ offen |
| [US-0041](stories/US-0041.md) | Abgelaufene und ausgemusterte Vorlagen sofort sperren | ⚪ offen |
| [US-0045](stories/US-0045.md) | Asynchrone Aufträge und Dashboard robust machen | ⚪ offen |

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

## [E-SECURITY](epics/E-SECURITY.md) — Sicherheit

Status: 🟡 in Arbeit

| Story | Titel | Status |
|---|---|---|
| [US-0054](stories/US-0054.md) | Vorlagen-Import in render nur authentifiziert annehmen | ⚪ offen |
| [US-0055](stories/US-0055.md) | CORS in Workbench und render auf bekannte Origins beschränken | ✅ verifiziert |
| [US-0056](stories/US-0056.md) | Quarkus-Plattform auf einen Stand ohne bekannte Lücken heben | ✅ verifiziert |
| [US-0057](stories/US-0057.md) | Quickstart ohne öffentlich bekannten JWT-Schlüssel | ✅ verifiziert |
| [US-0058](stories/US-0058.md) | Container ohne root-Rechte betreiben | ✅ verifiziert |
| [US-0059](stories/US-0059.md) | Actions im Release-Workflow auf Commit-SHA pinnen | ✅ verifiziert |
| [US-0060](stories/US-0060.md) | Bausteine nur aus der eigenen Bibliothek | ✅ verifiziert |

## [E-STUDIO](epics/E-STUDIO.md) — Portal und Micro-Frontends (Studio)

Status: ✅ verifiziert

| Story | Titel | Status |
|---|---|---|
| [US-0023](stories/US-0023.md) | Eine Portal-Shell für alle Module | ✅ verifiziert |
| [US-0024](stories/US-0024.md) | Moduloberflächen als Web Components | ✅ verifiziert |
| [US-0025](stories/US-0025.md) | Workbench-API über das Studio erreichen | ✅ verifiziert |

## [E-WORKBENCH](epics/E-WORKBENCH.md) — Template-Entwicklung (Workbench)

Status: 🟡 in Arbeit

| Story | Titel | Status |
|---|---|---|
| [US-0009](stories/US-0009.md) | Vorlage hochladen und sofort Feedback erhalten | ✅ verifiziert |
| [US-0010](stories/US-0010.md) | Vorlagen im Dashboard überblicken | ✅ verifiziert |
| [US-0011](stories/US-0011.md) | Mit Testdaten ausprobieren | ✅ verifiziert |
| [US-0012](stories/US-0012.md) | Sehen, welche Fälle meine Testdaten abdecken | ✅ verifiziert |
| [US-0013](stories/US-0013.md) | Textbausteine wie Vorlagen verwalten | ✅ verifiziert |
| [US-0014](stories/US-0014.md) | Vorlagen direkt in LibreOffice öffnen und speichern | ✅ verifiziert |
| [US-0015](stories/US-0015.md) | Vorlagen und Bausteine durchsuchen | ✅ verifiziert |
| [US-0043](stories/US-0043.md) | Suchindex vollständig nachführen | ✅ verifiziert |
| [US-0048](stories/US-0048.md) | Workbench-Abläufe konsistent machen | ⚪ offen |
| [US-0062](stories/US-0062.md) | Entwerfen mit Bausteinen | 🟡 in Arbeit |

## Offene Fragen

- [01-goals/epics/E-WORKBENCH.md](epics/E-WORKBENCH.md): UNKNOWN — offene Frage: Die Stories dieses Epics und von E-PERSISTENZ sind über Tests belegt, haben aber keine Requirements. Sollen sie wie E-FREIGABE Requirements bekommen? Dafür bekämen die Tests einen Anzeigenamen mit der Requirement-ID.
- [01-goals/stories/US-0040.md](stories/US-0040.md): UNKNOWN — offene Frage: Über welchen Weg soll gemeldet werden (E-Mail, Webhook, Anzeige in der Workbench), und an wen?
- [03-context.md](../03-context.md): UNKNOWN — offene Frage: Lädt render zur Laufzeit Textbausteine über die WebDAV-Adresse der Workbench nach, wenn eine freigegebene Vorlage auf sie verweist? Der Altbestand sagt ja; im Code löst blocpress-core ohne gesetzte System-Property blocpress.mode den Verweis (xlink:href) aus der Vorlage selbst auf, die Abhängigkeit render → workbench entstünde also nur über den Inhalt der Vorlage.
- [03-context.md](../03-context.md): UNKNOWN — offene Frage: Wie kommt ein Benutzer in einer produktiven Installation an sein Token? Das Studio kennt nur das Eingabefeld, keinen Anmeldeablauf gegen den Identity-Provider.
- [04-strategy.md](../04-strategy.md): UNKNOWN — offene Frage: Gelten die Zielwerte des Altbestands noch (Dokument bis 20 Seiten in unter 5 s, Suche über 1.000 Vorlagen in unter 2 s)? Gemessen ist bisher nur der Durchsatz von render (docs/guides/render-sizing.md), nicht die Suche.
- [05-building-blocks/studio.md](../05-building-blocks/studio.md): UNKNOWN — offene Frage: Soll das Studio das Token bei allen Aufrufen der Workbench mitsenden, solange die Workbench es nicht prüft, oder entfällt das Eingabefeld, bis eine Anmeldung gegen den Identity-Provider existiert?
- [06-runtime/approval-and-deployment.md](../06-runtime/approval-and-deployment.md): UNKNOWN — offene Frage: Soll das Zurückziehen wie die Freigabe fehlschlagen (503), wenn render die Vorlage nicht entfernen kann, und soll es nur die zurückgezogene Version statt aller Versionen des Namens entfernen?
- [06-runtime/dashboard.md](../06-runtime/dashboard.md): UNKNOWN — offene Frage: Sollen ältere Versionen eines Namens im Dashboard sichtbar sein, und sollen die Aktionen der produktiven Version auch erreichbar sein, wenn eine neuere Version in Arbeit ist?
- [06-runtime/render-by-name.md](../06-runtime/render-by-name.md): UNKNOWN — offene Frage: Soll ein abgelaufenes oder zurückgezogenes Template bis zu 10 Minuten (und in weiteren render-Instanzen) noch gerendert werden dürfen, oder muss der Cache das Ablaufdatum beachten?
- [08-concepts/versionierung.md](../08-concepts/versionierung.md): UNKNOWN — offene Frage: Ist die unterschiedliche Auswahl in Workbench und render gewollt, und soll das Zurückziehen einer Version wirklich alle Versionen dieses Namens aus production entfernen?
- [09-decisions/ADR-0008.md](../09-decisions/ADR-0008.md): UNKNOWN — offene Frage: Soll der Render-Service selbst ein Zeitlimit für synchrone Aufrufe bzw. für soffice erhalten?
- [09-decisions/ADR-0016.md](../09-decisions/ADR-0016.md): UNKNOWN — offene Frage: Bekommen die Endpunkte mit Release 3.0 neue Namen (`POST /api/render` statt `/api/render/template`, `/{name}` nach `/templates/{name}`)? Heute ist eine Vorlage namens „template“ per Namen nicht erreichbar.
- [09-decisions/ADR-0018.md](../09-decisions/ADR-0018.md): UNKNOWN — offene Frage: Was geschieht mit Vorlagen in `production`, deren Verknüpfungen heute nicht auf `/bausteine/{name}.odt` passen (etwa direkte Links auf fremde Server) — bei der Migration abweisen oder erst beim Rendern?

## Offene Entscheidungen

- [ADR-0013](../09-decisions/ADR-0013.md) — REST-API von render API-first aus openapi.yml
- [ADR-0016](../09-decisions/ADR-0016.md) — render in zwei Betriebsarten — Engine und voll
- [ADR-0017](../09-decisions/ADR-0017.md) — Alle fachlichen Aufrufe sind authentifiziert
- [ADR-0018](../09-decisions/ADR-0018.md) — Bausteine werden zur Renderzeit aus der Produktion aufgelöst

## Widersprüche

_keine_

## Kapitel ohne Inhalt

_keine_

## Teilweise gefüllte Kapitel

- 04-strategy
<!-- /generated -->
