# Risiken und technische Schulden

Je Risiko, Sicherheitsrisiko (`SEC-`) und technischer Schuld (`TD-`) eine Datei; die Übersicht
unten erzeugt docspine. Welche Story an einem Eintrag arbeitet, steht in der Story (`addresses`)
und wird am Eintrag angezeigt.

_(confidence: verified — blocpress-core/…/LibreOfficeProcessor.java (`soffice` aus dem
`PATH`, `waitFor()` ohne Timeout), blocpress-render/…/LibreOfficePool.java
(`Semaphore.acquire()` ohne Timeout), WebhookSender.java, blocpress-workbench (keine
Token-Prüfung, keine `@RolesAllowed`), blocpress-render/Dockerfile, blocpress-workbench/Dockerfile,
docs/guides/render-sizing.md, blocpress-workbench/…/service/TemplateValidator.java,
blocpress-render/…/RenderResource.java und AsyncRenderResource.java (kein Batch-Endpunkt);
derived_from: arc42.adoc:2633-2740 legacy (git history))_

Die Sicherheitsrisiken stammen aus dem Security-Test vom 2026-10-07 (secspine: Semgrep, Trivy,
gitleaks, Analyse von Code und Konfiguration; keine laufende Instanz getestet). Geprüft, kein
Risiko: Die `ProcessBuilder`-Aufrufe für `soffice` und ImageMagick bekommen keine
Nutzereingaben; der XML-Parser des PDF-Vergleichs liest nur die Ausgabe von `pdftohtml`; die
Jackson-Lücken zur Codeausführung setzen Default-Typing voraus, das blocpress nicht einschaltet.
Der Security-Test vom 2026-10-09 lief zusätzlich gegen lokal gestartete Container
(ZAP Baseline passiv, Nuclei ohne eingreifende Vorlagen, Trivy auf die Images); daraus
stammt SEC-0007.

Gegenüber dem Altbestand korrigiert: R-0001 sprach von Speicherlecks einer lange laufenden
LibreOffice-Instanz und von deren Neustart nach X Generierungen. Eine solche Instanz gibt es
nicht mehr; jede Konvertierung ist ein eigener Prozess. R-0003 empfahl, die LibreOffice-Version
im Dockerfile zu fixieren; das ist nicht geschehen. R-0005 nannte einen Hinweis in der
Oberfläche und einen Refresh-Knopf als Maßnahme; beides ist nicht belegt und hier nicht
übernommen. R-0006 nannte eine Frühwarnung 30 Tage im Voraus, Priorisierung und Eskalation;
gebaut ist nur die Liste mit 60 Tagen Vorlauf. Entfallen ist R-7 des Altbestands
(Änderungen der UNO-API): blocpress ruft `soffice` über die Kommandozeile auf, nicht über UNO.

Erledigt am 2026-10-06: TD-1 (wer in einer produktiven Installation die Tabellen anlegt),
seit die Dienste ihr Schema mit Liquibase selbst anlegen
([ADR-0015](../09-decisions/ADR-0015.md)). Aus dem Altbestand erledigt: TD-1 (Cache für
Vorlagen, heute `TemplateCache` in render), TD-5 (PDF-Unterschiede markiert,
[US-0018](../01-goals/stories/US-0018.md)), TD-6 (Vorschau in der Oberfläche,
[US-0011](../01-goals/stories/US-0011.md)), TD-7 (Bausteine werden versioniert wie Vorlagen,
[US-0013](../01-goals/stories/US-0013.md)). Nicht mehr zutreffend: TD-3 (fester
LibreOffice-Pfad; `soffice` wird über den `PATH` gefunden) und TD-4 (WebSocket für den
Job-Status; asynchrone Jobs melden ihr Ende per Webhook).

<!-- generated:risks -->
## Architekturrisiken

