# Messprotokoll: blocpress-render 2.5.1 (native) unter Last

**Datum:** 2026-10-02 · **Image:** `flaechsig/blocpress-render:2.5.1` · **Werkzeug:** `RenderLoadIT`
(`mvn verify -pl blocpress-e2e -Pload …`) · **Host:** 32 Kerne, 123 GiB RAM, Docker 29, cgroup v2 ·
**Lastmix:** `blocpress-e2e/src/test/resources/load/scenarios.json` (Rechnung mit Tabelle, alle
Zahlenformate, bedingter Text in zwei Zweigen, Tabellen-Schleife) · **Client-Timeout:** 120 s

Jede Zeile: frische Instanz, sequenzieller Referenzlauf (5 Renders, Aufwärmen), dann N parallele
Clients. Speicherspitze = `memory.peak` (inkl. Referenzlauf); CPU-s / gedrosselt = Differenz aus
`cpu.stat` während der Laststufe (gedrosselte Sekunden über CPUs summiert — Vergleichswert).
**Alle 1.488 synchronen Renders der Laststufen waren inhaltlich korrekt** (Pflichttexte im deutschen Format,
Wortfolge gleich der Referenz).

## Docker: CPU-Limit × Worker

| Konfiguration | Worker | Pfad | N | Renders | OK | Fehler | Durchsatz/s | p50 s | p95 s | max s | Speicherspitze | CPU-s | gedrosselt (Perioden / s) | Hinweis |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| docker, 0.5 CPU, 1Gi | 1 | sync | 1 | 20 | 20 | 0 | 1.08 | 0.91 | 0.98 | 0.98 | 219Mi | 9.28 | 185 / 9.12 |  |
| docker, 0.5 CPU, 1Gi | 1 | sync | 2 | 20 | 20 | 0 | 1.08 | 1.84 | 1.89 | 1.90 | 220Mi | 9.29 | 185 / 9.56 |  |
| docker, 0.5 CPU, 1Gi | 1 | sync | 4 | 20 | 20 | 0 | 1.07 | 3.74 | 3.82 | 4.61 | 221Mi | 9.35 | 183 / 9.56 |  |
| docker, 0.5 CPU, 1Gi | 1 | sync | 8 | 32 | 32 | 0 | 1.06 | 7.52 | 8.53 | 9.40 | 226Mi | 15.08 | 299 / 14.92 |  |
| docker, 0.5 CPU, 1Gi | 1 | sync | 16 | 64 | 64 | 0 | 1.05 | 14.85 | 19.93 | 23.80 | 237Mi | 30.04 | 587 / 31.63 |  |
| docker, 1 CPU, 1Gi | 1 | sync | 1 | 20 | 20 | 0 | 1.82 | 0.54 | 0.62 | 0.74 | 219Mi | 9.24 | 6 / 0.01 |  |
| docker, 1 CPU, 1Gi | 1 | sync | 2 | 20 | 20 | 0 | 2.09 | 0.95 | 0.99 | 1.02 | 220Mi | 9.28 | 25 / 0.07 |  |
| docker, 1 CPU, 1Gi | 1 | sync | 4 | 20 | 20 | 0 | 1.93 | 1.96 | 2.42 | 2.49 | 222Mi | 9.27 | 19 / 0.08 |  |
| docker, 1 CPU, 1Gi | 1 | sync | 8 | 32 | 32 | 0 | 2.05 | 3.88 | 5.82 | 5.83 | 229Mi | 14.74 | 27 / 0.22 |  |
| docker, 1 CPU, 1Gi | 1 | sync | 16 | 64 | 64 | 0 | 2.04 | 6.88 | 11.14 | 12.18 | 281Mi | 29.61 | 53 / 1.25 |  |
| docker, 0.5 CPU, 1Gi | 2 | sync | 1 | 20 | 20 | 0 | 1.07 | 0.92 | 0.99 | 1.09 | 219Mi | 9.24 | 179 / 8.66 |  |
| docker, 0.5 CPU, 1Gi | 2 | sync | 2 | 20 | 20 | 0 | 0.88 | 2.29 | 2.32 | 2.38 | 370Mi | 11.42 | 228 / 34.32 |  |
| docker, 0.5 CPU, 1Gi | 2 | sync | 4 | 20 | 20 | 0 | 0.91 | 4.39 | 4.70 | 6.33 | 373Mi | 11.06 | 221 / 34.76 |  |
| docker, 0.5 CPU, 1Gi | 2 | sync | 8 | 32 | 32 | 0 | 0.86 | 9.21 | 11.61 | 14.01 | 379Mi | 18.61 | 372 / 57.28 |  |
| docker, 0.5 CPU, 1Gi | 2 | sync | 16 | 64 | 64 | 0 | 0.86 | 18.50 | 23.20 | 27.49 | 449Mi | 37.25 | 745 / 115.84 |  |
| docker, 0.5 CPU, 1Gi | 4 | sync | 1 | 20 | 20 | 0 | 1.08 | 0.92 | 0.96 | 0.97 | 220Mi | 9.31 | 185 / 9.06 |  |
| docker, 0.5 CPU, 1Gi | 4 | sync | 2 | 20 | 20 | 0 | 0.86 | 2.31 | 2.41 | 2.49 | 371Mi | 11.72 | 235 / 35.33 |  |
| docker, 0.5 CPU, 1Gi | 4 | sync | 4 | 20 | 20 | 0 | 0.76 | 5.20 | 5.49 | 5.60 | 676Mi | 13.19 | 264 / 91.70 |  |
| docker, 0.5 CPU, 1Gi | 4 | sync | 8 | 32 | 32 | 0 | 0.77 | 10.30 | 15.10 | 15.50 | 659Mi | 20.73 | 414 / 147.91 |  |
| docker, 0.5 CPU, 1Gi | 4 | sync | 16 | 64 | 64 | 0 | 0.78 | 19.99 | 30.11 | 36.70 | 743Mi | 41.01 | 820 / 294.16 |  |
| docker, 1 CPU, 1Gi | 2 | sync | 1 | 20 | 20 | 0 | 1.96 | 0.50 | 0.57 | 0.58 | 219Mi | 9.90 | 10 / 0.02 |  |
| docker, 1 CPU, 1Gi | 2 | sync | 2 | 20 | 20 | 0 | 1.92 | 1.03 | 1.10 | 1.10 | 373Mi | 10.49 | 105 / 9.90 |  |
| docker, 1 CPU, 1Gi | 2 | sync | 4 | 20 | 20 | 0 | 2.00 | 1.97 | 2.12 | 2.12 | 374Mi | 10.08 | 100 / 10.10 |  |
| docker, 1 CPU, 1Gi | 2 | sync | 8 | 32 | 32 | 0 | 2.00 | 4.00 | 5.00 | 6.01 | 380Mi | 16.02 | 152 / 14.77 |  |
| docker, 1 CPU, 1Gi | 2 | sync | 16 | 64 | 64 | 0 | 1.85 | 7.98 | 12.10 | 13.15 | 437Mi | 34.65 | 344 / 35.89 |  |
| docker, 1 CPU, 1Gi | 4 | sync | 1 | 20 | 20 | 0 | 2.10 | 0.47 | 0.50 | 0.51 | 219Mi | 9.26 | 10 / 0.03 |  |
| docker, 1 CPU, 1Gi | 4 | sync | 2 | 20 | 20 | 0 | 1.86 | 1.07 | 1.22 | 1.27 | 373Mi | 10.82 | 108 / 10.37 |  |
| docker, 1 CPU, 1Gi | 4 | sync | 4 | 20 | 20 | 0 | 1.58 | 2.52 | 2.70 | 2.78 | 625Mi | 12.74 | 127 / 37.50 |  |
| docker, 1 CPU, 1Gi | 4 | sync | 8 | 32 | 32 | 0 | 1.60 | 4.90 | 7.59 | 7.61 | 673Mi | 20.12 | 201 / 60.46 |  |
| docker, 1 CPU, 1Gi | 4 | sync | 16 | 64 | 64 | 0 | 1.60 | 10.00 | 12.81 | 15.70 | 719Mi | 40.11 | 401 / 121.43 |  |
| docker, 2 CPU, 1Gi | 2 | sync | 1 | 20 | 20 | 0 | 2.02 | 0.49 | 0.56 | 0.56 | 221Mi | 9.62 | 0 / 0.00 |  |
| docker, 2 CPU, 1Gi | 2 | sync | 2 | 20 | 20 | 0 | 3.59 | 0.55 | 0.62 | 0.62 | 373Mi | 9.96 | 5 / 0.00 |  |
| docker, 2 CPU, 1Gi | 2 | sync | 4 | 20 | 20 | 0 | 3.79 | 1.04 | 1.11 | 1.63 | 377Mi | 10.09 | 25 / 0.07 |  |
| docker, 2 CPU, 1Gi | 2 | sync | 8 | 32 | 32 | 0 | 3.27 | 2.22 | 3.20 | 3.83 | 381Mi | 16.03 | 27 / 0.08 |  |
| docker, 2 CPU, 1Gi | 2 | sync | 16 | 64 | 64 | 0 | 3.70 | 4.29 | 5.39 | 6.89 | 454Mi | 31.80 | 46 / 0.42 |  |
| docker, 2 CPU, 1Gi | 4 | sync | 1 | 20 | 20 | 0 | 2.05 | 0.48 | 0.51 | 0.51 | 219Mi | 9.12 | 0 / 0.00 |  |
| docker, 2 CPU, 1Gi | 4 | sync | 2 | 20 | 20 | 0 | 3.61 | 0.55 | 0.58 | 0.58 | 374Mi | 9.91 | 5 / 0.02 |  |
| docker, 2 CPU, 1Gi | 4 | sync | 4 | 20 | 20 | 0 | 3.42 | 1.12 | 1.19 | 1.93 | 678Mi | 11.37 | 54 / 9.19 |  |
| docker, 2 CPU, 1Gi | 4 | sync | 8 | 32 | 32 | 0 | 3.49 | 2.27 | 2.90 | 4.00 | 680Mi | 18.18 | 86 / 15.53 |  |
| docker, 2 CPU, 1Gi | 4 | sync | 16 | 64 | 64 | 0 | 3.45 | 4.59 | 6.91 | 8.11 | 732Mi | 37.25 | 185 / 35.98 |  |
| docker, 4 CPU, 1Gi | 4 | sync | 1 | 20 | 20 | 0 | 2.09 | 0.47 | 0.50 | 0.51 | 221Mi | 9.24 | 0 / 0.00 |  |
| docker, 4 CPU, 1Gi | 4 | sync | 4 | 20 | 20 | 0 | 6.52 | 0.61 | 0.64 | 0.64 | 674Mi | 11.77 | 11 / 0.02 |  |
| docker, 4 CPU, 1Gi | 4 | sync | 8 | 32 | 32 | 0 | 6.39 | 1.25 | 1.29 | 1.86 | 687Mi | 18.96 | 23 / 0.06 |  |
| docker, 4 CPU, 1Gi | 4 | sync | 16 | 64 | 64 | 0 | 6.40 | 2.46 | 3.78 | 4.39 | 739Mi | 37.43 | 38 / 0.23 |  |

