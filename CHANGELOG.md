# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security

- **Quarkus 3.40.1 LTS** statt 3.27.2 (Support von 3.27 endete am 2026-09-24) in render, workbench und studio. Damit sind die von Trivy gemeldeten Lücken in Netty, Vert.x, `quarkus-vertx-http`, pgjdbc und Jackson behoben (u. a. Request Smuggling, Denial of Service, Umgehung pfadbasierter Berechtigungen). In `blocpress-core` bcprov 1.86 (transitiv über odfdom), commons-lang3 3.20.0 und Jackson 2.21.7.
- **CORS nur noch ausdrücklich:** render und workbench erlaubten Aufrufe aus dem Browser von jeder Origin (`*`). Jetzt gilt same-origin; fremde Origins nennt `BLOCPRESS_CORS_ORIGINS` (kommagetrennt). Das Studio braucht keine, es leitet alle Aufrufe weiter. Wer render oder workbench direkt aus einer anderen Web-Anwendung aufruft, muss deren Origin setzen.
- **Bausteine nur aus der eigenen Bibliothek** (ADR-0018, US-0060): render öffnet die Verknüpfung eines Bausteins (`text:section-source`) nicht mehr, sondern nimmt aus ihr nur den Namen (`…/bausteine/{name}.odt`) und lädt den zur Renderzeit gültigen, freigegebenen Baustein aus der eigenen Datenbank. Bisher konnte eine Vorlage render beliebige Adressen aufrufen oder lokale Dateien einbinden lassen. **Achtung:** `POST /api/render/template` lehnt Vorlagen mit Verknüpfungen jetzt mit 422 ab; andere Verknüpfungen oder unbekannte Bausteine beim Rendern per Namen ebenfalls 422. Bausteine, die vor diesem Release in die Produktion übernommen wurden, gelten dort als Vorlage und müssen einmal neu freigegeben werden, damit render sie als Baustein findet.
- **Import in die Produktion nur für Reviewer** (ADR-0019, US-0054): Mit `BLOCPRESS_AUTH_ENABLED=true` verlangt render für `POST`/`DELETE /api/render/templates/import` ein Token mit der Gruppe `reviewer` (ohne Token 401, ohne Gruppe 403). Bisher war der Import auch bei eingeschalteter Absicherung offen. Die Workbench reicht das Token des Benutzers beim Freigeben und Ausmustern durch, die Oberfläche schickt es beim Statuswechsel mit. **Achtung:** Wer die Absicherung eingeschaltet hat, muss Reviewern im Identity-Provider die Gruppe `reviewer` geben, sonst scheitert die Freigabe. Ohne Absicherung ändert sich nichts.
- **Quickstart ohne eingebauten JWT-Schlüssel** (US-0057): Das Quickstart-Image setzt `MP_JWT_VERIFY_PUBLICKEY` nicht mehr auf den öffentlich bekannten Dev-Schlüssel. Wer dort `BLOCPRESS_AUTH_ENABLED=true` setzt, muss einen eigenen Schlüssel angeben, sonst startet render nicht.
- **Container ohne root** (US-0058): render, workbench und studio laufen als UID 10001; die Kubernetes-Manifeste setzen einen restriktiven `securityContext` mit schreibgeschütztem Dateisystem und einem `emptyDir` unter `/tmp`.
- **GitHub Actions auf Commit-SHA gepinnt:** Ein umgehängter Tag einer fremden Action kann im Release-Workflow keinen fremden Code mehr mit Signaturschlüssel und Registry-Zugängen ausführen.

### Changed

- **WebDAV-Pfade für Entwurf und Freigabe** (REQ-0044, US-0062): Entwürfe liegen unter `/api/webdav/design/{templates|bausteine}/{name}.odt`, der gültige freigegebene Stand unter `/api/webdav/released/…`. Der bisherige Entwurfspfad `/api/webdav/{templates|bausteine}/` bleibt bis Release 3.0 als Alias.
- **Vorschau und Regressionstest mit Bausteinen** (REQ-0045): Die Workbench setzt verknüpfte Bausteine selbst ein — den Entwurf, sonst die gültige freigegebene Version — und schickt render eine Vorlage ohne Verknüpfung.
- **Import nach production mit Typ** (REQ-0036): Vorlage oder Baustein; per Name rendert render nur Vorlagen, eingebunden werden nur Bausteine. Neues Liquibase-Changeset `002-template-type`.
- **Suche der Workbench:** Low-Level-Client `Rest5Client` aus elasticsearch-java 9, wie ihn Quarkus 3.40 mitbringt; der Server bleibt Elasticsearch 8.x.

### Fixed

