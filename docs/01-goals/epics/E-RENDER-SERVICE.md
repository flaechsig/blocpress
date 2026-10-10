---
id: E-RENDER-SERVICE
title: Render-Service (REST-API, Auth, Jobs)
derived_from: product-backlog.adoc:25-97 legacy (git history) (Epic 1, ohne TF-5/TI-3)
---

## E-RENDER-SERVICE — der Render-Service als Produkt-API

`blocpress-render` macht die Pipeline als REST-Dienst nutzbar: synchron mit
mitgeschickter Vorlage, synchron per Vorlagenname aus der Produktionsdatenbank
und asynchron über eine Job-Queue — abgesichert per JWT.

**Warum.** Der Render-Service ist laut Altbestand die *einzige öffentlich
exponierte API* des Systems. Alles, was Fachanwendungen von blocpress sehen,
läuft hier durch — deshalb gehören Schnittstelle, Authentifizierung und
Lastverhalten (Großdokumente, Batch-Läufe) in ein gemeinsames Thema.

## Stories

<!-- generated:stories -->
- [US-0004](../stories/US-0004.md) — Dokument synchron per REST rendern
- [US-0005](../stories/US-0005.md) — Freigegebene Vorlage per Name rendern (versioniert)
- [US-0006](../stories/US-0006.md) — Asynchron rendern über eine Job-Queue
- [US-0007](../stories/US-0007.md) — Render-API optional per JWT absichern
- [US-0038](../stories/US-0038.md) — Fehlerpfade und Dashboard von render testen
- [US-0041](../stories/US-0041.md) — Abgelaufene und ausgemusterte Vorlagen sofort sperren
- [US-0045](../stories/US-0045.md) — Asynchrone Aufträge und Dashboard robust machen
- [US-0065](../stories/US-0065.md) — render ohne Datenbank als Engine betreiben
- [US-0072](../stories/US-0072.md) — Warme LibreOffice-Instanzen in render
- [US-0073](../stories/US-0073.md) — REST-API von render aus openapi.yml
<!-- /generated -->
