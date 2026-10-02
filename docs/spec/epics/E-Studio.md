---
id: E-Studio
title: Portal und Micro-Frontends (Studio)
status: in-progress
derived_from: docs/product-backlog.adoc:324-370 (Epic 6, ohne Quickstart-Image)
---

## E-Studio — Portal-Shell und Micro-Frontends

`blocpress-studio` ist der zentrale Einstiegspunkt im Browser: eine Portal-Shell,
die die Oberflächen der Module als Web Components einbindet und das JWT an sie
weiterreicht.

**Warum.** Die Module sind als Self-Contained Systems geschnitten; der Nutzer
soll trotzdem *eine* Anwendung sehen — ohne dass die Module ihre Ports nach außen
öffnen müssen.