- **Ein Name, eine Zeitachse** (US-0061, ADR-0018): Zu jedem Zeitpunkt gilt höchstens eine Version eines Namens; die Freigabe einer neuen beendet die bisherige an ihrem Beginn, in der Workbench und in production. render behält beim Zurückziehen jede Version und beendet nur ihre Gültigkeit, statt alle Versionen des Namens zu löschen. **Achtung, geänderte Regeln:** Ein Freigabedatum in der Vergangenheit wird abgelehnt (400), heute oder ohne Datum heißt „ab jetzt“ (bisher galt ohne Datum der Anlagezeitpunkt der Version). Je Name gibt es höchstens einen Entwurf (Entwurf, eingereicht oder abgelehnt); ein weiterer Upload, eine Kopie unter gleichem Namen oder „neuer Entwurf“ ergibt 409. Ein Name gehört entweder einer Vorlage oder einem Baustein (409). Vor eine schon freigegebene künftige Version lässt sich nicht freigeben (409). Bestehende Daten, die diese Regeln verletzen, meldet die Workbench beim Start als Warnung im Log und ändert sie nicht.
- **Abgelaufene, ausgemusterte und ersetzte Vorlagen wirken sofort** (US-0041, REQ-0038): render ermittelt die gültige Version bei jedem Rendern per Name aus der Datenbank und hält nur den Inhalt je Version im Cache. Bisher konnte eine abgelaufene oder ausgemusterte Version bis zu 10 Minuten weiter gerendert werden, bei mehreren Instanzen auch nach Import oder Ausmustern. Gilt für Vorlagen und Bausteine.
- **Ausmustern zuverlässig** (US-0042): Bestätigt render das Entfernen aus production nicht, scheitert das Ausmustern mit 503 und die Vorlage bleibt freigegeben; bisher stand sie auf RETIRED, blieb aber renderbar. Namen mit Leerzeichen lassen sich jetzt ausmustern. Freigegebene Vorlagen lassen sich nicht mehr löschen (409), und der Wechsel APPROVED → SUBMITTED ist nicht mehr möglich (400); erneut freigegeben wird über eine neue Version.
- **Vorschau und Regression mit eingeschalteter Absicherung** (US-0044): Die Workbench schickte render dabei kein Token, render antwortete mit 401 und die Vorschau scheiterte. Jetzt reicht sie das Token des Benutzers durch, die Oberfläche schickt es bei Vorschau und allen Regressionsfunktionen mit.
- **Kopie eines Bausteins** unter gleichem Namen wurde zur Vorlage; sie bleibt jetzt Baustein.
- **Genehmigen und Ablehnen im Dashboard der Workbench:** Die Knöpfe auf den Karten öffneten keinen Dialog; die Dialoge erschienen nur in der Arbeitsansicht einer Vorlage.
- **Workbench lief im Dev-Profil:** `quarkus.profile=dev` stand fest in der Konfiguration; die ausgelieferte Workbench lief deshalb mit dem kleinen Dev-Verbindungspool (höchstens 5). Jetzt gilt das Produktionsprofil (höchstens 20).

## [2.7.1] - 2026-10-06

### Changed

- **render leitet die Zahl der LibreOffice-Worker aus dem CPU-Limit ab** (`cpu.max` der cgroup, abgerundet, mindestens 1; ohne Limit die Prozessorzahl). `BLOCPRESS_LO_WORKERS` ist nur noch eine Obergrenze: Es kann die Zahl senken, etwa bei knappem Speicher, aber nicht über die Kerne heben. Bisher galt fest 2, und wer das CPU-Limit senkte, musste die Worker von Hand anpassen (US-0037).
- **Validierung beim Hochladen:** Ein Syntaxfehler in einer Bedingung ergibt genau einen Fehler `INVALID_CONDITION`, der die Bedingung nennt; die zusätzliche Warnung `INVALID_CONDITION_SYNTAX` entfällt (REQ-0026).

### Added

