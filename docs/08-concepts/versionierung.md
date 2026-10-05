# Versionierung

Eine Vorlage wird über ihren **Namen** angesprochen. Unter einem Namen liegen mehrere
Versionen; welche gilt, entscheiden Version, Gültigkeitsbeginn und Ablaufdatum
([US-0005](../01-goals/stories/US-0005.md)).

| Feld | Gesetzt | Bedeutung |
|---|---|---|
| `version` | beim Hochladen | höchste vorhandene Version desselben Namens und Typs plus 1, sonst 1; beim Duplizieren unter gleichem Namen höchste Version des Namens plus 1 |
| `validFrom` | beim Anlegen auf den Anlagezeitpunkt; bei der Freigabe auf das mitgegebene Datum (Tagesbeginn), sonst unverändert | ab wann die Version gilt; ein Datum in der Zukunft plant den Wechsel |
| `reviewCycleYears` | bei der Freigabe, optional | Review-Zyklus in Jahren |
| `validUntil` | bei der Freigabe `validFrom + reviewCycleYears`, ohne Zyklus leer; beim Zurückziehen der Zeitpunkt des Zurückziehens | bis wann die Version gilt; leer heißt unbegrenzt |

Eindeutig ist die Kombination (`name`, `valid_from`, `version`), in beiden Datenbanken.
Hochladen legt immer einen neuen Datensatz an; alte Versionen bleiben erhalten, solange
niemand sie ausdrücklich löscht.

_(confidence: verified — blocpress-workbench/…/entity/Template.java (`@UniqueConstraint`,
Vorbelegung von `validFrom`), TemplateResource.java (`upload`, `duplicate`, `updateStatus`),
docker/studio/init-studio.sql; derived_from:
Element_Design_Concept.adoc:847-876 legacy (git history),
Element_Design_Concept.adoc:975-998 legacy (git history))_

## Welche Version render nimmt

`POST /api/render/{name}` nimmt aus `production` unter allen Einträgen des Namens mit
`validFrom ≤ jetzt` und (`validUntil` leer oder `> jetzt`) den mit dem **jüngsten
`validFrom`**, bei Gleichstand die höchste Version. Findet sich keiner, antwortet render
mit 404. Weil render den Inhalt bis zu 10 Minuten zwischenspeichert, kann ein
Versionswechsel, der allein durch Zeitablauf eintritt, bis zu 10 Minuten später wirken;
ein Import leert den Zwischenspeicher sofort.

_(confidence: verified — blocpress-render/…/ProductionTemplate.java (`findLatestActiveByName`),
TemplateCache.java, RenderResource.java, application.properties (`expire-after-write=10M`))_

Die Workbench wählt für ihre eigenen Zugriffe nach Namen (`GET …/by-name/{name}/content`,
WebDAV unter `/api/webdav/released/`) anders: freigegeben, gültig und dann die **höchste
Version**, ohne Rücksicht auf `validFrom`. Liegt eine höhere Version mit älterem `validFrom`
neben einer niedrigeren mit jüngerem, liefern Workbench und render verschiedene Stände.

_(confidence: verified — blocpress-workbench/…/entity/Template.java (`findLatestActiveByName`))_

Gegenüber dem Altbestand korrigiert: `validFrom` wird bei der Freigabe nicht auf „jetzt“
gesetzt, sondern bleibt der Anlagezeitpunkt, wenn kein Datum mitgegeben wird. Eindeutig ist
nicht (`name`, `version`), sondern (`name`, `valid_from`, `version`). render wählt nicht die
höchste Version, sondern zuerst nach `validFrom`. Beim Zurückziehen löscht render alle
Einträge des Namens, nicht nur die zurückgezogene Version.

- UNKNOWN — offene Frage: Ist die unterschiedliche Auswahl in Workbench und render gewollt, und soll das Zurückziehen einer Version wirklich alle Versionen dieses Namens aus production entfernen?
