# blocpress — leichtgewichtige Dokument-Template-Engine

> LibreOffice-Writer-Vorlagen (ODT) + JSON-Daten → fertige Dokumente (ODT/PDF/RTF).
> Templates werden **gestaltet statt programmiert**.

Dies ist das **Eingangstor** in die Methodik-Doku. Es erklärt, *warum* es dieses
Projekt gibt und *wo* du für welche Frage weiterliest.

## Warum es das gibt

Serienbriefe, Angebote, Kündigungen, Verträge — überall dieselbe Aufgabe: eine
gestaltete Vorlage mit strukturierten Daten füllen. Klassisch endet das in
Code, der Layout und Logik vermischt. blocpress trennt beides: Fachleute
gestalten die Vorlage in LibreOffice Writer (Benutzerfelder, Abschnitte,
Bedingungen), die Engine füllt sie mit JSON. Ein neues Dokument ist eine **neue
Vorlage**, kein Deploy.

→ Die ganze Vision: **[spec/VISION.md](spec/VISION.md)**

## Der rote Faden

Von oben nach unten — die Doku folgt dieser Herunterbrechung:

**Vision → Themen (Epics) → Nutzergeschichten (Stories) → prüfbare Requirements**
— und quer dazu: *wie* es gebaut ist (Architektur) und *wo wir stehen* (Planung).

| Du willst wissen …                              | … dann hier                                            |
| ----------------------------------------------- | ------------------------------------------------------ |
| **Warum & Wohin** (die Vision)                  | [spec/VISION.md](spec/VISION.md)                       |
| **Was wir bauen** (Themen → Stories → Reqs)     | [planning/README.md](planning/README.md)               |
| **Die Anforderungen im Klartext**               | [spec/requirements/CATALOG.md](spec/requirements/CATALOG.md) |
| **Wie es gebaut ist** (arc42)                   | [architecture/index.adoc](architecture/index.adoc)     |
| **Wo wir stehen / was als Nächstes**            | [planning/ROADMAP.md](planning/ROADMAP.md)             |
| **Aktueller Status** (generiert)                | [STATUS.md](STATUS.md)                                 |
| **Wie diese Doku funktioniert** (Konventionen)  | [CONVENTIONS.md](CONVENTIONS.md)                       |

## Verhältnis zur bestehenden Doku

blocpress hat eine gewachsene Doku: [`product-backlog.adoc`](product-backlog.adoc),
[`specification/`](specification/) und die Design-Konzepte. Diese Methodik-Struktur
liegt **additiv** daneben und wächst entlang künftiger Änderungen; der Altbestand
wird nur **kontrolliert** überführt (siehe
[CONVENTIONS.md → Altbestand stilllegen](CONVENTIONS.md) und die
[ROADMAP](planning/ROADMAP.md)).

## In den Code

Multi-Modul-Maven (Java 21 / Quarkus). Build + Traceability-Gate:

```bash
mvn verify        # baut alle Module, prüft Requirements↔Tests, erzeugt STATUS/Kataloge
```

Die Modul-/Container-Landkarte und Betriebshinweise stehen in
[`../CLAUDE.md`](../CLAUDE.md).

---

_Dieses README ist handgeschrieben (das Eingangstor). Der generierte
Statusspiegel liegt in [STATUS.md](STATUS.md); die thematische Gruppierung
über die Epics in [planning/README.md](planning/README.md)._
