<!-- GENERIERT von blocpress-req-check aus den Requirement-Frontmattern.
     NICHT EDITIEREN — wird bei `mvn verify` neu erzeugt. -->

# Requirements-Katalog (Lese-Ansicht)

Menschenlesbare Fassung von [`docs/spec/requirements/`](.). Quelle jeder Anforderung ist die gleichnamige `REQ-NNNN.md`.

## REQ-0001  ·  `implemented`

> WHEN a template contains a user field bound to a dot-notation name, the render engine shall replace the field with the value found at that path in the JSON data.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |

**Rationale:** Die Feld-Ersetzung ist der Kern der Engine — ohne sie ist eine Vorlage nur ein statisches Dokument. Benutzerfelder mit Punkt-Notation (z.B. customer.name) bilden auf JSON-Pfade ab; genau diese Abbildung macht das „Gestalten statt Programmieren" möglich.

**Evidence:**

- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/RenderEngine.java`
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/FieldReplacementTest.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/RenderedPdfTest.java`

_Quelle: [`REQ-0001.md`](REQ-0001.md)_

---

## REQ-0002  ·  `implemented`

> WHEN a conditional element (section, conditional-text, paragraph or span) carries a JEXL condition, the render engine shall evaluate it against the JSON data and keep or drop the element according to the OpenDocument hide-condition semantics.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |
| **Source** | OpenDocument (ODF) 1.3 — text:condition |

**Rationale:** Bedingte Elemente erlauben eine einzige Vorlage für viele Fälle (z.B. Anrede je Geschlecht, optionale Klauseln). Die Auswertung muss gegen die Daten passieren, damit nicht zutreffende Teile verschwinden statt leer stehenzubleiben. Wichtig: ODF-Bedingungen sind *Hide*-Bedingungen (Bedingung wahr → Element ausgeblendet/ entfernt) — die ursprüngliche Seed-Formulierung hatte die Polarität vertauscht und wurde vor der ersten Verifikation korrigiert.

**Evidence:**

- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/OdtTemplateElement.java`
- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/JexlConditionEvaluator.java`
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/IfConditionTest.java`
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/SectionVisibilityTest.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/SectionPdfTest.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/RenderedPdfTest.java`

_Quelle: [`REQ-0002.md`](REQ-0002.md)_

---

## REQ-0003  ·  `implemented`

> WHEN a section or table row contains fields bound to a JSON array path, the render engine shall duplicate that group once per array element and index the field names accordingly.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |

**Rationale:** Listen (Positionen, Empfänger, Schadenfälle) sind der Normalfall echter Dokumente. Ohne Schleifen-Behandlung müsste die Vorlage eine feste Anzahl Zeilen vorsehen — unbrauchbar für variable Daten.

**Evidence:**

- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/RenderEngine.java`
- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/OdtTemplateDocument.java`
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/LoopTest.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/RenderedPdfTest.java`

_Quelle: [`REQ-0003.md`](REQ-0003.md)_

---

## REQ-0004  ·  `implemented`

> WHERE an output type of PDF or RTF is requested, the render engine shall convert the merged ODT document to that format using a headless LibreOffice process.

| | |
|---|---|
| **Obligation** | SOLLTE |
| **Status** | implemented |
| **Confidence** | verified |

**Rationale:** Empfänger erwarten meist PDF. Die Konvertierung gehört an den Rand (render-Modul, LibreOfficeProcessor), damit der Kern ohne LibreOffice-Abhängigkeit testbar bleibt. Als proposed markiert, solange die genaue Schnittstelle/Fehlerbehandlung noch nicht committed ist.

**Evidence:**

- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/LibreOfficeProcessor.java`
- `blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderResource.java`
- `blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/LibreOfficePool.java`
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/TransformTest.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/TemplateResourceTest.java`

_Quelle: [`REQ-0004.md`](REQ-0004.md)_

---

## REQ-0005  ·  `implemented`

> WHEN a number, currency, percentage or date style referenced by a user field declares a language, the render engine shall format the value with that language's decimal and grouping separators, regardless of the configured default locale.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |
| **Source** | OpenDocument (ODF) 1.3 — number:language / number:country |

**Rationale:** Der Gestalter legt das Zahlenformat bewusst in LibreOffice fest; die Sprache im Format ist Teil der Vorlage. Ein Vertrag mit number:language="de" darf nie als 500,000.00 EUR erscheinen — unabhängig davon, wo und mit welcher Einstellung der Dienst läuft. Genau das ist mit 2.5.0 im Native-Image bei tarifnova passiert (alle Angebots-PDFs im en-Format, auch bei ausdrücklich deutschem Format).

**Evidence:**

- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/UserFieldFormatter.java`
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/DefaultLocaleTest.java`

_Quelle: [`REQ-0005.md`](REQ-0005.md)_

---

## REQ-0006  ·  `implemented`

> WHEN a number, currency, percentage or date style referenced by a user field declares no language, the render engine shall format the value using the default locale supplied by the caller, independent of the operating-system locale.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |

**Rationale:** Die Ersatzsprache war fest einprogrammiert (de/DE) und von außen nicht einstellbar; die Betriebssystem-Locale (LANG, user.language) wirkt im Native-Image nicht verlässlich und hängt von der Laufzeitumgebung ab. ODF würde für Styles ohne Sprache die Standardsprache des Dokuments nehmen — bewusst abweichend gilt hier eine explizite Einstellung des Aufrufers, damit das Ergebnis reproduzierbar ist. Der Render-Service liefert sie aus blocpress.render.default-locale (BLOCPRESS_DEFAULT_LOCALE, Default de-DE = bisheriges Verhalten).

**Evidence:**

- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/UserFieldFormatter.java`
- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/RenderEngine.java`
- `blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderLocaleConfig.java`
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/DefaultLocaleTest.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/RenderLocaleConfigTest.java`

_Quelle: [`REQ-0006.md`](REQ-0006.md)_

---

## REQ-0007  ·  `implemented`

> IF the configured default locale is not a valid BCP-47 tag or no number-format data is available for it at runtime, THEN the render service shall refuse to start with an error naming the locale.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |

**Rationale:** Fehlende Sprachdaten waren die Ursache der 2.5.0-Regression: Ein Native-Image enthält nur die beim Build eingebundenen Locales, für alle anderen fällt DecimalFormatSymbols stillschweigend auf das Root-/en-Format zurück. Ein lauter Fehler beim Start ist besser als falsch formatierte Verträge im Betrieb, die erst beim Empfänger auffallen.

**Evidence:**

- `blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderLocaleConfig.java`
- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/LocaleSupport.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/RenderLocaleConfigTest.java`

_Quelle: [`REQ-0007.md`](REQ-0007.md)_

---

## REQ-0008  ·  `implemented`

> WHERE JWT authentication is enabled, the render service shall reject requests to the rendering, job and dashboard endpoints that carry no valid bearer token with HTTP 401; while it is disabled, these endpoints shall remain accessible without a token.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |
| **Source** | MicroProfile JWT Auth; RFC 6750 (Bearer Token Usage) |

**Rationale:** Die Render-API ist die einzige öffentlich exponierte Schnittstelle. Betreiber ohne vorgeschaltetes Gateway brauchen eine eingebaute Absicherung; bestehende Integrationen (u.a. tarifnova) rufen aber ohne Token auf. Deshalb optional und per Laufzeit-Schalter (BLOCPRESS_AUTH_ENABLED), Default aus — siehe ADR-002. Der zweite Halbsatz hält ausdrücklich fest, dass der Default nichts bricht.

**Evidence:**

- `blocpress-render/src/main/resources/application.properties`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/RenderAuthEnabledTest.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/RenderAuthDisabledTest.java`

