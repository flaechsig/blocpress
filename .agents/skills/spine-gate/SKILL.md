---
name: spine-gate
description: >-
  Connect docspine to a project's build and tests, or set up a new build connected from
  the start: recognise the tool chain, apply the matching integration step by step with
  explanations, and check the result. Use before the first build file or test is written,
  when the user wants the docspine check in the build or test results to prove
  requirements, or when the user calls /spine-gate.
---

# spine-gate

You connect docspine to the project's build and tests, following the integration
contract in `.docspine/STANDARD.md` section 11. How a particular tool chain meets the
contract is described in the integrations next to this file: `.agents/skills/spine-gate/integrations/*.md`. You explain every change before
you make it; the person approves. Talk to the person in the project language
(`language` in `.docspine/PROFILE.md`).

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

## Rules for the whole run

- **Explain, then write.** For each change: what, where, and why. Write only after
  approval.
- **Change as little as possible.** Add to existing build files; do not reorder or
  reformat them. If the project already configures a plugin, extend that configuration
  instead of adding a second one. Keep newer versions the project already uses.
- **Never change what a test checks.** Renaming a test's display name is allowed after
  approval; its assertions stay as they are.
- **Use only what an integration describes.** Do not invent configuration for a tool
  chain without an integration; see "No matching integration".

## Branch

Before writing anything, check the current branch (`git branch --show-current`). On the
main branch, propose a branch for this work (`feat/<topic>`, STANDARD 2.7) and create
it after approval. Never write to the main branch.

## Step 1 — Read the state (silently)

- the front matter of every file in `.agents/skills/spine-gate/integrations/`: `name`,
  `detect`, `keywords`, `test_command`, `test_reports`, `requires`
- which integrations match:
  - **existing build:** the file named in `detect` exists in the repository root
  - **no build yet:** the tool chain named in `docs/02-constraints.md` or in decisions
    (`docs/09-decisions/`) matches an integration's `keywords`
- `.docspine/PROFILE.md`: is `test_reports` already set?
- the build file of the matching integration: what is already configured?
- the tests: which carry a requirement ID in their name, which do not
- the requirements with `status: implemented` and how they are proven today
- requirements with `status: proposed` or `planned` whose tests already pass
- whether the prerequisites in `requires` are available (for example `python3 --version`)

## Step 2 — Explain the plan

Name the integration you found and why it matches. If several match, or if no build
exists and the constraints do not name a tool chain, ask which one applies. Then show, as
a numbered list, each change with a short explanation:

0. **New build** (only if no build file exists) — create it from the integration's
   section "New build", with the project's names filled in. It contains the settings of
   points 1 and 2 from the start.
1. **Test results** — what the integration requires so that requirement IDs appear in the
   reports (for example a reporter setting), and which tests would need an ID in their
   name. List those tests; do not rename them yet.
2. **Check in the build** — the configuration that runs `check` after the tests.
3. **Profile** — nothing to do, the check finds the reports itself. Set `test_reports`
   only if the search must be limited (STANDARD 8.1).
4. **AGENTS.md** — the workflow in this order: `python3 .docspine/docspine.pyz render`,
   then the integration's `test_command`.
5. **Constraints** — the prerequisites from `requires` that are new for the project, in
   `docs/02-constraints.md`.
6. **Decision** — offer an ADR with `status: proposed` that records the integration and
   the rejected alternative, running the check by hand (STANDARD 11). The person decides
   its status.

7. **Status** — once the reports are read, a passing test for a requirement that is not
   yet `implemented` fails the build. Propose the status change of STANDARD 8.2 in the
   same step, for released (`planned`) requirements only; for `proposed` ones, ask first
   whether to release them (STANDARD 3.4).

Mention what the person should expect: a documentation error will now fail the build,
and the tests must run before `check` (`check --without-tests` checks the documentation
alone).

Wait for approval. The person may approve single points.

## Step 3 — Carry out (after approval)

Make the approved changes. Rename test display names only for the tests the person
approved, and only to add the requirement ID.

## Step 4 — Check

Run `python3 .docspine/docspine.pyz render`, then the integration's `test_command`. The
build must succeed and the check in it must report `OK`. If it fails, show the error and
its cause; fix what this run changed, and ask about anything else.

Report which requirements are now proven by test results, and which still need a test or
a proof by hand.

## Step 5 — Close

Summarise the changes and suggest reviewing them with `git status` and committing them.

## No matching integration

If no integration matches the project:

1. Say so, and explain the contract in your own words.
2. Ask how the project runs its tests and whether its test tool can write JUnit XML.
3. Propose a way that meets the contract: JUnit XML with the requirement ID in the test
   name if the tool supports it, otherwise a small step that writes `req-results.json`
   (STANDARD 8.1), plus a call of `check` after the tests.
4. Write nothing that you have not tried in this project. Suggest describing the result
   as a new integration for docspine (see `integrations/README.md` in the docspine
   repository), so the next project can reuse it.
