<!-- docspine 0.13 · source: standard/en/STANDARD.md · do not edit in projects -->

# docspine Standard

Version 0.13 (draft)

This document defines the rules for projects that follow docspine, a way of developing
in which the documentation is the spine: from vision through requirements and decisions
to code and proof. It applies
equally to people and AI agents. `README.md` explains how to work with the
documentation; this file defines what applies. The project's own values and any
deviations are in `PROFILE.md`. Both files are configuration and live in `.docspine/`.

The words **must**, **must not**, **should** and **may** are used in their usual
normative sense.

## 1 Principles

1. **One source per statement.** Every statement lives in exactly one source file.
   Other places refer to it by ID or show it in a generated region (section 4).
   Copied text is an error.
2. **IDs are immutable.** Once an artifact is on the main branch, its ID is never
   renumbered, reused or deleted. Status changes; the number does not. On a branch that
   is not yet merged, a new ID is a reservation (section 2.7).
3. **The rationale comes first.** What the system does will be visible in the code.
   Why it was decided that way is recorded only here.
4. **Observation is not intention.** Behaviour derived from code is a description,
   not a requirement. Requirements state what is wanted; descriptions state what is.
5. **People decide what applies.** Tools and AI agents propose; files are written
   after a person has approved.
6. **Nothing is invented.** What nobody knows stays `UNKNOWN`, together with the open
   question in the project language, for example `UNKNOWN — open question: …` or
   `UNKNOWN — offene Frage: …`. The word `UNKNOWN` itself is never translated. An open
   question starts a line or a list item with `UNKNOWN`, so that the checker can list it
   in `docs/01-goals/README.md`.
7. **The checker decides what is implemented**, not a person and not an AI agent
   (section 11).
8. **Documentation grows with the changes.** Nothing is documented in advance. A
   chapter exists only once it has content.

## 2 Structure

### 2.1 Layout

The documentation is one document, spread across many files. Its outline is based on
the twelve chapters of arc42, with four deviations: chapter 1 also holds the complete
specification, chapter 9 consists only of the individual decisions, chapter 10 is
generated, and a chapter exists only once it has content.

```
AGENTS.md                     entry point for AI agents (project-specific)
.docspine/                    configuration and tool
  STANDARD.md                 this file (from docspine)
  PROFILE.md                  project values and deviations
  docspine.pyz                the checker (from docspine)
  README.en.md                English source of docs/README.md (from docspine)
  MANIFEST                    list of the files docspine delivered (from docspine)
  CHANGELOG.md                what changed in each version (from docspine)
docs/                         the documentation
  README.md                   how to work with this documentation (translated from docspine)

  01-goals/                   chapter 1: introduction and goals
    README.md                 entry: counts, epics and stories, open questions, gaps (generated)
    vision.md
    epics/E-<NAME>.md
    stories/US-NNNN.md
    requirements/REQ-NNNN.md
  02-constraints.md           chapter 2: constraints
  03-context.md               chapter 3: context and scope
  04-strategy.md              chapter 4: solution strategy
  05-building-blocks/<name>.md  chapter 5: building block view, one block per file
  06-runtime/<name>.md        chapter 6: runtime view, one scenario per file
  07-deployment.md            chapter 7: deployment view
  08-concepts/<name>.md       chapter 8: domain and technical concepts
  09-decisions/ADR-NNNN.md    chapter 9: architecture decisions
  10-quality.md               chapter 10: quality requirements (generated)
  11-risks.md                 chapter 11: risks and technical debt
  12-glossary.md              chapter 12: glossary
  diagrams/                   sources and images of large diagrams
  legacy/                     imported old documentation, temporary (section 10)
```

A chapter may be a single file or a folder; the layout above is binding for the
chapters it lists as folders. Projects may add their own folders under `docs/`
(for example `docs/guides/`). They are not checked, except that links into them must
resolve.

### 2.2 Controlling files

