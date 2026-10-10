# Hands-On: blocpress-render für die eigene Last bemessen

> **Kurz:** render hält je Worker eine **warme LibreOffice-Instanz** ([ADR-0021](../09-decisions/ADR-0021.md)).
> Ein Render (1–2 Seiten) braucht dann rund **0,02 CPU-Sekunden**; ein Kern schafft ~50 Renders/s.
> Die Worker-Zahl leitet render selbst ab: **CPU-Limit abgerundet** und **so viele, wie das
> Speicherlimit trägt** (Grundbedarf + 350 MiB je Worker), mindestens 1. Engpass ist meist der
> **Speicher**, nicht mehr die CPU. **Standard: 2 CPU, 1Gi, 2 Worker** (native) ≈ 100 Renders/s je
> Pod. Mehr Last über **Replicas**. `BLOCPRESS_LO_WORKERS` kann die Zahl nur senken.

Diese Anleitung erklärt, wie du die Ressourcen für `blocpress-render` **misst statt rätst**.
Sie stützt sich auf das [Messprotokoll zu 2.8.0](measurements/render-2.8.0-native-2026-10-10.md)
(warme Instanzen), zum Vergleich auf das [Messprotokoll zu 2.5.1](measurements/render-2.5.1-2026-10-02.md)
und den Lasttest `RenderLoadIT`, mit dem du dieselbe Messung gegen deine eigene Umgebung
und deine eigenen Vorlagen wiederholen kannst.

---

## 1. Warum das nötig ist

Aus dem Betrieb bei einem Nutzer (tarifnova, Kubernetes):

- **2.4.2 (JVM):** schon im Leerlauf ~470 MiB → *OOMKilled* am 512Mi-Limit.
- **2.5.1 (native):** Leerlauf ~52 MiB — aber dann wurde das **CPU-Limit** zum Engpass:
  Mit 500m und 10 gleichzeitigen Anfragen dauerte ein Angebot im Median 6 s (p95 11 s),
  der Container wurde insgesamt 17 s CPU-Zeit lang gedrosselt.
- Die eigenen Tests liefen **nacheinander**, blocpress sah nie zwei Renders gleichzeitig —
  deshalb fiel das erst im Betrieb auf.

## 2. Begriffe

| Begriff | Bedeutung für blocpress-render |
|---|---|
| **Worker** (CPU-Limit abgerundet, mindestens 1; `BLOCPRESS_LO_WORKERS` als Obergrenze) | Wie viele `soffice`-Konvertierungen gleichzeitig laufen. Eine Semaphore im `LibreOfficePool` lässt weitere Anfragen **warten**. Jeder laufende Worker ist ein eigener LibreOffice-Prozess (~150 MiB). |
| **CPU-Request** | Was der Scheduler dem Pod **zusichert**. Bestimmt die Platzierung, begrenzt nichts. |
| **CPU-Limit** | Obergrenze als cgroup-Kontingent `cpu.max`, z.B. `50000 100000` = 50 ms je 100 ms = 0,5 CPU. |
| **CFS-Throttling** | Ist das Kontingent im 100-ms-Fenster verbraucht, **hält der Kernel den ganzen Container an** bis zum nächsten Fenster — auch wenn der Host frei ist. Sichtbar in `cpu.stat` (`nr_throttled`, `throttled_usec`). |
| **Speicherspitze** | `memory.peak` der cgroup: höchster Verbrauch seit Start. Darum **vor jeder Messung neu starten**. Enthält auch Seiten-Cache, ist also eher eine obere Schranke. |
| **Speicher-Limit** | Überschreitet der Container es, wird er beendet (*OOMKilled*) — anders als bei CPU gibt es kein Bremsen. |

## 3. Messen — Schritt für Schritt

### 3.1 Werkzeug

Der Lasttest liegt in `blocpress-e2e` und läuft **nur** mit dem Profil `load` (nie im
normalen Build). Jeder Render wird **inhaltlich** geprüft — PDF vorhanden, Pflichttexte im
richtigen Zahlenformat, Wortfolge gleich der Referenz —, denn Fehler unter Last zeigen sich
nicht am HTTP-Status (2.4.2: leere PDFs, 2.5.0: `500,000 EUR` statt `500.000 EUR`).

