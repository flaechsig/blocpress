# ROADMAP — offene Fäden (ohne Priorisierung)

Handgepflegter Anker für die nächsten Aktivitäten. **Kein** Status-Rollup — den
liefert die generierte [`README.md`](README.md). Hier stehen nur die offenen
Fäden, jeweils mit Verweis auf die Story/das Requirement.

## Offene Beschreibung (wächst entlang der Änderungen)

- ~~Benutzerdefinierte Dokumenteigenschaften (`text:user-defined`)~~ — **entschieden 2026-10-03:
  werden nicht unterstützt** (überholt). Als Nicht-Ziel in der [VISION](../spec/VISION.md), Hinweis
  für Gestalter im README.
- ~~Textbaustein-Expansion~~ — **belegt** (2026-10-03): REQ-0011, `TextBlockTest` prüft Inhalt + PDF.

- **[E-Rendering]** Word-Vorlagen (DOCX) als Quelle — [US-0036](../spec/stories/US-0036.md)
  (`open`, noch nicht geplant; Weg über zweite `TemplateDocument`-Umsetzung, ADR-004).

## Widersprüche Altbestand ↔ Code (Mensch entscheidet, was gilt)

Bei der Überführung des Backlogs (2026-10-02) sichtbar geworden; in den Stories
als „Widerspruch/Abweichung" markiert, **nicht** still aufgelöst:

- ~~**REQ-0004-rationale**~~ — **entschieden 2026-10-03** ([ADR-004](../architecture/decisions/ADR-004.adoc)):
  Konvertierung bleibt in `blocpress-core`; REQ-0004 durch REQ-0012 abgelöst.
- ~~**CI-Umfang**~~ — **entschieden 2026-10-03** ([US-0029](../spec/stories/US-0029.md)):
  Push-CI ohne e2e; die E2E-Suite ist Gate im Release-Workflow (vor der Veröffentlichung).
- ~~**blocpress-proof / blocpress-admin**~~ — **entschieden 2026-10-03**
  ([ADR-003](../architecture/decisions/ADR-003.adoc)): keine eigenen Module; Freigabe bleibt in
  der Workbench, Benutzer/Rollen aus dem Identity-Provider. US-0020 abgelöst.
- **API-Pfade** — Backlog nennt `/render/template/upload` und `/render/{id}`; tatsächlich
  `/api/render/template` (beide Content-Types) und `/api/render/{name}` (US-0004/0005).

## Nachweis-Lücken (Stories `verified`, aber kein Test bindet sie)

Die aus dem Backlog überführten Stories tragen **keine** Requirements — sie sind über
`evidence` belegt. **Seit 2026-10-03 mit echten Tests** (vorher Platzhalter `assertTrue(true)`,
`@Disabled` oder gar keiner): US-0005, US-0012, US-0013, US-0014, US-0016, US-0017, US-0018,
US-0019 — siehe die jeweilige Story. Dabei gefunden und behoben: WebDAV-`PUT` validierte nicht.

Noch offen:
- ~~Fuzzy-/Prefix-Suche und Hervorhebung (US-0015)~~ — **belegt** (2026-10-03, `SearchIT`).
- Webhook und Bereinigung im Job-Pfad (US-0006).
- Ob für die Workbench-Stories Requirements (EARS) entstehen sollen, entscheidet der Mensch —
  heute sind sie über Tests als `evidence` belegt, nicht über `@Tag`.

## Bereits umgesetzt (via Nachweis-Bindung an bestehende Core-Tests)

- REQ-0001 Feld-Ersetzung (`ShowVariableTest#renderTemplate`), REQ-0002 bedingte
  Elemente (`IfConditionTest`, `SectionVisibilityTest`), REQ-0003 Schleifen
  (`LoopTest#testProductLoop`) — siehe generierte [planning/README.md](README.md).

## Vorschläge aus der Lastmessung (2026-10-02, nicht umgesetzt — Mensch entscheidet)

Belege: [Messprotokoll](../guides/measurements/render-2.5.1-2026-10-02.md),
[Hands-On](../guides/render-sizing.md), [US-0034](../spec/stories/US-0034.md).

- ~~**Fehler: Job-Pfad im Native-Image**~~ — **behoben** (2026-10-02, `@RegisterForReflection`
  an `JobStatus`/`JobRequest`, `AsyncRenderJobTest`, nativ per `RenderLoadIT -Dload.async=true`
  geprüft); enthalten in 2.6.0.
- ~~**Job-Durchsatz**~~ — **umgesetzt** (2026-10-03): der Worker arbeitet die Warteschlange mit
  `BLOCPRESS_LO_WORKERS` parallelen Schleifen ab; 0,51 → 3,57 Jobs/s (nativ, 2 CPU / 2 Worker).
  Hängende PROCESSING-Jobs werden nach `BLOCPRESS_ASYNC_STALE_AFTER` (10 min) wieder PENDING.
- **Worker-Default (entschieden 2026-10-02):** Standardgröße ist **2 CPU / 2 Worker** — passend
  zum Default `BLOCPRESS_LO_WORKERS=2`; Guide, Docker-Hub-README, Beispiel-Manifest und
  Compose-Limits (JVM 768m, native 640m) entsprechend. Offen nur noch als Komfort: Worker
  automatisch aus dem CPU-Kontingent ableiten, damit kleinere Limits nicht von Hand
  nachgezogen werden müssen.
- ~~**`tutorial-sysadmin.html`**~~ — **erledigt** (2026-10-03): JWT als optional beschrieben
  (`AUTH_ENABLED`), offene Pfade benannt, wirkungslose JWT-Variablen an der Workbench entfernt.

## Altbestand überführen (Retirement, kontrolliert)

Die gewachsene Doku bleibt Quelle, bis sie nach der Retirement-Disziplin
(siehe [CONVENTIONS.md](../CONVENTIONS.md)) aufgegangen ist:

- `product-backlog.adoc` — **inhaltlich überführt** (2026-10-02): 7 Epics → 8 neue
  Epics + 29 neue Stories (US-0004..US-0032), jede mit `derived_from` auf die
  Backlog-Zeilen. Stilllegung erst, wenn kein `derived_from` mehr darauf zeigt
  (Retirement-Bedingung 2) und der Mensch zustimmt — bis dahin eingefroren, Pflege
  nur noch in `spec/`.
- `specification/arc42.adoc` — in `architecture/index.adoc` aufgehen lassen,
  Kapitel für Kapitel, mit `confidence`/`derived_from`.
- `System_Design_Concept` — als Altdoku-Quelle für arc42-Kapitel 5/8 nutzen.

## Methodik-Adaption

- Erstaufsatz der tarifnova-Methodik in blocpress — siehe
  [ADR-001](../architecture/decisions/ADR-001.adoc). Nächste sinnvolle Schritte:
  weitere Kern-Requirements (Textblock-Expansion, Validierung) formulieren und an
  bestehende Core-Tests binden.
