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
