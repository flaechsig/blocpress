---
title: blocpress-render
path: [blocpress-render]
---

# Baustein blocpress-render

Render-Dienst: Quarkus-REST-Schnittstelle und Export über LibreOffice, ausgeliefert als
Container-Image.

Die REST-Schnittstelle liegt in `RenderResource` unter `/api/render`, z. B.
`POST /api/render/template` (Multipart). Sie nutzt `TemplateCache`, `LibreOfficePool` und
`RenderJobWorker`. Die Schnittstelle ist in `src/main/resources/META-INF/openapi.yml`
beschrieben.

_(confidence: verified — Pfade und Abhängigkeiten von `RenderResource` im Code geprüft)_

- UNKNOWN — offene Frage: Werden die Schnittstellen noch gebraucht, die der
  OpenAPI-Generator aus `openapi.yml` erzeugt? `RenderResource` ist von Hand geschrieben und
  implementiert sie nicht.

## Umgesetzte Requirements

<!-- generated:realized -->
- [REQ-0004](../01-goals/requirements/REQ-0004.md) WHERE an output type of PDF or RTF is requested, the render engine shall convert the merged ODT document to that format using a headless LibreOffice process.
- [REQ-0006](../01-goals/requirements/REQ-0006.md) WHEN a number, currency, percentage or date style referenced by a user field declares no language, the render engine shall format the value using the default locale supplied by the caller, independent of the operating-system locale.
- [REQ-0007](../01-goals/requirements/REQ-0007.md) IF the configured default locale is not a valid BCP-47 tag or no number-format data is available for it at runtime, THEN the render service shall refuse to start with an error naming the locale.
- [REQ-0008](../01-goals/requirements/REQ-0008.md) WHERE JWT authentication is enabled, the render service shall reject requests to the rendering, job and dashboard endpoints that carry no valid bearer token with HTTP 401; while it is disabled, these endpoints shall remain accessible without a token.
- [REQ-0009](../01-goals/requirements/REQ-0009.md) IF JWT authentication is enabled and no token verification key is configured, THEN the render service shall refuse to start with an error naming the missing setting.
- [REQ-0012](../01-goals/requirements/REQ-0012.md) WHERE PDF or RTF output is requested, the render engine shall convert the merged document to that format with a headless LibreOffice process provided by the core library.
<!-- /generated -->