| File | Purpose | Edited by hand |
|---|---|---|
| `docs/README.md` | how to work with the documentation | no, translated from docspine |
| `.docspine/STANDARD.md` | the rules | no, from docspine |
| `.docspine/PROFILE.md` | project values and justified deviations | yes |
| `.docspine/docspine.pyz` | the checker | no, from docspine |
| `.docspine/README.en.md`, `.docspine/MANIFEST`, `.docspine/CHANGELOG.md` | source of the README, list of delivered files, changes per version | no, from docspine |
| `AGENTS.md` | entry point for AI agents: where things are, how to check | yes |
| `.agents/skills/spine-*` | guided workflows for AI agents | no, from docspine |
| `.agents/skills/<other>` | project-specific workflows | yes |
| `.claude/` and similar | tool-specific settings; refer to `AGENTS.md` only | yes |

Configuration lives in `.docspine/`; `docs/` contains only documentation. The README
mentions the configuration files but does not link to them.

Everything that comes from docspine is overwritten when the standard is updated.
Changes to it belong in docspine, not in the project.

All rules are in files in the repository. Tool-specific files may make them easier to
use but must not contain rules of their own. Test: deleting a tool-specific folder
must not lose any rule.

`AGENTS.md` is written in the project language. In a section on the documentation it
contains: one sentence on what the project is; where things are (`docs/README.md`,
`.docspine/STANDARD.md`, `.docspine/PROFILE.md`, `docs/01-goals/README.md`); and how to
check, in the order of section 11, at the latest before merging and preferably before
every commit. It must not contain architecture content; it points into the document.

### 2.3 Profile

`.docspine/PROFILE.md` has this front matter:

```yaml
---
language: de                # language of all documents
statement_language: en      # language of requirement statements, default en
sources:                    # permitted values for a requirement's source
  - GDPR Art. 32
  - RFC 6749 (OAuth 2.0)
test_reports:               # optional: limits where JUnit XML reports are read (section 8.1)
  - target/surefire-reports
---
```

Below the front matter, the file lists deviations from this standard, each with a
reason. An empty list is the normal case.

### 2.4 Language

Configuration is in English; documentation is in the project language.

| What | Language |
|---|---|
| `STANDARD.md`, skills | English, copied unchanged |
| `README.md` | `language`, translated from docspine's English source |
| text, headings, `title`, `rationale` | `language` |
| generated regions and views | `language` |
| requirement `statement` | `statement_language` (default `en`, because WHEN and IF blur in many languages) |
| folder and file names | English |
| front-matter keys and values | English |

The installed version of docspine is named in the first line of `.docspine/STANDARD.md`.
The translated `README.md` starts with the version of the English source it was
translated from: `<!-- docspine 0.1 · from standard/en/README.md -->`. After an update,
the README is translated again from `.docspine/README.en.md`. Translations use the terms
in section 12, keep every link and anchor target unchanged, and in Mermaid diagrams
translate only the labels. With `language: en`, the README is copied unchanged.

### 2.5 IDs

| Artifact | ID | File |
|---|---|---|
| Epic | `E-<NAME>`, upper case, descriptive, not numbered | `01-goals/epics/E-<NAME>.md` |
| Story | `US-NNNN` | `01-goals/stories/US-NNNN.md` |
| Requirement | `REQ-NNNN` | `01-goals/requirements/REQ-NNNN.md` |
| Decision | `ADR-NNNN` | `09-decisions/ADR-NNNN.md` |

`NNNN` is four digits with leading zeros. A new artifact takes the next free number.
The `id` in the front matter must match the file name.

### 2.6 Prerequisites

- **Git.** The project is a Git repository. Decisions are superseded rather than
  rewritten, and the history is part of the proof (section 10).
- **Python 3.9 or later** for the checker (section 11).

### 2.7 Branches and teams

docspine is designed for one person or a small team of up to about five people.

