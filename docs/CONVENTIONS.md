# Dokumentations- & Anforderungskonventionen

Werkzeugneutrale Konventionen für die haltbare Doku unter `docs/`. Sie gelten
unabhängig von Agenten, Skills oder Frameworks — was hier steht, muss in Jahren
noch als gewöhnliche Datei lesbar sein.

> Diese Methodik wurde aus dem Schwesterprojekt `tarifnova` übernommen
> (siehe [ADR-001](architecture/decisions/ADR-001.adoc)). Sie steht **neben** der
> gewachsenen blocpress-Doku (`product-backlog.adoc`, `specification/`,
> `System_Design_Concept`) — deren kontrollierte Überführung ist ein offener Faden
> in der [ROADMAP](planning/ROADMAP.md), kein Bruch.

## Leitprinzip

- **IDs sind unveränderlich.** `REQ-0042` bleibt `REQ-0042`, auch wenn die
  Anforderung verworfen wird. Der Status ändert sich, die Nummer nie.
- **`rationale` ist das wichtigste Feld.** Was das System tut, steht in drei
  Jahren im Code. *Warum* es so entschieden wurde, sonst nirgends.
- **Nur was in der Pipeline geprüft wird, überlebt.** Disziplin driftet — der
  Traceability-Check (`blocpress-req-check`) erzwingt sie.

## Branch-Konvention

**Jede Änderung am Repository** läuft auf einem separaten Branch, nie direkt auf
`main` — **egal ob Doku, Code, Tests oder Skills**. Namensschema nach Art der
Änderung:

- `docs/<kurz>` — Doku/Spec/arc42 (z.B. `docs/req-traceability`)
- `feat/<kurz>` — neue Funktion / Feature-Code
- `fix/<kurz>` — Fehlerbehebung
- `test/<kurz>` — reine Test-Ergänzung/-Härtung

Atomare Commits, prüfbar und verwerfbar. Merge nach `main` mit `--no-ff`
(nachvollziehbare Branch-Historie), Branch danach löschen.

## Verzeichnisse

```
docs/
  README.md              HANDGESCHRIEBEN — das erzählende Eingangstor (Vision + roter Faden)
  STATUS.md              GENERIERT (mvn verify) — Statusspiegel, nicht editieren
  CONVENTIONS.md         diese Datei
  architecture/          arc42, 12 Kapitel, UNKNOWN-Platzhalter erlaubt
    index.adoc
    decisions/ADR-NNN.adoc
    diagrams/*.dot + *.svg      # DOT-Quelle + gerenderter, werkzeugfreier SVG (optional)
  spec/                  # Beschreibungshierarchie: was das System können soll
    VISION.md                   # die Spitze: warum es das gibt (Vision → Epics)
    SPEC.md                     # GENERIERT — Gesamtdokument (Epics + Stories inline, Status-Badges)
    requirements/REQ-NNNN.md    # atomar, normativ (die Wirbelsäule)
    requirements/CATALOG.md     # GENERIERT — Lese-Ansicht, nicht editieren
    epics/E-NAME.md             # Thema (grob)
    stories/US-NNNN.md          # Nutzersicht, verweist auf Requirements
  planning/              # ABGELEITETER Überblick + Anker für nächste Aktivitäten
    README.md                   # GENERIERT — Status-Rollup, Matrix, Lücken
    ROADMAP.md                  # handgepflegt, Anker (noch ohne Priorisierung)
```

Die bereits bestehende, veröffentlichte Website unter `docs/` (`index.html`,
`tutorial-*.html`, `images/`, `samples/`, `sitemap.xml`) bleibt davon unberührt —
die Methodik-Artefakte (`README.md`, `STATUS.md`, `spec/`, `planning/`,
`architecture/`) liegen additiv daneben.

Zwei Einstiege mit klarer Rollentrennung: **`README.md`** ist das
handgeschriebene Narrativ (warum & wo weiterlesen), **`STATUS.md`** der
generierte Statusspiegel. Die thematische Gruppierung läuft über die Epics
(`spec/epics/` → `planning/README.md`), die Architektur-Gliederung über das
arc42-Inhaltsverzeichnis. Die Beschreibung beginnt bei **`spec/VISION.md`** und
bricht sich nach unten fort: Vision → Epic → Story → Requirement.

**Warum die Trennung `spec/` ↔ `planning/`:** `spec/` ist die *Beschreibung*
(Epic ⊃ Story ⊃ Requirement — dieselbe Sache in drei Flughöhen). `planning/`
ist der *abgeleitete Blick darauf* (Status, Abdeckung, Lücken) plus der
handgepflegte Anker für die nächsten Schritte. Requirements sind die
Wirbelsäule, auf die `spec/`-Stories, arc42 **und** Tests zeigen — deshalb kein
Planungs-Artefakt.

