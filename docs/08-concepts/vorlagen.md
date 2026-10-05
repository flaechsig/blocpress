# Vorlagen

Vorlagen sind gewöhnliche ODT-Dateien aus LibreOffice Writer. Platzhalter sind
**Benutzerfelder** (Strg+F2), deren Namen in Punkt-Notation auf JSON-Pfade zeigen.
Abschnitte und Tabellenzeilen dienen als Wiederholgruppen für Arrays. Externe ODT-Dateien
lassen sich als Textbausteine über `text:section-source` einbinden, etwa für gemeinsame
Inhalte wie Allgemeine Geschäftsbedingungen.

Wie die Engine eine Vorlage verarbeitet, beschreibt der Baustein
[blocpress-core](../05-building-blocks/core.md).

_(confidence: verified — deckt sich mit REQ-0001, REQ-0003 und REQ-0011, die durch Tests
belegt sind)_
