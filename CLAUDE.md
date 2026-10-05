@AGENTS.md

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Blocpress is a lightweight document template/rendering engine. It takes LibreOffice Writer templates (ODT) plus JSON data and produces final documents (ODT/PDF/RTF). Two Maven modules:

- **blocpress-core** — Shared library. ODT parsing, validation, merge pipeline (pure Java/odfdom, no LibreOffice dependency).
- **blocpress-render** — Quarkus REST API. Document generation with LibreOffice format export, deployed as a Docker container.

## Build Commands

```bash
# Build everything (compile + tests), then the docspine check over all modules
mvn clean verify
python3 .docspine/docspine.pyz check

# Build without tests
mvn clean package -DskipTests

# Run only unit tests (core module)
mvn clean test -pl blocpress-core

# Run a single unit test
mvn clean test -pl blocpress-core -Dtest=ShowVariableTest -Dsurefire.failIfNoSpecifiedTests=false

# Run a single test method
mvn clean test -pl blocpress-core -Dtest=ShowVariableTest#renderTemplate -Dsurefire.failIfNoSpecifiedTests=false

# Documentation check alone (no tests)
python3 .docspine/docspine.pyz check --without-tests

# Run render integration tests (requires Docker — ITs are skipped by default)
mvn verify -pl blocpress-core,blocpress-render -DskipITs=false

# Build Docker image
mvn package -pl blocpress-core,blocpress-render -Dquarkus.container-image.build=true -DskipTests

# Load test for blocpress-render (never in the normal build; needs Docker) — see docs/guides/render-sizing.md
mvn verify -pl blocpress-e2e -Pload -Dload.image=flaechsig/blocpress-render:2.5.1 -Dload.cpus=1 -Dload.workers=1 -Dload.levels=1,4,16
```

## Requirements

- Java 21+
- Maven 3.9+
- Docker (for integration tests, render builds, and render's `@QuarkusTest`s — they start PostgreSQL via Quarkus DevServices)
- LibreOffice 24+ (`soffice` on PATH) — needed at runtime in blocpress-render for PDF/RTF conversion, and for tests:
  render's `TemplateResourceTest` requires it; core's `TransformTest` is skipped without it, but then the
  docspine check fails for REQ-0012 (skipped ≠ proven) — use `check --without-tests` on machines without LibreOffice

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

## Documentation

The documentation follows docspine: see `AGENTS.md` for where things are and how to
check, `.docspine/STANDARD.md` for the rules. Tests carry the requirement ID in
`@DisplayName("REQ-NNNN: …")`; the check runs after `mvn verify` and finds the test
reports of all modules itself (in CI as its own step).
