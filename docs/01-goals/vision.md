# Vision — blocpress

## Kernsatz

Wer eine Vorlage in LibreOffice gestalten kann, bringt neue Dokumente in Produktion –
**ohne Entwickler, ohne Deploy**: blocpress füllt die Vorlage mit JSON-Daten, sichert sie
über Test und Freigabe ab und liefert PDF, RTF oder ODT per HTTP-Aufruf.

## Warum es das gibt

Dokumente aus Vorlage + Daten zu erzeugen ist eine Allerweltsaufgabe — und wird
trotzdem immer wieder neu und schlecht gelöst: entweder in starren
Code-Templates (Layout und Logik verwoben, jede Änderung ein Deploy) oder in
schweren Serverprodukten. blocpress zielt auf die Mitte: **ein fachlich
gestaltbares Vorlagenformat** (LibreOffice Writer / ODT) plus **eine schlanke
Render-Engine**, die die Vorlage mit JSON füllt.

## Das treibende Ziel

Die Vorlage selbst trägt die Logik: Benutzerfelder mit Punkt-Notation bilden auf
JSON-Pfade ab; Abschnitte und Tabellenzeilen werden zu Wiederhol- und
Bedingungsgruppen; externe ODT-Dateien liefern geteilte Textbausteine. Daran
misst sich die Engine: je weniger Code eine neue Vorlage braucht, desto besser.

## Themen

Die großen Themen sind Render-Pipeline, Formatkonvertierung, Render-Service,
Template-Speicherung, Workbench, Freigabe, Administration, Studio, Auslieferung und
Release-Automatisierung. Epics, Stories und ihr Stand stehen in der
[Übersicht](README.md).

## Nicht-Ziele (Abgrenzung)

- Kein WYSIWYG-Editor — die Gestaltung passiert in LibreOffice Writer.
- Keine eigene Auszeichnungssprache — die Vorlage *ist* ein ODT.
- LibreOffice wird nur zur Laufzeit für PDF/RTF-Export gebraucht, nicht im Kern.
- Benutzerdefinierte Dokumenteigenschaften (`text:user-defined`, Datei → Eigenschaften →
  Benutzerdefiniert) werden nicht befüllt — Platzhalter sind ausschließlich Benutzerfelder
  (Strg+F2). Entschieden 2026-10-03: überholt, kein Bedarf.
