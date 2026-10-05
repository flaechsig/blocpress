# Glossar

| Begriff | Bedeutung |
|---|---|
| Abdeckung | Welche Bedingungszweige und Wiederholungsfälle einer Vorlage die vorhandenen Testdatensätze durchlaufen ([US-0012](01-goals/stories/US-0012.md)). |
| Ablaufdatum (`validUntil`) | Bis wann eine freigegebene Version gilt: Gültigkeitsbeginn plus Review-Zyklus, beim Zurückziehen der Zeitpunkt des Zurückziehens; leer heißt unbegrenzt. Danach rendert render die Vorlage nicht mehr ([Versionierung](08-concepts/versionierung.md)). |
| Ablehnen | Der Prüfer schickt eine eingereichte Vorlage mit Begründung zurück in den Entwurf (`DRAFT`). |
| Asynchrones Rendern | Render-Auftrag über `POST /api/render/jobs`: Antwort sofort mit Job-ID, das Ergebnis später per Abfrage oder Webhook ([ADR-0008](09-decisions/ADR-0008.md)). |
| Baseline | Siehe Soll-PDF. |
| Baustein (Architektur) | Teil der Software in Kapitel 5, z. B. blocpress-render. Nicht zu verwechseln mit dem Textbaustein. |
| Baustein (Textbaustein) | Wiederverwendbares ODT-Fragment, z. B. Allgemeine Geschäftsbedingungen, das Vorlagen als verknüpften Abschnitt (`text:section-source`) einbinden. Technisch eine Vorlage vom Typ `BAUSTEIN` mit demselben Lebenszyklus ([US-0013](01-goals/stories/US-0013.md), [US-0032](01-goals/stories/US-0032.md)). |
| Bedingung | JEXL-Ausdruck an einem Element der Vorlage (Abschnitt, bedingter Text, Absatz, Span), der es abhängig von den JSON-Daten ein- oder ausblendet. Im Altbestand „IF-Bedingung“. |
| Benutzerfeld | Platzhalter in der Vorlage (in LibreOffice Strg+F2), dessen Name in Punkt-Notation auf einen JSON-Pfad zeigt und beim Rendern durch den Wert ersetzt wird. Im Altbestand „User-Field“. |
| blocpress-core | Java-Bibliothek mit der Merge-Pipeline und der Konvertierung über `soffice`; auf Maven Central, kein eigener Dienst ([core](05-building-blocks/core.md)). |
| blocpress-render | Dienst, der Dokumente über REST erzeugt; liest Vorlagen nur aus der Datenbank `production` ([render](05-building-blocks/render.md)). |
| blocpress-studio | Oberfläche: Portal mit Navigation, lädt die Web Component der Workbench über einen Proxy ([studio](05-building-blocks/studio.md)). |
| blocpress-workbench | Dienst für Gestaltung, Test, Suche und Freigabe von Vorlagen, mit WebDAV-Zugang ([workbench](05-building-blocks/workbench.md)). |
| Compliance-Review | Turnusmäßige Prüfung einer freigegebenen Vorlage auf inhaltliche Aktualität; fällig, wenn das Ablaufdatum im Vorlauf liegt (Standard 60 Tage) ([US-0019](01-goals/stories/US-0019.md)). |
| Determinismus | Gleiche Vorlagenversion und gleiche Daten ergeben ein Dokument mit gleichem Inhalt ([REQ-0025](01-goals/requirements/REQ-0025.md)). Voraussetzung für Regressionstests. |
| Diff-PDF | PDF, das Soll- und Ist-Seite nebeneinander zeigt und Abweichungen rot markiert ([US-0018](01-goals/stories/US-0018.md)). |
| Einreichen | Der Gestalter übergibt einen gültigen Entwurf zur Prüfung (`DRAFT` → `SUBMITTED`). |
| Freigabe | Der Prüfer gibt eine eingereichte Vorlage frei (`APPROVED`); dabei wird sie unverändert nach `production` übergeben ([US-0016](01-goals/stories/US-0016.md), [US-0017](01-goals/stories/US-0017.md)). |
| Gestalter (Vorlagengestalter) | Rolle, die Vorlagen und Textbausteine in LibreOffice Writer gestaltet, hochlädt und testet ([Kontext](03-context.md)). |
| Gültigkeitsbeginn (`validFrom`) | Ab wann eine Version gilt; bei der Freigabe wählbar, ein Datum in der Zukunft plant den Wechsel ([Versionierung](08-concepts/versionierung.md)). |
| Identity-Provider | Externer Dienst, der Benutzer verwaltet und JWT ausstellt; blocpress hat keine eigene Benutzerverwaltung ([ADR-0003](09-decisions/ADR-0003.md)). |
| Ignoriermuster | Regulärer Ausdruck für Textstellen, die der Regressionsvergleich als akzeptierte Abweichung meldet, etwa ein Datum. Gilt je Vorlage oder je Testdatensatz. |
| JEXL | Apache Commons JEXL, die Ausdruckssprache der Bedingungen. |
| LibreOffice headless | LibreOffice ohne Oberfläche, hier als Kommandozeilenaufruf `soffice --convert-to` für PDF und RTF; je Konvertierung ein eigener Prozess ([ADR-0010](09-decisions/ADR-0010.md)). |
| Native Image | Mit GraalVM vorab kompilierter Dienst ohne JVM, schneller Start und wenig Speicher ([US-0028](01-goals/stories/US-0028.md)). |
| ODT | OpenDocument Text, das Dateiformat von LibreOffice Writer; Eingabeformat der Vorlagen und mögliches Ausgabeformat. |
| production | Datenbank des Render-Service mit den freigegebenen Vorlagen und den Render-Aufträgen. Was dort liegt, gilt als freigegeben ([Domänenmodell](08-concepts/domaenenmodell.md)). |
| Prüfer (Reviewer) | Rolle, die eingereichte Vorlagen prüft, freigibt, ablehnt, zurückzieht und Compliance-Reviews durchführt. Im Altbestand auch Freigeber, Testmanager, Compliance-Reviewer ([Kontext](03-context.md)). |
| Punkt-Notation | Schreibweise für JSON-Pfade in Namen von Benutzerfeldern, z. B. `kunde.name`. |
| Quickstart | Ein Container-Image mit allen Diensten, PostgreSQL und Elasticsearch zum Ausprobieren ([US-0027](01-goals/stories/US-0027.md)). |
| Regressionstest | Vergleich einer neuen Ausgabe eines Testdatensatzes mit seinem Soll-PDF auf Ebene von Text und Lage ([US-0018](01-goals/stories/US-0018.md)). |
| Render-Auftrag (Job) | Datensatz `render_job` in `production`, Status `PENDING`, `PROCESSING`, `DONE` oder `FAILED`; auch synchrone Aufrufe per Name werden so festgehalten ([ADR-0012](09-decisions/ADR-0012.md)). |
| Rendern | Eine Vorlage mit JSON-Daten füllen und als ODT, PDF oder RTF ausgeben. Im Altbestand „Dokumentengenerierung“. |
| Review-Zyklus | Anzahl der Jahre bis zum nächsten Compliance-Review, bei der Freigabe gesetzt; bestimmt das Ablaufdatum. |
| Soll-PDF | Gespeichertes erwartetes Ergebnis eines Testdatensatzes, gegen das Regressionstests vergleichen. Im Altbestand „Baseline-PDF“. |
| Testdatensatz | Benannte JSON-Daten zu genau einer Vorlage, optional mit Soll-PDF, Notizen und Ignoriermustern ([Domänenmodell](08-concepts/domaenenmodell.md)). Im Altbestand „Test Case“. |
| Vier-Augen-Prinzip | Gestalter und Prüfer einer Vorlage sind verschiedene Personen; organisatorisch, nicht technisch durchgesetzt ([Kontext](03-context.md)). |
| Vorlage | ODT-Datei mit Benutzerfeldern, Bedingungen, Wiederholungsgruppen und eingebundenen Textbausteinen; unter einem Namen in mehreren Versionen ([Vorlagen](08-concepts/vorlagen.md)). Im Code `Template`. |
| Web Component | Browser-Standard (Custom Elements, Shadow DOM) für eigenständige Oberflächenteile; die Workbench liefert `<bp-workbench>`, das Studio bindet sie ein ([US-0024](01-goals/stories/US-0024.md)). |
| WebDAV | HTTP-Erweiterung für Dateizugriff (RFC 4918); die Workbench bietet Level 1 unter `/api/webdav/`, damit LibreOffice Entwürfe direkt öffnet und speichert ([ADR-0011](09-decisions/ADR-0011.md)). |
| Webhook | URL, die render nach Ende eines asynchronen Auftrags einmal mit `{jobId, status}` aufruft. |
| Wiederholungsgruppe | Abschnitt oder Tabellenzeile, deren Felder auf einen JSON-Array-Pfad zeigen; wird je Array-Element einmal ausgegeben. Auch „Wiederholgruppe“. |
| workbench | Datenbank der Workbench mit Vorlagen, Textbausteinen und Testdatensätzen in allen Status. |
| Worker | Platz für eine gleichzeitige `soffice`-Konvertierung in render, Anzahl über `BLOCPRESS_LO_WORKERS` ([render bemessen](guides/render-sizing.md)). |
| Zurückziehen | Eine freigegebene Vorlage wird `RETIRED` und aus `production` entfernt ([REQ-0023](01-goals/requirements/REQ-0023.md)). |

_(confidence: verified — Begriffe gegen die Kapitel 3, 5 und 8, die Stories und den Code
geprüft (Template.java, TemplateType.java, TestDataSet.java, RenderJob.java,
blocpress-studio/…/components/bp-app.js, blocpress-workbench/src/main/resources/application.properties
`blocpress.compliance.review-lead-days`); derived_from:
docs/legacy/specification/arc42.adoc:2744-2846)_

Aus dem Altbestand nicht übernommen: blocpress-admin, blocpress-proof, Self-Contained System,
Stufenübergabe über drei Stufen, Testpool, Workflow-Engine, Freigabeprozess als eigene
Entität, Content Search Engine als eigene Komponente, Dynamic Import und Micro-Frontend als
eigene Begriffe sowie die Bedienung von LibreOffice über die UNO-API.
