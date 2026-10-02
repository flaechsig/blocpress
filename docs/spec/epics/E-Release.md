---
id: E-Release
title: Build-, Test- und Release-Automatisierung
status: in-progress
derived_from: docs/product-backlog.adoc:373-421 (Epic 7, ohne Native-Build) und :221-222
---

## E-Release — Build-, Test- und Release-Automatisierung

GitHub-Actions-Pipeline für Build und Tests bei jedem Push, ein Ein-Befehl-Release
nach Maven Central, Docker Hub und GitHub sowie eine E2E-Testsuite gegen das
Quickstart-Image.

**Warum.** Releases sollen reproduzierbar und ohne Handarbeit entstehen; die
E2E-Suite sichert das Zusammenspiel der Module ab, das Unit-Tests allein nicht
sehen.