## Requirement-Format

Markdown mit YAML-Frontmatter. Pflicht sind mindestens `id` und `status`:

```yaml
---
id: REQ-0042
statement: EARS-Syntax, englisch (ubiquitous / WHEN / WHILE / IF-THEN / WHERE)
obligation: MUSS | SOLLTE | WIRD
status: implemented | planned | proposed | rejected | superseded
source: z.B. "ODF 1.3 §x" — leer bei Eigenanforderung
supersedes: REQ-ID          # optional
superseded_by: REQ-ID       # optional
confidence: verified | unverified | contradicted
derived_from: Pfad + Zeilenbereich, falls aus Altdoku abgeleitet   # optional
evidence:                   # Pfade zu Test und Implementierung
  - pfad/zur/Implementierung.java
  - pfad/zum/Test.java
rationale: warum — Fließtext, Deutsch
---
```

Begründung der Festlegungen:

- **EARS, englisch:** Der Satz muss maschinell klassifizierbar sein; auf Deutsch
  kollabiert die Unterscheidung WHEN/IF.
- **`obligation` als eigenes Feld:** EARS kennt keine Verbindlichkeitsstufe; der
  Unterschied normativ (z.B. ODF-Konformität) vs. Eigenwunsch ist aber zentral.
- **`source`:** Externe Normen (ODF/OpenDocument, ODT-Struktur) ändern sich
  unabhängig. Bei einer Normaktualisierung muss in einer Minute filterbar sein,
  was betroffen ist.
- **Inhaltliche Änderung erzeugt eine NEUE Anforderung.** Die alte geht auf
  `superseded`, wird nie umgeschrieben. Git-History reicht nicht — sie liest
  niemand.

`statement` (EARS) englisch, `rationale` und Fachbegriffe deutsch.

## Traceability (reiner Build-Schritt, kein LLM)

- Tests tragen `@Tag("REQ-NNNN")` (JUnit 5).
- `blocpress-req-trace` — ein JUnit-Platform-`TestExecutionListener` (via
  ServiceLoader) schreibt REQ-ID → Ergebnis je Modul nach
  `target/req-coverage.json`. Läuft im äußeren Launcher-Classloader (unabhängig
  vom isolierten `@QuarkusTest`-Classloader der REST-Module). Module, deren Tests
  taggen, brauchen `blocpress-req-trace` als Test-Dependency.
- `blocpress-req-check` — letztes Reactor-Modul, an `verify` gebunden
  (`exec-maven-plugin`). Vergleicht alle `req-coverage.json` gegen
  `docs/spec/requirements/` und die Spec-Schicht und **bricht bei acht
  Fehlerklassen ab**:
  1. `status: implemented`, aber kein grüner Test zur REQ-ID
  2. Ein Test trägt eine REQ-ID, die im Katalog fehlt
  3. `status: planned`, aber Tests dazu sind grün
  4. Eine Story verweist auf ein Requirement, das es nicht gibt
  5. Eine Story verweist auf einen Epic, den es nicht gibt
  6. Eine Story/ein Epic hat einen fehlenden/ungültigen `status`
     (erlaubt: `open`/`in-progress`/`verified`/`superseded`/`retired`)
  7. `status: verified` (Story) ohne `requirements` **und** ohne `evidence` (Belegpflicht)
  8. `status: superseded`/`retired` ohne `superseded_by` (ADR-Pointer)
- Abschaltbar über `-Dreq.check.skip=true` (die generierten Ansichten entstehen
  trotzdem).
- **Kein Modell entscheidet, ob etwas implementiert ist** — das ist eine
  Tatsachenfrage für den Build.

## Spec-Schicht (`docs/spec/`) — die Beschreibung

- Eine Beschreibungshierarchie in drei Flughöhen: **Epic** (`epics/E-NAME.md`)
  ⊃ **User Story** (`stories/US-NNNN.md`) ⊃ **Requirement**
  (`requirements/REQ-NNNN.md`). Alles Markdown mit Frontmatter; IDs
  unveränderlich.
- Eine Story verbindet sich über `epic: E-NAME` und `requirements: [REQ-...]`
  mit den anderen Ebenen. Genau diese Verweise erzwingt das Gate
  (Fehlerklassen 4/5) — die Spec kann nicht still auseinanderdriften.
