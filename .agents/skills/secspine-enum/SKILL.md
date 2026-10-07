---
name: secspine-enum
description: >-
  Phase 2 des Pentests (Enumeration): die Angriffsfläche eines eigenen, autorisierten Ziels
  aufnehmen — offene Ports und Dienste, Technologie-Fingerprint, Web-Endpoints — Roh-Output
  nach logs/, Verdichtung nach security-review/enumeration.md. Erkennt Stack und Umgebung selbst.
  Verwenden, wenn der Scope steht (Phase 0) und die Angriffsfläche beschrieben werden soll,
  oder bei Aufruf /secspine-enum.
---

# secspine-enum

Du nimmst die Angriffsfläche eines **eigenen, autorisierten** Ziels auf. Zuerst den Scope
in `security-review/scope.md` lesen — teste nur, was dort drinsteht. Steht kein Scope oder keine
Freigabe (GATE-2), stoppst du und fragst. Alles andere findest du selbst.

## Vorgehen

1. **Ziel bestimmen** aus `security-review/scope.md` (Adressen, Ports, was ausdrücklich raus ist).
2. **Ports/Dienste**: bevorzugt `naabu` (permissiv, MIT), sonst `nmap`. Roh-Output nach
   `logs/02_ports_<datum>.txt`. Keine aggressiven/zerstörerischen Skripte.
3. **Web-Fingerprint**: Header, TLS, Technologie der erreichbaren Web-Dienste. Nach
   `logs/02_headers_<datum>.txt`.
4. **Endpoints/Pfade**: vorhandene Routen sichten (sanft, keine Brute-Force gegen Produktion
   ohne Freigabe). Nach `logs/02_paths_<datum>.txt`.
5. **Verdichten**: `security-review/enumeration.md` — Liste der Dienste (Port, Version) und
   Endpoints. Das ist das Gate-Ergebnis dieser Phase.

## Regeln

- Log-Namen nach Konvention: `logs/<phase>_<tool>_<datum>.txt`.
- Keine Verfügbarkeit gefährden; im Zweifel kleiner dosieren.
- Was du an Verdachtsmomenten siehst, wird **hier noch kein Befund** — das macht `secspine-scan`
  (automatisiert) und `secspine-verify` (bestätigt). Hier nur beschreiben.
- Gate: weiter erst, wenn `security-review/enumeration.md` die Angriffsfläche beschreibt.
