---
name: nachweis
description: >
  Prüft die Ehrlichkeit der Kette Anforderung↔Test — nicht ob Tests grün sind
  (das ist der Build), sondern ob der Nachweis *vollständig* (jede implemented-REQ
  und jede EARS-Klausel wirklich belegt) und *sinnhaft* ist (der zu REQ-X getaggte
  Test prüft tatsächlich REQ-X, kein hohler Tag). Zwei Modi: Preflight (das
  req-check-Gate laufen lassen und seine acht Fehlerklassen in lesbare, an den
  Eigentümer-Skill geroutete Handlungsanweisungen übersetzen) und semantischer
  Deckungs-Audit (je getaggte REQ ein Verdikt VOLL/TEIL/HOHL, bei vielen REQs per
  Fan-out). Der Skill PRÜFT den Nachweis, er erzeugt ihn nicht (das ist /umsetzung)
  und editiert weder Tests noch Spec — er flaggt und reicht weiter. NICHT Code-
  Coverage (JaCoCo) und NICHT allgemeine Testqualität (das sind code-review/
  test-tracker). Aufruf: /nachweis <REQ-NNNN | Modul | gate | (leer = alle implemented)>
  (z.B. /nachweis REQ-0007, /nachweis gate, /nachweis).
---

# /nachweis — die Ehrlichkeit der Traceability prüfen

Du prüfst, ob der **Nachweis** hinter der Traceability trägt. Das Gate
(`blocpress-req-check`) beantwortet eine *Tatsachenfrage* rein maschinell: „trägt
ein grüner Test die REQ-ID?" Du beantwortest die *Sinnfrage* darüber: „**beweist**
dieser Test wirklich, was die Anforderung sagt — vollständig und ehrlich?" Zwei
Ebenen, die sich ergänzen; deine ist die, die ein grünes Gate nicht sieht.

Zwei Fragen leiten dich:
1. **Vollständigkeit des Nachweises.** Ist jede `implemented`-REQ getaggt-getestet,
   und deckt der Test **jede EARS-Klausel** ab (WHEN-Trigger *und* Response; IF *und*
   THEN; WHILE-Zustand)? Eine halb bewiesene Anforderung ist nicht bewiesen.
2. **Sinnhaftigkeit des Nachweises.** Prüft der zu REQ-X getaggte Test wirklich das
   Verhalten von REQ-X — oder ist er ein **hohler Tag** (grün, aber trivialer Getter,
   unbezogene Assertion, Verhalten nie ausgeführt)?

**Was dieser Skill NICHT ist** (scharf halten, sonst franst er aus):
- **Kein Code-Coverage** (JaCoCo, Zeilen-/Branch-Prozente) — es geht um *Anforderungs*-
  Deckung, nicht um ausgeführte Zeilen.
- **Keine allgemeine Testqualität / Bug-Suche** — das sind `/code-review` und
  `test-tracker`.
- **Erzeugt den Nachweis nicht.** Test schreiben/nachschärfen macht `/umsetzung`;
  Requirement korrigieren macht `/anforderung`. Du **prüfst und flaggst**, editierst
  weder Tests noch `spec/`. (Jedes Artefakt bleibt bei seinem Eigentümer-Skill.)

Maßgeblich und **vor Beginn zu lesen**: `docs/CONVENTIONS.md`, Abschnitt
„Traceability" (die acht Fehlerklassen, die req-trace/req-check-Mechanik). Diese
Datei ist die Kurzfassung — bei Widerspruch gewinnt `CONVENTIONS.md`.

## Argumente

- `$1…` — der Prüfumfang:
  - **`gate`** (oder `preflight`) → **Modus A**: Gate laufen lassen, Fehlerklassen
    übersetzen und routen. Der schnelle „warum ist `mvn verify` rot?"-Pfad.
  - eine **REQ-ID** (`REQ-0007`) → **Modus B** für genau diese Anforderung.
  - ein **Modul** (`blocpress-render`) → **Modus B** über dessen
    getaggte REQs.
  - **leer** → **Modus B** über **alle `implemented`-REQs** (Voll-Audit, Fan-out).

## Eiserne Regeln (vor jedem Schritt präsent halten)

1. **Prüfen, nicht erzeugen.** Du schreibst keine Tests, editierst keine `spec/`-
   Datei und flippst keinen Status. Du lieferst Verdikte + geroutete Empfehlungen.
   (Ausnahme: reine Lese-/Build-Schritte — Gate laufen lassen, Reports lesen.)
2. **Grün ≠ bewiesen.** Ein grüner `@Tag`-Test ist die *Eintrittskarte*, nicht der
   Beweis. Erst die EARS-Klausel-für-Klausel-Prüfung entscheidet VOLL/TEIL/HOHL.
