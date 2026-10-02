---
name: anforderung
description: >
  Elicitation-Skill für neue Anforderungen. Stellt gezielte Nachfragen (Auslöser,
  Verbindlichkeit, Quelle/Norm, Begründung, Akzeptanz) und formt daraus — im
  Beratungsmodus, der Mensch bestätigt vor dem Schreiben — die passenden Artefakte
  der Spec-Hierarchie (Epic ⊃ Story ⊃ Requirement) in EARS + Frontmatter nach
  docs/CONVENTIONS.md, verankert sie in docs/planning/ROADMAP.md und wählt Status
  so, dass das req-check-Gate grün bleibt. Aufruf: /anforderung <kurzbeschreibung
  des Bedürfnisses> (z.B. /anforderung Datumsfelder sollen lokalisiert formatiert werden).
---

# /anforderung — Anforderungen sauber formulieren

Du hilfst dem Menschen, aus einem noch unscharfen **Bedürfnis** prüfbare
Artefakte der Beschreibungshierarchie zu machen: **Epic ⊃ Story ⊃ Requirement**.
Du fragst nach, schlägst Formulierungen vor und schreibst — nach Freigabe — die
Dateien im Hausformat. Du entscheidest **nicht**, was gilt; das sagt der Mensch
(vgl. `docs/CONVENTIONS.md`: „Der Mensch sagt, was gilt").

Maßgeblich und **vor Beginn zu lesen**: `docs/CONVENTIONS.md` (Abschnitte
„Requirement-Format", „Spec-Schicht", „Traceability", „Planung"). Diese Datei ist
die Kurzfassung — bei Widerspruch gewinnt `CONVENTIONS.md`.

## Argumente

- `$1…` — die Kurzbeschreibung des Bedürfnisses in eigenen Worten des Menschen
  (frei, deutsch), z.B. `Datumsfelder sollen im Dokument lokalisiert formatiert werden`.
  Ohne Argument: frage zuerst, worum es geht, bevor du irgendetwas anlegst.

## Eiserne Regeln (vor jedem Schritt präsent halten)

1. **Beratend, nicht autonom-schreibend.** Erst Dialog → Vorschlag als Klartext
   im Chat → **explizite Freigabe** → dann Dateien schreiben. Nie ungefragt
   Artefakte anlegen. (Ausnahme: reine Lese-/Zustandserkennung.)
2. **IDs sind unveränderlich.** Eine neue Anforderung bekommt die **nächste freie**
   Nummer und behält sie für immer. Bestehende REQ/US **nie umnummerieren, nie
   inhaltlich umschreiben** — inhaltliche Änderung erzeugt eine **neue** Anforderung;
   die alte geht auf `status: superseded` mit `superseded_by:` (siehe „Ändern statt
   Umschreiben").
3. **`rationale` ist das wichtigste Feld.** Was das System tut, steht in drei Jahren
   im Code — *warum* nur hier. Kein Requirement ohne belastbare Begründung.
   Notfalls nachfragen, nicht Plausibles erfinden.
4. **EARS, englisch, maschinell klassifizierbar.** Das `statement` folgt genau
   einem EARS-Muster (siehe unten). `statement` englisch; `rationale`, Titel und
   Fachbegriffe deutsch.
5. **Status gate-konform wählen.** Eine gerade erst formulierte Anforderung ist
   **nie** `implemented`/`verified` (es gibt noch keinen grünen Test). Siehe
   „Status-Wahl (Gate)".
6. **Nichts erfinden.** Fehlt eine Angabe (Quelle, Akzeptanz, Verbindlichkeit),
   frage. Unklares bleibt offen (`open`/`proposed`), nicht geraten.
7. **Generierte Ansichten nie von Hand editieren.** `SPEC.md`, `CATALOG.md`,
   `planning/README.md`, `STATUS.md` entstehen bei `mvn verify` neu. Du schreibst
   nur Quell-Artefakte (`requirements/`, `stories/`, `epics/`) + `ROADMAP.md`.

## Schritt 0 — Zustand erkennen (still, ohne zu schreiben)

Bevor du fragst, verschaffe dir die Landkarte:

```bash
# nächste freie IDs
echo "REQ next: REQ-$(printf '%04d' $(( $(ls docs/spec/requirements/REQ-*.md | sed -E 's/.*REQ-0*([0-9]+)\.md/\1/' | sort -n | tail -1) + 1 )))"
echo "US  next: US-$(printf '%04d' $(( $(ls docs/spec/stories/US-*.md | sed -E 's/.*US-0*([0-9]+)\.md/\1/' | sort -n | tail -1) + 1 )))"
# existierende Epics (NEUE nur anlegen, wenn wirklich ein neues Thema)
ls docs/spec/epics/ | sed 's/.md//'
```

Prüfe außerdem kurz, ob es das Bedürfnis **schon** gibt (Dublette/Überschneidung):
`grep -ri "<kernbegriff>" docs/spec/`. Ein Treffer heißt: klären, ob Verfeinerung
einer bestehenden Story, `superseded`-Fall oder wirklich neu.

## Schritt 1 — Der Elicitation-Dialog

Führe ein **kurzes, gezieltes** Gespräch — nicht alle Fragen auf einmal, sondern
so viele, wie zum sauberen Formulieren nötig. Nutze bei echten Entscheidungen
gern strukturierte Auswahlfragen. Kläre mindestens:

1. **Flughöhe.** Ist das ein **Thema** (Epic), eine **Nutzergeschichte** (Story)
   oder eine **konkrete, testbare Systemverhaltensregel** (Requirement)? Meist
   entsteht ein Bündel: eine Story, die 1–n Requirements zusammenfasst, unter einem
   (oft schon existierenden) Epic. Grobes ohne Testbarkeit bleibt Story mit
   `requirements: []`.
   - **UI-Warnung (wichtig).** Ist der genuine Wert **rein UI/UX** (Darstellung,
     Reiter, Baum, Interaktion im blocpress-studio-Frontend) — und die *Backend*-Fähigkeit
     besteht schon oder ist trivial —, dann **kein Backend-Requirement anhängen**.
     Das `@Tag`-Gate ist **backend-only**; ein backend-geformtes Requirement für
     UI-Arbeit ist ein Konstruktionsfehler (er wird nie ehrlich `implemented`). Solche
     Bedürfnisse werden **reine UI-Story** (`requirements: []`) mit **Frontend-/UAT-
     Evidence** (Frontend-Testpfad (z.B. Vitest/Playwright) im `evidence:` der Story). Prüfe daher vor dem
     Requirement: *Liegt das Neue über oder unter der Backend-@Tag-Linie?* Im Zweifel
     kurz gegen den Code prüfen, ob die Backend-Fähigkeit schon existiert.
2. **Akteur & Auslöser (→ EARS-Muster).** Wer löst aus, unter welcher Bedingung?
   Daraus ergibt sich das Muster:
   - dauernd gültig → *ubiquitous*: „The `<system>` shall `<response>`."
   - bei Ereignis → *event*: „WHEN `<trigger>`, the `<system>` shall `<response>`."
   - während Zustand → *state*: „WHILE `<state>`, the `<system>` shall `<response>`."
   - unerwünschter Fall → *unwanted*: „IF `<condition>`, THEN the `<system>` shall `<response>`."
   - nur mit Feature → *optional*: „WHERE `<feature>`, the `<system>` shall `<response>`."
3. **Verbindlichkeit (`obligation`).** MUSS (normativ/zwingend) · SOLLTE (empfohlen)
   · WIRD (Absicht/Zusage). EARS kennt keine Stufe — deshalb eigenes Feld.
4. **Quelle (`source`).** Externe Norm/Vorgabe (z.B. „OpenDocument (ODF) 1.3",
   „ODT-Paketstruktur", „OAuth 2.0") — oder leer bei Eigenanforderung. Wichtig, damit
   bei Normänderung filterbar ist, was betroffen ist.
5. **Begründung (`rationale`).** *Warum* so — der Kern. Was passiert ohne die
   Anforderung? Warum diese Verbindlichkeitsstufe? Fließtext deutsch.
6. **Akzeptanz / Prüfbarkeit.** Woran erkennt man Erfüllung? (Wird später ein
   `@Tag("REQ-NNNN")`-Test.) Wenn noch unklar: festhalten, dass Evidence später kommt.
7. **Epic-Zuordnung.** Welches der bestehenden Themen? Neues Epic nur, wenn wirklich
   ein neues Thema — dann kurzes `E-NAME` (sprechend, nicht nummeriert) + `title` +
   „Warum"-Absatz.
8. **Verfeinerung vs. Neu.** Berührt es eine bestehende Story/Requirement? Dann
   klären: ergänzen (neues Requirement unter bestehender Story) oder ablösen
   (`superseded`, siehe unten).

## Schritt 2 — Vorschlag im Chat (vor dem Schreiben)

Zeige den kompletten Entwurf als Klartext: welche Dateien entstehen, mit welchem
Frontmatter und Rumpf, und welche ROADMAP-Zeile dazukommt. **Warte auf Freigabe.**
Bei „ändere X" iterieren, nicht neu raten.

## Schritt 3 — Artefakte schreiben (nach Freigabe)

### Requirement — `docs/spec/requirements/REQ-NNNN.md`

```markdown
---
id: REQ-NNNN
statement: <genau ein EARS-Muster, englisch>
obligation: MUSS | SOLLTE | WIRD
status: proposed | planned          # NEU ⇒ nie implemented (Gate!)
source: <externe Norm oder leer>
confidence: unverified               # NEU ⇒ noch nicht gegen Code geprüft
# supersedes: REQ-XXXX               # nur im Ablöse-Fall
evidence: []                         # Pfade kommen mit der Umsetzung
rationale: >-
  <Warum — deutsch, Fließtext. Das wichtigste Feld.>
---

## REQ-NNNN — <Kurztitel deutsch>

<1–3 Sätze Kontext/Abgrenzung; verweist bei Bedarf auf die rationale.>
```

### Story — `docs/spec/stories/US-NNNN.md`

```markdown
---
id: US-NNNN
title: <Kurztitel deutsch>
epic: E-NAME                          # muss existieren (Gate Klasse 5)
requirements: [REQ-NNNN, ...]         # jede muss existieren (Gate Klasse 4); [] erlaubt
status: open                          # NEU ⇒ meist open (Kandidat)
---

Als **<Rolle>** möchte ich <Ziel>, damit <Nutzen>.

**Warum.** <kurze Begründung, was ohne die Story fehlt.>
```

### Epic (nur bei neuem Thema) — `docs/spec/epics/E-NAME.md`

```markdown
---
id: E-NAME
title: <Thema>
status: open
---

## E-NAME — <Thema>

<1–2 Sätze, was das Thema umspannt.>

**Warum.** <Motivation.>
```

**Verankerung untereinander:** Story `epic:` → existierendes/neues Epic; Story
`requirements:` → die neuen REQ-IDs. Diese Verweise erzwingt das Gate
(Fehlerklassen 4/5) — sie müssen stimmen, sonst bricht `mvn verify`.

## Schritt 4 — Roadmap verankern — `docs/planning/ROADMAP.md`

Eine neu formulierte, noch nicht gebaute Anforderung ist ein **offener Faden**.
Trage sie **ohne Priorisierung** unter „Offene Beschreibung (wächst entlang der
Änderungen)" (oder einem thematisch passenden Abschnitt) als knappe Zeile ein, die
auf die Story verlinkt — z.B.:

```markdown
- **[E-NAME]** <Kurzbeschreibung> — [US-NNNN](../spec/stories/US-NNNN.md)
  (REQ-NNNN, `status: proposed`).
```

Kein Status-Rollup in die ROADMAP schreiben — den liefert die generierte
`planning/README.md`.

## Status-Wahl (Gate) — damit `mvn verify` grün bleibt

Das `req-check`-Gate bricht bei acht Fehlerklassen (siehe CONVENTIONS „Traceability").
Für **neue** Artefakte relevant:

- **Requirement:** `status: proposed` (Idee, noch nicht committed zu bauen) oder
  `planned` (committed, wird gebaut) — **nie** `implemented` ohne grünen Test
  (Klasse 1). `confidence: unverified`.
- **Story:** `status: open` (Kandidat, `requirements: []` erlaubt) oder
  `in-progress` (Kern in Arbeit). **Nicht** `verified` (das verlangt `requirements:`
  **oder** `evidence:` und einen bestätigten Stand — Klasse 7).
- **Epic:** `status: open`.
- Jede in `requirements:`/`epic:` referenzierte ID **muss als Datei existieren**
  (Klassen 4/5). Erst die REQ/Epic-Dateien anlegen, dann die Story darauf zeigen lassen.
- **Reine UI-Story ohne Requirement:** Eine UI/UX-Story trägt `requirements: []` und
  wird später über `evidence:` (Frontend-Test, z.B. Vitest/Playwright) auf `verified` gehoben —
  Klasse 7 erlaubt „`requirements:` **oder** `evidence:`". So bleibt UI-Verifikation
  auf Story-Ebene, ohne das backend-only-@Tag-Gate zu verbiegen.

## Ändern statt Umschreiben (Supersede)

Soll eine **bestehende** Anforderung inhaltlich anders werden: die alte **nicht**
editieren. Stattdessen:

1. Neues `REQ-MMMM` mit `supersedes: REQ-NNNN` anlegen (nächste freie Nummer).
2. Im alten `REQ-NNNN`: `status: superseded` + `superseded_by: REQ-MMMM` setzen
   (nur diese zwei Felder — Statement/Rationale bleiben als Historie stehen).
3. Bei Stories analog: `status: superseded`/`retired` **nur mit** `superseded_by:`
   (ADR- oder Nachfolger-Pointer, Klasse 8). Ein ADR ist fällig, wenn die Ablösung
   eine Architekturentscheidung ist — dann kurz erwähnen, nicht selbst anlegen
   (dafür gäbe es einen eigenen ADR-Skill).

## Schritt 5 — Abschluss

1. **Gate-Vorprüfung** anbieten:
   `mvn -q -pl blocpress-req-check verify` (oder Hinweis, dass es beim nächsten
   `mvn verify` mitläuft). Bricht es, die gemeldete Fehlerklasse benennen und fixen.
   Die generierten Ansichten (`SPEC.md`, `CATALOG.md`, `planning/README.md`,
   `STATUS.md`) aktualisieren sich in diesem Schritt selbst — **nicht** von Hand.
2. **Commit** auf einem `docs/<kurz>`-Branch (Branch-Konvention), atomar, mit
   sprechender Nachricht (`docs(spec): <Titel> (REQ-NNNN/US-NNNN)`). Die
   generierten Ansichten (`STATUS.md`, `CATALOG.md`, `planning/README.md`,
   `SPEC.md`) sind **versioniert** und werden vom Gate-Lauf neu geschrieben —
   stage sie **zusammen** mit den Quell-Artefakten + `ROADMAP.md` in **einen**
   Commit, damit der Repo-Stand konsistent bleibt (nicht von Hand editieren,
   nur mitziehen, was der Build erzeugt hat).
3. **Kurz zusammenfassen**, welche IDs vergeben wurden und was der nächste
   natürliche Schritt ist (z.B. „Umsetzung → `@Tag(\"REQ-NNNN\")`-Test, dann Status
   auf `implemented`/`verified` heben").
4. **Auf `/arc42` hinweisen** (lose Kopplung, keine Zwangs-Kette): fragen, ob der
   **Architektur-Impact** der neuen Anforderung geprüft werden soll — `/arc42
   REQ-NNNN` klärt, ob etwas in der Architektur zu berücksichtigen/ändern/
   entscheiden ist, oder ob es keinen Impact gibt. Nicht selbst ausführen, nur
   anbieten.

## Leitplanken (Zusammenfassung)

- Beobachtung von Intention trennen: eine Anforderung ist eine **Absicht**, kein
  aus Code abgeleitetes Verhalten.
- Lieber wenige, scharfe, prüfbare Requirements als viele vage.
- Im Zweifel: fragen. Der Skill ist ein Gesprächspartner, kein Generator.
