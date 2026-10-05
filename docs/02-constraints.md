# Randbedingungen

| Was | Version | Wofür |
|---|---|---|
| Java | 21 oder neuer | Sprache aller Module |
| Maven | 3.9 oder neuer | Build |
| Docker | aktuell | Integrationstests, Container-Image von blocpress-render, `@QuarkusTest`s in render (PostgreSQL über Quarkus DevServices) |
| LibreOffice | 24 oder neuer, `soffice` im `PATH` | PDF- und RTF-Konvertierung zur Laufzeit in blocpress-render und in Tests |
| Python | 3.9 oder neuer | docspine-Prüfung |

_(confidence: unverified — übernommen aus der bisherigen `CLAUDE.md`, Versionen nicht einzeln
gegen Build und Laufzeit geprüft)_

Ohne LibreOffice wird `TransformTest` in core übersprungen; dann fehlt der Nachweis für
REQ-0012, und die Prüfung meldet ihn. Auf solchen Rechnern prüft
`python3 .docspine/docspine.pyz check --without-tests` nur die Dokumentation.
