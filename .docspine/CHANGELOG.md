# Changelog

What changed in what docspine delivers to projects (`.docspine/`, `.agents/skills/docspine-*`).
The newest version comes first. `docspine-update` shows the entries between the installed and
the new version.

## 0.20

- **Skills are named `docspine-*`** instead of `spine-*` (standard 2.2, ADR-0026), so that
  skills of several tools of the spine family (docspine, secspine, …) can live side by
  side in one project. Call `/docspine-init`, `/docspine-require` and so on. After the
  update, call `/docspine-update`: it removes the old folders `.agents/skills/spine-*`.
  Project skills named `docspine-*` must be renamed, because the prefix is now reserved.

## 0.19

- **Building blocks show only requirements in force** (standard 4). The generated
  region `realized` leaves out superseded and rejected requirements; they stay as
  history in their own files. Run `render` once after the update.

## 0.18

- **Hint on newer versions** (standard 11, ADR-0025). The new command
  `python3 .docspine/docspine.pyz version` shows whether a newer version exists, with
  its changelog entries and the installation command. It looks online at most once a
  day, keeps the answer in the user's cache folder (`--now` looks at once), and does
  not fail without a network. `spine-require`, `-impact`, `-decide`, `-build`, `-prove`
  and `-gate` run it when they start and offer to install a newer version.
- **The prefix `spine-` is reserved** for skills from docspine (standard 2.2). Project
  skills use other names, because an update overwrites `.agents/skills/spine-*` and
  removes folders docspine no longer delivers. The test for tool-specific folders now
  also covers the docspine skills: removing them must not lose any rule.
- **Four new skills complete the cycle:** `spine-impact` checks what a requirement means
  for the architecture and gives one verdict; `spine-decide` records a decision as an
  ADR; `spine-build` builds a released requirement, test first, hands back when the
  requirement or the architecture has a gap, and changes the status; `spine-prove`
  explains the check's errors and audits whether tests really prove the requirements.

## 0.17

- **Outdated diagram images are reported** (error 12, standard 9). The new command
  `python3 .docspine/docspine.pyz diagram` renders DOT and PlantUML sources to SVG with
  Graphviz or PlantUML and records a checksum of the source in the SVG; the check
  compares it. Existing SVGs without a checksum are reported until rendered once with
  `diagram`. Graphviz and PlantUML are needed only for this command.
- **`spine-adopt` learnt from its second run:** file extensions change in the move
  commit, so Git keeps the history; links resolve from the old location; relations the
  standard does not allow are listed in the plan; unchecked content of agent files goes
  to `docs/legacy/`.
- **`spine-adopt` learnt from its third run (3dPacMan):** a project may live in a
  subfolder of a repository; a `.gitignore` higher up that hides the installation is
  named; requirements written as prose get an EARS statement as a suggestion while the
  original text stays; an obligation without equivalent (`KANN`, `MAY`) becomes `SHOULD`
  with a note; a story with several epics keeps the first; features built after the
  documentation last changed become contradictions; links into build output become
  plain paths.
- **`spine-gate` without tests:** for a project without automated tests the check runs
  as a step of its own without test results, and requirements are proven by hand.

## 0.16

- **New skill `spine-adopt`** brings an existing project with code and documentation under
  docspine: it takes stock, proposes a target for every part in one plan, and moves the
  documentation on a branch, file moves before content changes so the history is kept.
- **`spine-gate` replaces an existing traceability gate** instead of running beside it, and
  moves requirement references from tags or comments into the test's display name.

## 0.15

- **Maven integration: integration tests.** Failsafe needs the same reporter setting as
  Surefire, otherwise requirements proven by integration tests count as not proven.
- **Narrow diagrams** (standard 9): Markdown readers cannot scale Mermaid diagrams; prefer
  top to bottom, few participants, short labels, details below the diagram.

## 0.14

- **Code spans stay in listed lines:** open questions and contradictions in the overview
  (`01-goals/README.md`) lost text in backticks. They are now listed as written.

## 0.13

- **The checker finds JUnit XML reports itself** anywhere in the repository; the profile
  field `test_reports` becomes optional and only limits the search.
- **The check may run as a separate step right after the build** (integration contract,
  standard 11), run by the continuous integration. The Maven integration uses this for
  multi-module projects instead of an extra module.
- **Maven integration corrected:** the reporter needs `usePhrasedTestCaseClassName`
  as well, otherwise a requirement ID in the display name of a class is lost. Notes on
  parameterized tests (`{displayName}` in the name pattern) and on multi-module projects
  (the check runs in a small last module).

## 0.12

- **Rules moved from the skills into the standard:** how the README is translated (2.4),
  what `AGENTS.md` contains (2.2), the requirement heading (3.4), quality goals become
  requirements (3.1), connecting the build is an ADR (11). `UNKNOWN` keeps its English
  keyword with the question in the project language (principle 6). The skills refer to
  these sections instead of repeating them.
- **`spine-init` names the main branch `main`** (`git init -b main`), independently of
  the machine's Git configuration. The remote instructions in the standard use the
  project's own main branch name.
