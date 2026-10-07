---
name: secspine-triage
description: >-
  Morgen-Triage nach einem (nächtlichen oder manuellen) Scan-Lauf: neue Roh-Logs in logs/
  gegen die bestehenden Befunde halten, echte neue Verdachtsfälle als Kandidaten anlegen,
  Lärm und Dubletten aussortieren, einen priorisierten Kurzüberblick geben. Verwenden nach
  einem Scanner-Lauf, oder bei Aufruf /secspine-triage.
---

# secspine-triage

Du machst aus dem rohen Scan-Ertrag eine kurze, sortierte Liste: was ist **neu**, was ist
**Lärm**, was lohnt die Verifikation. Du legst Kandidaten an, du bestätigst nichts (das ist
`secspine-verify`).

## Vorgehen

1. **Was ist neu?** Die jüngsten `logs/03_*`/`logs/0x_*` gegen die bestehenden
   `security-review/` halten (`python3 .secspine/secspine.py list`). Treffer, die schon
   einen Befund haben, nicht doppeln.
2. **Lärm trennen**: bekannte False Positives, informelle Hinweise, Dubletten über Scanner
   hinweg zusammenfassen. Lärm bleibt im Log, wird kein Befund.
3. **Neue Kandidaten**: je echtem neuem Verdacht `python3 .secspine/secspine.py new F-NN "…"`,
   Frontmatter + `## Was`, `source` auf den Scanner, `evidence` auf den Log.
4. **Priorisieren**: kurze Liste, nach Schwere sortiert, mit einem Satz je Eintrag, was als
   Nächstes zu verifizieren wäre.
5. `python3 .secspine/secspine.py check` — muss OK melden.

## Ton

- Kurz. Eine Übersicht, keine Nacherzählung jedes Log-Eintrags.
- Ehrlich über Unsicherheit: „Scanner meint X, noch nicht bestätigt" ist der richtige Ton
  für Kandidaten.
- Nichts aufbauschen. Ein ruhiger Lauf ohne neuen echten Verdacht ist ein gutes Ergebnis.
