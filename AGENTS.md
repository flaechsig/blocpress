# AGENTS.md

blocpress füllt LibreOffice-Writer-Vorlagen (ODT) mit JSON-Daten und erzeugt daraus
fertige Dokumente (ODT, PDF, RTF).

## Dokumentation (docspine)

Wo was steht:

- `docs/README.md`: wie die Dokumentation funktioniert
- `.docspine/STANDARD.md`: die Regeln
- `.docspine/PROFILE.md`: die Werte dieses Projekts
- `docs/01-goals/README.md`: Übersicht mit Status, offenen Fragen und Entscheidungen

Prüfen, spätestens vor dem Zusammenführen, am besten vor jedem Commit:

```
python3 .docspine/docspine.pyz render
mvn verify
python3 .docspine/docspine.pyz check
```

Die Tests tragen die Requirement-ID im `@DisplayName`, zum Beispiel
`@DisplayName("REQ-0001: …")`. `python3 .docspine/docspine.pyz check --without-tests`
prüft die Dokumentation allein.

## Bauen und testen

```
mvn clean package -DskipTests                       # bauen ohne Tests
mvn clean test -pl blocpress-core                   # Tests eines Moduls
mvn clean test -pl blocpress-core -Dtest=ShowVariableTest -Dsurefire.failIfNoSpecifiedTests=false
mvn verify -pl blocpress-core,blocpress-render -DskipITs=false        # Integrationstests (Docker)
mvn package -pl blocpress-core,blocpress-render -Dquarkus.container-image.build=true -DskipTests
mvn verify -pl blocpress-e2e -Pload -Dload.image=flaechsig/blocpress-render:2.5.1 -Dload.cpus=1 -Dload.workers=1 -Dload.levels=1,4,16
```

Der Lasttest läuft nie im normalen Build, siehe `docs/guides/render-sizing.md`.
Voraussetzungen: `docs/02-constraints.md`.

## Konventionen im Code

- Java 21, schlanke Abhängigkeiten, öffentliche Schnittstellen stabil halten.
- JaCoCo verlangt im Modul core mindestens 70 % Abdeckung der Anweisungen; der erzeugte
  OpenAPI-Code (`api.*`, `model.*`) zählt nicht mit.
- Unit-Tests vergleichen gerenderte ODT-Inhalte über den Text aus dem XML
  (`ResourceUtil.extractOdtContent()`).
- Testvorlagen liegen unter `src/test/resources/` als `.odt` mit passender `.json`-Datei.