3. **Gegen das EARS-Statement prüfen, nicht gegen den Testnamen.** Der Testname
   verspricht viel; es zählt, was die Assertions das Produktivverhalten *treiben*
   und *festnageln* lassen.
4. **Lücke ≠ immer Test-Lücke.** Ein Fund kann ein **Requirement-Defekt** sein
   (falscher Akteur, unprüfbares Statement, überladene Klausel). Dann ist der
   Ausweg `/anforderung` (Supersede/Schärfen), **nicht** ein nachgeschärfter Test,
   der ein falsches Statement zementiert. (Referenz aus der Methodik-Herkunft: ein falscher Akteur im Statement wird via Supersede behoben, nicht im Test.)
5. **Belegpflicht respektieren.** Reine UI-Stories werden über `evidence:`
   (Frontend-Test-Pfad (z.B. Vitest/Playwright)) belegt, nicht über Backend-`@Tag` — ein fehlender
   Backend-Tag ist dort **kein** Fund (Fehlerklasse 7 erlaubt „`requirements:`
   **oder** `evidence:`").
6. **Verdikt mit Beleg.** Jedes VOLL/TEIL/HOHL nennt die konkrete Test-Stelle
   (Datei:Zeile, Assertion) und die (nicht) abgedeckte EARS-Klausel. Keine
   Plausibilitäts-Urteile.
7. **Nichts Generiertes von Hand anfassen.** `STATUS.md`/`CATALOG.md` etc. entstehen
   im Gate-Lauf; du liest sie, editierst sie nie.

## Schritt 0 — Zustand erkennen (still)

```bash
# Katalog: welche REQs sind implemented (nur die MÜSSEN bewiesen sein)?
grep -l 'status: implemented' docs/spec/requirements/REQ-*.md
# Ist-Coverage je Modul (von req-trace geschrieben; Voraussetzung: volle Modul-Suite lief)
find . -path '*/target/req-coverage.json' -exec echo {} \; -exec cat {} \;
# getaggte Tests im Code (Sinn-Prüfung braucht die Test-Stelle)
grep -rn '@Tag("REQ-' --include=*.java . | grep -v /target/
```

> **Fallstrick (aus der Praxis):** `-Dtest=<Klasse>`-gefilterte Läufe überschreiben
> die `req-coverage.json` des Moduls auf nur diese Klasse → das Gate meldet dann
> *scheinbar* fehlende Nachweise. Vor jeder Nachweis-Prüfung die **volle** Modul-
> Suite laufen lassen (`mvn -q -pl <modul> test`), sonst prüfst du ein Artefakt.

## Modus A — Preflight (Gate übersetzen & routen)

1. Gate laufen lassen: `mvn -q -pl blocpress-req-check verify` (bzw. Hinweis auf
   `mvn verify`). Grün → melden „Nachweis-Kette formal konsistent" und ggf. Modus B
   für die *inhaltliche* Tiefe anbieten.
2. Rot → jede gemeldete Zeile in **eine der acht Fehlerklassen** einordnen, in
   Klartext übersetzen und an den **Eigentümer-Skill** routen:

   | Klasse | Bedeutung | Ausweg (Eigentümer) |
   |---|---|---|
   | 1 | `implemented`, aber kein grüner Test zur REQ-ID | Test bauen → `/umsetzung`; oder Status ehrlich zurück (`planned`) |
   | 2 | Test trägt REQ-ID, die im Katalog fehlt | REQ anlegen → `/anforderung`; oder Tag korrigieren |
   | 3 | `planned`, aber Tests dazu sind grün | Status-Flip `implemented` → `/umsetzung` (nach Deckungs-Check) |
   | 4 | Story → nicht existierendes Requirement | Verweis/REQ richten → `/anforderung` |
   | 5 | Story → nicht existierender Epic | Epic anlegen/Verweis richten → `/anforderung` |
   | 6 | Story/Epic mit ungültigem `status` | Status setzen → `/anforderung` |
   | 7 | `verified`-Story ohne `requirements` **und** ohne `evidence` | Beleg ergänzen → `/umsetzung` (evidence) / `/anforderung` |
   | 8 | `superseded`/`retired` ohne `superseded_by` | ADR-Pointer setzen → `/anforderung` / `/adr` |

   **Der Unterschied zum bloßen Gate-Log:** du sagst *warum* und *wohin*, nicht nur
   *dass*. Ein Klasse-1-Fehler kann heißen „Test fehlt" **oder** „Status ist
   unehrlich" — benenne beide Lesarten und empfiehl die richtige.

## Modus B — Semantischer Deckungs-Audit

Für **jede** zu prüfende `implemented`-REQ (nur diese müssen bewiesen sein):

1. **EARS zerlegen.** Statement in seine Klauseln brechen: Trigger/Bedingung
   (WHEN/IF/WHILE/WHERE) **und** Response („shall …"). Jede Klausel ist ein
   Nachweis-Posten. `obligation`/`rationale` mitlesen (was ist die *eigentliche*
   Akzeptanz?).
2. **Test lokalisieren.** Den/die `@Tag("REQ-NNNN")`-Test(s) lesen — was treiben die
   Assertions am Produktivverhalten, was nageln sie fest?
3. **Verdikt fällen:**
   - **VOLL** — jede EARS-Klausel wird durch eine Assertion belegt, die das echte
     Verhalten ausführt (nicht nur einen Getter/Konstanten).
   - **TEIL** — Kern läuft, aber eine Klausel/die eigentliche Akzeptanz ist unbelegt
     (z.B. „in parallel" nie gegen Sequenz gemessen; POST-Regel prüft nur den Getter,
     nicht die Auswertung; nur 1 Mapping → Selektion nie gezwungen).
   - **HOHL** — Tag vorhanden, aber der Test übt das REQ-Verhalten nicht aus
     (unbezogene/triviale Assertion). Grün, aber wertlos als Beleg.
4. **Fund klassifizieren:** ist es eine **Test-Lücke** (→ `/umsetzung` schärft den
   Test) oder ein **Requirement-Defekt** (falscher Akteur/unprüfbar/überladen →
   `/anforderung`)? (Eiserne Regel 4.)

**Fan-out bei Umfang.** Beim Voll-Audit (leeres Argument / Modul mit vielen REQs)
je REQ einen Audit-Subagenten (`Task`/`Explore`) parallel starten, jeder liefert das
strukturierte Verdikt {REQ, Klauseln[], Verdikt, Beleg (Datei:Zeile), Fund-Typ}.
Danach in einer Tabelle bündeln. (Validierter Trockenlauf 2026-09-22: 11 REQs, 4
parallele Agenten → 5× VOLL, 5× TEIL, 0× HOHL; alle 5 TEIL-Funde real und behoben —
unsichtbar fürs grüne Gate.)

## Schritt — Bericht & Weitergabe (kein Datei-Artefakt)

Der Nachweis-Bericht lebt **im Chat**, nicht als Datei (kein Doku-Bloat; die
eigentlichen Fixes landen über die Eigentümer-Skills in Tests/Spec). Liefere:

1. **Verdikt-Tabelle** je geprüfter REQ: `REQ · Verdikt (VOLL/TEIL/HOHL) · nicht
   gedeckte Klausel · Beleg (Datei:Zeile) · Fund-Typ (Test-Lücke | Requirement-Defekt)`.
2. **Geroutete Empfehlungen**, priorisiert: HOHL vor TEIL; Requirement-Defekte
   zuerst (sie zementieren sich sonst im Test). Je Fund der konkrete nächste Skill-
   Aufruf (`/umsetzung REQ-NNNN` bzw. `/anforderung …`), **nicht selbst ausführen**,
   nur anbieten.
3. Bei Modus A zusätzlich: ist das Gate nach den empfohlenen Schritten wieder grün
   zu erwarten? Wenn ein Fund nur ehrlicher Status ist (nicht ein fehlender Test),
   das klar sagen.

## Verhältnis zur Skill-Familie

- **Geburtsort:** `/umsetzung` Schritt 3.2 (Deckungs-Selbstcheck „beweist der Test
  wirklich das Statement?") — `/nachweis` ist genau dieser Check, aus der inneren
  Schleife herausgezogen und auf **den Bestand** anwendbar (Audit) plus auf **den
  Gate-Bruch** (Preflight).
- **Loop:** `/anforderung` (WAS) → `/arc42` (Architektur-Impact, ggf. `/adr`) →
  `/umsetzung` (Code+Test+Status-Flip) → **`/nachweis`** (prüft die Ehrlichkeit der
  entstandenen Kette). Rücksprünge geführt, nicht automatisch: TEIL/HOHL → `/umsetzung`;
  Requirement-Defekt → `/anforderung`.

## Leitplanken (Zusammenfassung)

- Grün ist die Eintrittskarte, nicht der Beweis — der Skill prüft den Beweis.
- Nur `implemented`-REQs müssen bewiesen sein; `planned`/`proposed` sind kein Fund.
- Beleg (Datei:Zeile, Klausel) schlägt Plausibilität; jedes Verdikt ist belegt.
- Eine Lücke kann ein Requirement-Defekt sein — nicht jeden Fund in den Test pressen.
- Prüfen und flaggen; fixen tun die Eigentümer-Skills. Im Zweifel: fragen.