```bash
# Docker: der Test startet render selbst, mit --cpus/--memory als Gegenstück zu k8s-Limits
mvn verify -pl blocpress-e2e -Pload -Dload.image=flaechsig/blocpress-render:2.5.1 \
    -Dload.cpus=0.5,1,2 -Dload.workers=1,2 -Dload.levels=1,2,4,8,16

# laufende Instanz (z.B. docker run --name blocpress-render) — cgroup-Werte und Neustart über den Containernamen
mvn verify -pl blocpress-e2e -Pload -Dload.mode=external \
    -Dload.url=http://localhost:8080 -Dload.docker.container=blocpress-render

# Kubernetes — der Test startet das Deployment je Stufe neu und macht den Port-Forward selbst
mvn verify -pl blocpress-e2e -Pload -Dload.mode=k8s -Dload.k8s.namespace=blocpress \
    -Dload.k8s.deployment=blocpress-render -Dload.k8s.selector=app=blocpress-render
```

Weitere Parameter: `-Dload.requests` (Renders je Stufe, Default max(20, 4×N)),
`-Dload.timeout` (Sekunden, Default 120), `-Dload.memory` (Docker, Default `1g`),
`-Dload.async=true` (zusätzlich den Job-Pfad). Ergebnis: Tabelle im Log und
`blocpress-e2e/target/load-report.md`.

**Eigene Vorlagen messen:** In `blocpress-e2e/src/test/resources/load/scenarios.json` je
Szenario Vorlage, Daten und die Texte eintragen, die im PDF stehen müssen (`expect`) oder
nicht stehen dürfen (`forbid`) — am besten formatierte Beträge, damit auch das Zahlenformat
unter Last geprüft wird.

Ohne den Test geht es auch von Hand: vor dem Lauf Pod neu starten, dann im Container
`cat /sys/fs/cgroup/cpu.stat` vorher/nachher und `cat /sys/fs/cgroup/memory.peak` danach.

### 3.2 Basis messen (N = 1)

Ein Render nach dem anderen. Daraus: **CPU-Sekunden je Render** (CPU-s ÷ Renders) und die
Mindest-Antwortzeit. Gemessen mit dem blocpress-Lastmix und warmen Instanzen (2.8.0):
**~0,02 CPU-s**, **~0,02 s** je Render bei N = 1. Mit 2.5.1, als jede Konvertierung `soffice`
neu startete, waren es ~0,46 CPU-s und ~0,5 s, und selbst bei N = 1 wurde gedrosselt.

### 3.3 Last steigern (N = 2, 4, 8, 16)

Beobachte je Stufe Durchsatz, p95/max, Speicherspitze und Drosselung.

### 3.4 Engpass erkennen

| Beobachtung | Engpass | Abhilfe |
|---|---|---|
| Durchsatz stagniert, **Drosselung hoch** (`nr_throttled` steigt mit N) | **CPU-Limit** | mehr CPU oder mehr Replicas — **nicht** mehr Worker |
| `memory.peak` nahe Limit, *OOMKilled* | **Speicher** | Limit = Spitze + Puffer; weniger Worker |
| Durchsatz stagniert, **kaum Drosselung**, CPU unter Limit | **Worker** (Anfragen warten auf eine freie Instanz) | mehr Speicher, damit render mehr Worker ableitet (bis zur Kernzahl) |
| Antwortzeit wächst linear mit N bei gleichem Durchsatz | Warteschlange — normal unter Sättigung | Client-Timeout und Parallelität abstimmen |

### 3.5 Werte ableiten

- **Worker** = das Kleinere aus CPU-Limit (abgerundet) und (Speicherlimit − Grundbedarf) ÷ 350 MiB,
  **mindestens 1** ([REQ-0027](../01-goals/requirements/REQ-0027.md), [REQ-0103](../01-goals/requirements/REQ-0103.md)).
  Den Grundbedarf misst render beim Start: eigener Speicher + 128 MiB, nativ ≈ 180 MiB, JVM ≈ 500 MiB.
- **Speicher-Limit** für N Worker ≈ Grundbedarf + N × 350 MiB (nativ: 640Mi → 1, 1Gi → 2,
  1,5Gi → 3, 1,75Gi → 4). Die 350 MiB sind vorsichtig: kleine Dokumente brauchen weit weniger,
  sehr große (über 200 Seiten) treiben eine Instanz bis über 500 MiB, bevor render sie ersetzt.
