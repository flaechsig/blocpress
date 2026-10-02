# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Blocpress is a lightweight document template/rendering engine. It takes LibreOffice Writer templates (ODT) plus JSON data and produces final documents (ODT/PDF/RTF). Two Maven modules:

- **blocpress-core** — Shared library. ODT parsing, validation, merge pipeline (pure Java/odfdom, no LibreOffice dependency).
- **blocpress-render** — Quarkus REST API. Document generation with LibreOffice format export, deployed as a Docker container.

## Build Commands

```bash
# Build everything (compile + unit tests)
mvn clean verify

# Build without tests
mvn clean package -DskipTests

# Run only unit tests (core module)
mvn test -pl blocpress-core

# Run a single unit test
mvn test -pl blocpress-core -Dtest=ShowVariableTest

# Run a single test method
mvn test -pl blocpress-core -Dtest=ShowVariableTest#renderTemplate

# Run integration tests (requires Docker — starts container via TestContainers)
mvn verify -pl blocpress-render

# Build Docker image
mvn package -pl blocpress-render -Dquarkus.container-image.build=true -DskipTests
```

## Requirements

- Java 21+
- Maven 3.9+
- Docker (for integration tests and render builds)
- LibreOffice 24+ (only needed at runtime in blocpress-render for PDF/RTF conversion; unit tests work without it)

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
- `blocpress-req-trace` — JUnit-Platform `TestExecutionListener` (ServiceLoader); tests tagged `@Tag("REQ-NNNN")` are written per module to `target/req-coverage.json`. Add it as a **test** dependency wherever tests tag requirements (currently `blocpress-core`).
- `blocpress-req-check` — **last reactor module**, bound to `verify`. Compares coverage against `docs/spec/requirements/` + the spec layer, fails on 8 error classes, and (re)generates the read views. Skip with `-Dreq.check.skip=true` (views are still generated). `mvn verify` runs it automatically; scoped smoke: `mvn -q clean verify -pl blocpress-req-trace,blocpress-core,blocpress-req-check`.
- **Never hand-edit generated files** (`STATUS.md`, `SPEC.md`, `CATALOG.md`, `planning/README.md`).

**Methodology skills** (`.claude/skills/`, consultative — the human decides):
- Spec loop: `/anforderung` (elicit REQ/Story in EARS) → `/arc42` (architecture impact) → `/adr` (record a decision) → `/umsetzung` (code + `@Tag` test + status-flip) → `/nachweis` (audit the requirement↔test honesty).
- Refactoring: `/agent-orchestrator` drives `/srp-splitter` → `/compiler-guard` → `/test-tracker`.
- The tarifnova insurer-onboarding skills were intentionally **not** adopted (no blocpress equivalent).