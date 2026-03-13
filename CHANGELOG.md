# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
