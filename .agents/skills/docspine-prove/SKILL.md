---
name: docspine-prove
description: >-
  Check whether the proof behind the traceability holds according to docspine: explain
  the check's errors and route each to the step that fixes it, and audit whether the test
  of each implemented requirement really exercises every clause of its statement (full,
  partial, hollow). Checks and reports; does not write tests or change requirements. Use
  when the check fails, when the user doubts that tests prove the requirements, or calls
  /docspine-prove (optionally with a REQ ID, a module, or "check").
---

# docspine-prove

You check whether the **proof** behind the traceability holds. The check answers a
question of fact: does a passing test carry the requirement ID? You answer the question
above it: does this test **prove** what the requirement says, completely and honestly?

Two questions guide you:

1. **Is the proof complete?** Every `implemented` requirement has a passing test or a
   proof by hand, and the proof covers every clause of the statement (WHEN and the
   response, IF and THEN, WHILE and the state).
2. **Is the proof meaningful?** Does the test of REQ-X exercise the behaviour of REQ-X, or
   is it a hollow ID: passing, but a trivial getter, an unrelated assertion, behaviour
   never run?

This skill is not code coverage (lines, branches) and not a general review of test
quality. It does not create the proof: tests are sharpened with `docspine-build`,
requirements with `docspine-require`. You check and report.

Before you start, read `.docspine/STANDARD.md` sections 3.4, 8 and 11. The rules there
apply; this skill only describes the procedure. Talk to the person in the project
language (`language` in `.docspine/PROFILE.md`).

## Arguments

- `check`: mode A, run the check and explain its errors
- a requirement ID, e.g. `/docspine-prove REQ-0007`: mode B for this requirement
- a module or folder: mode B for the requirements its tests carry
- nothing: mode B for all `implemented` requirements

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

- **Check, do not produce.** You write no tests, change no requirement and no status.
  Reading, running the build and the check are allowed.
- **Passing is not proven** (STANDARD 8.3). Only the clause-by-clause check decides.
- **Against the statement, not the test name.** What counts is what the assertions drive
  and pin down in the real behaviour.
- **A gap is not always a test gap.** A finding can be a requirement defect (wrong actor,
  untestable, overloaded clause). Then the way out is superseding the requirement, not a
  test that cements a wrong statement.
- **Every verdict with evidence:** the test (file and line, assertion) and the clause it
  covers or misses.
- **Only `implemented` requirements must be proven.** `planned` and `proposed` are not a
  finding. A story proven through `evidence` needs no requirement test.

## Newer version

Before anything else, run `python3 .docspine/docspine.pyz version`. It looks online at
most once a day and does not fail without a network. If it reports a newer version,
say so in one line and sum up its changelog entries in at most three points. Then offer
to install it first: on a branch of its own, run the command it shows, then the skill
`docspine-update`. Ask before doing so, because it fetches files from outside. If the
person declines, or there is nothing new, or the command could not check, carry on
without mentioning it again.

## Step 0 — Read the state (silently)

- the requirements on `implemented`, and how each is proven: test results (STANDARD 8.1)
  or a proof by hand (`evidence` and `verification`)
- the tests that carry requirement IDs in their names
- whether the test results are current: run the full test suite of a module before you
  judge it; a filtered run leaves results that seem to lack proofs

## Mode A — Explain the check

1. Run the check as `AGENTS.md` describes. If it reports `OK`, say so and offer mode B
   for the depth the check does not see.
2. Otherwise, explain every error in words and route it to the step that fixes it:

   | Error (STANDARD 11) | Usual way out |
   |---|---|
   | 1–4, 7 front matter, IDs, references, sources, successors | correct the artifact: `docspine-require`, or `docspine-decide` for an ADR |
   | 5, 6 story `verified` without proof, or with a requirement not `implemented` | status back to `in-progress`, or build the rest: `docspine-build` |
   | 8 `implemented` without a passing test or proof by hand | build the test: `docspine-build`; or set the status honestly back to `planned` |
   | 9 passing test, but `proposed` or `planned` | the status change after checking the proof: `docspine-build` |
   | 10 result for a requirement that does not exist, or unreadable results | correct the test name, or the requirement is missing: `docspine-require` |
   | 11 generated region differs | run `render` |
   | 12 diagram image outdated | render the diagram with the command `diagram` |
   | 13, 15 broken link or `evidence` path | correct the path |
   | 14 README from another version | `docspine-update` |

   An error can have two readings: "the test is missing" or "the status is not honest".
   Name both and recommend one.

## Mode B — Audit the proof

For each `implemented` requirement in scope:

1. **Split the statement** into its clauses: trigger or condition, and response. Each is
   one item to prove. Read the rationale for the actual acceptance.
2. **Find the proof:** the tests that carry the ID, or `evidence` and `verification` of a
   proof by hand. Read what the assertions or the evidence actually show.
3. **Verdict** (STANDARD 8.3): **full**, **partial** or **hollow**.
4. **Kind of finding:** a **test gap** (sharpen the test, `docspine-build`) or a
   **requirement defect** (`docspine-require`, superseding).

For many requirements, audit them in parallel if your tool allows it, each with the same
structured result: requirement, clauses, verdict, evidence, kind of finding. Then
combine them in one table.

## Report

The report lives in the chat, not in a file; the fixes go into tests and requirements
through the skills that own them. Give:

1. a table per requirement: verdict, clause not covered, evidence, kind of finding;
2. recommendations in order: hollow before partial, requirement defects first, since a
   test would otherwise cement them. For each, the next step (`docspine-build REQ-NNNN`,
   `docspine-require …`), offered, not started;
3. in mode A also: will the check pass after these steps? If a finding is only an
   honest status, say so.
