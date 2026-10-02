# ROADMAP — offene Fäden (ohne Priorisierung)

Handgepflegter Anker für die nächsten Aktivitäten. **Kein** Status-Rollup — den
liefert die generierte [`README.md`](README.md). Hier stehen nur die offenen
Fäden, jeweils mit Verweis auf die Story/das Requirement.

## Offene Beschreibung (wächst entlang der Änderungen)

- **[E-Rendering]** Textbaustein-Expansion — [US-0032](../spec/stories/US-0032.md).
  **Blockiert durch fehlenden Nachweis:** `TextBlockTest` prüft nur `assertNotNull`.
  Erst einen assertierenden Test bauen (`/umsetzung`), dann als Requirement binden.
  Bis dahin **kein** getaggtes Requirement (kein hohler Tag).

## Widersprüche Altbestand ↔ Code (Mensch entscheidet, was gilt)

- **REQ-0004-rationale** — sagt, die Konvertierung gehöre „an den Rand (render-Modul,
  LibreOfficeProcessor)", damit der Kern ohne LibreOffice bleibt. Tatsächlich liegt
  `LibreOfficeProcessor` in `blocpress-core` (keine Compile-Abhängigkeit, aber ein
  `soffice`-Prozessaufruf). Requirement ist unveränderlich → bei Bedarf `/anforderung`
  (supersede) oder `/arc42` (Klasse nach render verschieben?).
- **Test-Fixtures** — `blocpress-core/src/test/resources/kuendigung_generated.rtf` ist ein
  DOCX mit falscher Endung (seit REQ-0004 ungenutzt; render hat eine echte RTF-Kopie).

Bei der Überführung des Backlogs (2026-10-02) sichtbar geworden; in den Stories
als „Widerspruch/Abweichung" markiert, **nicht** still aufgelöst:

- **CI-Umfang** — [US-0029](../spec/stories/US-0029.md): `ci.yml` testet seit 2026-10-02
  alle Module außer e2e (inkl. Gate); offen: soll die E2E-Suite (braucht gebautes
  Quickstart-Image) in die CI?
- **blocpress-proof / blocpress-admin** — der Backlog beschreibt eigene Module; proof ist
  in der Workbench aufgegangen (E-Freigabe), admin existiert nicht (E-Administration,
  US-0020..0022). Gilt das Zielbild eigener Module noch? → `/adr`.
- **API-Pfade** — Backlog nennt `/render/template/upload` und `/render/{id}`; tatsächlich
  `/api/render/template` (beide Content-Types) und `/api/render/{name}` (US-0004/0005).

## Nachweis-Lücken (Stories `verified`, aber kein Test bindet sie)

Die 28 aus dem Backlog überführten Stories tragen **keine** Requirements — sie sind
über `evidence` (Code-Pfade) belegt. Requirements entstehen erst per `/anforderung`
+ `/umsetzung` mit echtem `@Tag`-Test. Besonders dünn getestet (Platzhalter
`assertTrue(true)`, `@Disabled` oder gar kein Test):

- Render per Name/Versionierung (US-0005), Job-Queue/Webhook/Dashboard (US-0006)
- Ablehnen/Freigeben (US-0016), Auto-Deploy + 503-Pfad (US-0017)
- Regressionslauf/Diff/Ignorieren (US-0018), Compliance-Review/Scheduler (US-0019)
- Coverage-Analyse (US-0012), Bausteine (US-0013), WebDAV (US-0014),
  Fuzzy-/Prefix-Suche (US-0015)
- Import-Endpunkt (`POST /api/render/templates/import`) antwortet auf eine leere/ungültige
  Anfrage mit 500 statt 400 (aufgefallen bei ADR-002, 2026-10-02).

## Bereits umgesetzt (via Nachweis-Bindung an bestehende Core-Tests)

- REQ-0001 Feld-Ersetzung (`ShowVariableTest#renderTemplate`), REQ-0002 bedingte
  Elemente (`IfConditionTest`, `SectionVisibilityTest`), REQ-0003 Schleifen
  (`LoopTest#testProductLoop`) — siehe generierte [planning/README.md](README.md).

## Vorschläge aus der Lastmessung (2026-10-02, nicht umgesetzt — Mensch entscheidet)

Belege: [Messprotokoll](../guides/measurements/render-2.5.1-2026-10-02.md),
[Hands-On](../guides/render-sizing.md), [US-0034](../spec/stories/US-0034.md).

- **Fehler: Job-Pfad im Native-Image** ([US-0006](../spec/stories/US-0006.md)) —
  `POST /api/render/jobs` und `GET …/jobs/{id}` liefern HTTP 500: `AsyncRenderResource.JobStatus`
  (Record in `Response` verpackt) ist nicht für Reflection registriert. Fix: `@RegisterForReflection`
  an `JobStatus` (und vorsorglich `JobRequest`) + nativer Test. Kandidat für 2.5.2.
- **Job-Durchsatz** — `RenderJobWorker` holt je Takt (2 s) genau einen Job → ≤ 0,5 Jobs/s je
  Instanz. Vorschlag: je Takt Jobs holen, bis die Warteschlange leer oder alle Worker belegt sind.
- **Worker-Default (entschieden 2026-10-02):** Standardgröße ist **2 CPU / 2 Worker** — passend
  zum Default `BLOCPRESS_LO_WORKERS=2`; Guide, Docker-Hub-README, Beispiel-Manifest und
  Compose-Limits (JVM 768m, native 640m) entsprechend. Offen nur noch als Komfort: Worker
  automatisch aus dem CPU-Kontingent ableiten, damit kleinere Limits nicht von Hand
  nachgezogen werden müssen.
- **`tutorial-sysadmin.html`** — Abschnitt „Configure JWT authentication" setzt noch den
  eingebauten Dev-Schlüssel voraus; seit ADR-002 ist JWT optional (`BLOCPRESS_AUTH_ENABLED`).

## Altbestand überführen (Retirement, kontrolliert)

Die gewachsene Doku bleibt Quelle, bis sie nach der Retirement-Disziplin
(siehe [CONVENTIONS.md](../CONVENTIONS.md)) aufgegangen ist:

- `product-backlog.adoc` — **inhaltlich überführt** (2026-10-02): 7 Epics → 8 neue
  Epics + 29 neue Stories (US-0004..US-0032), jede mit `derived_from` auf die
  Backlog-Zeilen. Stilllegung erst, wenn kein `derived_from` mehr darauf zeigt
  (Retirement-Bedingung 2) und der Mensch zustimmt — bis dahin eingefroren, Pflege
  nur noch in `spec/`.
- `specification/arc42.adoc` — in `architecture/index.adoc` aufgehen lassen,
  Kapitel für Kapitel, mit `confidence`/`derived_from`.
- `System_Design_Concept` — als Altdoku-Quelle für arc42-Kapitel 5/8 nutzen.

## Methodik-Adaption

- Erstaufsatz der tarifnova-Methodik in blocpress — siehe
  [ADR-001](../architecture/decisions/ADR-001.adoc). Nächste sinnvolle Schritte:
  weitere Kern-Requirements (Textblock-Expansion, Validierung) formulieren und an
  bestehende Core-Tests binden.
