---
title: blocpress-workbench
path: [blocpress-workbench]
---

# Baustein blocpress-workbench

Lebenszyklus der Vorlagen. _(confidence: verified — Modulstruktur im Code; übernommen aus dem
arc42-Gerüst, Detail folgt entlang der Änderungen)_

## Umgesetzte Requirements

<!-- generated:realized -->
- [REQ-0013](../01-goals/requirements/REQ-0013.md) WHEN a reviewer rejects a submitted template with a reason, the workbench shall set the template back to DRAFT, store the reason and deploy nothing to production.
- [REQ-0014](../01-goals/requirements/REQ-0014.md) WHEN a reviewer approves a submitted template, the workbench shall transfer the unchanged template content to the production store of the render service exactly once.
- [REQ-0015](../01-goals/requirements/REQ-0015.md) IF the render service cannot be reached during an approval, THEN the workbench shall reject the approval with status 503 and keep the template SUBMITTED.
- [REQ-0016](../01-goals/requirements/REQ-0016.md) WHEN a regression run compares a rendering with the stored expected PDF, the workbench shall report identical content as passed and changed content as failed.
- [REQ-0017](../01-goals/requirements/REQ-0017.md) WHERE no expected PDF is stored for a test data set, the workbench shall report the regression run as without baseline instead of failing it.
- [REQ-0018](../01-goals/requirements/REQ-0018.md) WHERE an ignored pattern matches a deviation, the workbench shall report that deviation as accepted without hiding other deviations.
- [REQ-0019](../01-goals/requirements/REQ-0019.md) WHEN the designer runs all regression tests of a template, the workbench shall run every test data set and provide the deviations as a diff PDF.
- [REQ-0020](../01-goals/requirements/REQ-0020.md) WHEN a reviewer approves a template with a review cycle of N years, the workbench shall set its expiry date to its valid-from date plus N years and transfer that expiry date to production.
- [REQ-0021](../01-goals/requirements/REQ-0021.md) WHEN templates due for review are requested, the workbench shall list every approved template whose expiry date lies within the configured lead time (default 60 days) and no other.
- [REQ-0023](../01-goals/requirements/REQ-0023.md) WHEN a reviewer retires an approved template, the workbench shall set it to RETIRED and remove it from production.
- [REQ-0024](../01-goals/requirements/REQ-0024.md) WHEN the designer submits a template in DRAFT, the workbench shall set it to SUBMITTED and deploy nothing to production.
<!-- /generated -->
