---
name: umsetzung
description: >
  Setzt eine Anforderung (REQ-NNNN) oder Story (US-NNNN) in Code + verifizierenden
  @Tag-Test um und schließt den Spec-Kreis: bei grünem Test schreibt der Skill den
  Status-Flip (proposed/planned → implemented, confidence → verified, evidence).
  Deckt die Umsetzung eine Anforderungs- oder Architektur-Lücke auf, pausiert der
  Skill am Erkenntnis-Checkpoint und empfiehlt den Rücksprung zu /anforderung bzw.
  /arc42 (geführte Schleife, menschlich freigegeben) — er editiert spec/ und arc42
  NIE selbst (Status-Flip ausgenommen). Reuse: compiler-guard + test-tracker für
  die innere Bau-Schleife. Aufruf: /umsetzung <REQ-/US-ID> (z.B. /umsetzung REQ-0011).
---

# /umsetzung — Anforderung in Code + Test umsetzen

Du baust die Umsetzung einer Anforderung/Story und schließt den Kreis, den
`/anforderung` (das WAS) und `/arc42` (die Architektur-Wirkung) geöffnet haben:
Code + `@Tag("REQ-NNNN")`-Test, grüner Build, Status-Flip. Zeigt sich beim Bauen,
dass die Anforderung oder die Architektur noch geschärft werden muss, **pausierst
du** und empfiehlst den Rücksprung — du reparierst weder die Anforderung noch die
Architektur im Vorbeigehen.