- **CPU** ≈ gewünschter Durchsatz × 0,02 CPU-s bei Dokumenten mit wenigen Seiten; große Dokumente
  kosten mehr (50 Seiten ≈ 1 s, siehe [Seiten pro Sekunde](measurements/libreoffice-warm-2026-10-10.md)).
  Mit deinen Vorlagen nachmessen.
- **Client-Timeout** > längste gemessene Antwortzeit bei der erwarteten Parallelität.
- **Mehr Last → mehr Replicas.**

## 4. Messwerte

### 4.1 2.8.0 (native, warme Instanzen)

Auszug aus dem [Messprotokoll](measurements/render-2.8.0-native-2026-10-10.md), Lastmix aus
Rechnung, Zahlenformaten, Bedingungen und Tabellen, alle Renders inhaltlich korrekt:

| CPU-Limit | Speicher | Worker (abgeleitet) | Durchsatz/s (N = 16) | p95 | Speicherspitze |
|---|---|---|---|---|---|
| 1 | 640Mi | 1 | 48 | 0,43 s | 220Mi |
| 2 | 640Mi | 1 | 74 | 0,29 s | 219Mi |
| 2 | 1Gi | 2 | **95** (N = 4: 115) | 0,26 s | **299Mi** |
| 4 | 1Gi | 2 | 128 | 0,19 s | 299Mi |
| 4 | 1,5Gi | 3 | 170 | 0,12 s | 400Mi |

### 4.2 2.5.1 (native, ein Prozess je Konvertierung) zum Vergleich

Auszug aus dem [Messprotokoll](measurements/render-2.5.1-2026-10-02.md), N = 16 parallele
Clients (Sättigung), gleicher Lastmix:

| CPU-Limit | Worker | Durchsatz/s | p50 / p95 | Speicherspitze | gedrosselt (Perioden) |
|---|---|---|---|---|---|
| 0,5 | 1 | 1,05 | 14,9 / 19,9 s | 237Mi | 587 |
| 0,5 | 2 | 0,86 | 18,5 / 23,2 s | 449Mi | 745 |
| 0,5 | 4 | 0,78 | 20,0 / 30,1 s | 743Mi | 820 |
| 1 | 1 | **2,04** | 6,9 / 11,1 s | **281Mi** | 53 |
| 1 | 2 | 1,85 | 8,0 / 12,1 s | 437Mi | 344 |
| 1 | 4 | 1,60 | 10,0 / 12,8 s | 719Mi | 401 |
| 2 | 2 | **3,70** | 4,3 / 5,4 s | **454Mi** | 46 |
| 2 | 4 | 3,45 | 4,6 / 6,9 s | 732Mi | 185 |
| 4 | 4 | 6,40 | 2,5 / 3,8 s | 739Mi | 38 |

Was man an 2.5.1 sah (der Grund für die warmen Instanzen):

1. **Durchsatz ≈ 2 Renders/s je CPU-Kern** — er folgte dem CPU-Limit, nicht der Worker-Zahl.
2. **Mehr Worker als Kerne schaden:** mehr Speicher (+~150 MiB je Worker), mehr Drosselung,
   *weniger* Durchsatz. Mit 0,5 CPU und 2 Workern (die Konstellation aus dem Betrieb) ist
   der Durchsatz 18 % schlechter als mit 1 Worker, bei doppeltem Speicher.
3. **Unter Sättigung wächst nur die Wartezeit:** Bei 1 CPU und 16 gleichzeitigen Anfragen
   wartet die langsamste ~12 s — ein Client-Timeout von 10 s würde dort Fehler erzeugen,
   obwohl der Dienst gesund ist.

## 5. Startempfehlung

| Größe | CPU (Request = Limit) | Speicher native / JVM (Request = Limit) | Worker (abgeleitet) | ≈ Durchsatz je Pod |
|---|---|---|---|---|
| **Standard** | **2** | **1Gi** / 1280Mi | **2** | ~100/s |
| sparsam (Test/Staging, geringe Last) | 1 | 640Mi / 896Mi | 1 | ~50/s |
| mehr Last | Replicas der Standardgröße | 1Gi je Pod | 2 je Pod | ~100/s × Replicas |

