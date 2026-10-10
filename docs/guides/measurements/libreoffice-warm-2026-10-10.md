# Messprotokoll: LibreOffice kalt gegen warm

**Datum:** 2026-10-10 · **Image:** `flaechsig/blocpress-render:2.8.0-SNAPSHOT` (LibreOffice
24.2.7, Ubuntu 24.04) · **Limit:** `docker run --cpus 1` · **Zweck:** Bestätigung für
[ADR-0021](../../09-decisions/ADR-0021.md)

**Dokumente:** die Rechnungsvorlage aus dem Lastmix (`site/samples/quickstart/invoice.odt`),
mit render 2.8.0-SNAPSHOT als ODT gemischt, mit 3, 50, 500 und 3000 Positionen (1, 2, 11 und
72 Seiten). Die Konvertierung danach ist der Teil, um den es geht.

**Varianten:**

- *kalt* — wie render heute (`LibreOfficeProcessor`): je Konvertierung ein neuer `soffice`
  mit frischem Profil, `--convert-to pdf`. CPU = Kindprozesse (`RUSAGE_CHILDREN`).
- *warm* — eine `soffice`-Instanz mit `--accept=pipe,…`, gesteuert über `python3-uno`
  (im Test nachinstalliert): `loadComponentFromURL` (Hidden, ReadOnly), `storeToURL` mit
  `writer_pdf_Export`, `close`. CPU = `/proc/<pid>/stat` der Instanz.

Je Größe fünf Läufe (3000 Positionen: drei), Mittelwerte.

## Zeit und CPU je Konvertierung

| Positionen (Seiten) | kalt: Wand / CPU | warm: Wand / CPU | CPU kalt ÷ warm |
|---|---|---|---|
| 3 (1) | 0,48 s / 0,45 s | 0,020 s / 0,018 s | 25× |
| 50 (2) | 0,47 s / 0,44 s | 0,030 s / 0,024 s | 18× |
| 500 (11) | 0,57 s / 0,55 s | 0,144 s / 0,138 s | 4× |
| 3000 (72) | 1,56 s / 1,53 s | 1,475 s / 1,470 s | 1× |

Der Start von LibreOffice kostet rund 0,4 CPU-Sekunden, unabhängig vom Dokument. Bei
Geschäftsdokumenten mit wenigen Seiten ist er fast der ganze Aufwand; bei sehr großen
Dokumenten überwiegt die Konvertierung, und warm bringt kaum etwas, schadet aber nicht.

Ein wiederverwendetes Profil allein (neuer Prozess, gleiches Profil) senkte die Zeit nur von
~0,42 s auf ~0,37 s (Testdokument mit drei Positionen).

## Speicher der warmen Instanz

| Zeitpunkt | RSS |
|---|---|
| nach Start und einer Konvertierung | 188 MiB |
| nach den Läufen bis 3000 Positionen (74 Konvertierungen) | 330 MiB |
| nach 250 weiteren Konvertierungen mit 500 Positionen | 330 MiB |

Der Speicher steigt mit dem größten bisher konvertierten Dokument und bleibt dann stehen
(Hochwassermarke); über 250 weitere Konvertierungen wuchs er nicht. Ein Leck zeigte sich in
diesem Lauf nicht.

## Gleiche Ausgabe

| Positionen | Seiten kalt / warm | Text (`pdftotext -layout`) | abweichende Pixel (60 dpi, `compare -metric AE`) | RTF |
|---|---|---|---|---|
| 3 | 1 / 1 | gleich | 0 | Inhalt gleich, Schrifttabelle anders nummeriert (s. u.) |
| 50 | 2 / 2 | gleich | 0 | byte-gleich |
| 500 | 11 / 11 | gleich | 0 | byte-gleich |
| 3000 | 72 / 72 | gleich | 0 | byte-gleich |

Das RTF des ersten Exports nach mehreren PDF-Exporten in derselben Instanz nummeriert
Schriften in der Schrifttabelle anders (`\af8` statt `\af9`); Schriften und Text sind gleich,
weitere RTF-Exporte sind byte-gleich mit `--convert-to`. Zwei kalte Läufe sind byte-gleich.
[REQ-0025](../../01-goals/requirements/REQ-0025.md) verlangt gleichen Inhalt, nicht gleiche
Bytes.

