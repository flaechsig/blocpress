---
name: compiler-guard
description: >
  Früher Fehler-Fänger. Verifiziert nach jeder Code-Änderung, dass das Modul kompiliert
  und — in den Quarkus-Modulen — der CDI-Graph auflösbar ist, BEVOR der volle Test läuft.
  Fängt subtile Killer ab (CDI-Qualifier, `*/` im Javadoc, Encoding, truncatierte Dateien).
---

# compiler-guard

Kompilierungs- und CDI-Wächter.

## Modul-Kontext (blocpress)

- `blocpress-core` — reines Java/odfdom, **kein CDI**: Schritt 4 (CDI-Graph) entfällt.
- `blocpress-render` / `blocpress-workbench` / `blocpress-studio` — Quarkus/CDI:
  Schritt 4 ist hier der wichtigste frühe Fänger.

## Wann
- Nach Herauslösung/Umbenennung einer Klasse.
- Vor jedem Merge in `main`.
- Bei Build-Fehler, der nicht klar ein Syntax-Fehler ist.

## Checkliste (Reihenfolge)

1. **Kompilierung**
   ```bash
   mvn -q compile -pl <modul>          # nur Modul
   mvn -q compile -pl <modul> -am      # + Abhängigkeits-Module
   ```
   → `BUILD SUCCESS` oder die 1–3 konkreten Fehler.

2. **Test-Kompilierung**
   ```bash
   mvn -q test-compile -pl <modul>
   ```

3. **Javadoc / Encoding / Truncation**
   - `*/` im Javadoc/Blockkommentar beendet den Kommentar vorzeitig → Parser-Fehler.
     `grep -nE '/\*.*\*/' <datei>` darf pro Kommentar nur das echte Abschluss-`*/` zeigen.
   - Encoding-Fehler als `Unzulässiges Zeichen` → `file <datei>` prüfen, ggf. als ASCII neu schreiben.
   - "Dateiende beim Parsen" → `wc -l` gegen `git show main:<datei>`; bei Truncation aus
     `git` wiederherstellen und gezielt neu setzen.

4. **CDI-Graph-Auflösbarkeit (nur Quarkus-Module)**
   - RestClients: `@RestClient`-Qualifier gesetzt?
   - Neue zustandslose Helfer: CDI-Scope vorhanden (`@Dependent` o.ä.)?
   - Schnellster Check: EINEN Quarkus-Test der Ziel-Klasse ausführen
     `mvn test -pl <modul> -Dtest=<NeueKlasse>Test`. Der Container löst den ganzen Graph;
     `UnsatisfiedResolutionException` wird hier sichtbar.
   - In `blocpress-core` übersprungen (kein CDI).

5. **Bericht**: "PASS" oder präzise Fehlerliste (Datei:Zeile + Grund + Fix).

## Anti-Pattern
- Nur `mvn test` ohne `compile`/`test-compile`/CDI-Check → maskiert Fehler durch
  "Could not load class …" im Discovery.
- Große Dateien (>1500 Zeilen) blind per Suchen/Ersetzen editieren — bei Truncation
  `git restore` + gezielt neu setzen.
