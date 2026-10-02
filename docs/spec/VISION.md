# Vision — blocpress

## Warum es das gibt

Dokumente aus Vorlage + Daten zu erzeugen ist eine Allerweltsaufgabe — und wird
trotzdem immer wieder neu und schlecht gelöst: entweder in starren
Code-Templates (Layout und Logik verwoben, jede Änderung ein Deploy) oder in
schweren Serverprodukten. blocpress zielt auf die Mitte: **ein fachlich
gestaltbares Vorlagenformat** (LibreOffice Writer / ODT) plus **eine schlanke
Render-Engine**, die die Vorlage mit JSON füllt.

## Das treibende Ziel

**Wer eine Vorlage gestalten kann, kann ein neues Dokument erzeugen — ohne
Entwickler, ohne Deploy.** Benutzerfelder mit Punkt-Notation bilden auf
JSON-Pfade ab; Abschnitte und Tabellenzeilen werden zu Wiederhol- und
Bedingungsgruppen; externe ODT-Dateien liefern geteilte Textbausteine. Daran
misst sich die Engine: je weniger Code eine neue Vorlage braucht, desto besser.

## Die Herunterbrechung

Diese Vision bricht sich nach unten fort:

- **Epics** (`spec/epics/`) — die großen Themen: Render-Pipeline,
  Formatkonvertierung, Render-Service, Template-Speicherung, Workbench, Freigabe,
  Administration, Studio, Auslieferung und Release-Automatisierung.
- **Stories** (`spec/stories/`) — konkrete Nutzergeschichten je Thema.
- **Requirements** (`spec/requirements/`) — atomare, in EARS formulierte,
  testgebundene Systemregeln (die Wirbelsäule).

Quer dazu beschreibt **arc42** (`architecture/`), *wie* es gebaut ist, und
**planning/** zeigt, *wo wir stehen*.

## Nicht-Ziele (Abgrenzung)

- Kein WYSIWYG-Editor — die Gestaltung passiert in LibreOffice Writer.
- Keine eigene Auszeichnungssprache — die Vorlage *ist* ein ODT.
- LibreOffice wird nur zur Laufzeit für PDF/RTF-Export gebraucht, nicht im Kern.
