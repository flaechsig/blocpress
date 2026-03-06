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
| `POST` | `/api/render/template` | None | Render from inline template (multipart or JSON/base64) |
| `POST` | `/api/render/{name}` | JWT | Render from stored, approved template |

Full API docs available at `/q/swagger-ui` once the container is running.

---

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `QUARKUS_DATASOURCE_JDBC_URL` | PostgreSQL JDBC URL (production schema) | `jdbc:postgresql://localhost:5432/production` |
| `QUARKUS_DATASOURCE_USERNAME` | Database username | `workbench` |
| `QUARKUS_DATASOURCE_PASSWORD` | Database password | `workbench` |
| `MP_JWT_VERIFY_PUBLICKEY` | RSA public key for JWT verification (PEM) | built-in dev key |
| `MP_JWT_VERIFY_ISSUER` | Expected JWT issuer URL | `https://blocpress.dev` |
| `RENDER_URL` | Internal URL of this service (used by blocpress-workbench) | `http://localhost:8080` |

> **Note:** The built-in dev key is for local testing only. Always override `MP_JWT_VERIFY_PUBLICKEY` and `MP_JWT_VERIFY_ISSUER` in production.

---

## Available Tags

| Tag | Description |
|-----|-------------|
| `latest` | Latest stable release |
| `2.0.0` | Current stable release |

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

- Java 21 · Quarkus · LibreOffice headless · PostgreSQL · OpenAPI

## License

MIT License — free to use, self-hosted, no vendor lock-in.
