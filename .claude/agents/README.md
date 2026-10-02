# Refactoring-Agenten (Skills)

Dieses Repo besitzt ein kleines Team spezialisierter Claude-Skills für
SRP-Refactorings. Sie stammen aus dem Schwesterprojekt `tarifnova` und wurden an
blocpress angepasst (siehe `docs/architecture/decisions/ADR-001.adoc`).

## Skills

| Skill | Rolle | Einstieg |
|---|---|---|
| `agent-orchestrator` | Tech-Lead / Koordination & Gatekeeper | „Starte das nächste Refactoring" |
| `srp-splitter` | Analyse & Zerlegungsplan (Beratungsmodus) | „Refaktoriere <Klasse> nach SRP" |
| `compiler-guard` | Build-/CDI-/Javadoc-Verifikation | läuft automatisch nach Umsetzung |
| `test-tracker` | Regression + neue isolierte Tests | nach Verifikation |

## Typischer Ablauf

```text
agent-orchestrator (Phase 0: Ziel)
  └─> srp-splitter (Phase 1: Analyse + Plan)  -> [Freigabe]
        └─> Umsetzung
              └─> compiler-guard (Phase 3: Kompilieren/CDI)
                    └─> test-tracker (Phase 4: Regression + neue Tests)
                          └─> Abschluss (Phase 5: Full-Suite grün, prüfen, committen)
```

## Modul-Landschaft (Flughöhe für die Zerlegung)

```text
blocpress-core     -> reine Merge-Pipeline (kein CDI)
  RenderEngine.mergeTemplate(URL, JsonNode)  -> Sequenzierung der vier Schritte
    ├─ Textblock-Expansion   (text:section-source inlinen)
    ├─ Bedingungsauswertung  (JexlConditionEvaluator)
    ├─ Schleifen-Behandlung  (Abschnitte/Tabellenzeilen je Array-Element)
    └─ Feld-Ersetzung        (user-field-get / variable-get ← JSON-Pfad)
blocpress-render   -> Quarkus-REST + LibreOfficeProcessor (ODT→PDF/RTF)
blocpress-workbench / blocpress-studio -> Quarkus (Template-Lebenszyklus, UI)
```

Diese vier Pipeline-Schritte sind das natürliche Vorbild für isolierbare, je
einzeln testbare Bausteine — noch **kein** durchgeführtes Referenz-Refactoring,
sondern die Soll-Flughöhe.

## Wichtige Lessons-Learned (für neue Refactorings)

1. **CDI-Auflösbarkeit zuerst (nur Quarkus-Module)**: Injizierte MicroProfile-RestClients
   brauchen den `@RestClient`-Qualifier; zustandslose Helfer `@Dependent`. Sonst
   `UnsatisfiedResolutionException` im Container (taucht erst im Discovery auf und
   wirkt wie ein fremder Test-Fehler). In `blocpress-core` entfällt das — kein CDI.
2. **Kompilieren, bevor der Container-Test läuft**: `mvn compile` + `test-compile`
   früh; der volle `mvn test` maskiert CDI-/Classloader-Fehler als „Could not load class".
3. **`*/` im Javadoc ist fatal**: Sequenzen wie `_a*/_b*/` beenden den Kommentar und
   zerbrechen das Parsing (tarnt sich gern als Encoding-Bug).
4. **Große Dateien nicht blind per Replace editieren**: Bei Truncation
   (`git show main:…` Länge) die Datei aus git wiederherstellen und gezielt neu setzen.
5. **Regression = Funktion, nicht Zeilen**: Bestehende Tests müssen dieselbe
   Funktionalität prüfen (nur Import/Set-up-Struktur darf sich ändern);
   neue isolierte Tests kommen HINZU.
6. **Immer `mvn clean test`** statt `mvn test` — der Quarkus-Classloader ist empfindlich
   auf veraltete `.class`-Dateien.
