# Messprotokoll: blocpress-render 2.8.0-SNAPSHOT (native) mit warmen LibreOffice-Instanzen

**Datum:** 2026-10-10 · **Image:** `blocpress-render:native-2.8.0` (lokal aus `main` mit
[ADR-0021](../../09-decisions/ADR-0021.md) gebaut, `Dockerfile.native`) · **Werkzeug:** `RenderLoadIT`
(`mvn verify -pl blocpress-e2e -Pload -Dload.image=blocpress-render:native-2.8.0 -Dload.cpus=1,2,4
-Dload.workers=8 -Dload.memory=640m|1g|1536m -Dload.levels=1,4,16`) · **Host:** AMD Ryzen 9 9950X,
Docker, cgroup v2 · **Lastmix:** `blocpress-e2e/src/test/resources/load/scenarios.json`

Die Spalte „Worker“ zeigt die eingestellte Obergrenze (8). Tatsächlich leitet render die Zahl aus
CPU-Kontingent und Speicherlimit ab (Grundbedarf ≈ eigener Speicher + 128 MiB, 350 MiB je Instanz):
640Mi → 1, 1Gi → 2, 1,5Gi → 3 (bei 1 CPU immer 1, bei 2 CPU höchstens 2).
**Alle 936 Renders waren inhaltlich korrekt** (Pflichttexte im deutschen Format, Wortfolge gleich
der Referenz), keine Fehler.

| Konfiguration | Worker | Pfad | N | Renders | OK | Fehler | Durchsatz/s | p50 s | p95 s | max s | Speicherspitze | CPU-s | gedrosselt (Perioden / s) | Hinweis |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| docker --cpus=1 --memory=640Mi, 8 Worker | 8 | sync | 1 | 20 | 20 | 0 | 41.53 | 0.02 | 0.05 | 0.05 | 211Mi | 0.42 | 0 / 0.00 |  |
| docker --cpus=1 --memory=640Mi, 8 Worker | 8 | sync | 4 | 20 | 20 | 0 | 53.69 | 0.07 | 0.10 | 0.11 | 209Mi | 0.43 | 4 / 0.08 |  |
| docker --cpus=1 --memory=640Mi, 8 Worker | 8 | sync | 16 | 64 | 64 | 0 | 48.47 | 0.30 | 0.43 | 0.50 | 220Mi | 1.34 | 10 / 0.61 |  |
| docker --cpus=2 --memory=640Mi, 8 Worker | 8 | sync | 1 | 20 | 20 | 0 | 44.69 | 0.02 | 0.04 | 0.05 | 227Mi | 0.41 | 0 / 0.00 |  |
| docker --cpus=2 --memory=640Mi, 8 Worker | 8 | sync | 4 | 20 | 20 | 0 | 67.51 | 0.06 | 0.07 | 0.09 | 227Mi | 0.40 | 0 / 0.00 |  |
| docker --cpus=2 --memory=640Mi, 8 Worker | 8 | sync | 16 | 64 | 64 | 0 | 73.69 | 0.20 | 0.29 | 0.35 | 219Mi | 1.23 | 0 / 0.00 |  |
| docker --cpus=4 --memory=640Mi, 8 Worker | 8 | sync | 1 | 20 | 20 | 0 | 48.08 | 0.02 | 0.03 | 0.03 | 227Mi | 0.41 | 0 / 0.00 |  |
| docker --cpus=4 --memory=640Mi, 8 Worker | 8 | sync | 4 | 20 | 20 | 0 | 72.73 | 0.05 | 0.06 | 0.06 | 227Mi | 0.41 | 0 / 0.00 |  |
| docker --cpus=4 --memory=640Mi, 8 Worker | 8 | sync | 16 | 64 | 64 | 0 | 73.03 | 0.20 | 0.29 | 0.34 | 227Mi | 1.27 | 0 / 0.00 |  |
| docker --cpus=1 --memory=1024Mi, 8 Worker | 8 | sync | 1 | 20 | 20 | 0 | 39.70 | 0.02 | 0.05 | 0.05 | 210Mi | 0.44 | 0 / 0.00 |  |
| docker --cpus=1 --memory=1024Mi, 8 Worker | 8 | sync | 4 | 20 | 20 | 0 | 54.22 | 0.06 | 0.10 | 0.10 | 224Mi | 0.42 | 3 / 0.06 |  |
| docker --cpus=1 --memory=1024Mi, 8 Worker | 8 | sync | 16 | 64 | 64 | 0 | 52.35 | 0.28 | 0.40 | 0.47 | 227Mi | 1.25 | 11 / 0.61 |  |
| docker --cpus=2 --memory=1024Mi, 8 Worker | 8 | sync | 1 | 20 | 20 | 0 | 42.70 | 0.02 | 0.04 | 0.04 | 261Mi | 0.44 | 0 / 0.00 |  |
| docker --cpus=2 --memory=1024Mi, 8 Worker | 8 | sync | 4 | 20 | 20 | 0 | 115.48 | 0.03 | 0.04 | 0.04 | 257Mi | 0.43 | 2 / 0.03 |  |
| docker --cpus=2 --memory=1024Mi, 8 Worker | 8 | sync | 16 | 64 | 64 | 0 | 94.60 | 0.14 | 0.26 | 0.31 | 299Mi | 1.38 | 5 / 0.54 |  |
| docker --cpus=4 --memory=1024Mi, 8 Worker | 8 | sync | 1 | 20 | 20 | 0 | 45.25 | 0.02 | 0.04 | 0.04 | 263Mi | 0.42 | 0 / 0.00 |  |
| docker --cpus=4 --memory=1024Mi, 8 Worker | 8 | sync | 4 | 20 | 20 | 0 | 121.96 | 0.03 | 0.04 | 0.06 | 256Mi | 0.43 | 0 / 0.00 |  |
| docker --cpus=4 --memory=1024Mi, 8 Worker | 8 | sync | 16 | 64 | 64 | 0 | 128.45 | 0.12 | 0.19 | 0.20 | 299Mi | 1.33 | 0 / 0.00 |  |
| docker --cpus=1 --memory=1536Mi, 8 Worker | 8 | sync | 1 | 20 | 20 | 0 | 40.72 | 0.02 | 0.04 | 0.05 | 212Mi | 0.43 | 0 / 0.00 |  |
| docker --cpus=1 --memory=1536Mi, 8 Worker | 8 | sync | 4 | 20 | 20 | 0 | 55.06 | 0.06 | 0.10 | 0.11 | 226Mi | 0.41 | 3 / 0.06 |  |
| docker --cpus=1 --memory=1536Mi, 8 Worker | 8 | sync | 16 | 64 | 64 | 0 | 52.82 | 0.26 | 0.44 | 0.48 | 226Mi | 1.27 | 11 / 1.15 |  |
| docker --cpus=2 --memory=1536Mi, 8 Worker | 8 | sync | 1 | 20 | 20 | 0 | 44.03 | 0.02 | 0.04 | 0.05 | 261Mi | 0.41 | 0 / 0.00 |  |
| docker --cpus=2 --memory=1536Mi, 8 Worker | 8 | sync | 4 | 20 | 20 | 0 | 115.74 | 0.03 | 0.05 | 0.06 | 261Mi | 0.42 | 1 / 0.05 |  |
| docker --cpus=2 --memory=1536Mi, 8 Worker | 8 | sync | 16 | 64 | 64 | 0 | 100.34 | 0.15 | 0.19 | 0.21 | 309Mi | 1.31 | 5 / 0.25 |  |
| docker --cpus=4 --memory=1536Mi, 8 Worker | 8 | sync | 1 | 20 | 20 | 0 | 42.43 | 0.02 | 0.04 | 0.05 | 346Mi | 0.43 | 0 / 0.00 |  |
| docker --cpus=4 --memory=1536Mi, 8 Worker | 8 | sync | 4 | 20 | 20 | 0 | 138.73 | 0.02 | 0.04 | 0.05 | 344Mi | 0.45 | 0 / 0.00 |  |
| docker --cpus=4 --memory=1536Mi, 8 Worker | 8 | sync | 16 | 64 | 64 | 0 | 170.03 | 0.08 | 0.12 | 0.20 | 400Mi | 1.35 | 2 / 0.07 |  |