- **Story-/Epic-`status` ist gepflegt, aber belegpflichtig.** Jede Story und jedes
  Epic trägt ein `status`-Feld aus genau dieser Menge (Lebenszyklus, *eine* Achse):

  | `status` | Badge | Bedeutung | Belegpflicht |
  |---|---|---|---|
  | `open` | ⚪ | Kandidat, nicht committed (typ. `requirements: []`) | — |
  | `in-progress` | 🟡 | Kern gebaut, Klausel(n) offen | — |
  | `verified` | ✅ | vollständig umgesetzt **und** gegen Code bestätigt | `requirements:` **oder** `evidence:` (Code-Pfad) |
  | `superseded` | ⛔ | durch Entscheidung abgelöst, wird so nicht gebaut | `superseded_by:` (ADR) |
  | `retired` | ⚰️ | war umgesetzt, entfernt/deprecated | `superseded_by:` (ADR) |

- Abgrenzung zu Requirements: ein **Requirement** trägt `status`
  (implemented/planned/…) **und** `confidence` (gegen Code geprüft?) — zwei Achsen,
  weil es die normative Wirbelsäule ist. Eine **Story** kommt mit der einen
  `status`-Achse aus. In arc42 heißt die Vertrauensangabe an einer *Beschreibung*
  `confidence` (verified/aspirational/stale) — anderes Artefakt, andere Frage.

## Planung (`docs/planning/`) — der Blick nach vorn

- **Kein Quell-Artefakt**, sondern der abgeleitete Überblick über die Spec plus
  der handgepflegte Anker für die nächsten Aktivitäten.
- `README.md` (generiert) rollt Status auf und zeigt Lücken; `ROADMAP.md`
  (handgepflegt) ist der Anker für offene Fäden — noch **ohne** Priorisierung.

## Generierte Lese-Ansichten (im selben `verify`-Schritt)

Die maschinenlesbaren Frontmatter werden zusätzlich menschenlesbar gerendert —
alle mit „nicht editieren"-Header:

- `docs/STATUS.md` — Statusübersicht, offene arc42-Kapitel, `contradicted`-Liste.
- `docs/spec/requirements/CATALOG.md` — Requirements als Fließtext.
- `docs/spec/SPEC.md` — das Gesamtdokument der Spec (Epics + Stories inline, Badges).
- `docs/planning/README.md` — Epics → Stories → Requirements + Rückwärtsindex.

## arc42 & Altbestand

- arc42 unter `docs/architecture/` als **AsciiDoc**. Requirements bleiben
  **Markdown** (leichtere Frontmatter-Extraktion).
- Nicht durchdokumentieren. Die Doku wächst entlang der Änderungen; unberührte
  Kapitel bleiben explizit `UNKNOWN` (`// arc42-status: UNKNOWN`).
- **Beobachtung strikt von Intention trennen.** Aus Code abgeleitetes Verhalten
  ist keine Anforderung. Aussagen aus dem Altbestand kommen nur mit
  `confidence` (default `unverified`) und `derived_from` ins arc42. Widersprüche
  zwischen Altdoku und Code bleiben als `contradicted` sichtbar
  (`// arc42-contradiction:`-Marker), werden nicht still aufgelöst.
- Drei Quellen, drei Geltungsgrade: **Code** sagt, was passiert. **Altdoku**
  (`product-backlog.adoc`, `specification/`, `System_Design_Concept`) sagt, was
  mal gedacht war. **Der Mensch** sagt, was gilt.

## Altbestand stilllegen (Retirement)

Die Doku *wächst* entlang der Änderungen — und der Altbestand *schrumpft*
spiegelbildlich, aber nur kontrolliert. Ein Altbestand-Dokument oder ein
einzelner Abschnitt darf erst **entfernt** werden, wenn **alle** vier
Bedingungen erfüllt sind:

1. **Inhaltlich aufgegangen** — jede tragende Aussage ist in die neue Struktur
   (arc42, `spec/`, `planning/`) übernommen.
2. **Belege umgehängt** — kein `derived_from` zeigt mehr auf die zu entfernende
   Stelle. Die betroffenen Aussagen sind neu belegt oder auf `confidence: verified`
   gehoben.
3. **Keine einzige Quelle** — der Inhalt existiert nachweislich auch anderswo
   (Code, neue Doku), nicht nur im Altbestand.
4. **Menschliche Freigabe** — die Stilllegung ist bestätigt.

Solange etwas noch Beleg (`derived_from`) oder einzige Quelle ist, bleibt es
stehen. Ein pauschales „Altlasten weg" ist ausgeschlossen — Retirement ist so
diszipliniert wie die Traceability.
