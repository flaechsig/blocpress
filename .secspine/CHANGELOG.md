# Changelog

## 0.9

Offene Lücken nicht veröffentlichen (ADR-0009). Arbeitsweise: Bei öffentlichem Hauptzweig
bleiben offene `SEC-` mit ihren Stories auf dem Security-Branch, Fix-Branches zweigen davon ab,
nach `main` geht alles, wenn die `SEC-` behoben sind. Notbremse: `secspine.py pre-push`
verweigert einen Push, dessen Stand ein offenes `SEC-` ohne `publish: <Datum>` enthält;
`secspine.py install-hook` richtet ihn als Git-Hook ein (ergänzt einen vorhandenen Hook, setzt
kein `core.hooksPath`). `secspine-run` bietet das beim ersten offenen `SEC-` an.


## 0.8

Im docspine-Modus gelten die Regeln von docspine (ADR-0008). Ab docspine 0.21 legt
`secspine-report` je bestätigtem Fund ein `docs/11-risks/SEC-NNNN.md` an, statt eine
Tabellenzeile zu schreiben; Stories nennen ihr Risiko in `addresses`, Verweise von Hand und
„behoben am …“ entfallen. Ein `R-`, das sich als Security-Thema herausstellt, wird durch ein
`SEC-` abgelöst. Ältere docspine-Versionen behalten die Tabelle als Übergang. `secspine-run`
zählt offene `SEC-`Risiken und die Stories dazu als offene Lücken vor Merge und Push.


## 0.7

`secspine-run` arbeitet auf einem eigenen `security/<datum>`-Branch und schließt ihn am Ende
sichtbarkeits-bewusst: privat/ohne Remote per --no-ff mergen, bei öffentlichem Hauptzweig mit
offenen Funden auf dem Branch belassen (kein Offenlegen). Nie pushen ohne Zustimmung (US-0014).


## 0.6

Neuer Skill `secspine-run`: Standardlauf Scan → Verifikation → Bericht ohne Rückfrage, Halt
erst vor der Dokumentations-Frage. Kandidaten werden schadensfrei aus der Analyse verifiziert;
aktive Nachweise werden markiert statt blind ausgeführt und am Ende gemeldet (US-0014). Der
docspine-Modus gleicht Funde mit schon dokumentierten Risiken ab, statt sie doppelt zu führen.


## 0.5

`secspine.py version` zeigt die installierte Version und prüft über den dist-Zweig auf
Updates (nur Python-stdlib, offline-sicher). Erster Teil von US-0007.


## 0.4

docspine-Modus (US-0012): `secspine-report` destilliert bestätigte Funde nach Freigabe in
arc42 Kapitel 11 (eigener Abschnitt, Präfix `SEC-`, Status-Spalte), legt Aufträge als Stories
im Epic `E-SECURITY` an und verlinkt beidseitig; behobene Einträge bleiben erhalten.
Installer schützt `logs/` und `certs/` per mitgeliefertem `.gitignore` (auch beim curl-Install).


## 0.3.1

Fix: `render` legt den Ordner `security-review/` an, falls er fehlt (vorher Absturz,
wenn noch kein Befund angelegt war).


## 0.3

Engagement-Ablage mit englischen Namen (docspine-Konvention: Namen englisch, Inhalt in
Projektsprache): Ordner `security-review/` mit `scope.md`, `enumeration.md`, dem generierten
`report.md` und den Befund-Dateien `F-NN.md` in einem Ordner. Das Prüfwerkzeug erkennt
Befunde am ID-Muster. Ersetzt `befunde/` mit `00_scope.md`/`06_bericht.md`.


## 0.2

Portscan standardmäßig mit naabu (MIT) statt nmap; nmap bleibt optional für die
Versionserkennung (ADR-0006). Hintergrund: nmaps NPSL schränkt die kommerzielle Nutzung ein,
secspine ist 0BSD. enum-Skill und Scanner-Flotte im Standard nennen naabu zuerst.

## 0.1

Erste ausgelieferte Fassung. Enthält die Befund-Kette (`STANDARD.md`,
`finding-template.md`), das Prüfwerkzeug `secspine.py` (nur Python-Standardbibliothek)
und die Phasen-Skills `secspine-enum`, `secspine-scan`, `secspine-verify`,
`secspine-triage`, `secspine-report`.
