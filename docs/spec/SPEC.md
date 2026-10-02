<!-- GENERIERT von blocpress-req-check aus docs/spec/VISION.md,
     docs/spec/epics/ und docs/spec/stories/.
     NICHT EDITIEREN — wird bei `mvn verify` neu erzeugt. -->

# blocpress — Spec (Gesamtdokument, generiert)

Die vollständige Beschreibung in einem Dokument: [Vision](VISION.md) → Epics → User Stories (inline). Der `status` jeder Story/jedes Epics ist gepflegt und **belegpflichtig** (Regeln: [CONVENTIONS](../CONVENTIONS.md)).

## Status-Legende

| Badge | `status` | Bedeutung |
|---|---|---|
| ✅ | `verified` | vollständig umgesetzt **und** gegen Code bestätigt |
| 🟡 | `in-progress` | Kern gebaut, Klausel(n) offen |
| ⚪ | `open` | Kandidat, nicht committed |
| ⛔ | `superseded` | durch Entscheidung abgelöst (→ ADR) |
| ⚰️ | `retired` | war umgesetzt, entfernt/deprecated |

## Überblick

| Status | Stories |
|---|---|
| ✅ verified | 27 |
| 🟡 in-progress | 3 |
| ⚪ open | 3 |
| **Summe** | **33** |

## E-Administration — Administration (Benutzer, Rollen, Audit)  ⚪ `open`

Benutzerverwaltung mit Rollenzuweisung, Rollenprüfung bei jedem API-Aufruf und
lückenloses Audit-Log der Workflow-Änderungen. Der Altbestand sieht dafür ein
Modul `blocpress-admin` vor.

**Warum.** Ohne eigene Benutzer- und Rollenverwaltung hängt die Freigabe an der
externen JWT-Ausstellung; ein Audit-Log macht Freigaben nachträglich
nachvollziehbar. Noch nicht begonnen.

### US-0020 — Benutzer und Rollen verwalten  ⚪ `open`

Als **Administrator** möchte ich Benutzer anlegen, ändern und löschen und ihnen
Rollen (Gestalter, Reviewer, …) zuweisen.

**Warum.** Grundlage für Rollenprüfung und Audit. Heute gibt es weder Modul
`blocpress-admin` noch eine Benutzer-Entity; Identitäten kommen ausschließlich
aus dem (extern ausgestellten) JWT.

**Herkunft:** docs/product-backlog.adoc:301-309 (UC-22, Entity E-9)

### US-0021 — Rollen bei jedem API-Aufruf prüfen  ⚪ `open`

Als **Betreiber** möchte ich, dass jeder API-Aufruf gegen die Rolle des Aufrufers
geprüft wird (RBAC).

**Warum.** Freigaben und Produktionsdeploys dürfen nur berechtigte Rollen
auslösen. Heute existiert kein `@RolesAllowed`; render-Endpunkte sind
`@PermitAll` (siehe US-0007), die Workbench prüft serverseitig gar nicht.

**Herkunft:** docs/product-backlog.adoc:311-314

### US-0022 — Workflow-Änderungen im Audit-Log festhalten  ⚪ `open`

Als **Prüfer/Auditor** möchte ich jede Workflow-Änderung (wer, wann, von welchem
in welchen Status, mit welcher Begründung) nachlesen können.

**Warum.** Nachvollziehbarkeit von Freigaben. Heute gibt es nur einen Eintrag je
synchronem Render-per-Name (`render_job`, US-0006) — Statuswechsel in der
Workbench werden nicht protokolliert.

**Herkunft:** docs/product-backlog.adoc:306-319 (Entity E-10, Audit-Logging)

## E-Auslieferung — Auslieferung (Docker, Quickstart, Native)  ✅ `verified`

Die Quarkus-Module werden als Docker-Images ausgeliefert — einzeln, als
All-in-one-Quickstart-Image und als GraalVM-Native-Variante; dazu Beispiele und
eine Landing Page für den Einstieg.

**Warum.** Ein Dokumenten-Service wird eingebettet, nicht installiert. Der Weg
vom ersten `docker run` zum ersten gerenderten Dokument muss in Minuten gehen.

### US-0026 — Module als Docker-Images mit Healthcheck  ✅ `verified`

Als **Betreiber** möchte ich jedes Modul als Docker-Image mit eingebautem
`HEALTHCHECK` betreiben, damit Orchestrierung (Compose, Kubernetes) den Zustand
erkennt.

**Warum.** blocpress wird eingebettet, nicht installiert.

**Evidence:** blocpress-render/Dockerfile, blocpress-workbench/Dockerfile, blocpress-studio/Dockerfile, blocpress-studio/src/test/java/io/github/flaechsig/blocpress/studio/StudioHealthIT.java · **Herkunft:** docs/product-backlog.adoc:66-69

### US-0027 — In Minuten ausprobieren (Quickstart)  ✅ `verified`