- **Every change on its own branch.** Specification and building alike; never directly
  on the main branch. The only exception is the first commit that sets up the project.
  Suggested names: `spec/<topic>` for specification, `feat/<topic>` for building,
  `fix/<topic>`, `docs/<topic>`.
- **Merge only when `check` reports `OK`**, with the tests run. A pull request or a merge
  with `--no-ff` keeps the branch visible in the history.
- **IDs become final on the main branch.** On a branch, a new ID takes the next free
  number and counts as a reservation. Before merging, bring the main branch into your
  branch. If two branches took the same number, Git reports a conflict on the file named
  after that ID, because both sides added it. Then abort the merge (`git merge --abort`),
  give your own new artifacts numbers that are free on both sides with
  `python3 .docspine/docspine.pyz renumber <old> <new>`, commit, and merge again. The
  command updates every reference in `docs/` and lists other files that still contain
  the old ID, such as test names, to be adjusted by hand.
- **After merging, run `render`.** Resolve conflicts in hand-written text as usual. Inside
  a generated region, take either side and run `render`; never resolve a generated region
  by hand.
- **A team shares a remote repository** as its meeting point. Where it lives and who has
  access is the team's decision. Connecting an existing project: create an empty
  repository at the provider, then `git remote add origin <url>` and
  `git push -u origin <main branch>`, with the name of the project's main branch
  (`main` for projects set up by `spine-init`).

## 3 Artifacts

All source files are Markdown. Artifacts with an ID have YAML front matter.

### 3.1 Vision

`01-goals/vision.md`, no front matter. Sections:

| Section | Required |
|---|---|
| Core statement: the vision in one sentence | yes |
| Problem | no |
| Target group and stakeholders | no |
| Success: how we know it works | no |
| Non-goals: what deliberately does not belong | no |
| Quality goals | no |
| Themes: link to `01-goals/README.md` | no |

Missing answers are written as `UNKNOWN` with the open question (principle 6). The
vision is stable text and contains no status information. Measurable quality goals
become requirements with `category: quality` (section 3.4).

### 3.2 Epic

```yaml
---
id: E-NAME
title: <short title>
---
```

Body: what the theme covers and why. An epic has no `status` field; its status is
derived from its stories (section 3.3). The list of its stories is a generated region.

### 3.3 Story

```yaml
---
id: US-NNNN
title: <short title>
epic: E-NAME                  # must exist
requirements: [REQ-NNNN]      # each must exist; [] is allowed
status: open
evidence: []                  # optional: paths that prove the story
superseded_by: ADR-NNNN       # only with superseded or retired
---
```

Body: `As <role> I want <goal> so that <benefit>.`, then why, then acceptance in the
users' terms. A story contains no decisions (those are ADRs) and no normative criteria
without a requirement.

| `status` | Meaning | Proof required |
|---|---|---|
| `open` | candidate, not committed | none |
| `in-progress` | core built, parts open | none |
| `verified` | fully implemented and confirmed | `requirements` or `evidence`; every listed requirement is `implemented` |
| `superseded` | replaced by a decision, will not be built this way | `superseded_by` |
| `retired` | was implemented, removed | `superseded_by` |

A story without requirements is allowed. It is proven through `evidence`, for example
a UI test that the requirement mechanism does not cover.

The derived status of an epic: `verified` if all its stories are `verified`, `open`
if none has started, otherwise `in-progress`. Stories with `superseded` or `retired`
are ignored for this.

### 3.4 Requirement

```yaml
---
id: REQ-NNNN
statement: <exactly one EARS pattern, in statement_language>
obligation: MUST | SHOULD | WILL
status: proposed
category: quality             # optional: marks a quality requirement
source: <external norm, or empty for own requirement>   # must be listed in the profile
confidence: unverified        # only without test results, see below
evidence: []                  # paths to the implementation; see below
verification: <how fulfilment is checked>               # optional
supersedes: REQ-NNNN          # optional
superseded_by: REQ-NNNN | ADR-NNNN   # only with superseded
rationale: >-
  <why, in language>
---
```