- **Suchindex vollständig** (US-0043): Die Workbench führt den Index nach jeder Änderung nach (auch Inhalt ersetzen, Kopie, neuer Entwurf, Einreichen, Ablehnen), schreibt erst nach dem Commit und behält ausgemusterte Vorlagen mit Status `RETIRED`. Überschriften sowie Kopf- und Fußzeilen sind durchsuchbar. Fehlt der Index oder ist ein Schreiben gescheitert, baut die Workbench ihn aus der Datenbank neu auf; `POST /api/workbench/search/reindex` baut ihn sofort neu auf.
- **Quickstart-Image betriebstauglich** (US-0046): PostgreSQL 18 unter supervisord (Neustart nach Absturz), Volume `/data` für Datenbank und Suchindex (`docker run … -v blocpress-data:/data`), Healthcheck über studio, render und workbench. Port 9200 ist nicht mehr freigegeben; Elasticsearch bleibt intern. Hibernate `update` entfällt auch hier, das Schema kommt von Liquibase.
- **WebDAV über das Studio** (REQ-0029): Der Studio-Proxy leitet OPTIONS, HEAD, PROPFIND, LOCK und UNLOCK samt WebDAV-Headern an die Workbench weiter; LibreOffice kann Vorlagen unter `/api/webdav` direkt über das Studio öffnen und speichern.
- **PostgreSQL 18** auch im Kubernetes-Beispiel zur Bemessung und im Lasttest.
- **Kubernetes-Manifeste unter `deploy/k8s`** (Kustomize): render, workbench, studio, Elasticsearch und optional PostgreSQL 18, Ingress für das Studio. Installation mit `kubectl apply -k "github.com/flaechsig/blocpress/deploy/k8s?ref=v<Version>"`; das Sysadmin-Tutorial der Website beschreibt den Weg, lokal mit k3d (US-0051).
- **Datenbankschema per Liquibase:** render und workbench legen ihre Tabellen beim Start selbst an und migrieren sie (`db/changeLog.xml`); bestehende Datenbanken werden ohne Datenverlust übernommen. Hibernate prüft nur noch, auch die Workbench (bisher `update`). Das Quickstart-Skript legt nur noch Benutzer und Datenbanken an (US-0052, ADR-0015).

### Fixed

- **Release veröffentlicht Maven Central zuletzt** (US-0047): Bisher ging blocpress-core vor Docker Hub und Tag nach Central; scheiterte danach ein Schritt, war core veröffentlicht ohne Tag und Images. Jetzt folgt Central erst nach Images und Tag.
- **Website nach dem Release:** `pages.yml` startet nach jedem erfolgreichen Release, damit die neue Version auf der Website erscheint (US-0047).
- **Docker-Hub-Texte:** Das Quickstart-Image hat eine eigene Beschreibung; die bisherige (render) wird jetzt beim render-Image veröffentlicht (US-0047).

### Removed

- **Ungenutzter Code** (US-0049): `RenderImportClient`, `TemplateCache.getTemplateContent(UUID)`, der nie erreichte 403-Zweig beim Rendern per Name sowie `docker/studio/nginx.conf` und `studio.html`.
- **docker-compose** (`docker-compose.yml`, `docker-compose.native.yml`, `docker/01-init.sql`, `docker/02-init-production.sh`). Die Init-Skripte waren veraltet, render startete damit nicht. Lieferwege sind das Quickstart-Image und Kubernetes (ADR-0014).

## [2.7.0] - 2026-10-03

### Changed

- **Release-Workflow mit E2E-Gate:** Vor der Veröffentlichung (Maven Central, Docker Hub) wird das native Quickstart-Image gebaut und die E2E-Suite dagegen ausgeführt; schlägt sie fehl, wird nichts veröffentlicht.
- **Architektur (ADR-004):** Die PDF/RTF-Konvertierung (`LibreOfficeProcessor`) bleibt in `blocpress-core` — keine Änderung der öffentlichen API. Word-Vorlagen (DOCX) als weiteres Quellformat sind als offene Story festgehalten (US-0036).
- **Architektur (ADR-003):** Keine eigenen Module `blocpress-proof`/`blocpress-admin`; Freigabe bleibt in der Workbench, Benutzer und Rollen kommen aus einem externen Identity-Provider.
- **Job-Pfad (`/api/render/jobs`) arbeitet die Warteschlange parallel ab.** Bisher holte der Worker alle 2 s genau einen Job (≤ 0,5 Jobs/s je Instanz, unabhängig von CPU und Workern). Jetzt laufen bis zu `BLOCPRESS_LO_WORKERS` Verarbeitungsschleifen, bis die Warteschlange leer ist — gemessen nativ bei 2 CPU / 2 Worker: 0,51 → 3,57 Jobs/s. Rendern läuft außerhalb der Datenbank-Transaktion; Jobs, die länger als `BLOCPRESS_ASYNC_STALE_AFTER` (Default 10 min) auf PROCESSING stehen (z.B. nach einem Absturz), werden wieder PENDING.

### Fixed

- **Datumsfelder mit Uhrzeit** (z.B. Format `TT.MM.JJJJ HH:MM`) wurden nicht formatiert, sondern als Rohwert ausgegeben (`2026-10-03T14:30:00`). Reine Datumsformate waren nicht betroffen.
- **Workbench lieferte abgelaufene Vorlagen als aktiv aus** (`GET …/by-name/{name}/content`, WebDAV `/released/`), obwohl render sie sperrte. Jetzt gilt in beiden `validUntil`.
- **WebDAV-Speichern validiert die Vorlage.** Ein `PUT` über WebDAV (Bearbeiten in LibreOffice) ersetzte den Inhalt, ohne die Vorlage neu zu validieren und zu indizieren: Feldliste, Schema und Coverage blieben veraltet, und ein per WebDAV neu angelegtes Dokument ließ sich nie zur Freigabe einreichen. Jetzt wie beim Upload.
- **Import-Endpunkt (`POST /api/render/templates/import`)** antwortete auf leere oder ungültige Anfragen mit HTTP 500; jetzt 400 mit Meldung, welche Pflichtfelder fehlen (`id`, `name`, `version`, `contentBase64`, `validFrom`) bzw. dass `contentBase64` kein gültiges Base64 ist.
- **Betreiber-Tutorial (`tutorial-sysadmin.html`):** JWT wurde als Pflicht dargestellt; wer dem Compose-Beispiel folgte, war seit 2.6.0 ungeschützt, weil JWT ohne `BLOCPRESS_AUTH_ENABLED=true` aus bleibt. Jetzt als optional beschrieben (`AUTH_ENABLED` im `.env`), offene Pfade benannt, wirkungslose JWT-Variablen an der Workbench entfernt.

