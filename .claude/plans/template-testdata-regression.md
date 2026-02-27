# Epic 3 Erweiterung: Template-Testdaten & Regressionstest

## Übersicht
Erweitern der Workbench um:
1. Auto-generierte Testdaten-Formulare basierend auf Template-Feldern
2. Verwaltung mehrerer Testdaten-Sets pro Template
3. Speicherung von PDFs als "expected result" für Regressionstests
4. Regression-Test Durchführung (PDF-Vergleich)

## Änderungen am Datenmodell

### 1. Neue Entität: `TestDataSet`
```java
@Entity
@Table(name = "test_data_set")
public class TestDataSet extends PanacheEntityBase {
    @Id
    public UUID id;

    @ManyToOne
    @JoinColumn(name = "template_id", nullable = false)
    public Template template;

    @Column(nullable = false)
    public String name;  // z.B. "Standardfall", "Grenzfall", "Fehlerfall"

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    public JsonNode testData;  // Die Test-JSON

    @Column(columnDefinition = "bytea")
    public byte[] expectedPdf;  // Gespeichertes "expected" PDF

    @Column
    public String pdfHash;  // SHA-256 Hash für schnellen Vergleich

    @Column(nullable = false)
    public Instant createdAt;

    @Column
    public Instant updatedAt;
}
```

### 2. Änderung an `Template` Entität
```java
// Neue Methode:
public TestDataSet getDefaultTestDataSet() {
    // Gibt das erste TestDataSet zurück oder erstellt ein leeres
}

// Neue Relation:
@OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
public List<TestDataSet> testDataSets = new ArrayList<>();
```

### 3. Neue REST-Endpoints in TemplateResource

```
POST   /api/workbench/templates/{id}/testdata
       → Neues TestDataSet anlegen

GET    /api/workbench/templates/{id}/testdata
       → Alle TestDataSets für Template

PUT    /api/workbench/templates/{id}/testdata/{testDataId}
       → TestDataSet aktualisieren

DELETE /api/workbench/templates/{id}/testdata/{testDataId}
       → TestDataSet löschen

POST   /api/workbench/templates/{id}/testdata/{testDataId}/render
       → PDF für TestDataSet generieren

POST   /api/workbench/templates/{id}/testdata/{testDataId}/save-expected
       → PDF als "expected result" speichern

GET    /api/workbench/templates/{id}/testdata/{testDataId}/expected-pdf
       → Speicherte "expected" PDF herunterladen

POST   /api/workbench/templates/{id}/testdata/{testDataId}/compare
       → Regressionstest: Ist-PDF vs. Expected-PDF vergleichen
```

## Implementierungsschritte

### Phase 1: Backend - Datenmodell (Sprint 1)

**1.1 TestDataSet Entität erstellen**
- Datei: `blocpress-workbench/src/main/java/io/github/flaechsig/blocpress/workbench/TestDataSet.java`
- Mit Flyway-Migration: `V002__add_testdata_set_table.sql`

**1.2 Template aktualisieren**
- Relation zu TestDataSet hinzufügen
- Methode `getDefaultTestDataSet()` implementieren

**1.3 Repository & Service**
- `TestDataSetRepository extends PanacheRepository<TestDataSet, UUID>`
- `TestDataSetService` mit CRUD-Operationen

**1.4 Validierung**
- Sicherstellen dass TestDataSet-JSON die erforderlichen Felder des Templates hat
- Validierung gegen Template.validationResult.userFields

### Phase 2: Backend - REST-API (Sprint 1)

**2.1 TestDataSet Endpoints**
```java
@POST
@Path("{templateId}/testdata")
@Transactional
public Response createTestDataSet(@PathParam("templateId") UUID templateId,
                                   TestDataSetDTO dto)
```

**2.2 PDF-Verwaltung**
```java
@POST
@Path("{templateId}/testdata/{testDataSetId}/save-expected")
@Transactional
public Response saveExpectedPdf(@PathParam("templateId") UUID templateId,
                                @PathParam("testDataSetId") UUID testDataSetId,
                                byte[] pdfContent)
```

