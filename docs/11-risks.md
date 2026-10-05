# Risiken und technische Schulden

## Risiken

| ID | Risiko | Folge | Maßnahme heute | Quelle |
|---|---|---|---|---|
| R-1 | **Speicher und CPU von `soffice`.** Jede Konvertierung startet einen eigenen LibreOffice-Prozess mit rund 150 MiB; zu knappe Limits führen zu Drosselung oder zum Abbruch des Containers (OOMKilled). | Langsame oder abgebrochene Renders | Worker-Zahl über `BLOCPRESS_LO_WORKERS` begrenzen, nach Messung bemessen ([render bemessen](guides/render-sizing.md)); die Worker-Zahl aus dem CPU-Kontingent abzuleiten ist offen ([US-0037](01-goals/stories/US-0037.md)) | [ADR-0010](09-decisions/ADR-0010.md) |
| R-2 | **`bytea` bei wachsendem Bestand.** Vorlagen und Soll-PDFs liegen in PostgreSQL; bei deutlich mehr als 5.000 Dokumenten können Abfragen und Backups langsam werden. | Langsamere Abfragen, große Backups | keine; Entscheidung bei Wachstum neu prüfen | [ADR-0006](09-decisions/ADR-0006.md) |
| R-3 | **LibreOffice-Version nicht gepinnt.** Sie kommt aus den Paketen von Ubuntu 24.04; ein neues Basis-Image kann Umbruch und Layout ändern. Gleiches gilt für poppler-utils, auf denen der Regressionsvergleich beruht. | Regressionstests schlagen ohne Änderung an der Vorlage fehl; Ausgabe in Produktion ändert sich unbemerkt | Regressionstests je Vorlage ([US-0018](01-goals/stories/US-0018.md)); Determinismus als Anforderung [REQ-0025](01-goals/requirements/REQ-0025.md) (vorgeschlagen) | [ADR-0010](09-decisions/ADR-0010.md) |
| R-4 | **Kein Zeitlimit für `soffice`.** render wartet ohne Timeout auf den Prozess, und der Platz im `LibreOfficePool` wird ohne Timeout belegt. Ein hängender Prozess blockiert einen Worker dauerhaft; synchrone und asynchrone Aufrufe teilen sich die Plätze. | Bei hängenden Prozessen steht render still | nur die Workbench bricht ihre Aufrufe an render nach 60 s ab | [ADR-0008](09-decisions/ADR-0008.md) |
| R-5 | **Suchindex verzögert und nicht wiederherstellbar.** Ein neuer Stand ist erst nach dem nächsten Index-Refresh auffindbar. Was bei ausgefallenem Elasticsearch gespeichert wurde, fehlt dauerhaft in der Suche, weil es keinen Neuaufbau des Index gibt. | Gestalter finden Vorlagen nicht | keine | [ADR-0009](09-decisions/ADR-0009.md) |
| R-6 | **Viele Reviews gleichzeitig fällig.** Fällige Vorlagen erscheinen nur in einer Liste (Vorlauf 60 Tage, [REQ-0021](01-goals/requirements/REQ-0021.md)); niemand wird aktiv benachrichtigt, und mit Ablauf rendert render die Vorlage nicht mehr ([REQ-0022](01-goals/requirements/REQ-0022.md)). | Abgelaufene Vorlagen fallen in Produktion aus | Liste der fälligen Reviews; aktive Meldung ist offen ([US-0040](01-goals/stories/US-0040.md)) | [US-0019](01-goals/stories/US-0019.md) |
| R-7 | **Bausteine zur Renderzeit über die Workbench.** Zeigt ein verknüpfter Abschnitt auf `/api/webdav/released/`, lädt render ihn beim Rendern von der Workbench. | Ist die Workbench nicht erreichbar, scheitert das Rendern in Produktion | keine | [ADR-0011](09-decisions/ADR-0011.md) |
| R-8 | **Gleichzeitiges Bearbeiten per WebDAV.** Ohne LOCK gewinnt der letzte PUT. | Änderungen eines Gestalters gehen verloren | hingenommen, ein Bearbeiter je Entwurf | [ADR-0011](09-decisions/ADR-0011.md) |
| R-9 | **Workbench ohne serverseitige Authentifizierung.** Die Workbench prüft kein Token; Freigabe, Ablehnung und Statuswechsel sind für jeden möglich, der sie erreicht. Die Übergabe an render (`/api/render/templates/import`) ist ebenfalls ohne Authentifizierung. | Unbefugte Freigabe oder Änderung produktiver Vorlagen | Betrieb im geschützten Netz; Rollenprüfung ist offen ([US-0021](01-goals/stories/US-0021.md)) | [ADR-0003](09-decisions/ADR-0003.md) |
| R-10 | **Webhooks ohne Wiederholung.** render schickt die Benachrichtigung zu einem Job einmal und wartet nicht auf die Antwort. | Aufrufer verpassen das Ende eines Jobs | Status lässt sich per `GET /api/render/jobs/{id}` abfragen | [ADR-0012](09-decisions/ADR-0012.md), [Kontext](03-context.md) |