---

## [2.6.1] - 2026-10-03

### Fixed

- **Platzhalter und Bedingungen in Kopf- und Fußzeilen (REQ-0010).** `text:user-field-get`, bedingter Text und bedingte Bereiche in Kopf-/Fußzeilen aller Master-Pages (`style:header`, `-left`, `-first`, `style:footer`, …, in `styles.xml`) wurden nicht verarbeitet; dort blieb der Beispielwert der Vorlage stehen, weil LibreOffice die Felder aus der unveränderten Deklaration neu berechnete. Jetzt gelten dieselben Regeln wie im Rumpf, inklusive Zahl-/Datumsformat und Sprachregeln (Formate werden für Kopf/Fuß in `styles.xml` gesucht). Felder, die nur in Kopf/Fuß vorkommen, erkennt die Workbench als Platzhalter.
- **Bedingte Bereiche (`text:section`) fehlten im PDF vollständig.** Beim Anzeigen blieb `text:display="condition"` ohne Bedingung stehen; LibreOffice blendete den Bereich aus. Betraf auch den Dokumentrumpf — die bisherigen Tests prüften nur `content.xml`. Neuer PDF-Test für bedingte Bereiche.
- `text:is-hidden` wurde bei Bereichen ohne vorhandenes Attribut ohne Namensraum gesetzt (ungültiges ODF).

---

## [2.6.0] - 2026-10-02

### Added

- **Lasttest für blocpress-render (`RenderLoadIT`, `mvn verify -pl blocpress-e2e -Pload`):** Parallelitätsstufen, Durchsatz, p50/p95/max, Speicherspitze und CPU-Drosselung aus der cgroup (`memory.peak`, `cpu.stat`) — gegen Docker mit `--cpus`/`--memory`, eine laufende Instanz oder Kubernetes. Jeder Render wird inhaltlich geprüft (Pflichttexte im Zahlenformat, Wortfolge). Nie Teil des normalen Builds.
- **Hands-On „render richtig bemessen“** (`docs/guides/render-sizing.md`) mit Messprotokoll zu 2.5.1 und Kubernetes-Beispiel: ~0,5 CPU-s je Render, 1 Worker je CPU-Kern, Standardgröße 2 CPU / 2 Worker / 640Mi (native).
- **Ressourcen-Limits für render in `docker-compose.yml`** (`cpus: "2"`, `mem_limit: 768m`, `BLOCPRESS_LO_WORKERS=2`) bzw. `640m` in `docker-compose.native.yml` — gemessene Standardgröße statt unbegrenzt.

- **Optionale JWT-Absicherung der Render-API (ADR-002).** Mit `BLOCPRESS_AUTH_ENABLED=true` verlangen alle Endpunkte unter `/api/render/` (Rendern, `{name}`, Jobs, Dashboard) ein gültiges Bearer-Token, sonst HTTP 401. Schlüssel über `MP_JWT_VERIFY_PUBLICKEY` bzw. `MP_JWT_VERIFY_PUBLICKEY_LOCATION`, Issuer über `MP_JWT_VERIFY_ISSUER`; ohne Schlüssel bricht der Start ab. Default bleibt **aus** — keine Änderung für bestehende Integrationen. Der interne Template-Import bleibt unauthentifiziert.

### Removed

- `PoolBenchmarkIT` (nur Laufzeiten gegen das Quickstart-Image) — ersetzt durch `RenderLoadIT`.

### Fixed

- **Job-Pfad im Native-Image (Regression aus 2.5.0):** `POST /api/render/jobs` und `GET /api/render/jobs/{id}` lieferten im Native-Image HTTP 500 — der Antwort-Record `JobStatus` war nicht für Reflection registriert. Der Job wurde trotzdem verarbeitet, der Client erfuhr aber seine ID nicht. Jetzt `@RegisterForReflection`; Ende-zu-Ende-Test für den Job-Pfad ergänzt, nativ per Lasttest geprüft (20/20 Jobs korrekt).
- **Dokumentation der Authentifizierung korrigiert:** OpenAPI-Beschreibung und Docker-Hub-README versprachen JWT für `/api/render/{name}` und einen eingebauten Dev-Schlüssel; tatsächlich war die API vollständig offen. Der Dev-Schlüssel ist aus der Produktionskonfiguration entfernt.

