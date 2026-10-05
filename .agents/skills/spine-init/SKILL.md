---
name: spine-init
description: >-
  Start a project's documentation according to docspine: agree on the project language,
  draft the vision with the user, and write profile, vision, themes, first stories, README
  and AGENTS.md. Use when the user wants to set up docspine, start the documentation of a
  new project, or calls /spine-init. After an update of docspine, use spine-update instead.
---

# spine-init

You set up the documentation of a project according to docspine. The rules are in
`.docspine/STANDARD.md`; read sections 1–3 before you write anything. You work in dialogue:
you propose, the person decides, and you write only after approval.

## Rules for the whole run

- **Propose instead of interrogating.** Turn what the person says into concrete drafts
  they can confirm or correct. Ask at most two rounds; never push.
- **Nothing is invented.** What the person does not know stays
  `UNKNOWN` with the open question (STANDARD principle 6).
- **Only the core statement of the vision is required.** Everything else may stay open.
- **Never overwrite existing files without asking.** Show what you would change.
- **Write only after explicit approval.**

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

## Step 0 — Check the installation (silently)

1. Run from the repository root. `.docspine/STANDARD.md` and `.docspine/docspine.pyz` must
   exist; they come from the installation one-liner. `.docspine/` holds the configuration,
   `docs/` only the documentation. If they are missing, tell the person
   and show the one-liner from the docspine quickstart.
2. Read the docspine version from the first line of `.docspine/STANDARD.md`
   (`<!-- docspine X · … -->`).
3. Check that `python3 --version` works. If it does not, tell the person that the checker
   needs Python 3.9 or later, and continue with everything except the check in step 6.
4. Check that `git --version` works and whether the folder is a Git repository
   (`git rev-parse --is-inside-work-tree`). Without Git, tell the person that docspine
   needs it (STANDARD 2.6) and stop.
5. Treat the folder as a real project. Do not assume it is a test or a throwaway, and
   do not skip steps on that assumption.

## Step 1 — Language

Ask in English, with a suggestion derived from the person's first message:

> Which language should the project documentation use? Suggested: German (de). OK?

Use the answer as `language` (ISO 639-1 code). From now on, talk to the person in that
language.

## Step 2 — Situation

- **`.docspine/PROFILE.md` exists** → the project is already set up. Say so, suggest
  `spine-update` after an update of docspine, otherwise `spine-require`, and stop.
- **The repository already contains code or documentation** (beyond the files from the
  installation) → say that `spine-adopt` is meant for existing projects. Continue only if
  the person explicitly wants to start the documentation fresh.
- **Otherwise** → new project, continue.

If the folder is not yet a Git repository, propose `git init -b main` with one sentence
why (branches for every change, the history as part of the proof) and run it after
approval, before anything is written. `-b main` names the main branch independently of
the machine's Git configuration.

## Step 3 — Vision in dialogue

If the person has not described the project yet, ask for two or three sentences.

From that description, propose drafts for all of the following in one message, in the
project language, numbered so the person can answer briefly:

1. **Core statement** — the vision in one sentence, for example "For <target group> who
   <need>, <product> is a <category> that <benefit>. Unlike <alternative>, …"
2. **Problem** — what is bad today, and for whom
3. **Target group and stakeholders**
4. **Success** — how we will know it works, measurable where possible
5. **Non-goals** — what deliberately does not belong
6. **Quality goals** — suggest at most three
7. **Themes** — suggest three to seven epics, each with an ID `E-<NAME>` (upper case, descriptive)
   and a one-line description
8. **First stories** — for each theme one to three stories in the form "As <role> I want
   <goal> so that <benefit>", with a short title. Number them `US-0001`, `US-0002`, …
   across all themes. Requirements come later with `spine-require`.
9. **Constraints** — technology, platform, norms, budget, time
10. **Decisions already made** — for example language or platform

Mark every draft that you derived rather than heard as a suggestion. For anything you
cannot derive, write `UNKNOWN` with the open question.

The person confirms, corrects or says "don't know yet". Incorporate the answers. Then
show the complete result once more and ask for approval to write.

## Step 4 — Write (after approval)

Write in the project language; folder and file names and front-matter keys stay English
(`.docspine/STANDARD.md` section 2.4).

| File | Content |
|---|---|
| `.docspine/PROFILE.md` | front matter `language`, `statement_language: en`, `sources: []`; below, the heading for deviations with an empty list |
| `docs/01-goals/vision.md` | core statement, problem, target group and stakeholders, success, non-goals, quality goals, and a section "Themes" that links to `README.md` (STANDARD 3.1) |
| `docs/01-goals/epics/E-<NAME>.md` | one per theme: front matter `id`, `title`; heading; what the theme covers and why |
| `docs/01-goals/stories/US-NNNN.md` | one per story: front matter `id`, `title`, `epic`, `requirements: []`, `status: open`; the body as STANDARD 3.3 describes |
| `docs/02-constraints.md` | only if constraints are known |
| `docs/09-decisions/ADR-NNNN.md` | one per decision already made, `status: proposed`, sections context, decision, consequences (STANDARD 3.5) |
| `docs/01-goals/requirements/REQ-NNNN.md` | only for measurable quality goals: `category: quality`, `status: proposed`, EARS statement in English (STANDARD 3.4) |
| `docs/README.md` | the translated README, see below |
| `AGENTS.md` | see below |
| `.claude/CLAUDE.md` | only if neither `CLAUDE.md` nor `.claude/CLAUDE.md` exists: the single line `@../AGENTS.md` |

Do not create empty chapters.

### The README

Translate `.docspine/README.en.md` into the project language and write it to
`docs/README.md`, as STANDARD 2.4 describes, with the version from step 0 in the first
line.

### AGENTS.md

If `AGENTS.md` does not exist, create it. If it exists, add a section "Documentation
(docspine)" and leave everything else unchanged. Content as STANDARD 2.2 describes; the
check, at the latest before merging and preferably before every commit, in this order:

  ```
  <the project's test command, e.g. mvn verify>
  python3 .docspine/docspine.pyz render
  python3 .docspine/docspine.pyz check
  ```
  As long as the project has no tests yet, write the first line as a placeholder
  comment, followed by: "Create the build and the first tests with `spine-gate`, so that
  they are connected to docspine from the start." `python3 .docspine/docspine.pyz check
  --without-tests` checks the documentation alone.

## Step 5 — Check

Run `python3 .docspine/docspine.pyz render`, then `python3 .docspine/docspine.pyz check`.
The check must report `OK`. Fix errors in the files you wrote; if an error points to
something you cannot decide, ask.

## Step 6 — Close

Summarise in a few lines what was written, list the open questions (they also appear in
`docs/01-goals/README.md`), and suggest:

- reviewing the changes with `git status` and committing everything, including the files
  from the installation, as the first commit on the main branch; offer to do it after
  approval. This is the only commit directly on the main branch; all later work happens
  on branches (STANDARD 2.7).
- connecting a remote repository, if there is none yet (`git remote -v`). Explain in two
  sentences: working alone needs no remote, branches work locally; a team shares a
  remote as its meeting point, and where it lives is the team's decision. If the person has already created an empty repository at a
  provider, offer to run `git remote add origin <url>` and `git push -u origin main`
  with the URL they give. Do not create repositories at a provider.
- `spine-require` for the most important theme as the next step
- `spine-gate` before the first build file or test is written, so that the build is
  connected to docspine from the start