| ID | Titel | Status | Schwere | Stories |
|---|---|---|---|---|
| [R-0001](R-0001.md) | Speicher und CPU von soffice | behoben | — | [US-0037](../01-goals/stories/US-0037.md), [US-0071](../01-goals/stories/US-0071.md), [US-0072](../01-goals/stories/US-0072.md) |
| [R-0002](R-0002.md) | bytea bei wachsendem Bestand | hingenommen | — | — |
| [R-0003](R-0003.md) | LibreOffice-Version nicht gepinnt | offen | — | [US-0018](../01-goals/stories/US-0018.md) |
| [R-0004](R-0004.md) | Kein Zeitlimit für soffice | behoben | — | [US-0072](../01-goals/stories/US-0072.md) |
| [R-0005](R-0005.md) | Suchindex verzögert und nicht wiederherstellbar | offen | — | — |
| [R-0006](R-0006.md) | Viele Reviews gleichzeitig fällig | offen | — | [US-0040](../01-goals/stories/US-0040.md) |
| [R-0007](R-0007.md) | Bausteine zur Renderzeit über die Workbench | behoben | — | [US-0060](../01-goals/stories/US-0060.md) |
| [R-0008](R-0008.md) | Gleichzeitiges Bearbeiten per WebDAV | hingenommen | — | — |
| [R-0009](R-0009.md) | Workbench und Import ohne Authentifizierung | abgelöst | mittel | — |
| [R-0010](R-0010.md) | Webhooks ohne Wiederholung | offen | — | — |

## Security-Risiken

| ID | Titel | Status | Schwere | Stories |
|---|---|---|---|---|
| [SEC-0001](SEC-0001.md) | CORS von jeder Origin | behoben | mittel | [US-0055](../01-goals/stories/US-0055.md) |
| [SEC-0002](SEC-0002.md) | Abhängigkeiten mit bekannten Lücken | behoben | mittel | [US-0056](../01-goals/stories/US-0056.md) |
| [SEC-0003](SEC-0003.md) | Öffentlicher JWT-Schlüssel im Quickstart | behoben | mittel | [US-0057](../01-goals/stories/US-0057.md) |
| [SEC-0004](SEC-0004.md) | Container als root | behoben | niedrig | [US-0058](../01-goals/stories/US-0058.md) |
| [SEC-0005](SEC-0005.md) | Actions über veränderliche Tags | behoben | niedrig | [US-0059](../01-goals/stories/US-0059.md) |
| [SEC-0006](SEC-0006.md) | Eingebundene Abschnitte laden beliebige Adressen | behoben | mittel | [US-0060](../01-goals/stories/US-0060.md) |
| [SEC-0007](SEC-0007.md) | Oberflächen ohne Security-Header | behoben | niedrig | [US-0064](../01-goals/stories/US-0064.md) |
| [SEC-0008](SEC-0008.md) | Workbench und Import ohne Authentifizierung | hingenommen | mittel | [US-0021](../01-goals/stories/US-0021.md), [US-0054](../01-goals/stories/US-0054.md), [US-0066](../01-goals/stories/US-0066.md), [US-0067](../01-goals/stories/US-0067.md), [US-0069](../01-goals/stories/US-0069.md), [US-0070](../01-goals/stories/US-0070.md) |

## Technische Schulden

| ID | Titel | Status | Schwere | Stories |
|---|---|---|---|---|
| [TD-0002](TD-0002.md) | Generierte OpenAPI-Interfaces ungenutzt | offen | — | [US-0073](../01-goals/stories/US-0073.md) |
| [TD-0003](TD-0003.md) | Verschiedene Versionswahl in Workbench und render | behoben | — | [US-0061](../01-goals/stories/US-0061.md) |
| [TD-0004](TD-0004.md) | Vorlagenprüfung nicht im Kern | offen | — | — |
| [TD-0005](TD-0005.md) | Pflege der Native-Builds | offen | — | — |
| [TD-0006](TD-0006.md) | Kein Batch-Endpunkt | offen | — | — |
<!-- /generated -->