- **Skills name conflicting instructions.** If an earlier instruction or a remembered
  note conflicts with a step, the skill says so and asks, instead of skipping the step
  silently (found in a trial: an old note suppressed `git init`, first commit and remote).
- **Error 12 is marked as not yet checked** in the standard. It applies only to large
  diagrams in DOT or PlantUML with a committed SVG; Mermaid diagrams cannot go stale.

## 0.11

- **Diagrams in Mermaid,** also on the README: ASCII art slipped out of place in many
  fonts (standard section 9).
- **Open decisions** in `docs/01-goals/README.md`: ADRs on `proposed` are listed, and
  the count of decisions links to the decisions folder.
- **Skills talk less:** compact proposals, short summaries, one approval per run except
  for steps that act outside the repository or are hard to undo, errors in plain words
  instead of numbers, "the check" instead of tool names.
- **`spine-init`** treats every folder as a real project, explains why Git, and that a
  remote is only needed for a team.

## 0.10

- **Small teams** (up to about five people, standard 2.7): every change on its own branch,
  merged only when `check` reports `OK`; IDs become final on the main branch.
- **New command `renumber OLD NEW`:** gives an artifact a new ID when two branches took
  the same number, updates every reference in `docs/`, and lists other files that still
  contain the old ID (such as test names).
- **Skills propose a branch** before writing on the main branch; `spine-init` makes the
  first commit and helps connecting a remote repository the team has created.

## 0.9

- **`spine-gate` also sets up a new build,** connected from the start, when the project
  has none yet; it picks the integration from the tool chain named in the constraints.
  The Maven integration has a tested minimal `pom.xml` for this.
- **`spine-gate` proposes the status change** for requirements whose tests already pass,
  in the same step, because a passing test on a `planned` requirement fails the build
  (error 9).
- **`spine-init`** recommends `spine-gate` before the first build file or test, also in
  `AGENTS.md`.
- **Specifying and building are separate.** `proposed` means described, `planned` means
  released for building; a person decides the release. Specification skills write no
  code; new requirements are always `proposed`; building works only on `planned`
  requirements. `spine-require` suggests the next story by default instead of building.
- **Git is a prerequisite** (standard 2.6), like Python. `spine-init` checks it, offers
  `git init` for a new folder, and offers the first commit at the end.
- **Positioning:** docspine is a way of developing with the documentation as its spine,
  from vision to code and proof. Designed for one person; small teams follow in 0.10.

## 0.8

- **New skill `spine-gate`:** recognises the tool chain, applies the matching integration
  step by step with explanations, and checks the result. The integrations travel with it
  in `.agents/skills/spine-gate/integrations/`.

## 0.7

- **Integration contract** in the standard (section 11): deliver test results, run
  `check` after the tests and fail the build on errors, keep `render` outside the build.
  `render` may run any time before `check`.
- **Integrations per tool chain** in the docspine repository under `integrations/`. First:
  `maven-junit5`, now including `check` in the Maven build (`exec-maven-plugin`).

## 0.6

- **Stricter proof.** An implemented requirement needs a passing test result or a proof
  by hand: `evidence` **and** `verification`. Implementation paths alone no longer count.
  Requirements proven by hand without `verification` now report error 8.
- **`check --without-tests`** checks the documentation without test results.
- **Order:** run the tests, then `render`, then `check`. `spine-init` writes this order
  into `AGENTS.md`, with the project's test command first.

## 0.5

- **Test results from JUnit XML reports.** The checker reads the reports named in the
  new profile field `test_reports`; each test case counts for every `REQ-NNNN` in its
  name or class name. For JUnit 5, put the ID in `@DisplayName`. Example for Maven:
  `examples/maven-junit5/` in the docspine repository. `req-results.json` still works.

## 0.4

- **New skill `spine-require`:** turns a need into a story with requirements (EARS), in
  dialogue, and checks the result.

## 0.3

- **No more `docs/STATUS.md`.** Counts, open questions, contradictions and chapters
  without content now appear in `docs/01-goals/README.md`, below the epics and stories.
  `spine-update` removes the old `docs/STATUS.md`.

## 0.2

- **Configuration moved to `.docspine/`.** `STANDARD.md` and `PROFILE.md` now live in
  `.docspine/`; `docs/` contains only documentation. `spine-update` moves an existing
  `docs/PROFILE.md` and removes `docs/STANDARD.md`.
- **No version in the profile.** The field `docspine:` in `PROFILE.md` is no longer used;
  `spine-update` removes it. Error 14 now compares the README with the installed standard.
- **New skill `spine-update`** to finish an update. `spine-init` only sets up.
- **`spine-init` writes first stories** for each theme.
- **The README** is based on arc42 and mentions the configuration files instead of
  linking them. Its English source is `.docspine/README.en.md`.
- **New:** `.docspine/MANIFEST` (delivered files), `.docspine/CHANGELOG.md` (this file),
  `.docspine/LICENSE` (0BSD).

## 0.1

First delivered version: standard, checker (`check`, `render`), skill `spine-init`.
