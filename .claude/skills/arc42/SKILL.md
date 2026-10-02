---
name: arc42
description: >
  Architektur-Impact-Analyse zu einer Anforderung oder Änderung. Beantwortet
  „muss für REQ-NNNN/diese Änderung etwas in der Architektur berücksichtigt,
  geändert oder entschieden werden?" — verifiziert die Frage gegen den Code,
  klassifiziert in vier Verdikte (Trägt schon / Berührt Kapitel / Entscheidung
  fällig / Kein Impact) und pflegt bei echtem Impact das arc42
  (docs/architecture/index.adoc) mit confidence/derived_from-Disziplin und
  sichtbaren Widersprüchen. Beratend; der Mensch entscheidet, was gilt. Aufruf:
  /arc42 <REQ-/US-ID | Kapitel | Änderungsbeschreibung> (z.B. /arc42 REQ-0011,
  /arc42 8.3, /arc42 "PDF-Export wird gecacht").
---

# /arc42 — Architektur-Impact prüfen und arc42 pflegen

Du beantwortest zu einer Anforderung oder Änderung die Frage: **„Was bedeutet das
für die Architektur?"** Du fragst dazu **den Code**, nicht den Menschen. Der
Mensch kommt nur ins Spiel, wenn Code und Doku sich widersprechen oder eine
Entscheidung fällig ist — dann sagt er, was gilt (vgl. `docs/CONVENTIONS.md`:
„Der Mensch sagt, was gilt").

Der Skill ist **kein Doku-Generator**. Sein häufigstes gutes Ergebnis ist ein
knappes, belegtes Verdikt — oft **„kein Architektur-Impact"**. arc42 dokumentiert
bewusst grob („nicht durchdokumentieren, Ebene 2 offen"); souverän *nichts* zu
schreiben ist so wertvoll wie zu schreiben.

Maßgeblich und **vor Beginn zu lesen**: `docs/CONVENTIONS.md` (Abschnitt
„arc42 & Altbestand"). Diese Datei ist die Kurzfassung — bei Widerspruch gewinnt
`CONVENTIONS.md`.

## Argumente

- `$1…` — Ausgangspunkt: eine **REQ-/US-ID** (z.B. `REQ-0011`), ein **Kapitel**
  (`5`, `8.3`) oder eine **Änderungsbeschreibung** in eigenen Worten.
  Ohne Argument: offene/berührte Kapitel erkennen (aus den `// arc42-status:`-
  Markern + `docs/STATUS.md`-Liste „offene arc42-Kapitel") und das nächste
  sinnvolle Ziel vorschlagen — **nicht** ungefragt durchdokumentieren.

## Eiserne Regeln (vor jedem Schritt präsent halten)

1. **Beleg vor Behauptung.** Keine Architektur-Aussage ohne Code-Beleg
   (grep/read/Test). Die Verifikation-gegen-Code dient hier der *Impact-Frage*,
   nicht dem Formulieren.
2. **Beobachtung ≠ Intention.** Aus Code abgeleitetes Verhalten ist *Beschreibung*
   (arc42, mit `confidence`), **keine** Anforderung. Absichten gehören nach
   `docs/spec/` → Verweis auf `/anforderung`.
3. **`confidence` + `derived_from` an jeder Aussage.** Werte: `verified`
   (Code-belegt) · `unverified` (nur Altdoku, ungeprüft) · `aspirational`
   (Zielbild, nicht gebaut) · `stale` (überholt). Ein Code-vs-Doku-Konflikt ist
   `contradicted`.
4. **Widerspruch bleibt sichtbar.** Bei Divergenz Altdoku/Doku ↔ Code: `[CAUTION]`-
   Block + `// arc42-contradiction:`-Marker setzen — **nie** still glätten. Die
   Auflösung entscheidet der Mensch.
5. **`UNKNOWN` respektieren, kapitel-lokal, nicht durchdokumentieren.** Nur das
   vom Impact berührte Kapitel anfassen; unberührtes bleibt `UNKNOWN`.
6. **Beratend, nicht autonom-schreibend.** Erst Verdikt + (falls Impact) AsciiDoc-
   Snippet als Klartext → **explizite Freigabe** → dann `index.adoc` editieren.
7. **Generierte Ansichten nie von Hand editieren.** `STATUS.md` (offene Kapitel,
   `contradicted`-Liste) entsteht bei `mvn verify` neu. DOT→SVG-Diagramme neu
   rendern, wenn ein Diagramm berührt wird — nicht das SVG von Hand.

## Die Flughöhen-Grenze (was ist arc42, was darunter?)

**arc42-Ebene (Impact möglich):** neue/veränderte **Bausteine** (Kap. 5), neue
**Abhängigkeiten zwischen Modulen**, **Laufzeit-Interaktionen** (Kap. 6),
**Verteilung/Betrieb** (Kap. 7), **Querschnittskonzepte** (Kap. 8, z.B.
Sicherheit, Caching, Transformation, Persistenzschicht), **Qualitätsanforderungen**
(Kap. 10), **Risiken** (Kap. 11), und alles, was eine **Architekturentscheidung**
ist (Kap. 9 / ADR).

**Darunter (i.d.R. „Kein Impact"):** ein einzelner neuer Endpoint, eine
UI-Komponente, ein Feld an einer bestehenden Entität, eine JEXL-Regel, ein
Template — solange kein Baustein, keine Modulabhängigkeit, kein Querschnitts-
konzept und keine Entscheidung berührt ist.

Im Zweifel: die **Wirkung** prüfen, nicht die Größe der Änderung. Ein kleines Feld
kann ein Querschnittskonzept berühren (z.B. neue PII → Kap. 8.3 Sicherheit);
ein großes Feature kann rein element-lokal sein.

## Schritt 0 — Zustand erkennen (still, ohne zu schreiben)

- Ausgangspunkt laden: bei REQ-/US-ID die Datei unter `docs/spec/` lesen
  (Statement, rationale, evidence). Bei Kapitel: den Abschnitt in `index.adoc` +
  seinen `// arc42-status`-Marker. Bei Änderungsbeschreibung: die betroffenen
  Module/Dateien identifizieren.
- Landkarte: `grep -n "// arc42-status" docs/architecture/index.adoc` und die
  `STATUS.md`-Offenliste sichten; bestehende `confidence`/`derived_from`/
  `// arc42-contradiction`-Marker im Zielkapitel merken.

## Schritt 1 — Impact klassifizieren (Herzstück)

Für die Ausgangs-Anforderung/Änderung die Architektur-Frage gegen den Code
beantworten. Belege **inline** beschaffen (grep/read/Test); nur wenn die Suche
über viele Module/Dateien streut, `Explore`-Subagenten fan-out.

Prüfe entlang der Flughöhen-Grenze und ordne in **genau ein** Verdikt ein:

| Verdikt | Bedeutung | Folge |
|---|---|---|
| **Trägt schon** | Bestehende Architektur deckt es ab | Nichts ändern; ggf. einen **Constraint** nennen, den die Umsetzung einhalten muss (belegt) |
| **Berührt Kapitel X** | Architektur ändert sich | Kapitel-Edit vorschlagen (mit Beleg + `confidence`) |
| **Entscheidung fällig** | Es steckt eine Architekturentscheidung drin | Kontext/Optionen skizzieren, an **`/adr`** übergeben (nicht selbst als ADR schreiben) |
| **Kein Impact** | Rein element-/UI-lokal, unterhalb arc42 | Ehrlich „nichts auf Architektur-Ebene zu dokumentieren" — begründen, fertig |

Ein Ergebnis kann kombiniert sein (z.B. *Trägt schon* + ein *Constraint*). Nenne
für jede Aussage den Code-Beleg.

## Schritt 2 — Widersprüche/Drift prüfen (nur bei Kapitel-Berührung)

Wenn du ein bestehendes Kapitel anfasst: neue Evidenz gegen die dort stehenden
Aussagen (und Altbestand-Marker) halten. Jede Divergenz Code ↔ Doku wird
`contradicted` + `[CAUTION]`/`// arc42-contradiction:` — nicht stillschweigend
korrigiert. Die inhaltliche Auflösung legst du dem Menschen vor.

## Schritt 3 — Vorschlag im Chat (vor dem Schreiben)

Zeige das **Verdikt** als Klartext. Bei Impact zusätzlich das konkrete AsciiDoc-
Snippet: welches Kapitel, welcher Text, welche Provenienz-/`confidence`-Fußzeile,
welche `link:decisions/ADR-NNNN.adoc`-Querverweise, welche Marker. **Warte auf
Freigabe.** Bei „ändere X" iterieren.

Bei Verdikt **Kein Impact** endet der Skill hier — keine Datei-Änderung, nur die
belegte Begründung.

## Schritt 4 — arc42 schreiben (nur bei echtem Impact, nach Freigabe)

- `docs/architecture/index.adoc` editieren; das berührte Kapitel auf
  `// arc42-status: FILLED` setzen (falls es `UNKNOWN` war).
- Jede eingetragene Aussage bekommt die Provenienz-/`confidence`-Fußzeile im
  Hausstil, z.B.:
  `_(derived_from: <Quelle/Code-Pfad> · confidence: verified — <kurzer Beleg>)_`
- ADR-Querverweise als `link:decisions/ADR-NNNN.adoc[ADR-NNNN]` setzen.
- Diagramm berührt? DOT-Quelle unter `diagrams/*.dot` pflegen und neu rendern:
  `dot -Tsvg diagrams/<name>.dot -o diagrams/<name>.svg` (fehlt Graphviz: nur das
  DOT ändern und die Neu-Rendern-Notwendigkeit als TODO melden).

## Schritt 5 — Abschluss

1. **`mvn verify`** anstoßen (oder darauf hinweisen): regeneriert `STATUS.md`
   inkl. Offenliste + `contradicted`-Liste. Generierte Ansichten **nicht** von
   Hand anfassen.
2. **Commit** auf einem `docs/<kurz>`-Branch (Branch-Konvention), atomar. Die
   regenerierten Ansichten **zusammen** mit `index.adoc` (+ ggf. SVG) in **einen**
   Commit — Repo-Stand konsistent halten. Nachricht z.B.
   `docs(arc42): <Kapitel/Thema> — <Impact> (REQ-NNNN)`.
3. **Zusammenfassen:** das Verdikt, was (nicht) geändert wurde, und der nächste
   Schritt. Bei **Entscheidung fällig**: ausdrücklich an `/adr` verweisen (mit dem
   skizzierten Kontext). Bei **Constraint**: den Constraint klar benennen, damit
   die Umsetzung ihn kennt.

## Leitplanken (Zusammenfassung)

- Der Skill misst **Wirkung auf die Architektur**, nicht Größe der Änderung.
- „Kein Impact" ist ein vollwertiges, wertvolles Ergebnis — es schützt arc42 vor
  Bloat.
- Beleg schlägt Plausibilität. Widerspruch bleibt sichtbar. Entscheidungen gehen
  an `/adr`, Absichten an `/anforderung`.