Body: a heading `## REQ-NNNN — <short title>`, then one to three sentences of context.

**EARS patterns.** The statement follows exactly one pattern:

| Pattern | Form |
|---|---|
| ubiquitous | `The <system> shall <response>.` |
| event-driven | `WHEN <trigger>, the <system> shall <response>.` |
| state-driven | `WHILE <state>, the <system> shall <response>.` |
| unwanted behaviour | `IF <condition>, THEN the <system> shall <response>.` |
| optional feature | `WHERE <feature>, the <system> shall <response>.` |

**Obligation.** `MUST` is binding, `SHOULD` recommended, `WILL` a stated intention.
EARS has no level of obligation, hence the separate field. Views in other languages
show translated values (in German: MUSS, SOLLTE, WIRD).

**Source.** External norms change independently. When one changes, it must be possible
to find all affected requirements in a minute.

| `status` | Meaning | Rule |
|---|---|---|
| `proposed` | described, not released for building | no passing test result |
| `planned` | released for building, not (fully) built | no passing test result |
| `implemented` | built | passing test result **or** a proof by hand: `evidence` **and** `verification` |
| `rejected` | discarded, never built | none |
| `superseded` | replaced by a requirement or a decision | `superseded_by` |

A requirement is atomic; there is no `in-progress`. Partly built means `planned`. If
that happens often, the requirement is cut too coarsely.

A new requirement is always `proposed`.

**Specifying and building.** The change from `proposed` to `planned` is the release for
building. A person decides it, for single requirements or in batches. Specification
work (vision, stories, requirements, architecture, decisions) writes no code. Building
works only on requirements with `planned`; a requirement on `proposed` is released
first. This lets a project specify completely before building, or release and build
each requirement right away.

**Confidence.** Has the statement been checked against the running system?
`verified | unverified | contradicted`.
- If test results exist for the requirement (section 8.1), `confidence` is not
  maintained; the checker derives it.
- Otherwise it is maintained by hand. `verified` then requires `evidence` and
  `verification`.

**Evidence.** `evidence` lists paths, and what belongs there depends on whether test
results exist:
- **With test results**, `evidence` lists only the **implementation**. The tests are
  named in the results and are not repeated here. The implementation paths are needed
  to match the requirement to its building block (section 4).
- **Without test results**, `evidence` and `verification` together are the proof by
  hand: `evidence` lists the implementation and whatever proves it (a screenshot, a
  measurement), `verification` says how fulfilment was checked. Implementation paths
  alone prove nothing.

**Quality requirements** carry `category: quality`. Chapter 10 is generated from them.

### 3.5 Decision (ADR)

```yaml
---
id: ADR-NNNN
title: <short title>
status: proposed | accepted | rejected | superseded
date: YYYY-MM-DD              # date of the decision
supersedes: ADR-NNNN          # optional
superseded_by: ADR-NNNN       # only with superseded
requires: [REQ-NNNN]          # optional: testable consequences
---
```

Sections:

| Section | Required |
|---|---|
| Context: the problem, the forces, the current state | yes |
| Decision: what applies; rejected alternatives with a short reason, if there were any | yes |
| Rationale: why this option | no, may be part of context or decision |
| Consequences: including the inconvenient ones | yes |

An ADR with `proposed` is under discussion and may be edited directly. From `accepted`
on it is immutable and can only be superseded.

### 3.6 Building block

`05-building-blocks/<name>.md`:

```yaml
---
title: <name of the block>
path: [<code path>, …]        # where the block lives in the code
---
```

Body: responsibility, interfaces, important internals. The requirements the block
realises are a generated region (section 4).

### 3.7 Runtime scenario

`06-runtime/<name>.md`:

