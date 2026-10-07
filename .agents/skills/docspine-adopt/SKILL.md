---
name: docspine-adopt
description: >-
  Bring an existing project with code and documentation under docspine: take stock of the
  existing documentation, skills and traceability, agree on a target for every part in one
  proposal, then move and convert the documentation step by step on a branch. Use when the
  user wants to adopt docspine in a project that already has code or documentation, or
  calls /docspine-adopt. For a new project use docspine-init; for the build use docspine-gate.
---

# docspine-adopt

You move an existing project's documentation into the docspine structure. The rules are
in `.docspine/STANDARD.md`; read sections 1–5, 9 and 10 before you propose anything. You
take stock first, propose one plan, and change files only after approval.

This is documentation work. You do not change code, tests or the build; connecting the
build and the tests is the job of `docspine-gate`, which you hand over to at the end. What
you notice in the code goes into the documentation as an open question, a contradiction
or a story, never as a fix.

## Rules for the whole run

- **Move, then edit.** Move every file with `git mv` in a commit of its own, before its
  content or format changes. Otherwise Git loses the rename, and with it the history of
  the file.
- **Nothing is lost.** Every file under `docs/` gets a target in the plan. What does not
  fit the structure goes to `docs/legacy/` or stays where it is; it is deleted only if it
  is generated or the person approves.
- **IDs stay.** IDs that already follow STANDARD 2.5 keep their number. Only the format
  changes (three digits become four: `ADR-001` → `ADR-0001`). Other ID schemes get new
  IDs and a mapping table in `docs/legacy/`.
- **Nothing is invented.** Missing content becomes `UNKNOWN` with the open question
  (STANDARD principle 6). Observed behaviour is a description, not a requirement
  (principle 4).
- **Small commits.** One commit per step in step 5, so each can be reviewed or reverted.

## Talking to the person

- **Briefly.** Proposals as a compact list; summaries in at most five lines. Do not
  repeat what the person has just confirmed.
- **One approval per run.** Collect everything into one proposal. Ask again only for
  steps that act outside the repository or are hard to undo, such as connecting a
  remote, deleting files, or setting a decision to `accepted`.
- **In plain words.** Say "the check" and "the check reports OK", not command or file
  names of the tool. Describe errors in words ("the test passes, but the requirement is
  still planned"); give the error number at most in brackets. Show commands only where
  the person is to run them.
- **Conflicting instructions.** If something you remember or were told earlier conflicts
  with a step of this skill, name it and ask which applies. Never skip or change a step
  silently.

## Step 0 — Starting point (silently)

1. Run from the project root. Check that `git --version` and `python3 --version` work
   and that the folder is inside a Git repository. Without Git, say that docspine needs
   it (STANDARD 2.6) and stop. If the project root is a subfolder of the repository
   (`git rev-parse --show-toplevel` differs), for example one project in a collection
   repository: docspine lives in the subfolder, all paths are relative to it, branches
   and the clean state of step 4 apply to the whole repository. Record this as a
   deviation in the profile.
2. **Installation.** `.docspine/STANDARD.md` and `.docspine/docspine.pyz` must exist, and
   `.claude/skills` must be the link to `.agents/skills` that the installation creates. If
   `.claude/skills` is a folder of the project's own, the installation could not create
   the link; tell the person to move the folder first (`git mv .claude/skills
   .agents/skills`) and run the installation one-liner again, as the docspine quickstart
   describes. Read the version from the first line of `.docspine/STANDARD.md`. Check
   with `git check-ignore` that `.docspine/`, `.agents/skills/`, `.claude/skills` and
   `.claude/CLAUDE.md` are not ignored, also by a `.gitignore` higher up; a pattern such
   as `.claude/` there keeps them out of Git. Name such a pattern in the plan with a
   narrower replacement (for example `/.claude/`).
3. **Already set up?** If `.docspine/PROFILE.md` exists, say so, suggest `docspine-update`
   or `docspine-require`, and stop.
4. **Clean state.** Apart from the files of the installation (`.docspine/`,
   `.agents/skills/`, `.claude/skills`), the working tree has no uncommitted changes, and
   no local branch is left that is not merged into the main branch
   (`git branch --no-merged`). Moving the documentation would collide with work on other
   branches. If either is not the case, list what you found and stop; the person commits,
   merges or removes it first.

## Step 1 — Language

Derive the language from the existing documentation and ask in that language, for
example:

> Die Dokumentation ist auf Deutsch. Soll sie es bleiben (de)?

Use the answer as `language` (ISO 639-1 code). If the documentation is written in one
language throughout, you may instead put the language as the first line of the plan in
step 2, so that the person approves both at once.

## Step 2 — Take stock (silently, then one plan)

Read the project without changing anything:

- **Documentation:** every file and folder under `docs/` and every Markdown or AsciiDoc
  file in the repository root. Note format, ID scheme, front-matter fields and status
  values, and which files are generated (by the build or a script).
- **Where paths are used:** workflows, build files, scripts and skills that read or write
  paths under `docs/`. A folder that a build step or a script works on is a working area,
  not documentation.
- **Agent files:** `AGENTS.md`, `CLAUDE.md`, `.claude/`, `.agents/` and similar. Note
  which skills duplicate docspine's methodology (writing requirements, ADRs, architecture
  impact, building, proving) and which are the project's own. Note leftovers of other
  methods or tools, such as planning folders or agent definitions, that no longer serve
  the project.