## Grenzen

Nur eine Vorlage; ein Worker; Wandzeit ohne das Mischen in Java und ohne HTTP. Die Messung mit
`RenderLoadIT` unter Last folgt mit dem Bau.

## Seiten pro Sekunde (Nachtrag, gleicher Tag)

**Host:** AMD Ryzen 9 9950X (Desktop, hohe Einzelkernleistung), render auf **1 CPU**
begrenzt (`--cpus 1`, `BLOCPRESS_LO_WORKERS=1`), Aufrufe nacheinander. Rechnungsvorlage mit so
vielen Positionen, dass das PDF 1, 10, 30 und 50 Seiten hat.

Ende-zu-Ende über `POST /api/render/template` (Mischen und PDF), render 2.8.0-SNAPSHOT:

| Seiten | Positionen | Ø je Dokument | Seiten/s |
|---|---|---|---|
| 1 | 1 | 0,49 s | 2,1 |
| 10 | 425 | 2,48 s | 4,0 |
| 30 | 1349 | 21,8 s | 1,4 |
| 50 | 2129 | 51,7 s | 1,0 |

Nur die Konvertierung derselben, schon gemischten Dokumente:

| Seiten | kalt (heute): Wand / CPU | warm: Wand / CPU |
|---|---|---|
| 1 | 0,47 s / 0,43 s | 0,019 s / 0,016 s |
| 10 | 0,55 s / 0,52 s | 0,132 s / 0,122 s |
| 30 | 0,80 s / 0,76 s | 0,425 s / 0,427 s |
| 50 | 1,09 s / 1,05 s | 0,909 s / 0,903 s |

Bei großen Tabellen dominiert das Mischen in Java und wächst etwa quadratisch mit der Zahl
der Zeilen (dreimal so viele Zeilen, fast neunmal so lange). Vermutete Ursache: Für jedes Feld
werden die Zahlen- und Datumsformate mit `getElementsByTagName` im ganzen Dokument gesucht
(`blocpress-core/…/odt/UserFieldFormatter.java`), auch in `content.xml`, das mit jeder Zeile
wächst. Diese Werte sind deshalb kein Maß für LibreOffice und nicht zur Veröffentlichung
gedacht; nach Behebung und ADR-0021 wird neu gemessen.

## Nach US-0071 (lineares Mischen)

Gleiche Messung, render neu gebaut mit dem Index der Formate ([US-0071](../../01-goals/stories/US-0071.md)),
LibreOffice noch wie bisher mit einem Prozess je Konvertierung:

| Seiten | Positionen | Ø je Dokument | Seiten/s | vorher |
|---|---|---|---|---|
| 1 | 1 | 0,51 s | 2,0 | 0,49 s / 2,1 |
| 10 | 425 | 0,63 s | 16,0 | 2,48 s / 4,0 |
| 30 | 1349 | 1,16 s | 25,8 | 21,8 s / 1,4 |
| 50 | 2129 | 1,48 s | 33,8 | 51,7 s / 1,0 |

Bei großen Dokumenten ist jetzt die Konvertierung der größte Teil (50 Seiten: rund 1,1 s von
1,5 s); bei einseitigen Dokumenten bleibt der Start von LibreOffice der Kostentreiber, den
ADR-0021 angeht.

Große Mengen, gleiche Bedingungen (Ende-zu-Ende, 1 CPU, 1 Worker):

| Positionen | Seiten | Ø je Dokument | Seiten/s |
|---|---|---|---|
| 3 | 1 | 0,54 s | 1,9 |
| 50 | 2 | 0,55 s | 3,6 |
| 500 | 11 | 0,66 s | 16,7 |
| 3000 | 72 | 1,77 s | 40,6 |
| 10 000 | 251 | 4,65 s | 53,9 |

Die Zeit wächst linear mit einem festen Sockel von rund 0,5 s je Dokument (vor allem der
Start von LibreOffice): 3,3-fache Positionen von 3000 auf 10 000, 2,6-fache Zeit.