```yaml
---
title: <name of the scenario>
stories: [US-NNNN]            # optional: stories the scenario realises
---
```

### 3.8 Other chapters

No required front matter. A chapter that is only partly filled carries
`arc42_status: PARTIAL` in its front matter. A missing file means the chapter is
`UNKNOWN`.

## 4 Relations and generated regions

Relations are stated **only in the front matter, in one direction**:

| From | Field | To |
|---|---|---|
| Story | `epic` | Epic |
| Story | `requirements` | Requirements |
| Runtime scenario | `stories` | Stories |
| ADR | `requires` | Requirements |
| Building block | `path` | Code; requirements are matched through their `evidence` paths |
| Requirement, story, ADR | `superseded_by` | successor |

Every other direction, and every piece of content shown in more than one place, is
generated into a **generated region** inside a hand-written file:

```markdown
<!-- generated:<type> -->
…
<!-- /generated -->
```

| Type | In | Shows |
|---|---|---|
| `status` | `01-goals/README.md` | counts, epics and stories with status, open questions, open decisions, contradictions, missing chapters |
| `stories` | epic | its stories with status |
| `requirements` | story | statements and status of its requirements |
| `context` | requirement | epic and story it belongs to, ADRs that require it |
| `realized` | building block | requirements whose evidence lies under its path |
| `scenarios` | story | runtime scenarios that realise it |

Generated regions must not be edited by hand. A region that differs from what the
checker would generate is an error. On merge conflicts inside a region, discard the
region and regenerate.

Hand-written back references (for example "part of US-0042") must not be written.

## 5 Changing instead of rewriting

**Requirement.** A change in content creates a new requirement:
1. Create `REQ-MMMM` with `supersedes: REQ-NNNN`.
2. In `REQ-NNNN`, set only `status: superseded` and `superseded_by: REQ-MMMM`.
   Statement and rationale stay as history.

**Story.** A story that will not be built as described goes to `superseded`; one that
was built and is removed goes to `retired`. Both name the decision in `superseded_by`.

**ADR.** An accepted decision is not edited. A new ADR names the old one in
`supersedes`; the old one gets `status: superseded` and `superseded_by`.

## 6 Architecture impact

For every new requirement or change, the question is: must the architecture take
something into account, change, or decide something? Exactly one verdict applies:

| Verdict | Meaning | Consequence |
|---|---|---|
| already covered | the existing architecture covers it | nothing changes; name any constraint the implementation must respect |
| affects chapter | the architecture changes | edit the chapter, with evidence and confidence |
| decision due | an architecture decision is contained | write an ADR |
| no impact | local to an element, below architecture level | nothing to document |

**What is architecture level:** building blocks and their dependencies, runtime
interactions, deployment and operation, cross-cutting concepts (security, caching,
persistence, …), quality requirements, risks, and anything that is a decision.

**What usually is not:** a single endpoint, a UI component, a field on an existing
entity, a rule, a template — as long as no block, dependency, concept or decision is
affected.

Check the effect, not the size. A small field can affect a cross-cutting concept; a
large feature can be entirely local.

## 7 Statements about the current state

Descriptive statements in chapters 2–8 and 11 carry their confidence inline:

```markdown
_(confidence: verified — src/orders/OrderService.java, REQ-0012)_
```

| Value | Meaning |
|---|---|
| `verified` | checked against code, configuration or a proven requirement |
| `unverified` | taken over, not checked |
| `aspirational` | target state, not built |
| `contradicted` | documentation and code disagree |

A contradiction stays visible and is never resolved silently:

```markdown
> [!CAUTION]
> The documentation says X, the code does Y. (contradiction)
```

The checker lists every block marked `(contradiction)` in `docs/01-goals/README.md`. A person
decides how it is resolved.

## 8 Proof

### 8.1 Test results

The checker reads test results from two sources. A project may use either or both.