**2.3 Regression-Test**
```java
@POST
@Path("{templateId}/testdata/{testDataSetId}/compare")
@Consumes(MediaType.APPLICATION_JSON)
public Response compareWithExpected(@PathParam("templateId") UUID templateId,
                                   @PathParam("testDataSetId") UUID testDataSetId)
// Gibt: { isMatch: boolean, expectedHash: string, actualHash: string, diff?: string }
```

### Phase 3: Frontend - Web Component (Sprint 1-2)

**3.1 Testdaten-Form Generator**
- Neue Komponente `_renderTestDataForm()`
- Liest `template.validationResult.userFields`
- Erstellt Input-Felder basierend auf Feldtypen:
  - String → Text Input
  - Number → Number Input
  - Boolean → Checkbox
  - Array → Repeatable section
  - Object → Nested form

**3.2 TestDataSet-Verwaltung UI**
- Liste aller TestDataSets
- Add/Edit/Delete-Buttons
- JSON-Vorschau
- Optionen: "Als Standard setzen", "Expected PDF speichern"

**3.3 Regression-Test UI**
- Wenn Expected PDF gespeichert: "Test ausführen" Button
- Zeigt: ✓ Match oder ✗ Difference
- Option: Neues PDF als Expected speichern

## Datenbank-Migration

```sql
-- V002__add_testdata_set_table.sql
CREATE TABLE test_data_set (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES template(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    test_data JSONB NOT NULL,
    expected_pdf BYTEA,
    pdf_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_test_data_set_template_id ON test_data_set(template_id);
```

## API-Datenstrukturen

```json
// GET /api/workbench/templates/{id}/testdata
{
  "id": "uuid",
  "name": "Standardfall",
  "testData": {
    "firstname": "John",
    "lastname": "Doe",
    "items": [...]
  },
  "hasExpectedPdf": true,
  "pdfHash": "sha256...",
  "createdAt": "2026-02-28T...",
  "updatedAt": "2026-02-28T..."
}

// POST /api/workbench/templates/{id}/testdata/{testDataSetId}/compare
{
  "isMatch": false,
  "expectedHash": "sha256...",
  "actualHash": "sha256...",
  "similarity": 0.95,
  "message": "95% Ähnlichkeit - 5 Pixel unterschieden"
}
```

## Tests

### Unit Tests
- `TestDataSetValidationTest.java`
  - Validierung vs. Template-Felder
  - Fehlende erforderliche Felder

- `TestDataSetServiceTest.java`
  - CRUD-Operationen
  - Beziehung zu Template

### Integration Tests (WorkbenchHealthIT)
- TestDataSet anlegen/abrufen
- PDF als expected speichern
- Regression-Test durchführen
- Mit unterschiedlichen Daten vergleichen

## Abhängigkeiten

- **Vor Phase 1:** Keine (unabhängig)
- **Vor Phase 3:** Phase 1+2 müssen abgeschlossen sein

## Aufwandsschätzung

- Phase 1 (Datenmodell): 3-4 Stunden
- Phase 2 (REST-API): 4-5 Stunden
- Phase 3 (Frontend): 6-8 Stunden
- Tests: 4-5 Stunden
- **Total: ~20-25 Stunden**

## Offene Fragen

1. **PDF-Vergleich:** Pixel-basiert oder Text-basiert?
   - Empfehlung: Text-Extraktion (robuster gegen Layout-Änderungen)
   - Bibliothek: Apache PDFBox oder iText

2. **Hash-Strategie:** Sollten wir nur PDF-Inhalts-Hash vergleichen?
   - Ja, um Metadaten/Timestamps zu ignorieren

3. **Multiple Testdaten parallel:** Pro Template max. wie viele TestDataSets?
   - Empfehlung: Keine Limite, aber UI-Warning ab 20

4. **Performance:** Sollten PDFs komprimiert gespeichert werden?
   - Ja, für Production sinnvoll