---

## [2.5.1] - 2026-10-02

### Fixed

- **Native-Image formatiert Zahlen wieder im richtigen Sprachformat (Regression aus 2.5.0).** Das Native-Image von `blocpress-render` enthielt nur die Build-Locale; `DecimalFormatSymbols` fiel für alle anderen Sprachen stillschweigend auf das Root-/en-Format zurück — `500,000 EUR` statt `500.000 EUR`, `83.00` statt `83,00`, auch bei Formaten mit ausdrücklich `number:language="de" number:country="DE"`. `blocpress-render` wird jetzt mit `quarkus.locales=all` gebaut. `LANG` im Container war dafür nie wirksam.

### Added

- **Einstellbare Ersatzsprache `BLOCPRESS_DEFAULT_LOCALE`** (`blocpress.render.default-locale`, BCP-47, Default `de-DE`) für Zahlen-/Datumsformate der Vorlage, die selbst keine Sprache angeben. Eine Sprache im Format der Vorlage hat immer Vorrang. Ersetzt das bisher fest einprogrammierte `de`/`DE` in `UserFieldFormatter`.
- **Startprüfung:** `blocpress-render` bricht beim Start mit klarer Meldung ab, wenn für die eingestellte Sprache keine Sprachdaten vorhanden sind, statt still im en-Format zu formatieren. Verlangt eine Vorlage eine Sprache ohne Sprachdaten, wird einmalig gewarnt.
- **`blocpress-core` API:** `RenderEngine.mergeTemplate(URL, JsonNode, Locale)` und `TemplateDocument.setFieldValue(..., Locale)` reichen die Ersatzsprache explizit durch (unabhängig von `Locale.getDefault()`). Die bisherigen Signaturen bleiben und verwenden `de-DE` (`LocaleSupport.FALLBACK_LOCALE`) — keine Verhaltensänderung für bestehende Aufrufer. Eigene Implementierungen des Interfaces `TemplateDocument` müssen statt `setFieldValue(TemplateElement, String)` nun `setFieldValue(TemplateElement, String, Locale)` implementieren (die alte Methode ist jetzt eine `default`-Methode).

### Changed

- **Native-Docker-Images deutlich kleiner:** Die `Dockerfile.native` (render, workbench, studio, Quickstart) setzen das Ausführungsrecht jetzt per `COPY --chmod=755` statt per nachgelagertem `RUN chmod`, das jedes Binary ein zweites Mal in einen eigenen Layer schrieb. Gemessen: render 1008 → 848 MB, workbench 442 → 312 MB, studio 204 → 148 MB, Quickstart (native) 2940 → 2595 MB. Benötigt BuildKit (Standard ab Docker 23, im Release-Workflow über buildx).

---

## [2.5.0] - 2026-06-03

### Added

- **GraalVM-Native-Image-Support für alle drei Quarkus-Module** (`blocpress-render`, `blocpress-workbench`, `blocpress-studio`):
  - Neues Maven-Profil `native` (Parent-POM) baut die Apps als eigenständige Native-Binaries — im Mandrel-Container, ohne lokales GraalVM (`-Dnative`).
  - Startzeit ~0,06 s (statt JVM-Sekunden), kein JRE im Image.
  - `Dockerfile.native` je Modul + `docker/studio/Dockerfile.native` (natives All-in-one-Quickstart-Image) + `docker-compose.native.yml`.
  - Reflection-Konfiguration für odfdom (ODF-Element-/Attribut-/Manifest-Klassen) und Apache Xerces in `blocpress-core` (`META-INF/native-image/.../reflect-config.json`).

### Changed

- **`blocpress-render`: Dokumentkonvertierung von JODConverter (UNO) auf den LibreOffice-CLI-Pfad umgestellt.** Die OpenOffice-UNO-Jars sind versiegelt und nicht GraalVM-Native-kompatibel. `LibreOfficePool` drosselt jetzt die Nebenläufigkeit über eine Semaphore (`blocpress.libreoffice.workers`); `LibreOfficeProcessor` nutzt pro Aufruf ein eigenes LibreOffice-Profil (`-env:UserInstallation`) und ist damit parallel-sicher.
- **`LibreOfficeProcessor`**: Arbeitsverzeichnis wird zur Laufzeit aus `java.io.tmpdir` abgeleitet (vorher build-time `user.home`, im Native-Image eingefroren).
- **`blocpress-studio`**: `HttpClient` im `WorkbenchApiProxy` lazy initialisiert (kein build-time `static final` im Image-Heap).

---

## [2.4.2] - 2026-05-23

