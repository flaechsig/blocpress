# Hands-On: blocpress-render für die eigene Last bemessen

> **Kurz:** Ein Render braucht rund **0,5 CPU-Sekunden**. Plane **1 Worker je CPU-Kern**
> (mindestens 1), **~150 MiB Speicher je Worker** plus Grundbedarf, und skaliere bei mehr
> Last über **Replicas**, nicht über Worker. Der Engpass ist fast immer das **CPU-Limit**.
> **Standard: 2 CPU, 2 Worker, 640Mi** (native) ≈ 3,7 Renders/s je Pod. Die Worker-Zahl
> leitet render ab 2.8.0 selbst aus dem CPU-Limit ab (abgerundet, mindestens 1);
> `BLOCPRESS_LO_WORKERS` kann sie nur noch senken, etwa bei knappem Speicher.

Diese Anleitung erklärt, wie du die Ressourcen für `blocpress-render` **misst statt rätst**.
Sie stützt sich auf das [Messprotokoll zu 2.5.1](measurements/render-2.5.1-2026-10-02.md)
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
Mindest-Antwortzeit. Gemessen mit dem blocpress-Lastmix: **~0,46 CPU-s**, **~0,5 s** je Render
ab 1 CPU — bei 0,5 CPU schon **~0,9 s**, und selbst bei N = 1 wird gedrosselt, weil
`soffice` beim Start kurz mehrere Kerne nutzt.

### 3.3 Last steigern (N = 2, 4, 8, 16)

Beobachte je Stufe Durchsatz, p95/max, Speicherspitze und Drosselung.

### 3.4 Engpass erkennen

| Beobachtung | Engpass | Abhilfe |
|---|---|---|
| Durchsatz stagniert, **Drosselung hoch** (`nr_throttled` steigt mit N) | **CPU-Limit** | mehr CPU oder mehr Replicas — **nicht** mehr Worker |
| `memory.peak` nahe Limit, *OOMKilled* | **Speicher** | Limit = Spitze + Puffer; weniger Worker |
| Durchsatz stagniert, **kaum Drosselung**, CPU unter Limit | **Worker** (Anfragen warten an der Semaphore) | Worker bis zur Kernzahl erhöhen |
| Antwortzeit wächst linear mit N bei gleichem Durchsatz | Warteschlange — normal unter Sättigung | Client-Timeout und Parallelität abstimmen |

### 3.5 Werte ableiten

- **Worker** = CPU-Limit abgerundet, **mindestens 1** (0,5 → 1, 1 → 1, 2 → 2, 4 → 4).
- **CPU** ≈ gewünschter Durchsatz × 0,5 CPU-s (mit deinen Vorlagen nachmessen).
- **Speicher-Limit** ≈ gemessene Spitze bei voller Last + ~30 % Puffer
  (Faustregel: ~220 MiB + ~150 MiB je weiterem Worker).
- **Client-Timeout** > längste gemessene Antwortzeit bei der erwarteten Parallelität.
- **Mehr Last → mehr Replicas.** Mehrere kleine Pods sind effizienter als ein großer
  (4 CPU / 4 Worker: 6,4 Renders/s; zwei Pods à 2 CPU / 2 Worker: ~7,4 Renders/s).

## 4. Messwerte (2.5.1, native) als Beispiel

Auszug aus dem [Messprotokoll](measurements/render-2.5.1-2026-10-02.md), N = 16 parallele
Clients (Sättigung), Lastmix aus Rechnung, Zahlenformaten, Bedingungen und Tabellen:

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

Was man daran sieht:

1. **Durchsatz ≈ 2 Renders/s je CPU-Kern** — er folgt dem CPU-Limit, nicht der Worker-Zahl.
2. **Mehr Worker als Kerne schaden:** mehr Speicher (+~150 MiB je Worker), mehr Drosselung,
   *weniger* Durchsatz. Mit 0,5 CPU und 2 Workern (die Konstellation aus dem Betrieb) ist
   der Durchsatz 18 % schlechter als mit 1 Worker, bei doppeltem Speicher.
3. **Unter Sättigung wächst nur die Wartezeit:** Bei 1 CPU und 16 gleichzeitigen Anfragen
   wartet die langsamste ~12 s — ein Client-Timeout von 10 s würde dort Fehler erzeugen,
   obwohl der Dienst gesund ist.

## 5. Startempfehlung

| Größe | CPU (Request = Limit) | Worker | Speicher native / JVM (Request = Limit) | ≈ Durchsatz je Pod |
|---|---|---|---|---|
| **Standard** | **2** | **2** (abgeleitet) | **640Mi** / 768Mi | 3,7/s |
| sparsam (Test/Staging, geringe Last) | 1 | **1** (abgeleitet) | 384Mi / — | 2/s |
| mehr Last | Replicas der Standardgröße | 2 je Pod | 640Mi je Pod | 3,7/s × Replicas |

- **Worker = CPU-Limit abgerundet, mindestens 1.** Das leitet render ab 2.8.0 selbst aus
  `cpu.max` der cgroup ab ([REQ-0027](../01-goals/requirements/REQ-0027.md)); bis 2.7.0 galt
  fest 2, und bei 500m kosteten 2 Worker 18 % Durchsatz und doppelten Speicher.
  `BLOCPRESS_LO_WORKERS` ist nur noch eine Obergrenze, etwa wenn das Speicher-Limit weniger
  Worker trägt. **Ohne CPU-Limit** nimmt render alle Kerne des Knotens — dann ein Limit setzen
  oder die Worker über `BLOCPRESS_LO_WORKERS` begrenzen.
- Die sparsame Größe ist je Kern sogar etwas effizienter (2,04 statt 1,85 Renders/s), reserviert
  aber weniger Reserve für Lastspitzen und keine Redundanz — für Produktion lieber zwei
  Standard-Pods als einen großen.
- **JVM-Image** (`Dockerfile`): gleicher Durchsatz, aber mehr
  Speicher — gemessen 585Mi Spitze bei 2 CPU / 2 Worker, daher 768Mi.

- **Request = Limit** bei CPU *und* Speicher macht den Pod zur QoS-Klasse *Guaranteed* und
  das Verhalten vorhersagbar. Request 1 / Limit 2 ist günstiger und darf bis 2 CPU nutzen,
  wenn der Knoten frei ist — dazu gibt es aber keine eigenen Messwerte (unter Konkurrenz
  verhält sich der Pod dann wie „1 CPU, 2 Worker“). Ein CPU-Limit unter 1 lohnt nicht: die
  Antwortzeit verdoppelt sich schon ohne Last.
- **Client-Timeout** mindestens 30 s, besser die Parallelität am Client begrenzen.
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
