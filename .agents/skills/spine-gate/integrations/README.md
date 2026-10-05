# Integrations

How docspine connects to a project's build and tests, one file per tool chain. The
contract they fulfil is neutral and lives in `STANDARD.md` section 11 (in a project:
`.docspine/STANDARD.md`); each file here describes how one tool chain meets it.

| Integration | Tool chain |
|---|---|
| [maven-junit5](maven-junit5.md) | Maven with JUnit 5 |

## Adding an integration

Copy `maven-junit5.md` and keep its structure, so that people and the skill
`spine-gate` can rely on it:

- **Front matter:** `name`, `title`, `detect` (a file that identifies the tool chain),
  `keywords` (words that name the tool chain in a project's constraints, for a project
  without a build yet), `test_command`, `test_reports`, `requires`, `tested_with`.
- **Test results:** how the requirement ID gets into the test name in the report, or how
  `req-results.json` is written.
- **Check in the build:** how `check` runs after the tests and fails the build.
- **New build:** a minimal, tested build file that already contains the settings above.
- **Prerequisites.**

Only what has been tried goes in; `tested_with` names the versions.
