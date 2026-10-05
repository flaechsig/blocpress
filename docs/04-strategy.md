---
arc42_status: PARTIAL
---

# Lösungsstrategie

## Grundansatz

| Ansatz | Kurz | Entscheidung |
|---|---|---|
| Kern als Bibliothek | `blocpress-core` füllt Vorlagen in reinem Java (odfdom, JEXL): Textbausteine, Bedingungen, Schleifen, Felder. Die Bibliothek liegt auf Maven Central und ist ohne die Dienste nutzbar. | [ADR-0004](09-decisions/ADR-0004.md) |
| Drei Quarkus-Dienste | render erzeugt Dokumente über REST; workbench verwaltet Vorlagen, Tests und Freigabe; studio ist die Oberfläche und bindet die Web Component der Workbench ein. Kein eigenes Modul für Prüfung und Administration; Benutzer und Rollen kommen aus einem externen Identity-Provider. | [ADR-0007](09-decisions/ADR-0007.md), [ADR-0003](09-decisions/ADR-0003.md), [ADR-0002](09-decisions/ADR-0002.md) |
| LibreOffice nur am Rand | Merge und Validierung brauchen kein LibreOffice. Erst die Ausgabe als PDF oder RTF startet je Konvertierung einen eigenen `soffice`-Prozess; render begrenzt die Zahl gleichzeitiger Prozesse. | [ADR-0010](09-decisions/ADR-0010.md), [ADR-0004](09-decisions/ADR-0004.md) |
| Zwei Datenbestände, Kopie bei Freigabe | Die Workbench arbeitet in der Datenbank `workbench`, render liest nur aus `production`. Nur freigegebene Vorlagen gelangen per REST nach `production`. | [US-0017](01-goals/stories/US-0017.md), [Domänenmodell](08-concepts/domaenenmodell.md) |
| Alles in PostgreSQL | Vorlagen, Soll-PDFs und Job-Ergebnisse als `bytea`; die Warteschlange für asynchrones Rendern ist eine Tabelle mit `SKIP LOCKED`. Kein Object Storage, kein Message Broker. | [ADR-0006](09-decisions/ADR-0006.md), [ADR-0012](09-decisions/ADR-0012.md) |
| Synchron als Standard | Ein Aufruf liefert das Dokument direkt; asynchrone Jobs mit Webhook sind die Ausnahme für große Läufe. | [ADR-0008](09-decisions/ADR-0008.md) |
| Suche getrennt vom Bestand | Elasticsearch indexiert Text, Felder und Bedingungen der Vorlagen; fällt es aus, laufen Upload und Freigabe weiter. | [ADR-0009](09-decisions/ADR-0009.md) |
| Bearbeiten im gewohnten Werkzeug | Gestalter öffnen und speichern Vorlagen und Bausteine per WebDAV direkt aus LibreOffice Writer. | [ADR-0011](09-decisions/ADR-0011.md) |
| Container und Native Images | Jeder Dienst als eigenes Image, zusätzlich als Native-Variante (`Dockerfile.native`); ein Quickstart-Image bündelt alles. | [ADR-0007](09-decisions/ADR-0007.md), [US-0026](01-goals/stories/US-0026.md), [US-0027](01-goals/stories/US-0027.md), [US-0028](01-goals/stories/US-0028.md) |

_(confidence: verified — blocpress-core/…/RenderEngine.java, LibreOfficeProcessor.java,
blocpress-render/…/LibreOfficePool.java, RenderJobWorker.java,
blocpress-studio/src/main/resources/application.properties (`workbench.url`),
Dockerfile und Dockerfile.native je Dienst, docker/studio/Dockerfile; derived_from:
arc42.adoc:331-468 legacy (git history))_

Gegenüber dem Altbestand entfallen: Self-Contained Systems mit fünf Modulen und eigenen
Schemata `proof` und `admin` ([ADR-0003](09-decisions/ADR-0003.md)), RBAC, API-Rate-Limiting,
OpenTelemetry, die Ansteuerung von LibreOffice über UNO bzw. JODConverter und
Kubernetes-Autoscaling als Teil der Strategie. Keines davon ist gebaut.

## Qualitätsziele

| Ziel | Wie die Strategie es erreicht |
|---|---|
| Verlässliche Ausgabe | Gleiche Vorlage und gleiche Daten ergeben dasselbe Dokument ([REQ-0025](01-goals/requirements/REQ-0025.md)); Regressionstests vergleichen gegen ein Soll-PDF ([US-0018](01-goals/stories/US-0018.md)). |
| Nur Freigegebenes in Produktion | Kopie bei Freigabe; render kennt keine Entwürfe ([US-0008](01-goals/stories/US-0008.md)). |
| Durchsatz von render | Parallele `soffice`-Prozesse je Instanz, mehr Last über weitere Instanzen; die Warteschlange verteilt Jobs über Instanzen hinweg ([render bemessen](guides/render-sizing.md)). |
| Schneller Start, wenig Speicher | Quarkus und Native Images. |
| Fehler früh sehen | Prüfung jeder Vorlage beim Hochladen ([US-0009](01-goals/stories/US-0009.md), [REQ-0026](01-goals/requirements/REQ-0026.md)). |

_(confidence: unverified — Rangfolge und Ziele aus dem Altbestand zusammengefasst, nicht mit
einer Person abgestimmt; derived_from: arc42.adoc:50-80 legacy (git history),
arc42.adoc:426-459 legacy (git history))_

- UNKNOWN — offene Frage: Gelten die Zielwerte des Altbestands noch (Dokument bis 20 Seiten in unter 5 s, Suche über 1.000 Vorlagen in unter 2 s)? Gemessen ist bisher nur der Durchsatz von render (docs/guides/render-sizing.md), nicht die Suche.
