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
- `blocpress-core/src/test/java/io/github/flaechsig/blocpress/core/ShowVariableTest.java`

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