- **Traceability:** how tests refer to requirements today (tags, names, comments), and
  whether the project has its own gate or generator for it. This goes to `docspine-gate`.

Then show the plan as one table, every row with a target:

| Found | Target |
|---|---|
| specification with requirements, stories, epics | `docs/01-goals/` (STANDARD 3.2–3.4) |
| vision or project description | core statement in `docs/01-goals/vision.md` (3.1) |
| decisions | `docs/09-decisions/ADR-NNNN.md` (3.5) |
| architecture chapters with content | chapter files (2.1); empty chapters are dropped |
| diagram sources and images | Mermaid where small; otherwise source and SVG under `docs/diagrams/`, the SVG rendered with the command `diagram` (9) |
| guides, runbooks and other own documentation | own folder under `docs/` (2.1) |
| old documentation without structure | `docs/legacy/` (10) |
| working areas (data, scripts, files a build step uses) | out of `docs/`, with every path updated; or stays, if moving breaks too much |
| generated views, own conventions replaced by the standard | delete |
| methodology skills | delete; `docspine-*` takes over |
| project skills | `.agents/skills/<name>/`, links to moved paths updated |
| architecture and rules in `CLAUDE.md`, `AGENTS.md` or similar | commands and working rules into `AGENTS.md` (2.2); content checked against the code into the chapters, unchecked content to `docs/legacy/`; the tool file only points to `AGENTS.md` |
| leftovers of other methods or tools | delete |

Add to the plan:

- **Schema changes** in one list: field names and values that change (for example
  `obligation: MUSS` → `MUST`, epic IDs in upper case), fields that are dropped (an epic's
  `status`; `confidence` and test paths in `evidence` for requirements that will be
  proven by test results, STANDARD 3.4), status values that map to the ones of
  STANDARD 3.3 and 3.4, hand-written back references that are removed (STANDARD 4),
  and relations the standard does not allow, for example a `verified` story that lists a
  rejected or superseded requirement, or a decision named as `source` instead of an
  external norm (it becomes `requires` in the decision).
  - **Requirements without front matter** (prose with sections such as requirement,
    rationale, acceptance): the requirement text becomes `statement`, one EARS sentence
    in `statement_language`, drafted by you and marked as a suggestion; the rationale
    becomes `rationale`, the acceptance criteria `verification`. The original text stays
    in the body as context, so nothing is lost.
  - **A level of obligation without equivalent** (for example `KANN`, `MAY`, `CAN`):
    `SHOULD`, with the original level noted in the body and a deviation in the profile.
    Check whether the `WHERE` pattern (optional feature) fits the statement.
  - **A story with more than one epic:** the first named epic goes into `epic`, the
    others are named in the body.
  - **Values with a colon** in YAML, such as titles (`Gameplay: Dots, …`), are quoted.
