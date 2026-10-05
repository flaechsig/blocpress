---
name: test-tracker
description: >
  Regression-Wächter. Stellt sicher, dass ein Refactoring die bestehende Funktionalität
  unverändert lässt UND jede herausgelöste Einheit zusätzlich isoliert getestet wird
  (Test-Säume aus srp-splitter), ohne Logik bestehender Tests zu verändern.
---

# test-tracker

Regression- und Abdeckungs-Wächter.

## Aufgaben
1. **Regression**: Bestehende Gesamtlogik-Tests inhaltlich unverändert (nur strukturelle
   Anpassung wie Import oder setUp-Konstruktor). Keine Assertion-Lockerung, kein Umverhalten.
2. **Ergänzen statt ersetzen**: Neue isolierte Unit-Tests je herausgelöstem Baustein kommen
   HINZU (verlängern die Suite).
3. **Metrik**: Modul-Testlauf ausführen, Ergebnis (Tests run/Failures/Errors) gegen vorher melden.

## Schritte

1. **Regression**
   ```bash
   git diff main -- <modul>/src/test/...
   ```
   Nur Import-/Konstruktor-/Set-up-Anpassungen legitim; keine Logik-/Assertions-Änderung
   an Tests der orchestrierten Funktionalität. In `blocpress-core` vergleichen die Tests
   gerenderte Inhalte über `ResourceUtil.extractOdtContent()` — dieses Vergleichsmuster
   nicht aufweichen.

2. **Test-Säume anlegen** (je Baustein eine Testklasse, gleiches Paket wie die Klasse).
   Konventionen: Erfolgsfall + Randfälle (leeres JSON, fehlender Pfad, leeres Array,
   verschachtelte Schleife) isoliert prüfen. Für die vier Pipeline-Schritte je einen
   scharfen Fall, der genau diesen Schritt beweist. Bei prozess-/dateisystemnahen
   Bausteinen (z.B. `LibreOfficeProcessor`) den Seiteneffekt kapseln/mocken.

3. **Vollen Modul-Testlauf**
   ```bash
   mvn clean test -pl <modul>
   ```
   Erfasse: `Tests run, Failures, Errors, Skipped`, `BUILD SUCCESS/FAILURE`.

4. **Bericht**
   ```markdown
   ## Testbericht
   ### Regression
   - Bestehende Tests funktional unverändert: [PASS/FAIL]
   - Strukturelle Anpassungen: <Dateien>
   ### Neue isolierte Tests
   | Testklasse | # | deckt |
   |---|---|---|
   ### Gesamtsuite
   - Tests: <n> · Failures: <n> · Errors: <n> · BUILD: <SUCCESS/FAILURE>
   ```

## Regeln
- Bestehende Tests nie nur zum Durchlaufen öffnen; nur strukturelle Anpassung.
- Je Verantwortlichkeit ein Baustein = genau eine Testklasse.
- Roter bestehender Test = Regression-Bug → zurück an Code-Baustein, KEIN Test umschreiben,
  um grün zu werden.
- **Immer `mvn clean test`** (nicht nur `mvn test`) — der Classloader ist empfindlich auf
  veraltete `.class`-Dateien.