## Ergebnis

| CPU | Speicher | Worker (abgeleitet) | Durchsatz bei N = 16 | p95 bei N = 16 | Speicherspitze |
|---|---|---|---|---|---|
| 1 | 640Mi | 1 | 48/s | 0,43 s | 220Mi |
| 2 | 640Mi | 1 | 74/s | 0,29 s | 219Mi |
| 2 | 1Gi | 2 | 95/s (N = 4: 115/s) | 0,26 s | 299Mi |
| 4 | 1Gi | 2 | 128/s | 0,19 s | 299Mi |
| 4 | 1,5Gi | 3 | 170/s | 0,12 s | 400Mi |

- Ein Render kostet rund **0,02 CPU-Sekunden** (CPU-s ÷ Renders), mit 2.5.1 waren es ~0,46.
  Gedrosselt wird kaum noch.
- Engpass ist jetzt die **Worker-Zahl**, und die begrenzt das **Speicherlimit**: Mit 640Mi bleibt
  es bei einem Worker, egal wie viele Kerne.
- Bei diesem Lastmix (Dokumente mit 1–2 Seiten) bleibt der Speicher weit unter dem Limit, weil
  sich die Instanzen die Bibliotheken teilen. Sehr große Dokumente treiben eine Instanz bis über
  500 MiB, bevor sie ersetzt wird (251 Seiten: Spitze 617 MiB in einem 640Mi-Container mit einem
  Worker, kein OOM). Deshalb rechnet render für die Worker-Zahl bewusst mit 350 MiB je Instanz.