_(confidence: verified — blocpress-core/…/LibreOfficeProcessor.java (`soffice` aus dem
`PATH`, `waitFor()` ohne Timeout), blocpress-render/…/LibreOfficePool.java
(`Semaphore.acquire()` ohne Timeout), WebhookSender.java, blocpress-workbench (keine
Token-Prüfung, keine `@RolesAllowed`), blocpress-render/Dockerfile, blocpress-workbench/Dockerfile,
docs/guides/render-sizing.md; derived_from: docs/legacy/specification/arc42.adoc:2633-2684)_

Gegenüber dem Altbestand korrigiert: R-1 sprach von Speicherlecks einer lange laufenden
LibreOffice-Instanz und von deren Neustart nach X Generierungen. Eine solche Instanz gibt es
nicht mehr; jede Konvertierung ist ein eigener Prozess. R-3 empfahl, die LibreOffice-Version
im Dockerfile zu fixieren; das ist nicht geschehen. R-5 nannte einen Hinweis in der
Oberfläche und einen Refresh-Knopf als Maßnahme; beides ist nicht belegt und hier nicht
übernommen. R-6 nannte eine Frühwarnung 30 Tage im Voraus, Priorisierung und Eskalation;
gebaut ist nur die Liste mit 60 Tagen Vorlauf. Entfallen ist R-7 des Altbestands
(Änderungen der UNO-API): blocpress ruft `soffice` über die Kommandozeile auf, nicht über UNO.

## Technische Schulden

| ID | Schuld | Folge | Quelle |
|---|---|---|---|
| TD-1 | Die SQL-Skripte für `docker-compose.yml` (`docker/01-init.sql`, `docker/02-init-production.sh`) sind veraltet: Es fehlen Spalten und die Tabelle `render_job`. Vollständig ist nur `docker/studio/init-studio.sql`. | render und workbench scheitern mit docker-compose an der Schemaprüfung | [US-0039](01-goals/stories/US-0039.md), [ADR-0012](09-decisions/ADR-0012.md) |
| TD-2 | Die aus `openapi.yml` erzeugten Interfaces von render werden nicht genutzt. | Spezifikation und Umsetzung der REST-API können auseinanderlaufen | [ADR-0013](09-decisions/ADR-0013.md) |
| TD-3 | Workbench und render wählen unter einem Vorlagennamen verschiedene Versionen (höchste Version gegenüber jüngstem `validFrom`). | Vorschau und Produktion können verschiedene Stände zeigen | [Versionierung](08-concepts/versionierung.md) |
| TD-4 | Die Prüfung der Vorlagen beim Hochladen liegt in der Workbench (`TemplateValidator`), nicht in `blocpress-core`. | Nutzer der Bibliothek können Vorlagen nicht vorab prüfen | [US-0009](01-goals/stories/US-0009.md) |
| TD-5 | Native-Builds brauchen gepflegte Reflection-Registrierung und Build-Argumente. | Neue Abhängigkeiten können das Native-Image brechen | [ADR-0007](09-decisions/ADR-0007.md) |
| TD-6 | Kein Batch-Endpunkt: Viele Dokumente bedeuten viele Aufrufe oder viele Jobs. | Mehr Aufrufe und mehr Verwaltungsaufwand beim Aufrufer | Altbestand TD-2 |

_(confidence: verified — docker/01-init.sql, docker/02-init-production.sh,
docker/studio/init-studio.sql, blocpress-workbench/…/service/TemplateValidator.java,
blocpress-render/…/RenderResource.java und AsyncRenderResource.java (kein Batch-Endpunkt);
derived_from: docs/legacy/specification/arc42.adoc:2686-2740)_

Aus dem Altbestand erledigt: TD-1 (Cache für Vorlagen, heute `TemplateCache` in render),
TD-5 (PDF-Unterschiede markiert, [US-0018](01-goals/stories/US-0018.md)), TD-6 (Vorschau in
der Oberfläche, [US-0011](01-goals/stories/US-0011.md)), TD-7 (Bausteine werden versioniert wie Vorlagen,
[US-0013](01-goals/stories/US-0013.md)). Nicht mehr zutreffend: TD-3 (fester
LibreOffice-Pfad; `soffice` wird über den `PATH` gefunden) und TD-4 (WebSocket für den
Job-Status; asynchrone Jobs melden ihr Ende per Webhook).