Als **Interessent** möchte ich blocpress mit einem einzigen Container
(Studio + Workbench + Render + PostgreSQL + Elasticsearch) starten und anhand
mitgelieferter Beispiele (`docs/samples/quickstart/`: Rechnung, Brief,
Begrüßung) sofort ein Dokument erzeugen; eine Landing Page führt hin.

**Warum.** Die Einstiegshürde entscheidet, ob jemand das Werkzeug überhaupt
bewertet.

**Evidence:** docker/studio/Dockerfile, docker/studio/supervisord.conf, docker/studio/entrypoint.sh, docs/index.html, blocpress-e2e/src/test/java/io/github/flaechsig/blocpress/e2e/StudioE2EIT.java · **Herkunft:** docs/product-backlog.adoc:71-74, :355-358

### US-0028 — Native Images für schnellen Start  ✅ `verified`

Als **Betreiber** möchte ich render, workbench und studio als GraalVM-Native-Binaries
betreiben (Start im Bereich ~0,06 s, kein JRE), gebaut per Container-Build
(Mandrel) ohne lokales GraalVM.

**Warum.** Schneller Start und geringer Speicher für Scale-to-zero. Dafür wurde
render von JODConverter (sealed UNO-Jars, nicht native-fähig) auf den
LibreOffice-Kommandozeilenaufruf (`soffice --convert-to`) mit Profil-Isolation
und begrenzter Parallelität umgestellt.

**Evidence:** pom.xml, blocpress-render/Dockerfile.native, docker/studio/Dockerfile.native, blocpress-core/src/main/resources/META-INF/native-image/org.odftoolkit/odfdom/reflect-config.json · **Herkunft:** docs/product-backlog.adoc:416-419

## E-Formate — Formatkonvertierung (ODT → PDF/RTF)  ✅ `verified`

Das gefüllte ODT wird bei Bedarf in weitere Ausgabeformate konvertiert (PDF, RTF)
— über einen headless LibreOffice-Prozess (`LibreOfficeProcessor`), gekapselt im
Modul `blocpress-render`.

**Warum.** Empfänger wollen oft PDF, nicht ODT. Die Konvertierung gehört an den
Rand (render-Modul), damit der Kern (`blocpress-core`) ohne LibreOffice-Abhängigkeit
und damit schnell testbar bleibt.

### US-0003 — Dokument als PDF/RTF ausgeben  ✅ `verified`

Als **API-Nutzer** möchte ich beim Rendern ein Ausgabeformat (PDF oder RTF)
wählen, damit ich das fertige Dokument direkt versandfertig erhalte.

**Warum.** Empfänger erwarten meist PDF; eine nachgelagerte Konvertierung von
Hand wäre ein Bruch im automatisierten Ablauf.

**Stand.** Der Altbestand führt TI-3 als DONE; der Code bestätigt das:
`LibreOfficeProcessor` (in `blocpress-core`, nicht wie im Epic beschrieben in
render) ruft `soffice --convert-to` mit isoliertem Profil je Aufruf, render kapselt
das in `LibreOfficePool`. Seit 2026-10-02 belegt durch REQ-0004 (implemented).
Dazu wurde `TransformTest` reaktiviert — er war zuvor komplett abgeschaltet
(`@Test` auskommentiert), und seine RTF-Referenzdatei in core ist in Wahrheit ein
DOCX, der RTF-Vergleich wäre nie grün geworden.

**Requirements:** REQ-0004 · **Evidence:** blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/LibreOfficeProcessor.java, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/LibreOfficePool.java, blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/TransformTest.java, blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/TemplateResourceTest.java · **Herkunft:** docs/product-backlog.adoc:41-44 (TI-3)

## E-Freigabe — Prüfung und Freigabe  ✅ `verified`

Vorlagen durchlaufen einen Workflow (DRAFT → SUBMITTED → APPROVED, Ablehnung
zurück nach DRAFT), werden über Regressionstests gegen Baseline-PDFs abgesichert,
bei Freigabe automatisch nach `production` übergeben und nach Ablauf ihres
Gültigkeitszeitraums gesperrt.

**Warum.** Dokumente mit Außenwirkung (Verträge, Rechnungen) dürfen nur in
geprüfter Form produktiv gehen und müssen periodisch überprüft werden.
Das Vier-Augen-Prinzip ist bewusst **organisatorisch**, nicht technisch
erzwungen (Entscheidung 2026-03-01) — eine schlanke Umsetzung mit Option auf
späteres Verschärfen.

