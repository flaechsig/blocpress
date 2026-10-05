---
id: E-RELEASE
title: Build-, Test- und Release-Automatisierung
derived_from: product-backlog.adoc:373-421 legacy (git history) (Epic 7, ohne Native-Build) und :221-222
---

## E-RELEASE — Build-, Test- und Release-Automatisierung

GitHub-Actions-Pipeline für Build und Tests bei jedem Push, ein Ein-Befehl-Release
nach Maven Central, Docker Hub und GitHub sowie eine E2E-Testsuite gegen das
Quickstart-Image.

**Warum.** Releases sollen reproduzierbar und ohne Handarbeit entstehen; die
E2E-Suite sichert das Zusammenspiel der Module ab, das Unit-Tests allein nicht
sehen.

## Stories

<!-- generated:stories -->
- [US-0029](../stories/US-0029.md) — Jeder Push wird gebaut und getestet
- [US-0030](../stories/US-0030.md) — Release mit einem Befehl
- [US-0031](../stories/US-0031.md) — Zusammenspiel der Module Ende-zu-Ende prüfen
- [US-0047](../stories/US-0047.md) — Release-Reihenfolge und Veröffentlichung absichern
- [US-0049](../stories/US-0049.md) — Ungenutzten Code und Dateien entfernen
<!-- /generated -->