### Fixed

- **OdtTemplateElement.resolveCondition**: `text:section` with a falsy condition is now correctly displayed. The `text:is-hidden` flag was set to `"true"` instead of `"false"`, causing sections to be hidden in LibreOffice even when their condition evaluated to false (i.e. "don't hide").

---

## [2.4.1] - 2026-05-23

### Fixed

- **JexlConditionEvaluator**: TRUE/FALSE keyword normalisation no longer affects string literals inside conditions. Previously, `field == "TRUE"` was incorrectly rewritten to `field == "true"`, causing mismatches when data contained uppercase string sentinels. The same protection now applies to all keyword replacements (AND, OR, NOT, EQ, NEQ).

---

## [2.3.0] - 2026-03-23

### Added

- **Compliance-Review (UC-12 / TF-4 / TF-7)** — Gültigkeitszeitraum und Sperrmechanismus für Templates:
  - `validUntil` + `reviewCycleYears` auf der Template-Entity (berechnet bei Freigabe)
  - Status `RETIRED` — entfernt Template aus Elasticsearch und Production-DB
  - `GET /api/workbench/templates/due-for-review` — listet Templates die binnen 60 Tagen ablaufen
  - `ComplianceReviewScheduler` — täglicher Check um 08:00, Vorlaufzeit konfigurierbar via `BLOCPRESS_COMPLIANCE_LEAD_DAYS`
  - Approval-Dialog im Frontend mit `validFrom`-Datepicker und `reviewCycleYears`-Auswahl
  - render-Service: abgelaufene Templates werden mit 404 gesperrt

## [2.2.0] - 2026-03-13

### Added

- **Elasticsearch Volltextsuche (UC-19 / TI-7)** — Suchfeld im Workbench-Dashboard durchsucht alle Templates und Bausteine über Name, Feldnamen, Bedingungen und extrahierten ODT-Text.
  - Multi-Match-Query mit german Analyzer, Fuzzy-Suche und `<mark>`-Highlighting
  - Typ-Filter (Templates / Bausteine), Ergebnisse ersetzen die Tab-Ansicht
  - Klick auf Treffer öffnet direkt die Template-Detailansicht
  - ESC oder leeres Feld bringt die normale Listenansicht zurück
- **`OdtTextExtractor`** (blocpress-core, Package `core.odt`) — extrahiert lesbaren Plaintext aus ODT-Bytes via odfdom, kein Größenlimit
- **`ElasticsearchIndexService`** — best-effort Indexierung bei Upload, Status-Änderung und Löschung; Fehler werden geloggt ohne DB-Rollback
- **`SearchResource`** — `GET /api/workbench/search?q=...&type=...` mit JSON-Response `{total, hits[]}`
- **Elasticsearch im Quickstart-Image** — ES 8.11 läuft als supervisord-Prozess (priority=5) im All-in-one Image; workbench wartet auf ES-Readiness vor Start
- **Elasticsearch in docker-compose** — eigener Service `blocpress-elasticsearch` mit Health-Check und Volume `elasticsearch_data`
- **Integration-Test `SearchIT`** — Testcontainers-basiert mit eigenem `ElasticsearchTestResource` Lifecycle Manager
- **Integration tests for preview endpoint (`PreviewIT`)** — Four `@QuarkusTest` cases covering:
  happy path (200 + PDF content-type), render-500 → workbench-502, render-422 → workbench-502,
  and unknown template → 404. Uses `MockRenderServerResource` (JDK built-in `HttpServer` on a
  random port) so tests run without Docker or LibreOffice.
- **E2E regression test for invoice template** — `StudioE2EIT` orders 9 and 10 upload
  `invoice.odt` and render a preview with real numeric fields (`paymentTermsDays: 14`,
  `unitPrice: 9.99`, etc.), serving as a regression guard for the `NumberFormatException` bug.

### Fixed

- **502 Bad Gateway on preview** — `LibreOfficeProcessor` throws `IllegalStateException` (not
  `IOException`) when the LibreOffice process exits with a non-zero code. `RenderResource` only
  caught `IOException`, so the exception propagated uncaught, Quarkus returned an HTML 500 page,
  and the workbench converted any ≥ 400 render response to 502. Fixed by adding an explicit
  `IllegalStateException` catch that returns a proper HTTP 500 with a plain-text body.
- **`NumberFormatException` on numeric ODT fields** — `JsonSchemaGenerator.inferType()` compared
  mixed-case keywords (e.g. `"paymentTerms"`, `"netTotal"`, `"unitPrice"`) against a
  `toLowerCase()`-d string, so all checks always failed and numeric fields were typed as
  `"string"`. The sample JSON generator then produced string placeholders like
  `"paymentTermsDays_example"` instead of a number. Fixed by correcting all keyword literals to
  lowercase. Root cause: `"paymentTermsDays_example"` in render request caused a
  `NumberFormatException` in the render service.
