# Versionierung

Eine Vorlage wird über ihren **Namen** angesprochen. Unter einem Namen liegen mehrere
Versionen; welche gilt, entscheiden Gültigkeitsbeginn und Ablaufdatum
([US-0005](../01-goals/stories/US-0005.md)). Ein Name bezeichnet genau eine Sache, eine Vorlage
oder einen Baustein ([REQ-0039](../01-goals/requirements/REQ-0039.md)); je Name gibt es höchstens
einen Entwurf ([REQ-0042](../01-goals/requirements/REQ-0042.md)), und zu jedem Zeitpunkt gilt
höchstens eine Version ([REQ-0040](../01-goals/requirements/REQ-0040.md),
[ADR-0018](../09-decisions/ADR-0018.md)).

| Feld | Gesetzt | Bedeutung |
|---|---|---|
| `version` | beim Hochladen | höchste vorhandene Version desselben Namens und Typs plus 1, sonst 1; beim Duplizieren unter gleichem Namen höchste Version des Namens plus 1 |
| `validFrom` | bei der Freigabe: ohne Datum oder mit heutigem Datum „ab jetzt“, mit künftigem Datum ab Tagesbeginn; ein vergangenes Datum wird mit 400 abgelehnt ([REQ-0041](../01-goals/requirements/REQ-0041.md)). Vor der Freigabe ohne Bedeutung | ab wann die Version gilt; ein Datum in der Zukunft plant den Wechsel |
| `reviewCycleYears` | bei der Freigabe, optional | Review-Zyklus in Jahren |
| `validUntil` | bei der Freigabe `validFrom + reviewCycleYears`, ohne Zyklus leer; beim Zurückziehen der Zeitpunkt des Zurückziehens; bei der Freigabe einer Nachfolgerin deren `validFrom` | bis wann die Version gilt; leer heißt unbegrenzt |

Die Freigabe einer Version beendet die bis dahin gültige zum Beginn der neuen, in der Workbench
und beim Import in render ([REQ-0040](../01-goals/requirements/REQ-0040.md)). Ist schon eine
Version freigegeben, die später beginnt, wird die Freigabe mit 409 abgelehnt
([REQ-0064](../01-goals/requirements/REQ-0064.md)). Zurückziehen beendet in render die
Gültigkeit, gelöscht wird dort nichts ([REQ-0037](../01-goals/requirements/REQ-0037.md)). Welche
Version bei einem Druck galt, folgt damit aus Druckzeitpunkt und Bestand.

Eindeutig ist die Kombination (`name`, `valid_from`, `version`), in beiden Datenbanken.
Hochladen, Kopieren unter gleichem Namen und „neuer Entwurf“ legen einen neuen Datensatz an,
aber nur, wenn es zu dem Namen keinen Entwurf gibt und der Name nicht vom anderen Typ benutzt
wird (sonst 409); WebDAV-PUT legt nur für einen unbekannten Namen Version 1 an. Alte Versionen
bleiben erhalten, solange niemand sie ausdrücklich löscht; eine freigegebene lässt sich nicht
löschen. Bestehende Daten, die diese Regeln verletzen, meldet die Workbench beim Start im Log und
ändert sie nicht ([REQ-0043](../01-goals/requirements/REQ-0043.md), `TimelineConflictReport`).

_(confidence: verified — blocpress-workbench/…/entity/Template.java (`@UniqueConstraint`,
`hasDraft`, `usedByOtherType`), TemplateResource.java (`upload`, `duplicate`, `createNewDraft`,
`updateStatus`, `approvalStart`, `endPreviousVersion`), WebDavResource.java (`putDraft`),
TimelineConflictReport.java, blocpress-render/…/TemplateImportResource.java,
docker/studio/init-studio.sql; derived_from:
Element_Design_Concept.adoc:847-876 legacy (git history),
Element_Design_Concept.adoc:975-998 legacy (git history))_

## Welche Version render nimmt

`POST /api/render/{name}` nimmt aus `production` unter allen Einträgen des Namens mit
`validFrom ≤ jetzt` und (`validUntil` leer oder `> jetzt`) den mit dem **jüngsten
`validFrom`**, bei Gleichstand die höchste Version. Findet sich keiner, antwortet render
mit 404. render ermittelt die gültige Version bei jedem Aufruf neu; ein Versionswechsel durch
Zeitablauf, ein Import oder ein Zurückziehen wirkt sofort, auf allen Instanzen
([REQ-0038](../01-goals/requirements/REQ-0038.md), [REQ-0063](../01-goals/requirements/REQ-0063.md)).

_(confidence: verified — blocpress-render/…/ProductionTemplate.java (`findValidId`),
TemplateCache.java, TemplateContentCache.java, RenderResource.java)_

Die Workbench wählt für ihre eigenen Zugriffe nach Namen (`GET …/by-name/{name}/content`,
WebDAV unter `/api/webdav/released/`) unter den freigegebenen, gültigen Versionen die **höchste
Version**. Weil zu jedem Zeitpunkt höchstens eine Version gilt, ist das dieselbe wie in render;
nur bei Altdaten mit überlappender Gültigkeit, die der Start meldet, können sie sich
unterscheiden.

_(confidence: verified — blocpress-workbench/…/entity/Template.java (`findLatestActiveByName`))_

Gegenüber dem Altbestand korrigiert: Eindeutig ist nicht (`name`, `version`), sondern
(`name`, `valid_from`, `version`). render wählt nicht die höchste Version, sondern zuerst nach
`validFrom`.
