# Blocpress Quickstart

**All-in-one image: design, test and approve LibreOffice ODT templates in the browser, then render them to PDF, RTF and ODT via REST.**

One container with the studio (web UI), the workbench (template development and approval), render (document generation), PostgreSQL and Elasticsearch — for trying blocpress out and for local development. For production, use the single images `flaechsig/blocpress-render`, `flaechsig/blocpress-workbench` and `flaechsig/blocpress-studio`.

→ [GitHub](https://github.com/flaechsig/blocpress) · [Documentation](https://flaechsig.github.io/blocpress/) · [Release Notes](https://github.com/flaechsig/blocpress/releases)

---

## Quickstart

```bash
docker run -d -p 8080:8080 -p 8081:8081 -v blocpress-data:/data --name blocpress flaechsig/blocpress-studio-quickstart:latest
```

- **Studio** (web UI): http://localhost:8080
- **render** (REST API): http://localhost:8081, API docs at http://localhost:8081/q/swagger-ui

The container is ready once its health check is green (studio, workbench and render report ready).
Templates, render jobs and the search index live in the volume `/data` and survive removing the
container. Without `-v`, Docker creates an anonymous volume that is lost with the container.

Render an approved template by name:

```bash
curl -X POST http://localhost:8081/api/render/invoice \
  -H "Content-Type: application/json" \
  -d '{ "data": { "customer": { "name": "Jane Doe" } }, "outputType": "pdf" }' \
  -o invoice.pdf
```

---

## Ports

| Port | Service |
|------|---------|
| `8080` | Studio: web UI, proxies `/api/*` to the workbench (including WebDAV at `/api/webdav`) |
| `8081` | render: REST API |

The workbench (8082), PostgreSQL (5432) and Elasticsearch (9200) are only reachable inside the container.

---

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `BLOCPRESS_LO_WORKERS` | Upper limit for concurrent LibreOffice conversions in render; render shares the CPU with the other services here | `1` |
| `BLOCPRESS_AUTH_ENABLED` | Require a JWT Bearer token for render's API | `false` |
| `BLOCPRESS_CORS_ORIGINS` | Browser origins allowed to call render or the workbench cross-origin, comma-separated; the studio needs none | not set |
| `MP_JWT_VERIFY_PUBLICKEY` | RSA public key for JWT verification (PEM). Required with `BLOCPRESS_AUTH_ENABLED=true` — render refuses to start without it | not set |
| `MP_JWT_VERIFY_ISSUER` | Expected JWT issuer (`iss` claim) | `https://blocpress.dev` |

> **Note:** This image is meant for evaluation and development. It has no built-in verification key: without `BLOCPRESS_AUTH_ENABLED=true` and your own key, tokens are not checked, and the token entered in the studio has no protective effect. Do not expose it to untrusted networks: without authentication, anyone who reaches it can change templates and replace or delete production templates. Mandatory authentication is planned for 3.0.

---

## Technology Stack

- Java 21 · Quarkus (GraalVM native image) · LibreOffice headless · PostgreSQL 18 · Elasticsearch 8

## License

MIT License — free to use, self-hosted, no vendor lock-in.