- **ODT default values not used for sample JSON** — Numeric, boolean and date field values stored
  in the ODT declaration (`office:value`, `office:boolean-value`, `office:date-value`) were not
  read; only `office:string-value` was checked. `OdtTemplateElement` now reads all four ODF value
  attributes in priority order and exposes the ODF `value-type` via `getValueType()`.
  `TemplateValidator` maps the ODF type (`float`, `boolean`, etc.) to JSON Schema types and passes
  a `fieldTypes` map to `JsonSchemaGenerator`, giving ODT-declared types priority over the
  name-based heuristic. Result: the auto-generated sample JSON now uses the actual default values
  from the template (e.g. `"paymentTermsDays": 7` instead of a string placeholder).
- **Prefix / typeahead search returning no results** — Elasticsearch `multi_match` with
  `fuzziness: AUTO` does not perform prefix matching. Typing `"bloc"` did not find `"blocpress"`
  because the edit distance (5) exceeds the AUTO threshold (2 for 4-char queries). Fixed by
  combining the existing `multi_match` with `match_phrase_prefix` queries on `name` (boost 5)
  and `extractedText` (boost 1), so incremental typing immediately produces results.
- **Workbench JaCoCo coverage at 13%** — Quarkus's custom `QuarkusClassLoader` bypasses the
  standard `-javaagent` JaCoCo instrumentation. Added the `io.quarkus:quarkus-jacoco` extension
  (test scope) and configured `quarkus.jacoco.data-file=target/jacoco-quarkus.exec` so the
  parent POM's `jacoco-*.exec` glob picks it up during the merge goal.

---

## [2.1.0] - 2026-03-08

### Added

- **Coverage analysis** — The workbench now shows how well your test datasets cover a
  template. Coverage is calculated across three dimensions:
  - *Fields* — which declared user fields appear in at least one test dataset
  - *Conditions* — which JEXL condition expressions are exercised in both the `true` and
    `false` case
  - *Repetition groups* — which array paths are tested with zero, one, and two-or-more
    elements
  The coverage panel is collapsible and displays a percentage score.

- **Test-case suggestions** — For every uncovered dimension the workbench proposes a new
  test dataset that would close the gap. One click creates it.

- **Regression tests** — Run all test datasets against the current template and compare
  the rendered PDF to the stored baseline (expected PDF). Results show pass / fail per
  dataset with an inline pixel-diff viewer. Individual differences can be accepted
  ("ignore block"); accepted deviations are persisted per test dataset.

- **"Save rendered as expected"** — After reviewing a rendered PDF the user can promote
  it to the new baseline in one click, updating `expectedPdf` and `pdfHash`.

- **All-in-one Quickstart image** (`flaechsig/blocpress-studio:latest`) — A single Docker
  container bundles blocpress-studio, blocpress-workbench, blocpress-render and PostgreSQL
  16, managed by supervisord. Start everything with one command:

  ```
  docker run -d -p 8080:8080 -p 8081:8081 --name blocpress-studio \
    flaechsig/blocpress-studio:latest
  ```

  A built-in dev JWT keypair is included; override via `MP_JWT_VERIFY_PUBLICKEY` and
  `MP_JWT_VERIFY_ISSUER` for production use.

- **Studio API proxy** — blocpress-studio now transparently forwards all `/api/*` browser
  requests to blocpress-workbench (internal port 8082). Port 8082 is not exposed; the
  browser only ever talks to port 8080.

- **Token guard** — The workbench UI is no longer reachable without a JWT. An explanatory
  message and a token input field are shown until a valid token is set.

- **Designer tutorial** (`docs/tutorial-designer.html`) — Step-by-step guide for template
  authors: creating user fields, conditional text, uploading, validating, previewing and
  submitting a template for approval.

- **Sysadmin tutorial** (`docs/tutorial-sysadmin.html`) — Installation guide for the
  Quickstart image including JWT configuration and production deployment hints.

- **Field discovery from `text:user-field-decls`** — The validator now reads the ODT
  declaration list (`text:user-field-decl`) as the authoritative source for user fields,
  including fields that appear only inside conditions (e.g. `customer.gender` used in
  conditional text but never placed directly in the document body). Previously these
  fields were missing from the generated JSON schema.

### Changed

- `ValidationResult` is extended with two new fields: `conditions` (list of distinct JEXL
  condition expressions found in the template) and `repetitionGroups` (list of detected
  array paths). Existing records with `null` values deserialise without error.
- `JexlConditionEvaluator` gains a new overload `evaluate(String expr, JsonNode data)`
  used by the coverage analysis to evaluate conditions against test dataset JSON.
- Static file caching is disabled in blocpress-studio (`Cache-Control` header omitted).
  A container rebuild is immediately visible in the browser without a hard-refresh.