> **Abweichung zum Altbestand:** Der Altbestand verortet dieses Thema im Modul
> `blocpress-proof`. Das Modul existiert nicht; die Freigabe läuft in
> `blocpress-workbench` (dort als „Interim" bezeichnet). Ob ein eigenes Modul
> noch Ziel ist, ist offen (siehe ROADMAP).

### US-0016 — Vorlage einreichen, freigeben oder ablehnen  ✅ `verified`

Als **Vorlagengestalter** möchte ich eine Vorlage zur Freigabe einreichen; als
**Reviewer** möchte ich sie freigeben oder mit Begründung ablehnen (zurück nach
DRAFT, Begründung sichtbar im Arbeitsbereich).

**Warum.** Nur geprüfte Vorlagen gehen produktiv. Das Vier-Augen-Prinzip ist
**organisatorisch** durchgesetzt, nicht technisch erzwungen (Entscheidung
2026-03-01) — wer beide Rollen hat, kann selbst freigeben; das ist ein
QS-Thema der Organisation.

> **Abweichung zum Altbestand:** Ablehnung führt nach DRAFT (mit
> `rejectionReason`); der Enum-Wert REJECTED wird dabei faktisch nicht genutzt.
> Läuft in der Workbench, nicht in einem Modul blocpress-proof.
> **Nachweis-Lücke:** Ablehnen ungetestet, Freigabe-Test `@Disabled`.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/TemplateResource.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/entity/TemplateStatus.java, blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/TemplateResourceDirectMethodTest.java, blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/WorkbenchIT.java · **Herkunft:** docs/product-backlog.adoc:156-159 (UC-2), :186-189 (Ablehnung), :242-250 (UC-8, TF-2)

### US-0017 — Freigabe deployt automatisch nach Produktion  ✅ `verified`

Als **Reviewer** möchte ich, dass eine Freigabe die Vorlage automatisch in die
Produktionsdatenhaltung des Render-Service überträgt; ist render nicht
erreichbar, schlägt die Freigabe fehl (503) und die Vorlage bleibt eingereicht.

**Warum.** Freigabe und Deployment dürfen nicht auseinanderfallen — sonst gäbe
es freigegebene Vorlagen, die nicht produktiv sind, oder umgekehrt.

> **Abweichung:** Die Übertragung nutzt `java.net.http.HttpClient` (der
> MicroProfile-REST-Client scheiterte an der Serialisierung, 2026-03-06);
> `RenderImportClient` ist ungenutzt. Der Elasticsearch-Status wird **vor** dem
> Deploy aktualisiert und bei 503 nicht zurückgerollt.
> **Nachweis-Lücke:** kein Test für Deploy oder 503-Pfad.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/TemplateResource.java, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/TemplateImportResource.java · **Herkunft:** docs/product-backlog.adoc:252-255, :156-157 (UC-2, Stufenübergabe)

### US-0018 — Änderungen per Regressionstest absichern  ✅ `verified`

Als **Vorlagengestalter** möchte ich das PDF eines Testdatensatzes als
erwartetes Ergebnis speichern und später alle Regressionstests auf einmal
ausführen; Abweichungen sehe ich als Inline-Diff, kann bekannte Abweichungen per
Klick ignorieren und akzeptieren.

**Warum.** Eine Änderung an Vorlage oder Baustein kann unbemerkt andere
Ausprägungen verändern; der Vergleich mit einer Baseline macht das sichtbar,
bevor freigegeben wird.

> **Nachweis-Lücke:** Getestet ist nur `save-expected`; Ausführen, Diff und
> Ignorieren sind ungetestet (im E2E nur die Rechnungs-Regression).

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/service/PdfComparisonService.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/TemplateResource.java, blocpress-workbench/src/main/resources/META-INF/resources/components/bp-workbench.js, blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/WorkbenchIT.java · **Herkunft:** docs/product-backlog.adoc:176-184 (TF-8, UC-11), :257-270 (UC-14, UC-16, TF-6)

### US-0019 — Vorlagen periodisch überprüfen (Compliance-Review)  ✅ `verified`

Als **Verantwortlicher für Compliance** möchte ich bei der Freigabe einen
Review-Zyklus (z.B. 1/3/5 Jahre) wählen, aus dem das Ablaufdatum berechnet wird
(`validUntil = validFrom + reviewCycleYears`); abgelaufene Vorlagen werden im
Render-Service gesperrt (404), fällige Reviews sind abrufbar
(`GET …/due-for-review`) und werden täglich um 08:00 vorab gemeldet
(Vorlauf konfigurierbar, Standard 60 Tage). Ausgemusterte Vorlagen gehen auf
RETIRED.

**Warum.** Vertragstexte veralten (Rechtslage, Konditionen); ohne festes
Ablaufdatum bleiben sie unbemerkt produktiv.

> **Abweichung:** Der Scheduler loggt nur eine Warnung, er benachrichtigt nicht.
> **Nachweis-Lücke:** weder Berechnung, Sperre noch Scheduler sind getestet.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/entity/Template.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/TemplateResource.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/service/ComplianceReviewScheduler.java, blocpress-workbench/src/main/resources/application.properties · **Herkunft:** docs/product-backlog.adoc:272-285 (UC-12, TF-4, TF-7)

## E-Persistenz — Template-Speicherung (workbench / production)  ✅ `verified`

Vorlagen werden in zwei getrennten Datenhaltungen geführt: `workbench`
(Entwicklung/Test, alles erlaubt) und `production` (nur freigegebene Vorlagen,
physisch kopiert).

**Warum.** Die physische Kopie nach `production` ist der Compliance-Nachweis,
dass nur validierte und freigegebene Vorlagen produktiv gedruckt werden. Die
ursprünglich gedachten vier Schemata (workbench, proof, production, admin) wurden
am 2026-03-01 auf zwei reduziert — eine separate Proof-/Admin-Instanz ist dafür
nicht nötig.

### US-0008 — Produktion nur aus freigegebenen Vorlagen  ✅ `verified`

Als **Verantwortlicher für Compliance** möchte ich, dass der Render-Service
ausschließlich aus einer eigenen Produktionsdatenhaltung liest, in die nur
freigegebene Vorlagen physisch kopiert werden.

**Warum.** Die physische Trennung ist der Nachweis, dass nur validierte und
freigegebene Vorlagen produktiv gedruckt werden; Entwicklung und Test können in
der Workbench nichts Produktives verändern (Entscheidung 2026-03-01: zwei statt
vier Schemata).

> **Abweichung zum Altbestand:** Gesprochen wird von „Schema production"; tatsächlich
> sind es zwei **Datenbanken** (`…/workbench`, `…/production`) auf derselben
> PostgreSQL-Instanz. Der Import-Endpunkt (`POST /api/render/templates/import`) ist
> unauthentifiziert und verlässt sich auf Netzwerk-Isolation.

**Evidence:** blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/ProductionTemplate.java, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderJob.java, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/TemplateImportResource.java, blocpress-render/src/main/resources/application.properties · **Herkunft:** docs/product-backlog.adoc:112-125 (TI-2, Entity E-1/E-3, Repository Layer)

## E-Release — Build-, Test- und Release-Automatisierung  🟡 `in-progress`

GitHub-Actions-Pipeline für Build und Tests bei jedem Push, ein Ein-Befehl-Release
nach Maven Central, Docker Hub und GitHub sowie eine E2E-Testsuite gegen das
Quickstart-Image.

**Warum.** Releases sollen reproduzierbar und ohne Handarbeit entstehen; die
E2E-Suite sichert das Zusammenspiel der Module ab, das Unit-Tests allein nicht
sehen.

### US-0029 — Jeder Push wird gebaut und getestet  🟡 `in-progress`

Als **Entwickler** möchte ich, dass jeder Push auf `main`/`workbench-*` und jeder
Pull Request gebaut und mit Unit- und Integrationstests (Testcontainers) geprüft
wird.

**Warum.** Regressionen sollen vor dem Merge auffallen, nicht beim Release.

> **Stand 2026-10-02:** `ci.yml` baut und testet jetzt alle Module außer e2e
> (inkl. LibreOffice und req-check-Gate). Offen ist nur noch die E2E-Suite gegen das
> Quickstart-Image (braucht das gebaute Image). Daher weiter `in-progress`.

**Evidence:** .github/workflows/ci.yml · **Herkunft:** docs/product-backlog.adoc:381-384

### US-0030 — Release mit einem Befehl  ✅ `verified`

Als **Maintainer** möchte ich ein Release mit einem Befehl
(`mvn validate -Ptrigger-release`) auslösen: Versionssprung (pom + Landing Page),
Tests, `blocpress-core` GPG-signiert nach Maven Central, Docker-Images
(`:X.Y.Z` + `:latest`) nach Docker Hub inkl. README aus `docs/dockerhub.md`,
Git-Tag, Merge nach `main`, nächste SNAPSHOT-Version, GitHub Release aus dem
CHANGELOG.

**Warum.** Nur die Bibliothek gehört nach Maven Central; die Quarkus-Anwendungen
werden als Images konsumiert. Ein manueller Release-Ablauf mit so vielen
Schritten wäre fehleranfällig.

**Evidence:** .github/workflows/release.yml, pom.xml, docs/dockerhub.md · **Herkunft:** docs/product-backlog.adoc:386-404

### US-0031 — Zusammenspiel der Module Ende-zu-Ende prüfen  ✅ `verified`

Als **Entwickler** möchte ich eine E2E-Suite gegen das Quickstart-Image
(Health, Upload, Liste, Vorschau, Suche, Submit/Approve, Render-by-Name, WebDAV,
Rechnungs-Regression) und eine Code-Abdeckung, die auch Code in laufenden
Containern und `@QuarkusTest`-Code korrekt erfasst.

**Warum.** Unit-Tests sehen das Zusammenspiel der Module nicht. Die Abdeckung
wird per JaCoCo-TCP-Dump aus den Container-JVMs (Ports 6300/6301) bzw. über
`quarkus-jacoco` gemessen, weil der Quarkus-Classloader die normale
JaCoCo-Instrumentierung umgeht.

**Evidence:** blocpress-e2e/src/test/java/io/github/flaechsig/blocpress/e2e/StudioE2EIT.java, blocpress-e2e/src/test/java/io/github/flaechsig/blocpress/e2e/RenderApiIT.java, blocpress-e2e/src/test/java/io/github/flaechsig/blocpress/e2e/PoolBenchmarkIT.java, blocpress-e2e/pom.xml, blocpress-workbench/pom.xml · **Herkunft:** docs/product-backlog.adoc:406-414, :221-224

## E-RenderService — Render-Service (REST-API, Auth, Jobs)  🟡 `in-progress`

`blocpress-render` macht die Pipeline als REST-Dienst nutzbar: synchron mit
mitgeschickter Vorlage, synchron per Vorlagenname aus der Produktionsdatenbank
und asynchron über eine Job-Queue — abgesichert per JWT.

**Warum.** Der Render-Service ist laut Altbestand die *einzige öffentlich
exponierte API* des Systems. Alles, was Fachanwendungen von blocpress sehen,
läuft hier durch — deshalb gehören Schnittstelle, Authentifizierung und
Lastverhalten (Großdokumente, Batch-Läufe) in ein gemeinsames Thema.

### US-0004 — Dokument synchron per REST rendern  ✅ `verified`

Als **API-Nutzer** möchte ich eine Vorlage zusammen mit JSON-Daten an einen
REST-Endpunkt schicken und das fertige Dokument direkt in der Antwort erhalten —
wahlweise als Multipart-Upload oder als JSON mit Base64-codierter Vorlage.

**Warum.** Der einfachste Integrationsweg: kein vorheriges Deployment der
Vorlage, ein Aufruf, ein Dokument. Die API ist per OpenAPI beschrieben und über
Swagger UI (`/q/swagger-ui`) ausprobierbar.

> **Abweichung zum Altbestand:** Der Backlog nennt `POST /render/template/upload`
> und `POST /render/template`. Tatsächlich bedienen beide Varianten **einen** Pfad
> `POST /api/render/template`, unterschieden über den Content-Type.

**Evidence:** blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderResource.java, blocpress-render/src/main/resources/META-INF/openapi.yml, blocpress-e2e/src/test/java/io/github/flaechsig/blocpress/e2e/RenderApiIT.java · **Herkunft:** docs/product-backlog.adoc:46-64 (TI-1, Swagger UI)

### US-0005 — Freigegebene Vorlage per Name rendern (versioniert)  ✅ `verified`

Als **API-Nutzer** möchte ich eine freigegebene Vorlage über ihren Namen
ansprechen und nur die Daten mitschicken; es soll automatisch die aktuell gültige
Version verwendet werden (`validFrom` erreicht, `validUntil` nicht überschritten).

**Warum.** Fachanwendungen sollen Vorlagen nicht selbst mitführen; eine neue
Vorlagenversion wird mit ihrem Gültigkeitsdatum wirksam, ohne dass die Anwendung
geändert wird.

> **Abweichung zum Altbestand:** Der Backlog nennt `POST /render/{id}`; tatsächlich
> `POST /api/render/{name}` (namensbasiert). Abgelaufene Vorlagen ergeben 404,
> weil die Abfrage sie ausfiltert.
>
> **Nachweis-Lücke:** `RenderByNameIntegrationTest` und `TemplateVersioningIntegrationTest`
> sind Platzhalter (`assertTrue(true)`); belegt ist der Pfad nur im E2E-Szenario
> „Render-by-Name".

**Evidence:** blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderResource.java, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/ProductionTemplate.java, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/TemplateCache.java, blocpress-e2e/src/test/java/io/github/flaechsig/blocpress/e2e/StudioE2EIT.java · **Herkunft:** docs/product-backlog.adoc:76-84 (UC-10, UC-10.1)

### US-0006 — Asynchron rendern über eine Job-Queue  ✅ `verified`

Als **API-Nutzer** möchte ich große Dokumente und Batch-Läufe als Job
einreichen (`POST /api/render/jobs` → 202 + Job-ID), den Status abfragen
(PENDING/PROCESSING/DONE/FAILED), das Ergebnis abholen und mich optional per
Webhook benachrichtigen lassen. Ein Dashboard (`GET /api/render/dashboard`)
zeigt Statuszahlen und Jobliste.

**Warum.** Großdokumente dürfen den HTTP-Thread nicht blockieren. Bewusst ohne
Message Broker: eine PostgreSQL-Tabelle `render_job` mit
`SELECT … FOR UPDATE SKIP LOCKED` reicht für die aktuelle Last und spart eine
Infrastrukturkomponente (Kafka/RabbitMQ). Ergebnisse werden zweistufig bereinigt
(Bytes nach 24 h, Datensatz nach 7 Tagen).

> **Abweichung zum Altbestand:** Ein Audit-Eintrag entsteht nur beim synchronen
> Rendern **per Name**, nicht bei `POST /api/render/template`.
>
> **Nachweis-Lücke:** Job-Queue, Worker, Webhook und Dashboard haben keinen Test.

**Evidence:** blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/AsyncRenderResource.java, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderJob.java, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderJobWorker.java, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderDashboardResource.java · **Herkunft:** docs/product-backlog.adoc:86-94 (UC-23, TI-8)

### US-0007 — Render-API optional per JWT absichern  ✅ `verified`

Als **Betreiber** möchte ich die Render-API bei Bedarf per JWT absichern
(`BLOCPRESS_AUTH_ENABLED=true`) und Public Key sowie Issuer über Umgebungsvariablen
konfigurieren, damit nur berechtigte Anwendungen Dokumente erzeugen — ohne dass
bestehende Integrationen ohne Token brechen (Default: aus).

**Warum.** Die Render-API ist die einzige öffentlich exponierte Schnittstelle;
Absicherung gehört dorthin, Schlüssel gehören nicht ins Image.

> **Historie:** Der Altbestand führte TI-4 als DONE, im Code war JWT jedoch
> abgeschaltet (`mp.jwt.*` auskommentiert, alle Endpunkte `@PermitAll`). Aufgelöst
> durch [ADR-002](../../architecture/decisions/ADR-002.adoc): optional, Default aus.

**Requirements:** REQ-0008, REQ-0009 · **Evidence:** blocpress-render/pom.xml, blocpress-render/src/main/resources/application.properties, blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderAuthConfig.java, docs/architecture/decisions/ADR-002.adoc · **Herkunft:** docs/product-backlog.adoc:56-59 (TI-4)

## E-Rendering — Render-Pipeline (Vorlage + Daten → Dokument)  🟡 `in-progress`

Das Herzstück: `RenderEngine.mergeTemplate(URL, JsonNode)` führt vier
sequentielle Schritte aus — Textblock-Expansion, Bedingungsauswertung,
Schleifen-Behandlung, Feld-Ersetzung — und macht aus einer ODT-Vorlage plus JSON
ein fertiges Dokument.

**Warum.** Ohne eine verlässliche, getestete Pipeline ist jede Vorlage ein
Sonderfall. Dieses Thema bündelt die Regeln, nach denen *jede* Vorlage gefüllt
wird.

### US-0001 — Platzhalter automatisch aus JSON füllen  ✅ `verified`

Als **Vorlagengestalter** möchte ich in meiner ODT-Vorlage Benutzerfelder mit
Punkt-Notation setzen, damit die Engine sie automatisch aus meinen JSON-Daten
füllt — ohne dass ich Code schreiben muss.

**Warum.** Das ist der Kern des Versprechens „Gestalten statt Programmieren".
Ohne verlässliche Feld-Ersetzung ist jede Vorlage statisch.

**Requirements:** REQ-0001 · **Herkunft:** docs/product-backlog.adoc:36-39 (TF-5, Feldersetzung)

### US-0002 — Bedingungen und Listen in einer Vorlage  ✅ `verified`

Als **Vorlagengestalter** möchte ich Abschnitte an Bedingungen knüpfen und
Tabellenzeilen/Abschnitte über Listen wiederholen, damit **eine** Vorlage viele
Fälle abdeckt (optionale Klauseln, variable Positionslisten).

**Warum.** Echte Dokumente haben variable Struktur. Ohne Bedingungen und
Schleifen bräuchte man je Fall eine eigene Vorlage.

**Requirements:** REQ-0002, REQ-0003 · **Herkunft:** docs/product-backlog.adoc:36-39 (TF-5, Bedingungen + Schleifen)

### US-0032 — Gemeinsame Textbausteine einbinden  🟡 `in-progress`

Als **Vorlagengestalter** möchte ich gemeinsame Inhalte (z.B. AGB) als eigene
ODT-Datei pflegen und per verknüpftem Bereich (`text:section-source`) in
beliebig viele Vorlagen einbinden.

**Warum.** Eine Änderung an den AGB soll *eine* Datei betreffen, nicht jede
Vorlage.

> **Nachweis-Lücke:** Die Expansion ist implementiert (erster Pipeline-Schritt),
> aber `TextBlockTest` prüft nur `assertNotNull`. Erst ein assertierender Test,
> dann ein Requirement — siehe ROADMAP.

**Evidence:** blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/OdtTemplateSectionElement.java, blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/RenderEngine.java · **Herkunft:** docs/product-backlog.adoc:36-39 (TF-5, Text-Blöcke)

### US-0033 — Zahlen und Daten im Sprachformat der Vorlage  ✅ `verified`

Als **Vorlagengestalter** möchte ich, dass Zahlen und Daten in der Sprache formatiert
werden, die ich im Format der Vorlage festlege — und andernfalls in einer vom
Betreiber eingestellten Sprache —, damit `500.000,00 EUR` nicht je nach
Laufzeitumgebung als `500,000.00 EUR` erscheint.

**Warum.** Formatierte Beträge in Verträgen und Angeboten sind rechtlich und
fachlich relevant. Mit 2.5.0 wurden sie im Native-Image stillschweigend im
en-Format ausgegeben (behoben in 2.5.1).

**Requirements:** REQ-0005, REQ-0006, REQ-0007

## E-Studio — Portal und Micro-Frontends (Studio)  🟡 `in-progress`

`blocpress-studio` ist der zentrale Einstiegspunkt im Browser: eine Portal-Shell,
die die Oberflächen der Module als Web Components einbindet und das JWT an sie
weiterreicht.

**Warum.** Die Module sind als Self-Contained Systems geschnitten; der Nutzer
soll trotzdem *eine* Anwendung sehen — ohne dass die Module ihre Ports nach außen
öffnen müssen.

### US-0023 — Eine Portal-Shell für alle Module  ✅ `verified`

Als **Nutzer** möchte ich alle blocpress-Oberflächen in einer Portal-Shell mit
gemeinsamer Navigation sehen; mein JWT gebe ich einmal ein, die Shell reicht es
als Property an die eingebundenen Web Components weiter. Ohne Token zeigt die
Shell eine Token-Eingabe statt einer leeren Fehlerseite.

**Warum.** Die Module sind als Self-Contained Systems geschnitten; Import Maps
erlauben, ihre Web Components zur Laufzeit einzubinden, ohne gemeinsamen
Build.

**Evidence:** blocpress-studio/src/main/resources/META-INF/resources/index.html, blocpress-studio/src/main/resources/META-INF/resources/components/bp-app.js, blocpress-studio/src/main/resources/META-INF/resources/components/bp-nav.js, blocpress-studio/src/main/resources/META-INF/resources/components/bp-token-input.js, blocpress-e2e/src/test/java/io/github/flaechsig/blocpress/e2e/StudioE2EIT.java · **Herkunft:** docs/product-backlog.adoc:335-353, :365-368

### US-0024 — Moduloberflächen als Web Components  🟡 `in-progress`

Als **Nutzer** möchte ich die Oberflächen von Workbench und Administration als
Web Components (`<bp-workbench>`, `<bp-admin>`) im Studio nutzen.

**Warum.** Jedes Modul liefert seine UI selbst aus und bleibt unabhängig
deploybar. `<bp-workbench>` existiert; `<bp-admin>` fehlt, weil das Modul
blocpress-admin noch nicht existiert (E-Administration).

**Evidence:** blocpress-workbench/src/main/resources/META-INF/resources/components/bp-workbench.js, blocpress-studio/src/main/java/io/github/flaechsig/blocpress/studio/WorkbenchProxyResource.java · **Herkunft:** docs/product-backlog.adoc:345-348

### US-0025 — Workbench-API über das Studio erreichen  ✅ `verified`

Als **Betreiber** möchte ich, dass Browser-Aufrufe auf `/api/*` transparent an die
interne Workbench (Port 8082) weitergeleitet werden, damit dieser Port nach außen
geschlossen bleiben kann.

**Warum.** Ein Einstiegspunkt, keine CORS-Konfiguration je Modul, kleinere
Angriffsfläche.

**Evidence:** blocpress-studio/src/main/java/io/github/flaechsig/blocpress/studio/WorkbenchApiProxy.java, docker/studio/nginx.conf, blocpress-e2e/src/test/java/io/github/flaechsig/blocpress/e2e/StudioE2EIT.java · **Herkunft:** docs/product-backlog.adoc:360-363

## E-Workbench — Template-Entwicklung (Workbench)  ✅ `verified`

`blocpress-workbench` ist der Arbeitsplatz der Gestalter: Vorlagen und
Textbausteine hochladen, validieren, mit Testdaten ausprobieren, die
Testabdeckung einer Vorlage einschätzen, Inhalte durchsuchen und direkt aus
LibreOffice per WebDAV bearbeiten.

**Warum.** Das Versprechen „Gestalten statt Programmieren" trägt nur, wenn der
Gestalter seine Vorlage *selbst* prüfen kann — ohne Entwickler, der Testdaten
baut oder Fehler im ODT sucht.

### US-0009 — Vorlage hochladen und sofort Feedback erhalten  ✅ `verified`

Als **Vorlagengestalter** möchte ich eine ODT-Vorlage hochladen und sofort
erfahren, welche Benutzerfelder, Wiederholungsgruppen und Bedingungen erkannt
wurden und ob etwas ungültig ist.

**Warum.** Fehler in der Vorlage sollen beim Hochladen auffallen, nicht beim
ersten produktiven Druck.

> **Nachweis-Lücke:** Kein Test zielt gezielt auf die Erkennung von
> Wiederholungsgruppen. Der Validator liegt in der Workbench, nicht in core.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/service/TemplateValidator.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/TemplateResource.java, blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/TemplateValidatorIntegrationTest.java, blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/TemplateValidatorQuarkusIT.java · **Herkunft:** docs/product-backlog.adoc:141-149 (UC-1, TF-1)

### US-0010 — Vorlagen im Dashboard überblicken  ✅ `verified`

Als **Vorlagengestalter** möchte ich alle Vorlagen in einem Dashboard mit
Statusfiltern sehen und die Details einer Vorlage (Felder, Versionen, Status)
öffnen.

**Warum.** Überblick, was in Arbeit, eingereicht oder produktiv ist. Der
Statusfilter arbeitet clientseitig.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/TemplateResource.java, blocpress-workbench/src/main/resources/META-INF/resources/components/bp-workbench.js, blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/WorkbenchIT.java, blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/TemplateResourceComprehensiveIT.java · **Herkunft:** docs/product-backlog.adoc:151-164 (UC-3, UC-5)

### US-0011 — Mit Testdaten ausprobieren  ✅ `verified`

Als **Vorlagengestalter** möchte ich aus den Feldern meiner Vorlage einen
Beispiel-Datensatz erzeugen lassen und mehrere benannte Testdatensätze je
Vorlage pflegen.

**Warum.** Ohne Testdaten kann der Gestalter seine Vorlage nicht selbst prüfen —
er bräuchte einen Entwickler, der JSON schreibt.

> **Hinweis:** Der Generator läuft nur im Frontend (Beispiel-JSON aus dem
> JSON-Schema) und ist ungetestet; die Datensatz-Verwaltung (CRUD) ist getestet.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/service/TestDataSetService.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/entity/TestDataSet.java, blocpress-workbench/src/main/resources/META-INF/resources/components/bp-workbench.js, blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/WorkbenchIT.java · **Herkunft:** docs/product-backlog.adoc:166-174 (UC-20, UC-21)

### US-0012 — Sehen, welche Fälle meine Testdaten abdecken  ✅ `verified`

Als **Vorlagengestalter** möchte ich sehen, welche Felder, Bedingungszweige
(wahr/falsch) und Wiederholungsfälle (0/1/2+ Elemente) meine Testdatensätze
abdecken, und Vorschläge für fehlende Testfälle direkt als Datensatz übernehmen.

**Warum.** Eine Vorlage mit Bedingungen und Schleifen hat viele Ausprägungen;
ungetestete Zweige fallen sonst erst im Produktivdruck auf.

> **Nachweis-Lücke:** Kein Test ruft `/coverage` auf.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/service/CoverageAnalysisService.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/TemplateResource.java · **Herkunft:** docs/product-backlog.adoc:191-194

### US-0013 — Textbausteine wie Vorlagen verwalten  ✅ `verified`

Als **Vorlagengestalter** möchte ich Textbausteine hochladen und durch denselben
Freigabe-Workflow schicken wie Vorlagen, getrennt in eigenen Reitern, und je Name
sehen, welche Version produktiv ist und ob ein Entwurf in Arbeit ist.

**Warum.** Bausteine (z.B. AGB) wirken in viele Vorlagen hinein und brauchen
deshalb dieselbe Freigabe-Disziplin.

> **Abweichung zum Altbestand:** Keine eigene Entity E-2 — Bausteine sind
> `Template` mit `TemplateType.BAUSTEIN`. **Nachweis-Lücke:** kein Test für Bausteine.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/entity/TemplateType.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/entity/Template.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/TemplateResource.java, blocpress-workbench/src/main/resources/META-INF/resources/components/bp-workbench.js · **Herkunft:** docs/product-backlog.adoc:196-199

### US-0014 — Vorlagen direkt in LibreOffice öffnen und speichern  ✅ `verified`

Als **Vorlagengestalter** möchte ich Vorlagen und Bausteine über WebDAV
(`/api/webdav/`) direkt in LibreOffice öffnen und speichern: Entwürfe
schreibbar, freigegebene Versionen nur lesbar unter `/released/`.

**Warum.** Herunterladen, bearbeiten, wieder hochladen ist umständlich und
fehleranfällig; freigegebene Stände dürfen nicht versehentlich überschrieben
werden (PUT auf `/released/` → 403).

> **Nachweis-Lücke:** nur im E2E-Szenario „WebDAV" berührt, kein Modultest.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/WebDavResource.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/PROPFIND.java · **Herkunft:** docs/product-backlog.adoc:201-204

### US-0015 — Vorlagen und Bausteine durchsuchen  ✅ `verified`

Als **Vorlagengestalter** möchte ich im Dashboard über Name, Feldnamen,
Bedingungen und den Text der Vorlagen suchen — fehlertolerant, mit
Sofort-Treffern beim Tippen, Hervorhebung und Filter nach Typ — und per Klick
in die Detailansicht springen.

**Warum.** Bei vielen Vorlagen ist die Frage „wo kommt diese Klausel / dieses
Feld vor?" sonst nicht beantwortbar. Elasticsearch-Indizierung ist bewusst
*best effort*: ein Indexfehler wird geloggt, rollt aber keine DB-Änderung zurück.

> **Nachweis-Lücke:** `SearchIT` prüft Treffer und Typfilter, nicht aber
> Fuzzy-, Prefix-Suche und Hervorhebung.

**Evidence:** blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/SearchResource.java, blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/service/ElasticsearchIndexService.java, blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/OdtTextExtractor.java, blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/SearchIT.java, blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/OdtTextExtractorTest.java · **Herkunft:** docs/product-backlog.adoc:206-219 (UC-19, TI-7, Prefix-Suche)

