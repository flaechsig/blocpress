# Randbedingungen

## Entwicklung und Laufzeit

| Was | Version | Wofür |
|---|---|---|
| Java | 21 oder neuer | Sprache aller Module |
| Maven | 3.9 oder neuer | Build |
| Docker | aktuell | Integrationstests, Container-Image von blocpress-render, `@QuarkusTest`s in render (PostgreSQL über Quarkus DevServices) |
| LibreOffice | 24 oder neuer, `soffice` im `PATH` | PDF- und RTF-Konvertierung zur Laufzeit in blocpress-render und in Tests |
| Python | 3.9 oder neuer | docspine-Prüfung |

_(confidence: unverified — übernommen aus der bisherigen `CLAUDE.md`, Versionen nicht einzeln
gegen Build und Laufzeit geprüft)_

Ohne LibreOffice wird `TransformTest` in core übersprungen; dann fehlt der Nachweis für
REQ-0012, und die Prüfung meldet ihn. Auf solchen Rechnern prüft
`python3 .docspine/docspine.pyz check --without-tests` nur die Dokumentation.

## Plattform

| Was | Version | Wofür |
|---|---|---|
| Quarkus | 3.27.2 (`quarkus.platform.version`) | Framework von render, workbench und studio ([ADR-0007](09-decisions/ADR-0007.md)); `blocpress-core` hängt nicht von Quarkus ab |
| PostgreSQL | 18 oder neuer | Datenbanken `workbench` und `production`, Binärdaten als `bytea` ([ADR-0006](09-decisions/ADR-0006.md)), Job-Warteschlange ([ADR-0012](09-decisions/ADR-0012.md)) |
| Elasticsearch | 8.x, eingesetzt 8.11.0 | Suche der Workbench über `quarkus-elasticsearch-rest-client` ([ADR-0009](09-decisions/ADR-0009.md)) |
| Docker | aktuell | Betrieb: jeder Dienst als eigenes Image auf Basis Ubuntu 24.04, dazu ein Quickstart-Image mit allen Diensten, PostgreSQL und Elasticsearch ([US-0026](01-goals/stories/US-0026.md), [US-0027](01-goals/stories/US-0027.md)) |
| poppler-utils, ImageMagick | aus Ubuntu 24.04 | Regressionsvergleich der Workbench (`pdftohtml`, `pdftoppm`, `convert`, `montage`) |

_(confidence: verified — Quarkus: blocpress-render/pom.xml, blocpress-workbench/pom.xml,
blocpress-studio/pom.xml; Elasticsearch: docker-compose.yml, docker/studio/Dockerfile,
blocpress-workbench/…/ElasticsearchTestResource.java, blocpress-workbench/pom.xml; Docker:
blocpress-render/Dockerfile, blocpress-workbench/Dockerfile, blocpress-studio/Dockerfile,
docker/studio/Dockerfile; poppler-utils und ImageMagick: blocpress-workbench/Dockerfile,
PdfComparisonService.java; derived_from: arc42.adoc:117-146 legacy (git history),
Element_Design_Concept.adoc:1277-1283 legacy (git history),
Element_Design_Concept.adoc:1293-1299 legacy (git history),
Element_Design_Concept.adoc:1337-1342 legacy (git history))_

PostgreSQL 18 ist seit 2026-10-05 entschieden; der Altbestand nannte die Grenze schon, die
Images folgen ihr noch nicht:

> [!CAUTION]
> `docker-compose.yml` startet `postgres:17-alpine`, das Quickstart-Image
> (`docker/studio/Dockerfile`, `Dockerfile.native`) installiert PostgreSQL 16, das
> Kubernetes-Beispiel (`docs/guides/examples/blocpress-render-k8s.yaml`) und der Lasttest
> (`blocpress-e2e/…/load/RenderTarget.java`) nutzen `postgres:16`. Die Anhebung ist Teil von
> [US-0039](01-goals/stories/US-0039.md).
> Randbedingung PostgreSQL 18 oder neuer, Images und Beispiele nutzen 16 und 17, Anhebung in US-0039. (contradiction)

Der Altbestand verlangte Elasticsearch „7.x oder höher“. Eingesetzt und getestet wird nur
8.11.0; 7.x ist hier deshalb nicht übernommen.

## Formate

| Richtung | Formate |
|---|---|
| Vorlagen (Eingabe) | ODT; Word-Vorlagen (DOCX) sind offen ([US-0036](01-goals/stories/US-0036.md)) |
| Dokumente (Ausgabe) | ODT, PDF, RTF |

_(confidence: verified — blocpress-core/…/OutputFormat.java, WebDavResource.java
(`application/vnd.oasis.opendocument.text`); derived_from:
arc42.adoc:117-146 legacy (git history))_

Entschieden (2026-10-06): OTT (Dokumentvorlage), das der Altbestand nannte, wird nicht
unterstützt; Eingabeformat ist ODT. Der Code prüft das Format beim Hochladen nicht ausdrücklich.
