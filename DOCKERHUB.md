# Blocpress Render

Lightweight document rendering engine — turns LibreOffice ODT templates plus JSON data into PDF, RTF or ODT via REST API.

## Quick Start

```bash
docker run --name blocpress-render -d -p 8080:8080 flaechsig/blocpress-render
```

The API is available at `http://localhost:8080/api` and the interactive Swagger UI at `http://localhost:8080/q/swagger-ui`.

## Endpoints

| Method | Path | Content-Type | Description |
|--------|------|-------------|-------------|
| POST | `/api/render/template/upload` | `multipart/form-data` | Upload ODT template + JSON data |
| POST | `/api/render/template` | `application/json` | Base64-encoded template + JSON data |

## Configuration

The image ships with a built-in dev key for quick testing. Override via environment variables to use your own JWT identity provider:

| Variable | Default | Description |
|----------|---------|-------------|
| `MP_JWT_VERIFY_PUBLICKEY` | Built-in dev key | RSA public key (Base64-encoded, no PEM header/footer) |
| `MP_JWT_VERIFY_ISSUER` | `https://blocpress.dev` | Expected JWT issuer claim |

Example with custom JWT configuration:

```bash
docker run -p 8080:8080 \
  -e MP_JWT_VERIFY_PUBLICKEY="MIIBIjAN..." \
  -e MP_JWT_VERIFY_ISSUER="https://my-idp.example.com" \
  flaechsig/blocpress-render
```

## Sample Request

```bash
curl -X POST http://localhost:8080/api/render/template/upload \
  -H "Authorization: Bearer <your-jwt-token>" \
  -H "Accept: application/pdf" \
  -F "template=@template.odt" \
  -F "data=@data.json" \
  -o output.pdf
```

## Links

- [GitHub](https://github.com/flaechsig/blocpress)
- [Landing Page & Quickstart Samples](https://flaechsig.github.io/blocpress/)