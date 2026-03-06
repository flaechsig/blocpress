# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