## Kubernetes (k3d, Beispiel-Manifest `../examples/blocpress-render-k8s.yaml`)

| Konfiguration | Worker | Pfad | N | Renders | OK | Fehler | Durchsatz/s | p50 s | p95 s | max s | Speicherspitze | CPU-s | gedrosselt (Perioden / s) | Hinweis |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| k8s (k3d), 1 CPU, 384Mi | 1 | sync | 1 | 20 | 20 | 0 | 1.84 | 0.54 | 0.64 | 0.73 | 220Mi | 8.89 | 3 / 0.00 |  |
| k8s (k3d), 1 CPU, 384Mi | 1 | sync | 4 | 20 | 20 | 0 | 1.91 | 2.09 | 2.22 | 2.24 | 221Mi | 8.93 | 14 / 0.03 |  |
| k8s (k3d), 1 CPU, 384Mi | 1 | sync | 16 | 64 | 64 | 0 | 2.07 | 7.24 | 10.14 | 11.61 | 284Mi | 28.70 | 38 / 1.01 |  |

## Job-Pfad (`/api/render/jobs`)

In allen sechs Docker-Konfigurationen lieferten **20 von 20** Einreichungen `HTTP 500` ohne
Job-ID: Das Native-Image kann die Antwort `AsyncRenderResource.JobStatus` nicht serialisieren
(Record ohne Reflection-Registrierung). Die Jobs werden trotzdem gespeichert und verarbeitet,
der Client erfährt aber ihre ID nicht. Fehler in 2.5.0/2.5.1, nicht im JVM-Betrieb.
