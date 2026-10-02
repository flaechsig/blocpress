# Blocpress Render

**Open-source Java REST API for rendering LibreOffice ODT templates to PDF, RTF, and ODT.**

Generate production-ready documents from ODT templates and JSON data via a single HTTP call —
self-hosted, Docker-ready, no cloud dependency.

→ [GitHub](https://github.com/flaechsig/blocpress) · [Documentation](https://flaechsig.github.io/blocpress/) · [Release Notes](https://github.com/flaechsig/blocpress/releases)

---

## Quickstart

```bash
docker run --name blocpress-render -d -p 8080:8080 flaechsig/blocpress-render
```

Generate a PDF (no authentication required for stateless rendering):

```bash
curl -X POST http://localhost:8080/api/render/template \
  -H "Accept: application/pdf" \
  -F "template=@invoice.odt" \
  -F "data=@invoice.json" \
  -o invoice.pdf
```

Or with a base64-encoded template and JSON body:

```bash
curl -X POST http://localhost:8080/api/render/template \
  -H "Content-Type: application/json" \
  -d '{
    "template": "<base64-encoded ODT>",
    "data": { "customer": { "name": "Jane Doe" } },
    "outputType": "pdf"
  }' \
  -o invoice.pdf
```

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/render/template` | optional JWT¹ | Render from inline template (multipart or JSON/base64) |
| `POST` | `/api/render/{name}` | optional JWT¹ | Render from stored, approved template |

¹ Only when `BLOCPRESS_AUTH_ENABLED=true` (default: off) — see Environment Variables.

Full API docs available at `/q/swagger-ui` once the container is running.

---

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `QUARKUS_DATASOURCE_JDBC_URL` | PostgreSQL JDBC URL (production schema) | `jdbc:postgresql://localhost:5432/production` |
| `QUARKUS_DATASOURCE_USERNAME` | Database username | `workbench` |
| `QUARKUS_DATASOURCE_PASSWORD` | Database password | `workbench` |
| `BLOCPRESS_AUTH_ENABLED` | Require a JWT Bearer token for all endpoints below `/api/render/` (rendering, jobs, dashboard). Requests without a valid token get HTTP 401. The internal template import and `/q/*` stay open. | `false` |
| `MP_JWT_VERIFY_PUBLICKEY` | RSA public key for JWT verification (PEM). Required when `BLOCPRESS_AUTH_ENABLED=true` (or `MP_JWT_VERIFY_PUBLICKEY_LOCATION`) — the service refuses to start without one. | — |
| `MP_JWT_VERIFY_ISSUER` | Expected JWT issuer (`iss` claim) | — |
| `RENDER_URL` | Internal URL of this service (used by blocpress-workbench) | `http://localhost:8080` |
| `BLOCPRESS_LO_WORKERS` | Number of concurrent LibreOffice conversions (each ~150 MiB). The default `2` fits the standard size of 2 CPUs. **If you give the container less CPU, lower it:** CPU limit rounded down, at least 1 — more workers than cores lower throughput and raise memory. | `2` |
| `BLOCPRESS_DEFAULT_LOCALE` | Default locale (BCP-47, e.g. `de-DE`, `en-US`) for number/date formats in templates that do not declare a language themselves. A language set in the template's format always wins. Checked at startup — the service refuses to start if the locale is not available. | `de-DE` |

> **Note:** JWT authentication is **off by default** so existing integrations keep working. If the service is reachable from outside a trusted network, set `BLOCPRESS_AUTH_ENABLED=true` with your own key and issuer. The internal template import (`/api/render/templates/import`) is never authenticated — expose it only to blocpress-workbench.

---

## Sizing

Measured with 2.5.1 (native): one render ≈ 0.5 CPU-seconds, throughput ≈ 2 renders/s per CPU core.

| Size | CPU (request = limit) | `BLOCPRESS_LO_WORKERS` | Memory (request = limit) | ≈ renders/s per pod |
|------|------|------|------|------|
| **standard** | 2 | 2 (default) | 640Mi | 3.7 |
| lean (test/staging) | 1 | **1** | 384Mi | 2 |
| more load | replicas of *standard* | 2 | 640Mi | 3.7 × replicas |

The CPU limit is the usual bottleneck (CFS throttling); more workers than cores do not help.
Guide and load test: [render-sizing.md](https://github.com/flaechsig/blocpress/blob/main/docs/guides/render-sizing.md).

---

## Available Tags

| Tag | Description |
|-----|-------------|
| `latest` | Latest stable release |
| `2.5.0` | Current stable release (GraalVM native) |

---

## Template Authoring

Templates are standard LibreOffice Writer (`.odt`) files using **User Fields** (Ctrl+F2) with
dot-notation names that map to JSON paths:

- `customer.name` → `{ "customer": { "name": "Jane Doe" } }`
- Conditional sections and repeat groups for arrays are supported
- External ODT sections can reference shared building blocks via HTTP URL

See the [documentation](https://flaechsig.github.io/blocpress/) for authoring details and sample templates.

---

## Technology Stack

- Java 21 · Quarkus (GraalVM native image) · LibreOffice headless · PostgreSQL · OpenAPI

> Since **v2.5.0** the images are built as GraalVM native binaries — ~0.06s startup, no JRE in the container.

## License

MIT License — free to use, self-hosted, no vendor lock-in.