_Quelle: [`REQ-0008.md`](REQ-0008.md)_

---

## REQ-0009  ·  `implemented`

> IF JWT authentication is enabled and no token verification key is configured, THEN the render service shall refuse to start with an error naming the missing setting.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |

**Rationale:** Ohne Schlüssel kann kein Token geprüft werden: Entweder scheitert jede Anfrage erst zur Laufzeit, oder der Betreiber glaubt sich abgesichert, ohne es zu sein. Ein Startabbruch macht die Fehlkonfiguration sofort sichtbar — gleiches Muster wie REQ-0007 für fehlende Sprachdaten. Siehe ADR-002.

**Evidence:**

- `blocpress-render/src/main/java/io/github/flaechsig/blocpress/render/RenderAuthConfig.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/RenderAuthConfigTest.java`

_Quelle: [`REQ-0009.md`](REQ-0009.md)_

---

## REQ-0010  ·  `implemented`

> WHEN a header or footer of any master page contains user fields, conditional text or conditional sections, the render engine shall resolve them with the same rules as in the document body, including number and date formatting.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |
| **Source** | OpenDocument (ODF) 1.3 — style:master-page (style:header, style:footer, -left, -first) |

**Rationale:** Kopf- und Fußzeilen tragen oft genau die Angaben, die auf jeder Seite stehen müssen — Referenz-/Vorgangsnummern für Support und Logs, Kundennummer, Seitenkopf mit Anrede. blocpress verarbeitete bisher nur den Dokumentrumpf (content.xml); in Kopf-/Fußzeilen (styles.xml) blieb der Beispielwert der Vorlage stehen, weil LibreOffice die Felder dort aus der unveränderten Deklaration neu berechnet (Anlass: tarifnova, offer.reference in der Fußzeile). Bewusst mit blocpress' eigener Formatierung statt durch Setzen der Deklaration, damit die Sprachregeln (REQ-0005/0006) und die JEXL-Bedingungslogik in Kopf/Fuß dieselben sind wie im Rumpf. Schleifen in Kopf-/Fußzeilen sind ausgenommen.

**Evidence:**

- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/OdtTemplateDocument.java`
- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/UserFieldFormatter.java`
- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/OdtTemplateElement.java`
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/HeaderFooterTest.java`
- `blocpress-render/src/test/java/io/github/flaechsig/blocpress/render/HeaderFooterPdfTest.java`
- `blocpress-workbench/src/test/java/io/github/flaechsig/blocpress/workbench/HeaderFooterFieldDetectionTest.java`

_Quelle: [`REQ-0010.md`](REQ-0010.md)_

---

## REQ-0011  ·  `implemented`

> WHEN a template contains a section linked to an external ODT document (text:section-source), the render engine shall inline that document's content before conditions, loops and fields are resolved, and shall fill the text block's fields from the JSON paths mapped in the section name.

| | |
|---|---|
| **Obligation** | MUSS |
| **Status** | implemented |
| **Confidence** | verified |
| **Source** | OpenDocument (ODF) 1.3 — text:section-source |

**Rationale:** Gemeinsame Inhalte (AGB, Sondervereinbarungen) sollen als eigene ODT-Datei gepflegt und in viele Vorlagen eingebunden werden — eine Änderung betrifft dann eine Datei statt jeder Vorlage. Der Baustein kennt seine eigenen Feldnamen (z.B. firstname); welche Daten der einbindenden Vorlage sie füllen, legt der Bereichsname fest, z.B. "SpecialAgreement(firstname=customer.0.firstName, lastname=customer.0.lastName)". So bleibt der Baustein unabhängig von der Datenstruktur der jeweiligen Vorlage.

**Evidence:**

- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/RenderEngine.java`
- `blocpress-core/src/main/java/io/github/flaechsig/blocpress/core/odt/OdtTemplateSectionElement.java`
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/TextBlockTest.java`

_Quelle: [`REQ-0011.md`](REQ-0011.md)_

---