Maßgeblich: `docs/CONVENTIONS.md` (Abschnitte „Requirement-Format",
„Traceability"). Bei Widerspruch gewinnt `CONVENTIONS.md`.

## Argumente

- `$1` — `REQ-NNNN` oder `US-NNNN`. Bei einer Story: auf ihre `requirements:`
  auffächern und je Requirement den Test-/Code-Bezug herstellen.
  - **Story ohne Requirements (`requirements: []`):** i.d.R. nicht umsetzungsreif →
    zurück an `/anforderung` (erst Requirement formulieren). **Ausnahme: reine
    UI-Story** — wenn die Backend-Fähigkeit schon besteht und der Wert rein im
    Frontend liegt, IST sie umsetzungsreif; Beleg dann über einen **Frontend-Test
    (Vitest) als `evidence:`** der Story (statt Backend-`@Tag`). Das Gate erlaubt
    `verified` via `evidence:` (Fehlerklasse 7). Referenzfall aus der Methodik-Herkunft.

## Eiserne Regeln (vor jedem Schritt präsent halten)

1. **Flaggen statt editieren.** Du schreibst **nie** an `docs/spec/` oder
   `docs/architecture/` — mit *einer* Ausnahme: dem Status-Flip (Regel 2). Deckt
   die Umsetzung eine Lücke auf, wird sie **gemeldet und übergeben**, nicht selbst
   geflickt. (Muster wie `config-ableitung`: das LLM flaggt, editiert abgeleitete
   Artefakte nie von Hand.)
2. **Status-Flip ist Pflicht, kein Umschreiben.** Bei grünem `@Tag`-Test setzt du
   im Requirement `status: implemented`, `confidence: verified` und die
   `evidence:`-Pfade (Impl + Test); die Story auf `status: verified`. Das *erzwingt*
   das Gate (sonst Fehlerklasse 3: „planned, aber Tests grün"). Statement und
   rationale bleiben **unangetastet** — nur Status/Confidence/Evidence ändern sich
   („der Status ändert sich, die Nummer nie").
3. **Immutabilität wahren.** Ist die Anforderung inhaltlich falsch/mehrdeutig,
   wird sie **nicht** editiert — `/anforderung` macht daraus (ggf.) ein
   superseding Requirement. Du flaggst nur.
4. **Menschlich getaktet.** Am Erkenntnis-Checkpoint pausierst du und **empfiehlst**
   den Rücksprung; du rufst `/anforderung`/`/arc42` **nicht** selbst auf.
5. **Arc42-Constraints einhalten.** Existiert zur Anforderung ein `/arc42`-Verdikt
   mit Constraint (z.B. „der Kern blocpress-core bleibt ohne LibreOffice-Abhängigkeit"), ist er
   verbindliche Randbedingung der Umsetzung.
6. **Vorlagen-Logik in die Vorlage, nicht in den Code.** Feldnamen, Bedingungen und
   Bausteine gehören in die ODT-Vorlage; die Engine bleibt generisch (Projektprinzip).
7. **Branch + innere Schleife.** Arbeit auf `feat/<kurz>`- oder `docs/<kurz>`-Branch;
   nach jeder Code-Änderung `compiler-guard` (kompiliert + CDI-Graph auflösbar) und
   `test-tracker` (bestehende Tests unverändert grün + isolierter Test der neuen
   Einheit) einsetzen.

## Schritt 0 — Laden & Kontext

- Requirement(s)/Story lesen: EARS-`statement`, `obligation`, `rationale`, `status`,
  bestehende `evidence`. Die **Akzeptanz** aus Statement + rationale herausziehen —
  daraus wird der Test.
- **Arc42-Bezug prüfen:** gibt es zur Anforderung einen `/arc42`-Constraint (Chat-
  Historie, arc42-Kapitel, Story-Rumpf)? Wenn ja: als Randbedingung notieren.
- **Zielmodul(e)** bestimmen (`grep`/Modultabelle in `CLAUDE.md`). Bestehende
  Tests/Patterns im Modul sichten (Muster wiederverwenden, nicht neu erfinden).

## Schritt 1 — Plan + Erkenntnis-Checkpoint

Bilde das EARS-Statement auf **(a)** die konkrete Code-Änderung und **(b)** den
verifizierenden `@Tag("REQ-NNNN")`-Test ab. Formuliere zuerst, *wie der Test
aussieht, der die Anforderung beweist* — erst dann den Code.

**Erkenntnis-Checkpoint — hier entscheidet sich die Schleife.** Prüfe:

- Lässt sich das Statement **sauber auf einen Test** abbilden? Ist die Akzeptanz
  eindeutig?
- **Trägt die Architektur** die Umsetzung (Bausteine, Abhängigkeiten, Constraints)?
- **Wer liest das neue Feld/die neue Ebene?** (Vollständigkeits-Check). Führst du
  ein neues Datenfeld, eine neue Scope-/Modell-Ebene oder einen neuen Zustand ein,
  dann `grep` **alle Konsumenten** der bestehenden Nachbar-Felder (z.B. Export-/
  Konvertierungs-Pfad, Aggregationen, Exporte) und plane die **Lese-Seite mit ein**
  — nicht nur die Schreib-Seite. Eine neue Ebene, die nur geschrieben, aber nirgends
  gelesen wird, ist eine halbe Umsetzung. (Retro (Methodik-Herkunft): die neue Datenebene war
  schreibbar, aber der Lese-Pfad las sie zunächst nicht — die Abhängigkeit hätte im
  ersten Zug mitgehen müssen.)

Wenn etwas klemmt, **stoppe** und klassifiziere die Lücke:

| Lücke | Symptom | Empfehlung (pausieren, Freigabe) |
|---|---|---|
| **Anforderungs-Lücke** | Das WAS ist mehrdeutig/unvollständig/falsch; Akzeptanz nicht testbar | `⟲ /anforderung` (schärfen bzw. supersede) → danach `/arc42` → zurück |
| **Architektur-Lücke** | WAS ok, aber WIE/WO fehlt: Baustein, Abhängigkeit, Constraint, Entscheidung | `⟲ /arc42` (ggf. `/adr`) → zurück |
| **Beides** | beide Symptome | erst `/anforderung`, dann `/arc42`, dann zurück |

**Häufiger Sonderfall — UI-only unter der @Tag-Linie.** Stellt sich heraus, dass das
genuin Neue **reine UI** ist (Reiter/Baum/Interaktion im Frontend) und die vom
Requirement beschriebene **Backend-Fähigkeit schon existiert** oder trivial ist,
dann ist ein backend-`@Tag`-Test hier **kein ehrlicher Beleg** (er beweist die
Akzeptanz „im Baum" nicht). Das ist eine **Anforderungs-Lücke**: `⟲ /anforderung`
→ Requirement `rejected`/re-scopen, das Bedürfnis wird **reine UI-Story**
(`requirements: []`) mit **Frontend-Test (Vitest) als `evidence:`**. Beleg dazu:
prüfe, ob die Backend-Endpunkte (create/update/delete mit Scope) schon da sind —
dann liegt das Neue **unter** der Backend-@Tag-Linie. (Referenzfall aus der Methodik-Herkunft.)

Melde die Lücke als knappen, belegten Befund (was fehlt, warum es blockt, welcher
Rücksprung). **Nicht** raten, nicht die Anforderung „passend interpretieren".

Ist der Plan sauber: kurz im Chat zeigen (Test-Skizze + Code-Umriss + berührte
Dateien) und **umsetzen** — für die Bau-Arbeit selbst gilt der HI-Autopilot
(autonom auf dem Branch, Freigaben nur an den definierten HIs).

## Schritt 2 — Bauen (innere Schleife)

- Code + `@Tag("REQ-NNNN")`-Test schreiben (JUnit 5). Test tagt die REQ-ID, die er
  beweist; Modul braucht `blocpress-req-trace` als Test-Dependency (falls es dort
  noch nicht taggt).
- Nach jeder Änderung: **`compiler-guard`** (früher Fehler-Fänger: Kompiliert das
  Modul? CDI-Graph auflösbar?), dann **`test-tracker`** (bestehende Tests
  unverändert; neue Einheit isoliert getestet — Logik bestehender Tests nicht
  anfassen).

## Schritt 3 — Verifizieren (inkl. Test-Deckungs-Selbstcheck)

1. Modultests grün: `mvn test -pl <modul> -Dtest=<TestKlasse>`.
2. **Deckungs-Selbstcheck (Proto-`/trace`):** Beweist der `@Tag`-Test *wirklich*
   das EARS-Statement — nicht nur „läuft grün"? Prüft er die eigentliche Akzeptanz?
   Wenn nein: Test nachschärfen, nicht den Status flippen.
3. Gate grün: `mvn -q -pl blocpress-req-check verify` (Katalog ↔ Coverage konsistent).

## Schritt 4 — Status-Flip (Pflicht bei grünem Test)

Im Requirement `docs/spec/requirements/REQ-NNNN.md`:
- `status: implemented`
- `confidence: verified`
- `evidence:` um die realen Pfade ergänzen (Implementierung **und** Test).

In der Story `docs/spec/stories/US-NNNN.md`: `status: verified` (hat `requirements:`
→ Belegpflicht erfüllt, Fehlerklasse 7 ok). Statement/rationale/Titel unverändert.

## Schritt 5 — Abschluss

1. **`mvn verify`** — regeneriert die Lese-Ansichten (`STATUS.md`, `CATALOG.md`,
   `planning/README.md`, `SPEC.md`). **Nicht** von Hand editieren.
2. **Commit(s)** auf dem Branch, atomar: Code + Test in einem, den Status-Flip +
   regenerierte Ansichten sinnvoll gebündelt. Nachricht z.B.
   `feat(<modul>): <Titel> (REQ-NNNN)` bzw. `docs(spec): REQ-NNNN implemented`.
3. **Post-`/arc42` nur bei echtem Architektur-Zuwachs:** Ist beim Bauen ein neuer
   Baustein/eine neue Modulabhängigkeit/ein Querschnittskonzept entstanden, auf
   `/arc42 REQ-NNNN` hinweisen (Doku wächst entlang der Änderung). Meist nicht nötig.
4. **Zusammenfassen:** was gebaut wurde, welcher Test die REQ beweist, Status-Flip,
   offene Fäden.

## Leitplanken (Zusammenfassung)

- Der Skill **baut und beweist** — er formuliert keine Anforderungen und entscheidet
  keine Architektur. Beides wird bei Bedarf **zurückgegeben**, nicht übernommen.
- Test zuerst denken (was beweist die REQ?), dann Code.
- Grüner Test ohne echte Deckung ist wertlos — der Deckungs-Selbstcheck ist Pflicht.
- Im Zweifel am Checkpoint: pausieren und fragen, nicht die Anforderung verbiegen.
