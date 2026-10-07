---
name: docspine-build
description: >-
  Build a released requirement or story according to docspine: plan the test that proves
  the requirement before the code, stop and hand back when the requirement or the
  architecture has a gap, write code and test, and change the status once the test passes.
  Never rewrites requirements or chapters. Use when the user wants to implement a
  requirement or story that is planned, or calls /docspine-build with a REQ or US ID.
---

# docspine-build

You build what a requirement demands and close the loop that `docspine-require` (what) and
`docspine-impact` (effect on the architecture) opened: code, a test that carries the
requirement ID, a passing build, the status change. If building shows that the
requirement or the architecture is not yet sharp enough, you **stop** and hand back; you
repair neither of them on the way.

Before you start, read `.docspine/STANDARD.md` sections 3.3, 3.4, 5, 8 and 11, and
`AGENTS.md` for how the project builds and tests. The rules there apply; this skill only
describes the procedure. Talk to the person in the project language (`language` in
`.docspine/PROFILE.md`).

## Arguments

- a requirement ID, e.g. `/docspine-build REQ-0011`
- a story ID, e.g. `/docspine-build US-0004`: build its requirements one after the other. A
  story without requirements is ready for building only if its value lies in a part the
  requirement mechanism does not cover (for example a user interface over an existing
  capability); it is then proven through `evidence` (STANDARD 3.3). Otherwise send it to
  `docspine-require` first.
- nothing: list the requirements on `planned` and ask which one

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

- **Only released requirements.** Building works on `planned` (STANDARD 3.4). A
  requirement on `proposed` is released by the person first; ask, do not assume.
- **Flag, do not edit.** You never change the text of requirements, stories, decisions
  or chapters. The one exception is the status change in step 4. A gap is reported and
  handed back.
- **Constraints apply.** A constraint that `docspine-impact` named, or a decision that
  requires something, binds the building.
- **Existing tests stay as they are.** Add tests; do not change what an existing test
  checks.

## Newer version

Before anything else, run `python3 .docspine/docspine.pyz version`. It looks online at
most once a day and does not fail without a network. If it reports a newer version,
say so in one line and sum up its changelog entries in at most three points. Then offer
to install it first: on a branch of its own, run the command it shows, then the skill
`docspine-update`. Ask before doing so, because it fetches files from outside. If the
person declines, or there is nothing new, or the command could not check, carry on
without mentioning it again.

## Branch

Before writing anything, check the current branch (`git branch --show-current`). On the
main branch, propose a branch for this work (`feat/<topic>`, STANDARD 2.7) and create
it after approval. Never write to the main branch.

## Step 0 — Read the state (silently)

- the requirement: statement, obligation, rationale, verification, status, evidence;
  for a story, its requirements
- constraints and decisions that apply: ADRs with the requirement in `requires`, the
  chapters and building blocks around the code to change
- the modules to change, and how tests are written and named there; reuse the patterns
  you find
- how the project proves requirements: test results (STANDARD 8.1), or proof by hand if
  it has no automated tests (`AGENTS.md`, the profile, a decision)

## Step 1 — Plan and checkpoint

Map the statement to (a) the test that proves it and (b) the change to the code. Think
of the test first: which assertion shows each clause of the statement (trigger or
condition, and response)?

**Checkpoint.** Before building, check:

- Can the statement be mapped cleanly to a test? Is the acceptance unambiguous?
- Does the architecture carry the change (blocks, dependencies, constraints)?
- **Who reads what you add?** A new field, level or state that is only written and never
  read is half a change. Search for every consumer of the neighbouring fields and plan
  the reading side too.

If something does not fit, stop and classify the gap:

| Gap | Symptom | Hand back to |
|---|---|---|
| requirement | the what is ambiguous, incomplete or wrong; the acceptance cannot be tested | `docspine-require` (sharpen or supersede), then back |
| architecture | the what is fine, the how or where is missing: block, dependency, constraint, decision | `docspine-impact`, possibly `docspine-decide`, then back |
| both | both symptoms | first `docspine-require`, then `docspine-impact`, then back |

A frequent case: the genuinely new part lies below what the requirement's test can
show (for example only in the user interface, while the capability already exists). A
test of the capability would then be no honest proof; this is a requirement gap.

Report a gap as a short finding with evidence: what is missing, why it blocks, where it
goes. Do not guess, and do not interpret the requirement to fit.

If the plan is clean, show it briefly (test sketch, outline of the code, files touched)
and, after approval, build.

## Step 2 — Build

- Write the test so that its name carries the requirement ID (STANDARD 8.1), and the
  code. Follow the project's patterns.
- After each change, compile and run the tests of the module: the existing ones stay
  green, the new one passes.
- **Without automated tests** (proof by hand, STANDARD 3.4): instead of the test,
  prepare the proof: what is checked, how, and what shows the result (a screenshot, a
  measurement). Produce it where you can, and say where the person has to check by hand.

## Step 3 — Verify

1. Run the module's tests.
2. **Does the test prove the statement?** Go through the clauses: is each one exercised
   against real behaviour (STANDARD 8.3)? If not, sharpen the test; do not change the
   status.
3. Run the build with the check, as `AGENTS.md` describes. It reports a passing test for
   a requirement that is still `planned`; that is expected until step 4.

## Step 4 — Status change

As STANDARD 8.2 describes:

- in the requirement: `status: implemented`, and the implementation paths in `evidence`
  (with test results, not the tests);
- without test results: `evidence` with implementation and proof, `verification` with
  how it was checked, `confidence: verified`;
- in the story: `status: verified` once all its requirements are `implemented`.

Statement, rationale and title stay unchanged.

## Step 5 — Check

Run `python3 .docspine/docspine.pyz render`, then the build with the check. It must
report `OK`. Fix errors in what you changed; ask about anything else.

## Step 6 — Close

Summarise what was built, which test proves which requirement, and the status change.
Then suggest the next step, without starting it:

- if a new building block, dependency or cross-cutting concept appeared: `docspine-impact`
  for the requirement
- `docspine-prove` to have the proof checked
- reviewing the changes with `git status` and committing them: code and test together,
  the status change with them or right after