**JUnit XML reports.** Most test tools can write this format (Maven Surefire, Gradle,
pytest, Jest, Go, .NET). The checker finds the reports itself: every XML file in the
repository whose root is a test suite, outside `.git/`, `docs/`, `.docspine/` and the
skill folders. New modules are found without configuration. To limit the search, the
profile names the locations; then only those are read:

```yaml
test_reports: [target/surefire-reports]
```

Every test case counts for each requirement ID (`REQ-NNNN`) that appears in its name or
class name. So the ID must be part of the test's name; for JUnit 5 that is
`@DisplayName("REQ-0012: rejects an empty cart")`. A test case with `<failure>` or
`<error>` counts as failed, with `<skipped>` as skipped, otherwise as passed. JUnit tags
(`@Tag`) do not appear in the reports and cannot be used. Some tools must be told to
write display names into the report; how to do that for a tool chain is described in
the docspine repository under `integrations/`.

**`req-results.json`.** For tools without such reports, a project writes results in
this format, in any number of files with this name:

```json
{
  "results": [
    { "req": "REQ-0012", "result": "passed", "test": "OrderServiceTest.rejectsEmptyCart" }
  ]
}
```

`result` is `passed`, `failed` or `skipped`. How the file is produced is up to the
project.

From both sources together, a requirement counts as passed if at least one result is
`passed` and none is `failed`.

### 8.2 Status change

When a requirement passes: set `status: implemented` and add the implementation paths
to `evidence` (section 3.4). Set the story to `verified` once all its requirements are
`implemented`. Statement, rationale and title stay unchanged.

Without test results, `evidence` and `verification` together prove the requirement, and
`confidence` is set by hand.

### 8.3 Is the proof honest?

A passing test is the entry ticket, not the proof. For each implemented requirement:
split the statement into its clauses (trigger or condition, and response) and check
which clauses the test's assertions actually exercise.

| Verdict | Meaning |
|---|---|
| full | every clause is exercised against real behaviour |
| partial | the core runs, a clause or the actual acceptance is not exercised |
| hollow | the test carries the ID but does not exercise the behaviour |

A finding is either a **test gap** (sharpen the test) or a **requirement defect**
(wrong actor, untestable, overloaded clause). A requirement defect is fixed by
superseding the requirement, not by a test that cements a wrong statement.

## 9 Diagrams

Diagrams are always kept as text source in the repository, never only as images.

- **Mermaid** for all diagrams in the documentation, including entry pages: flows,
  sequences, class and domain models, state machines. GitHub, IDE previews and Markdown
  readers such as Obsidian render it.
- **No ASCII art with box-drawing characters or arrows:** many fonts draw them wider
  than one character, and the lines slip out of place.
- **DOT or PlantUML with a committed SVG** under `docs/diagrams/` for large overviews
  where Mermaid's layout is not enough. The SVG must be produced from the current
  version of its source.
- **Images without a source** only where none can exist, such as screenshots.

## 10 Legacy

Applies only while `docs/legacy/` exists.

- Statements taken from legacy documentation enter with `confidence: unverified` and
  `derived_from: <path and line range>`.
- Three sources, three degrees of validity: the **code** says what happens, the
  **legacy documentation** what was once intended, a **person** what applies.
- A legacy document or section may be removed only when all four conditions hold:
  1. Its content is taken over: every relevant statement is in the new structure.
  2. Its references are moved: no `derived_from` points to it any more.
  3. It is not the only source: the content demonstrably exists elsewhere.
  4. A person has approved the removal.
- On removal, `derived_from` is replaced by `<name> legacy (git history)`.

## 11 Checker

The checker is a command-line tool that runs without a build system, as
`python3 .docspine/docspine.pyz <command>`:

- `check` — validates everything below and fails on any error.
- `check --without-tests` — the same, but skips what needs test results (errors 8, 9
  and 10), for quick checks of the documentation alone.
- `render` — writes generated regions and views.

