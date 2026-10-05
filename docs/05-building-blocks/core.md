---
title: blocpress-core
path: [blocpress-core]
---

# Baustein blocpress-core

Parsing, Validierung und Merge-Pipeline der Vorlagen, reines Java mit odfdom. _(confidence: verified — Modulstruktur im Code; übernommen aus dem
arc42-Gerüst, Detail folgt entlang der Änderungen)_

## Umgesetzte Requirements

<!-- generated:realized -->
- [REQ-0001](../01-goals/requirements/REQ-0001.md) WHEN a template contains a user field bound to a dot-notation name, the render engine shall replace the field with the value found at that path in the JSON data.
- [REQ-0002](../01-goals/requirements/REQ-0002.md) WHEN a conditional element (section, conditional-text, paragraph or span) carries a JEXL condition, the render engine shall evaluate it against the JSON data and keep or drop the element according to the OpenDocument hide-condition semantics.
- [REQ-0003](../01-goals/requirements/REQ-0003.md) WHEN a section or table row contains fields bound to a JSON array path, the render engine shall duplicate that group once per array element and index the field names accordingly.
- [REQ-0004](../01-goals/requirements/REQ-0004.md) WHERE an output type of PDF or RTF is requested, the render engine shall convert the merged ODT document to that format using a headless LibreOffice process.
- [REQ-0005](../01-goals/requirements/REQ-0005.md) WHEN a number, currency, percentage or date style referenced by a user field declares a language, the render engine shall format the value with that language's decimal and grouping separators, regardless of the configured default locale.
- [REQ-0006](../01-goals/requirements/REQ-0006.md) WHEN a number, currency, percentage or date style referenced by a user field declares no language, the render engine shall format the value using the default locale supplied by the caller, independent of the operating-system locale.
- [REQ-0007](../01-goals/requirements/REQ-0007.md) IF the configured default locale is not a valid BCP-47 tag or no number-format data is available for it at runtime, THEN the render service shall refuse to start with an error naming the locale.
- [REQ-0010](../01-goals/requirements/REQ-0010.md) WHEN a header or footer of any master page contains user fields, conditional text or conditional sections, the render engine shall resolve them with the same rules as in the document body, including number and date formatting.
- [REQ-0011](../01-goals/requirements/REQ-0011.md) WHEN a template contains a section linked to an external ODT document (text:section-source), the render engine shall inline that document's content before conditions, loops and fields are resolved, and shall fill the text block's fields from the JSON paths mapped in the section name.
- [REQ-0012](../01-goals/requirements/REQ-0012.md) WHERE PDF or RTF output is requested, the render engine shall convert the merged document to that format with a headless LibreOffice process provided by the core library.
<!-- /generated -->
