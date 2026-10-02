---
id: E-RenderService
title: Render-Service (REST-API, Auth, Jobs)
status: in-progress
derived_from: docs/product-backlog.adoc:25-97 (Epic 1, ohne TF-5/TI-3)
---

## E-RenderService — der Render-Service als Produkt-API

`blocpress-render` macht die Pipeline als REST-Dienst nutzbar: synchron mit
mitgeschickter Vorlage, synchron per Vorlagenname aus der Produktionsdatenbank
und asynchron über eine Job-Queue — abgesichert per JWT.

**Warum.** Der Render-Service ist laut Altbestand die *einzige öffentlich
exponierte API* des Systems. Alles, was Fachanwendungen von blocpress sehen,
läuft hier durch — deshalb gehören Schnittstelle, Authentifizierung und
Lastverhalten (Großdokumente, Batch-Läufe) in ein gemeinsames Thema.