**Order.** `render` runs any time before `check`; it does not need test results.
`check` runs after the tests. Without a test run, `check` cannot know what is
implemented and reports it.

**Integration with build and tests.** A project connects docspine to its build so that
the check cannot be forgotten. Whatever the tool chain, an integration must:

1. deliver test results as described in section 8.1 (JUnit XML reports with the
   requirement ID in the test name, or `req-results.json`);
2. run `check` after the tests and fail on any error: either inside the build, or as a
   separate step right after it. In the second case the continuous integration runs
   that step, so that nothing reaches the main branch unchecked;
3. leave `render` outside the build, so that a build never changes files under `docs/`.

How a particular tool chain meets this contract is described in the docspine
repository under `integrations/`, one file per tool chain; in a project they are
installed with the skill `spine-gate` under `.agents/skills/spine-gate/integrations/`.

Connecting the build is an architecture decision and is recorded as an ADR (section 3.5).

**Versions.** Every change to what docspine delivers raises its version, named in the
first line of `.docspine/STANDARD.md`. `.docspine/CHANGELOG.md` describes what changed.

**Installing and updating.** docspine is installed and updated with the same command,
which writes only `.docspine/`, `.agents/skills/spine-*` and `.claude/skills`. After an
update, the skill `spine-update` translates the README again where needed and removes
files that docspine no longer delivers (listed by comparison with `.docspine/MANIFEST`).

**Errors:**

| # | Error |
|---|---|
| 1 | required front-matter field missing, or value not permitted |
| 2 | ID does not match the file name, or is used twice |
| 3 | reference to an ID that does not exist |
| 4 | `source` not listed in the profile |
| 5 | story `verified` without `requirements` and without `evidence` |
| 6 | story `verified` refers to a requirement that is not `implemented` |
| 7 | `superseded` or `retired` without `superseded_by` |
| 8 | requirement `implemented` without a passing test result and without a proof by hand (`evidence` and `verification`) |
| 9 | requirement `planned` or `proposed`, but a passing test result exists |
| 10 | test result for a requirement that does not exist, or test results that cannot be read |
| 11 | generated region differs from what would be generated |
| 12 | diagram image not produced from the current version of its source (not yet checked) |
| 13 | broken relative link |
| 14 | `README.md` was translated from a different docspine version than the installed `STANDARD.md` |
| 15 | an `evidence` path does not exist |

**Generated views:**

| File | Content |
|---|---|
| `01-goals/README.md` | the entry into the documentation: counts per status, epics and stories with status, open questions, open decisions (ADRs on `proposed`), contradictions, chapters without content and partly filled chapters. Generated regions are not searched for open questions or contradictions. |
| `10-quality.md` | all requirements with `category: quality` |

## 12 Terminology

Documentation in other languages (`README.md`, generated regions and views) uses these
terms. The German column is binding for projects with `language: de`.

| English | Deutsch |
|---|---|
| acceptance | Akzeptanz |
| architecture impact | Architekturwirkung |
| building block | Baustein |
| checker | Prüfwerkzeug |
| confidence | Konfidenz |
| controlling files | steuernde Dateien |
| core statement | Kernsatz |
| decision (ADR) | Entscheidung (ADR) |
| epic | Epic |
| evidence | Beleg |
| generated region | generierter Bereich |
| legacy documentation | Altbestand |
| non-goal | Nicht-Ziel |
| obligation | Verbindlichkeit |
| open question | offene Frage |
| profile | Profil |
| proof | Nachweis |
| proof required | Belegpflicht |
| rationale | Begründung |
| requirement | Requirement, Anforderung |
| requirement defect | Requirement-Defekt |
| runtime scenario | Laufzeitszenario |
| source (norm) | Quelle |
| story | Story |
| supersede | ablösen |
| test gap | Test-Lücke |
| test result | Testergebnis |
| verdict: full / partial / hollow | Verdikt: voll / teil / hohl |