- **Worker leitet render ab** aus `cpu.max` und `memory.max` der cgroup. **Ohne CPU-Limit** nimmt
  render alle Kerne des Knotens, **ohne Speicherlimit** nur die CPU — dann ein Limit setzen oder
  die Worker über `BLOCPRESS_LO_WORKERS` begrenzen; jede Instanz belegt dauerhaft Speicher.
- **2 CPU mit 640Mi** verschenkt einen Kern: render leitet nur einen Worker ab (74/s statt ~100/s).
- **JVM-Image** (`Dockerfile`): Die JVM braucht rund 380 MiB, der Grundbedarf liegt bei ≈ 500 MiB.
  Die JVM-Größen sind aus dieser Regel abgeleitet, nicht unter Last gemessen.

- **Request = Limit** bei CPU *und* Speicher macht den Pod zur QoS-Klasse *Guaranteed* und
  das Verhalten vorhersagbar. Request 1 / Limit 2 ist günstiger und darf bis 2 CPU nutzen,
  wenn der Knoten frei ist — dazu gibt es aber keine eigenen Messwerte (unter Konkurrenz
  verhält sich der Pod dann wie „1 CPU, 2 Worker“). Ein CPU-Limit unter 1 lohnt nicht: die
  Antwortzeit verdoppelt sich schon ohne Last.
- **Client-Timeout** mindestens 30 s, besser die Parallelität am Client begrenzen.
- Die Instanzen starten beim Hochfahren; render ist nach wenigen Sekunden bereit (nativ ~2 s,
  Readiness „LibreOffice instances“).
- Vollständiges Beispiel (Standardgröße): [`examples/blocpress-render-k8s.yaml`](examples/blocpress-render-k8s.yaml).

> **Mit eigenen Vorlagen nachmessen.** Große Vorlagen (viele Seiten, Bilder, lange Tabellen)
> brauchen mehr als 0,5 CPU-s und mehr Speicher je Render. Die Werte oben sind ein Start,
> keine Garantie.

## 6. Sprache im Native-Image (Fallstrick aus 2.5.0)

Zahlen und Daten formatiert blocpress selbst, nicht LibreOffice. Ein Native-Image enthält nur
die beim Build eingebundenen Sprachdaten; 2.5.0 formatierte deshalb `500,000 EUR` statt
`500.000 EUR`. Ab 2.5.1:

- `blocpress-render` wird mit `quarkus.locales=all` gebaut — eigene Native-Builds mit
  reduzierter Liste müssen alle Sprachen enthalten, die eure Vorlagen nutzen.
- `BLOCPRESS_DEFAULT_LOCALE` (Default `de-DE`) gilt für Formate **ohne** Sprachangabe in der
  Vorlage; eine Sprache im Format der Vorlage hat immer Vorrang. `LANG` im Container wirkt nicht.
- Fehlen die Sprachdaten für die eingestellte Sprache, **startet render nicht** — statt still
  falsch zu formatieren.

Der Lasttest prüft genau das unter Last mit: Pflichttexte wie `500.000,00 EUR` müssen in
jedem PDF stehen.

## 7. Bekannte Grenzen (Stand 2.5.1)

- **Job-Pfad (`/api/render/jobs`) im Native-Image 2.5.0/2.5.1 defekt:** Das Einreichen liefert
  HTTP 500 ohne Job-ID (der Job wird trotzdem verarbeitet). Behoben in **2.6.0**.
- **Job-Durchsatz (bis 2.6.1):** Der Job-Worker holte alle 2 s genau **einen** Job — höchstens
  ~0,5 Jobs/s je Instanz. Ab 2.7.0 arbeitet er die Warteschlange mit `BLOCPRESS_LO_WORKERS`
  parallelen Schleifen ab: gemessen 3,6 Jobs/s bei 2 CPU / 2 Worker (wie der synchrone Pfad),
  28 gleichzeitig eingereichte Jobs nach 7,6 s statt ~38 s fertig. Das Poll-Intervall
  (`BLOCPRESS_ASYNC_POLL_INTERVAL`) bestimmt nur noch die Startverzögerung nach Leerlauf.
