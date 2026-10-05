@AGENTS.md

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Blocpress is a lightweight document template/rendering engine. It takes LibreOffice Writer templates (ODT) plus JSON data and produces final documents (ODT/PDF/RTF). Two Maven modules:

- **blocpress-core** — Shared library. ODT parsing, validation, merge pipeline (pure Java/odfdom, no LibreOffice dependency).
- **blocpress-render** — Quarkus REST API. Document generation with LibreOffice format export, deployed as a Docker container.

## Build Commands

```bash
# Build everything (compile + tests + req-check gate)
mvn clean verify

# Build without tests
mvn clean package -DskipTests

# Partial builds: core and render depend on blocpress-req-trace (test scope), which is
# NOT installed in ~/.m2 — always include it in -pl, e.g.:

# Run only unit tests (core module)
mvn clean test -pl blocpress-req-trace,blocpress-core

# Run a single unit test (-Dsurefire... keeps req-trace from failing on "no tests matched")
mvn clean test -pl blocpress-req-trace,blocpress-core -Dtest=ShowVariableTest -Dsurefire.failIfNoSpecifiedTests=false

# Run a single test method
mvn clean test -pl blocpress-req-trace,blocpress-core -Dtest=ShowVariableTest#renderTemplate -Dsurefire.failIfNoSpecifiedTests=false

# Traceability gate smoke (core requirements only)
mvn -q clean verify -pl blocpress-req-trace,blocpress-core,blocpress-req-check

# Run render integration tests (requires Docker — ITs are skipped by default)
mvn verify -pl blocpress-req-trace,blocpress-core,blocpress-render -DskipITs=false

# Build Docker image
mvn package -pl blocpress-req-trace,blocpress-core,blocpress-render -Dquarkus.container-image.build=true -DskipTests

# Load test for blocpress-render (never in the normal build; needs Docker) — see docs/guides/render-sizing.md
mvn verify -pl blocpress-e2e -Pload -Dload.image=flaechsig/blocpress-render:2.5.1 -Dload.cpus=1 -Dload.workers=1 -Dload.levels=1,4,16
```

## Requirements

- Java 21+
- Maven 3.9+
- Docker (for integration tests, render builds, and render's `@QuarkusTest`s — they start PostgreSQL via Quarkus DevServices)
- LibreOffice 24+ (`soffice` on PATH) — needed at runtime in blocpress-render for PDF/RTF conversion, and for tests:
  render's `TemplateResourceTest` requires it; core's `TransformTest` is skipped without it, but then the
  req-check gate fails for REQ-0012 (skipped ≠ proven) — use `-Dreq.check.skip=true` on machines without LibreOffice

## Architecture

### Core Library (blocpress-core)

`RenderEngine.mergeTemplate(URL template, JsonNode data)` is the main entry point. It runs four sequential steps:

1. **Text block expansion** — Sections referencing external ODT files (`text:section-source`) are inlined
2. **Condition evaluation** — Conditional elements (`text:section`, `text:conditional-text`, `text:p`, `text:span`) are resolved against JSON using JEXL expressions
3. **Loop handling** — Sections and table rows containing array-path fields are duplicated per array element, fields get indexed names (e.g. `customer.0.name`)
4. **Field replacement** — User fields (`text:user-field-get`, `text:variable-get`) are replaced with values from JSON using dot-notation paths

Key abstractions:
- `TemplateDocument` (interface) → `OdtTemplateDocument` — wraps odfdom's `OdfTextDocument`
- `TemplateElement` (interface) → `OdtTemplateElement` — wraps individual ODF elements, handles condition evaluation via `JexlConditionEvaluator`
- `LibreOfficeProcessor` — spawns headless LibreOffice process for ODT→PDF/RTF conversion

### Render Module (blocpress-render)

REST endpoints are **generated from OpenAPI** (`src/main/resources/META-INF/openapi.yml`) by `openapi-generator-maven-plugin`. Generated interfaces land in `target/generated-sources/openapi/src/gen/java`. Implementation classes:

- `RenderResource` implements `RenderApi` — `POST /render/template/upload` (multipart) and `POST /render/template` (JSON with base64-encoded template)

Integration tests use **TestContainers** to spin up the Docker image and test against the running container.

## Code Style & Conventions

- Java 21, prefer clean architecture, minimize dependencies, keep public APIs stable
- JaCoCo enforces **70% instruction coverage** on the core module
- Generated OpenAPI code is excluded from coverage (`api.*`, `model.*`)
- Unit tests compare rendered ODT content by extracting text from the XML (`ResourceUtil.extractOdtContent()`)
- Test templates live under `src/test/resources/` as `.odt` files with corresponding `.json` data files

## Template Concepts

Templates are regular ODT files using LibreOffice **User Fields** (CTRL+F2) with dot-notation names mapping to JSON paths. Sections and table rows serve as repeat groups for arrays. External ODT files can be referenced as text blocks via `text:section-source` for shared content like Terms & Conditions.

## Documentation & Traceability Methodology (adapted from tarifnova — ADR-001)

Living documentation under `docs/`, with a build-enforced link between requirements and tests. Rationale and full rules: **`docs/CONVENTIONS.md`**. Decision to adopt: `docs/architecture/decisions/ADR-001.adoc`.

**Structure** (`docs/`, additive next to the existing website and legacy docs):
- `README.md` (hand-written entry) · `STATUS.md` (generated) · `CONVENTIONS.md`
- `spec/` — description hierarchy **Epic ⊃ Story ⊃ Requirement** (Markdown + YAML frontmatter). Requirements use **EARS** statements. `SPEC.md`, `requirements/CATALOG.md` are **generated**.
- `planning/` — `ROADMAP.md` (hand-written anchors) + generated `README.md` (status roll-up).
- `architecture/` — arc42 (`index.adoc`, `UNKNOWN` placeholders allowed) + `decisions/ADR-NNN.adoc`.

**Traceability gate** (two build-only Maven modules, no LLM):
- `blocpress-req-trace` — JUnit-Platform `TestExecutionListener` (ServiceLoader); tests tagged `@Tag("REQ-NNNN")` are written per module to `target/req-coverage.json`. Add it as a **test** dependency wherever tests tag requirements (currently `blocpress-core` and `blocpress-render`).
- `blocpress-req-check` — **last reactor module**, bound to `verify`. Compares coverage against `docs/spec/requirements/` + the spec layer, fails on 8 error classes, and (re)generates the read views. Skip with `-Dreq.check.skip=true` (views are still generated). `mvn verify` runs it automatically; scoped smoke: `mvn -q clean verify -pl blocpress-req-trace,blocpress-core,blocpress-req-check`.
- **Never hand-edit generated files** (`STATUS.md`, `SPEC.md`, `CATALOG.md`, `planning/README.md`).

**Methodology skills** (`.claude/skills/`, consultative — the human decides):
- Spec loop: `/anforderung` (elicit REQ/Story in EARS) → `/arc42` (architecture impact) → `/adr` (record a decision) → `/umsetzung` (code + `@Tag` test + status-flip) → `/nachweis` (audit the requirement↔test honesty).
- Refactoring: `/agent-orchestrator` drives `/srp-splitter` → `/compiler-guard` → `/test-tracker`.
- The tarifnova insurer-onboarding skills were intentionally **not** adopted (no blocpress equivalent).