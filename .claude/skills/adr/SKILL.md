---
name: adr
description: >
  Fasst eine fällige Architekturentscheidung als ADR (Architecture Decision
  Record) im Hausformat unter docs/architecture/decisions/ — im Beratungsmodus,
  der Mensch entscheidet, was gilt. Nimmt typischerweise die Übergabe aus /arc42
  („Entscheidung fällig" mit skizziertem Kontext/Optionen) auf, vergibt die nächste
  freie ADR-Nummer (unveränderlich), schreibt Kontext/Entscheidung/Begründung/
  Konsequenzen mit derived_from-Belegen, verankert die Entscheidung im arc42-Kap.-9-
  Register und verdrahtet Querverweise (REQ/US-superseded, arc42-Kapitel,
  contradiction-Marker). Aufruf: /adr <Entscheidungsthema | ADR-NNNN | /arc42-Übergabe>
  (z.B. /adr Autorisierung des Config-Imports, /adr ADR-019).
---

# /adr — Architekturentscheidungen festhalten

Du hilfst dem Menschen, eine **fällige Entscheidung** als nachvollziehbaren
**ADR** festzuhalten: *welche* Optionen standen zur Wahl, *was* wurde entschieden,
*warum*, und *welche Konsequenzen* folgen. Du schärfst den Kontext, schlägst
Formulierungen vor und schreibst — nach Freigabe — die Datei im Hausformat. Du
**entscheidest nicht selbst**, was gilt; das sagt der Mensch (vgl.
`docs/CONVENTIONS.md`: „Der Mensch sagt, was gilt").

Ein ADR ist ein **Entscheidungsdokument**, kein Anforderungs- und kein
Beschreibungsdokument. Absichten/Systemverhalten gehören nach `docs/spec/`
(→ `/anforderung`); aus Code abgeleitetes Verhalten ins arc42 (→ `/arc42`). Der
ADR hält fest, *warum* die Architektur so aussieht, wie sie aussieht.

Maßgeblich und **vor Beginn zu lesen**: `docs/CONVENTIONS.md` und die
arc42-Kapitel-9-Regel (jede Entscheidung eine Datei `decisions/ADR-NNN.adoc`,
im Register gelistet). Diese Datei ist die Kurzfassung — bei Widerspruch gewinnt
`CONVENTIONS.md`.

## Argumente

- `$1…` — der Ausgangspunkt, in eigenen Worten oder als Übergabe:
  - ein **Entscheidungsthema** (z.B. `Autorisierung des Config-Imports`),
  - eine **Übergabe aus `/arc42`** („Entscheidung fällig" mit skizziertem
    Kontext + Optionen) — dann nicht neu erheben, sondern die Skizze aufnehmen und
    schärfen,
  - eine **bestehende ADR-ID** (`ADR-019`) zum Ergänzen, Nachziehen des Registers
    oder Status-Flip (proposed → accepted).
  - Ohne Argument: frage zuerst, welche Entscheidung festgehalten werden soll —
    lege nie ungefragt einen ADR an.

## Eiserne Regeln (vor jedem Schritt präsent halten)

1. **Beratend, nicht autonom-schreibend.** Erst Kontext schärfen → Vorschlag als
   Klartext im Chat → **explizite Freigabe** → dann Datei schreiben. (Ausnahme:
   reine Lese-/Zustandserkennung.)
2. **Die Entscheidung trifft der Mensch.** Steht die Richtung noch offen (mehrere
   tragfähige Optionen), lege sie dem Menschen zur Wahl vor (gern strukturierte
   Auswahlfrage) — der Skill *empfiehlt*, entscheidet aber nicht.
3. **ADR-Nummern sind unveränderlich.** Nächste freie Nummer, für immer. Ein
   bestehender ADR wird **nie umnummeriert und nie inhaltlich umgeschrieben** —
   eine geänderte Entscheidung ist ein **neuer** ADR, der den alten *supersedet*
   (siehe „Ändern statt Umschreiben").
4. **`Begründung` ist der Kern.** Ein ADR ohne belastbares *Warum* und ohne die
   *verworfenen* Alternativen ist wertlos. Notfalls nachfragen, nichts Plausibles
   erfinden.
5. **Ist-Zustand code-belegt, Entscheidung menschlich.** Der im ADR beschriebene
   *Ausgangszustand* wird gegen den Code belegt (`derived_from`), wie in `/arc42`.
   Die *Entscheidung* selbst ist autoritativ (`confidence: n/a`).
6. **Status ehrlich wählen.** `proposed`, solange die Richtung offen/unbestätigt
   ist; `accepted` erst, wenn der Mensch die Richtung freigegeben hat. Nie ein
   „accepted" vortäuschen, das niemand beschlossen hat (siehe „Status").
7. **Immer im Register verankern.** Ein ADR, der nicht in der arc42-Kapitel-9-
   Tabelle steht, ist unauffindbar. Das Register-Update gehört **zwingend** in
   denselben Vorgang (häufigster Fehler).
8. **Generierte Ansichten nie von Hand editieren.** `STATUS.md` (u.a. die
   `contradicted`-Liste) entsteht bei `mvn verify` neu — nur `index.adoc`-Marker
   und die ADR-/Register-Quelle anfassen.

## Schritt 0 — Zustand erkennen (still, ohne zu schreiben)

```bash
# nächste freie ADR-Nummer
echo "ADR next: ADR-$(printf '%03d' $(( $(ls docs/architecture/decisions/ADR-*.adoc | sed -E 's/.*ADR-0*([0-9]+)\.adoc/\1/' | sort -n | tail -1) + 1 )))"
# bestehende ADRs (Titel + Status)
grep -h '^= ADR-\|^:adr-status' docs/architecture/decisions/ADR-*.adoc
# das Register in arc42 Kap. 9 (muss die Tabelle mitpflegen)
grep -n 'link:decisions/ADR-' docs/architecture/index.adoc
```

Kläre außerdem still:
- Gibt es die Entscheidung (oder eine überlappende) **schon**? `grep -ri
  "<kernbegriff>" docs/architecture/decisions/`. Ein Treffer heißt: ergänzen,
  supersede-Fall oder wirklich neu.
- Kommt der Auftrag aus `/arc42`? Dann liegen Kontext + Optionen als Skizze vor —
  aufnehmen, nicht neu erheben.
- Berührt die Entscheidung eine bestehende REQ/US (Ablösung) oder einen
  `// arc42-contradiction:`-Marker? Merken für die Verankerung (Schritt 4).

## Schritt 1 — Die Entscheidung schärfen

Führe ein **kurzes, gezieltes** Gespräch — nur so viel, wie zum sauberen ADR nötig.
Kläre:

1. **Ist die Entscheidung wirklich Architektur?** Ein ADR lohnt für alles, was
   schwer umkehrbar ist und mehrere Bausteine/Querschnittskonzepte/Betrieb prägt
   (vgl. `/arc42`-Flughöhe). Rein element-/UI-lokale Fragen sind **kein** ADR.
2. **Kontext.** Welches Problem/welcher Zwang erzwingt eine Entscheidung? Der
   Ausgangszustand wird gegen den Code belegt (Datei/Pfad).
3. **Optionen.** Mindestens die real erwogenen Alternativen — je mit Pro/Contra.
   Ein ADR ohne verworfene Optionen verschenkt seinen Wert.
4. **Entscheidung.** Welche Option gilt? Steht sie noch offen → dem Menschen zur
   Wahl vorlegen (Regel 2), Empfehlung aussprechen, nicht selbst entscheiden.
5. **Begründung.** *Warum* diese Option — und warum die anderen **nicht**.
6. **Konsequenzen.** Was folgt (neue Abhängigkeit, Migration, Betriebswirkung,
   berührte REQ/US, aufzulösende contradiction-Marker, Folge-Umsetzung via
   `/anforderung`)? Auch unbequeme Folgen ehrlich nennen.
7. **Verhältnis zu bestehenden ADRs.** Ergänzt/verfeinert es einen ADR, oder löst
   es einen ab (supersede)? Querverweise sammeln.

## Schritt 2 — Vorschlag im Chat (vor dem Schreiben)

Zeige den kompletten Entwurf als Klartext: ADR-Nummer, Titel, Status, die fünf
Abschnitte (Kontext/Entscheidung/Begründung/Konsequenzen inkl. Optionen), die
`derived_from`-Belege, die geplante **Register-Zeile** in Kap. 9 und alle
Querverweise/Marker-Änderungen. **Warte auf Freigabe.** Steht die Richtung offen,
ist die Auswahlfrage Teil dieses Schritts. Bei „ändere X" iterieren.

## Schritt 3 — ADR schreiben (nach Freigabe)

Datei `docs/architecture/decisions/ADR-NNN.adoc`, Hausstruktur:

```asciidoc
= ADR-NNN: <prägnanter Entscheidungstitel deutsch>
:adr-status: proposed | accepted

== Status
<Vorgeschlagen|Akzeptiert (YYYY-MM-DD)> — <ein Satz, was gilt / was noch offen ist>.
<ggf. Auslöser: link:../index.adoc[arc42 Kap. X] / Übergabe aus /arc42.>

== Kontext
<Welches Problem/Zwang? Ausgangszustand code-belegt. Warum ist eine Entscheidung fällig?>

== Entscheidung
<Welche Option gilt — konkret und umsetzbar. Was ausdrücklich NICHT gewählt wurde (+ knapp warum).>

== Begründung
<Warum diese Option — und warum die Alternativen nicht. Der Kern.>

== Konsequenzen
* <Folge: Abhängigkeit / Migration / Betrieb / berührte REQ/US / Marker-Auflösung / Folge-Umsetzung.>
* <auch unbequeme Folgen.>

_(derived_from: <Code-Pfade/Belege des Ist-Zustands>, <berührte ADRs/REQ> · confidence: n/a — ADR ist Entscheidungsdokument; der Ist-Zustand darin ist code-verifiziert.)_
```

- Optionen mit Pro/Contra gehören in `== Entscheidung` (kurz) oder — bei mehreren
  gleichwertigen — als eigener `== Optionen (Abwägung)`-Block vor der Begründung
  (Muster ADR-019).
- Querverweise als `link:ADR-NNN.adoc[ADR-NNN]` (innerhalb `decisions/`, relativ),
  arc42 als `link:../index.adoc[...]`, Spec als
  `link:../../spec/requirements/REQ-NNNN.md[REQ-NNNN]`.
- Datum aus dem `currentDate`-Kontext übernehmen, nicht raten.

## Schritt 4 — Verankern (zwingend, im selben Vorgang)

1. **arc42-Kap.-9-Register** (`docs/architecture/index.adoc`, die Tabelle unter
   „== 9. Architekturentscheidungen"): neue Zeile **am Ende** ergänzen —
   ```asciidoc
   |link:decisions/ADR-NNN.adoc[ADR-NNN] |<Kurzbeschreibung> |<proposed|accepted>
   ```
   Beim späteren Status-Flip (proposed → accepted) **beide** Stellen angleichen:
   `:adr-status:` in der ADR-Datei **und** die Register-Zeile.
2. **Querverweise/Marker:**
   - Löst der ADR eine REQ/US ab: im alten Artefakt `status: superseded` +
     `superseded_by:` setzen (das macht der Ablöse-Pfad in `/anforderung`; hier nur
     anstoßen/erwähnen, nicht die Spec umschreiben).
   - Löst der ADR einen `// arc42-contradiction:`-Marker auf: den Marker erst
     entfernen, **wenn die Umsetzung den Widerspruch real behebt** — bis dahin
     stehen lassen und im ADR referenzieren (Muster ADR-019: Marker bleibt bis
     Implementierung).
   - Berührt der ADR ein arc42-Kapitel inhaltlich: dort `link:decisions/ADR-NNN.adoc`
     setzen (ggf. via `/arc42`, wenn es mehr als ein Querverweis ist).
3. **Außerhalb `docs/` (nur erwähnen, nicht automatisch ändern):** die ADR-Liste in
   `CLAUDE.md`/`AGENTS.md` und die Projekt-Memory sind handgepflegt — auf Aktualisierung
   hinweisen, wenn der ADR bedeutsam ist.

## Status — proposed vs. accepted

- **`proposed`** — die Entscheidung ist formuliert, aber die Richtung ist noch offen
  oder nicht freigegeben (mehrere Optionen stehen, oder es fehlt die menschliche
  Zusage). Register-Status ebenfalls `proposed`.
- **`accepted`** — der Mensch hat die Richtung freigegeben. `== Status` nennt das
  Datum und ggf. die gewählte Option; verworfene Optionen bleiben in `== Entscheidung`/
  `== Begründung` als Historie sichtbar (nie löschen).
- **Später `superseded`** — durch einen neueren ADR abgelöst; im alten ADR im
  `== Status` vermerken und auf den Nachfolger verweisen (Nummer bleibt, Inhalt bleibt
  als Historie).

## Ändern statt Umschreiben (Supersede)

Soll eine **getroffene** Entscheidung revidiert werden: den alten ADR **nicht**
editieren (außer der `== Status`-Zeile „abgelöst durch ADR-MMM"). Stattdessen einen
**neuen** ADR-MMM mit eigener Nummer anlegen, der den alten im `== Kontext`/
`== Entscheidung` benennt und ablöst. Register-Zeile des alten auf `superseded`.

## Schritt 5 — Abschluss

1. **Gate-Vorprüfung** anbieten: `mvn -q -pl blocpress-req-check verify`. Der Lauf
   regeneriert `STATUS.md` (u.a. `contradicted`-Liste, falls du einen Marker berührt
   hast). Generierte Ansichten **nicht** von Hand anfassen.
2. **Commit** auf einem `docs/<kurz>`-Branch (Branch-Konvention), atomar: die ADR-
   Datei **+** die Register-Änderung in `index.adoc` (+ ggf. regeneriertes
   `STATUS.md`, ROADMAP-Notiz) in **einen** Commit, damit der Repo-Stand konsistent
   bleibt. Nachricht z.B. `docs(adr): ADR-NNN — <Titel> (<status>)`.
3. **Kurz zusammenfassen:** welche Nummer/Status vergeben wurde, was verankert wurde,
   und der nächste natürliche Schritt.
4. **Weiterreichen (lose Kopplung, keine Zwangs-Kette):**
   - Verlangt der ADR neues Systemverhalten → `/anforderung` (REQ/US + `@Tag`-Test).
   - Berührt er mehr als einen Querverweis im arc42 → `/arc42` für die Kapitelpflege.
   - Nicht selbst ausführen, nur anbieten.

## Leitplanken (Zusammenfassung)

- Ein ADR erklärt das **Warum** — verworfene Optionen sind Teil des Werts, nicht Ballast.
- Der Skill misst **Architektur-Wirkung**, nicht Größe: nur schwer Umkehrbares wird ADR.
- Beleg (Ist-Zustand) schlägt Plausibilität; die Entscheidung trifft der Mensch.
- Nummer unveränderlich, Register Pflicht, Status ehrlich.
- Im Zweifel: fragen. Der Skill ist Gesprächspartner, kein Generator.