- The Dynamic Import of `bp-workbench.js` now goes through a server-side proxy endpoint
  (`/proxy/bp-workbench.js`) to avoid CORS enforcement on cross-origin `import()` calls.

### Fixed

- User fields referenced only inside JEXL conditions (e.g. in `text:conditional-text`)
  were not included in the generated JSON schema and therefore missing from auto-generated
  test data. Fixed by scanning `text:user-field-decl` declarations instead of
  `text:user-field-get` usages.
- Cached `bp-app.js` with a stale workbench URL caused all API calls to hit the wrong
  service after a container rebuild. Fixed by disabling immutable browser caching for
  static assets in blocpress-studio.

---

## [2.0.0] - 2026-03-06

### Breaking Changes

- **Render endpoint URL changed**: `POST /api/render/template/upload` (multipart) is now
  `POST /api/render/template`. Clients using the multipart upload endpoint must update their URL.
  The JSON/base64 endpoint (`POST /api/render/template`) is unchanged.

### Added

- **Bausteinverwaltung** — Reusable ODT building blocks (e.g. terms & conditions, footers)
  with the same DRAFT → SUBMITTED → APPROVED workflow as templates. Managed via a dedicated
  "Bausteine" tab in the workbench UI.
- **WebDAV server** (`/api/webdav/`) — LibreOffice can reference building blocks directly
  via HTTP URL at design time. GNOME Files / davfs2 compatible. Read-write for DRAFT,
  read-only for APPROVED versions under `/api/webdav/released/`.
- **Two-database architecture** (TI-2) — Workbench and production use separate PostgreSQL
  databases. Approved templates are physically copied to production on status change.
- **Template-name-based rendering** — `POST /api/render/{name}` renders using an approved
  template stored in the production database. Template content is cached (10 min TTL).
- **Template versioning** (UC-10.1) — Each re-upload increments the version number.
  `validFrom` timestamps allow time-based template selection.
- **Combined status view** — The workbench dashboard shows one card per template name,
  combining the active production version and the current draft in a single view.
- **Filename auto-fill** — The template name field in the upload form is pre-filled from
  the selected filename (without extension).
- **Test Data Management** — Multiple JSON test datasets per template, array/tree editing,
  auto-generated sample data from template field schema (UC-20, UC-21).
- **Expected PDF storage** — Save a rendered PDF as baseline for future regression tests (TF-8).
- **Template Management Dashboard** — Status filters (DRAFT / SUBMITTED / APPROVED / REJECTED)
  and inline workflow actions (UC-5).

### Changed

- Stateless render endpoint (`POST /api/render/template`) no longer requires authentication.
  The JWT requirement now applies only to name-based rendering (`POST /api/render/{name}`).
- `/api` prefix is now explicit in `@Path` annotations; `quarkus.rest.path` removed.
- Server-to-server calls from workbench to render service use Java `HttpClient` directly
  instead of MicroProfile REST Client (eliminates 503 serialization errors).
- Invalid `Accept` header on the multipart endpoint now returns `406 Not Acceptable`
  instead of an unhandled `IllegalStateException`.

### Fixed

- 503 SERVICE_UNAVAILABLE errors during template approval caused by MicroProfile REST
  Client serialization failures with complex object types.
- 401 CORS errors when calling the render service from the studio frontend.
- Output type comparison was case-sensitive; fixed with `toLowerCase()` (TD-12).
- Hibernate column name mapping for camelCase columns (`expectedPdf`, `pdfHash`).

---

## [1.2.0] - 2026-02-10

### Added

- Template Management Dashboard with status filters and workflow actions (UC-5).
- Test data generator from template fields (UC-20, UC-21).
- Expected PDF storage as baseline for regression tests (TF-8).
- Template-ID-based rendering (`POST /render/{id}`) with cache (UC-10).
- Template versioning with `validFrom` date (UC-10.1).
- Swagger UI always included (`/q/swagger-ui`).

---

## [1.1.0] - 2026-01-20

### Added

- Template upload and validation (UC-1, TF-1).
- Template details view (UC-3).
- Submit/approval workflow (UC-2): DRAFT → SUBMITTED → APPROVED → REJECTED.
- blocpress-studio portal shell with import maps and JWT forwarding.
- `<bp-workbench>` web component.

---

## [1.0.1] - 2025-12-15

### Fixed

- JaCoCo coverage merging for unit and integration tests.
- CORS configuration for cross-origin requests from studio frontend.

---

## [1.0.0] - 2025-12-01

### Added

- Initial release.
- `POST /api/render/template/upload` — stateless multipart rendering (ODT → PDF/RTF/ODT).
- `POST /api/render/template` — JSON/base64 rendering.
- JWT authentication (configurable via environment variables).
- Docker image with health check.
- Merge pipeline: text block expansion, condition evaluation, loop handling, field replacement.