- **Norms** cited in the requirements' `source`, for `sources` in the profile.
- **The core statement** of the vision, drafted from existing text and marked as a
  suggestion; `UNKNOWN` with the open question if nothing fits.
- **Findings** that the move does not resolve: contradictions between documentation and
  code, requirements without a test, open points. They become open questions or
  contradictions in the documentation, or stories with `status: open`. Compare the
  commits since the documentation last changed (`git log` on the code paths) with the
  documented status: a feature that was built while the documentation still calls it
  open is a contradiction. The status itself stays as documented; proving it is the job
  of `docspine-gate` or the person.
- **What `docspine-gate` does afterwards:** the traceability you found and how it is
  replaced.

Mark everything you derived rather than read as a suggestion. Wait for approval; the
person may change single rows.

## Step 3 — Branch

Check the current branch. On the main branch, propose `docs/docspine-migration`
(STANDARD 2.7) and create it after approval. Never write to the main branch.

## Step 4 — Installation and skills

1. Commit the files of the installation as they are, together with a move of the
   project's skills to `.agents/skills/` if that happened in step 0.
2. Delete the methodology skills with `git rm`, and update paths in the project skills
   the plan keeps. Commit.

## Step 5 — Move and convert (one commit each)

1. **Working areas** out of `docs/`, if the plan says so, with every path in workflows,
   build files, scripts and skills updated.
2. **Move** every file to its target with `git mv`, including renames to the new IDs,
   file names and extensions (`ADR-001.adoc` → `ADR-0001.md`); create missing target
   folders first. Content and format stay unchanged. Git recognises a rename only if
   the content stays similar; a format change in the same commit loses it for short or
   table-heavy files.
3. **Convert formats:** AsciiDoc and other formats to Markdown. Keep the text; change
   only the markup. A file that is split into several (for example one arc42 document
   into chapter files) has no history to keep; split it here. Render DOT and PlantUML
   images once with `python3 .docspine/docspine.pyz diagram`, so that they carry the
   checksum of their source; if Graphviz or PlantUML is missing, name it as an open point.
4. **Align the schema** as listed in the plan, and update every reference to the new paths
   and IDs: resolve each relative link from the file's old location, and write ID
   references in the documentation in the new format (`ADR-006` → `ADR-0006`); code and
   working areas keep theirs. A link to a file that Git ignores or the build produces
   (a screenshot under `build/`) breaks the link check; write it as a path in backticks.
   Rewrite ADRs only in form (front matter, section headings); an accepted
   decision keeps its content (STANDARD 3.5).
5. **Write the frame:** `docs/01-goals/vision.md` with the approved core statement,
   `.docspine/PROFILE.md` (front matter `language`, `statement_language: en`, `sources`;
   below, the heading for deviations), `docs/README.md` translated from
   `.docspine/README.en.md` (STANDARD 2.4), `AGENTS.md` as STANDARD 2.2 describes (add a
   section "Documentation (docspine)" if the file exists), and the tool file pointing to
   it (for `.claude/CLAUDE.md` the single line `@../AGENTS.md`). As long as the build is
   not connected, the check in `AGENTS.md` is `check --without-tests`, with a note that
   `docspine-gate` connects the build.
6. **Delete** generated views, replaced conventions and leftovers, as approved, with
   `git rm`. Untracked files (for example rendered images that were never committed)
   stay; name them in the summary.
7. **Findings** as open questions, contradictions (STANDARD 7) or stories, as approved.

Do not write generated regions; the checker does that.

## Step 6 — Check

Run `python3 .docspine/docspine.pyz render`, then
`python3 .docspine/docspine.pyz check --without-tests`. The check must report `OK`. Fix
errors in what you moved or wrote; if an error points to something you cannot decide,
ask. Commit.

## Step 7 — Close

Summarise in a few lines what moved where, what was deleted, and the open questions
(they also appear in `docs/01-goals/README.md`). Then suggest:

- `docspine-gate` as the next step, before merging, so that the tests prove the requirements
  and the project's own traceability gate is replaced. Until then, requirements that are
  `implemented` are not proven by the check.
- merging the branch into the main branch with `--no-ff` once the check reports `OK`
  after `docspine-gate`; offer to do it after approval.
