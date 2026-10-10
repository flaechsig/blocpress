---
name: secspine-run
description: >-
  Standardlauf eines Security-Tests auf einem eigenen Branch: Scan, Verifikation aller
  Kandidaten und Bericht in einem Zug, ohne Rückfrage, bis zur Frage der Dokumentation; am
  Ende den Branch schließen (sichtbarkeits-bewusst). Verwenden, wenn ein Projekt einmal
  durchlaufen werden soll, oder bei Aufruf /secspine-run. Einzelne Phasen: secspine-scan,
  secspine-verify, secspine-report.
---

# secspine-run

Du führst den **Standardlauf** auf einem **eigenen Branch** — von Phase zu Phase ohne Rückfrage,
bis zu dem einen Punkt, an dem ein Mensch entscheiden muss (Übertragung in die Dokumentation),
und schließt den Branch am Ende.

## Ablauf

0. **Branch anlegen.** Der Arbeitsbaum muss sauber sein — sonst stoppen und das sagen. Merke dir
   den aktuellen Branch als **Basis** und lege `security/scan-<datum>` an (`git checkout -b`).
   Der ganze Lauf passiert dort, nie direkt auf dem Hauptzweig.
1. **Scope** lesen (`security-review/scope.md`). Fehlt für einen **aktiven** Teil Scope oder
   Freigabe, diesen Teil überspringen und am Ende als offenen Punkt melden — nicht blockieren.
2. **Enumeration** nur, wenn ein laufendes Ziel im Scope ist, sonst überspringen → `secspine-enum`.
3. **Scan** → `secspine-scan`: Kandidaten anlegen.
4. **Verifikation aller Kandidaten** → `secspine-verify` je Kandidat:
   - **Schadensfrei aus der Analyse** (Code lesen, Erreichbarkeit, Konfiguration) wird direkt
     verifiziert — `confirmed` oder `dismissed`.
   - Braucht ein Kandidat einen **aktiven/eingreifenden** Nachweis (Live-Exploit, DAST gegen ein
     laufendes System) oder ein **menschliches Urteil**, wird er **nicht blind ausgeführt**:
     als `candidate` belassen, als „braucht manuelle Prüfung" vermerkt, weiterlaufen.
5. **Bericht** → `secspine-report`: `check`, dann `render`.
6. **Halt an der Dokumentations-Frage.** Erkennst du eine Architektur-Doku (docspine), schlägst
   du die Übertragung nach Kapitel 11 vor (secspine-report, docspine-Modus) und **wartest auf
   Freigabe**. Ohne Doku sind `security-review/` + Bericht das Ergebnis.

## Abschluss — den Branch schließen (sichtbarkeits-bewusst)

1. **Committen** auf dem Branch: die in die Doku gelangten Risiken/Stories und, falls gewünscht,
   der Install. `security-review/` bleibt je nach Modus Arbeitsstand/ignoriert; `logs/` und
   `certs/` sind durch das mitgelieferte `.gitignore` geschützt.
2. **Prüfen**: in docspine-Projekten `python3 .docspine/docspine.pyz check` — muss OK melden.
3. **Sichtbarkeit prüfen** (`git remote` + Hoster): ist der Hauptzweig **öffentlich**, **nicht
   mergen und nicht pushen**, solange bestätigte Funde **offen** sind — das legt Schwachstellen
   offen. Dann auf dem Branch belassen und sagen: schließen, sobald behoben (oder Repo privat).
   Arbeitsweise bis dahin (ADR-0009): Fix-Branches zweigen vom Security-Branch ab und werden
   dorthin zurückgeführt; nach `main` geht der Security-Branch erst, wenn alle seine `SEC-`
   `closed` sind. Der Security-Branch selbst wird nicht auf ein öffentliches Remote gepusht.
   Ein offenes `SEC-` zu veröffentlichen entscheidet nur ein Mensch, im Einzelfall; dann trägt
   es `publish: <Datum>`.
   **Notbremse:** Ist noch keine Sperre eingerichtet (`.git/hooks/pre-push` ruft
   `secspine.py pre-push` nicht auf), biete an, sie mit `python3 .secspine/secspine.py
   install-hook` einzurichten. Nur nach Zustimmung; ein vorhandener Hook wird ergänzt, nicht
   ersetzt.
   In docspine-Projekten zählt als offen auch jedes `docs/11-risks/SEC-NNNN.md` mit
   `status: open`, jede Story, die ein solches `SEC-` in `addresses` nennt, und jede
   Commit-Nachricht oder Branch-Bezeichnung, die die Lücke beschreibt. Das Präfix `SEC-` macht
   sie mit `git grep` und `git log --grep` auffindbar.
4. **Schließen**: ist die Basis **privat oder ohne Remote** und sind keine blockierenden Punkte
   offen (Doku-Entscheidung getroffen), den Branch per `git merge --no-ff` in die Basis
   zurückführen und löschen. **Pushen** nur mit ausdrücklicher Zustimmung.

Bleibt etwas Wesentliches offen (manuelle Verifikation aussteht, Doku nicht freigegeben, Ziel
öffentlich mit offenen Funden), bleibt der Branch bestehen — du sagst, was zum Schließen fehlt.

## Am Ende meldest du kurz

- wie viele Funde bestätigt / verworfen,
- welche Kandidaten **noch manuell** zu verifizieren sind (mit Grund),
- was **übersprungen** wurde (fehlender Scope/fehlende Freigabe),
- die offene Entscheidung (Doku ja/nein) und **ob der Branch geschlossen** oder noch offen ist.

## Regeln

- **Kein aktiver oder zerstörerischer Nachweis ohne ausdrückliche Freigabe** — im Zweifel
  markieren statt tun. Das ist der „Abbruch vorher": als Info gemeldet, nicht verschwiegen.
- **Nichts in die Dokumentation schreiben ohne Freigabe** (Schritt 6).
- **Offene Funde nie in einen öffentlichen Hauptzweig** mergen oder pushen; das gilt auch für
  offene `SEC-` in der Doku und die Stories dazu (ADR-0009).
- **Die Sperre nie umgehen** (`--no-verify`) ohne ausdrückliche Entscheidung des Menschen.
- Einzelne Phasen bleiben einzeln aufrufbar; `secspine-run` ist der bequeme Standardweg.
