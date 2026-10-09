---
name: docspine-impact
description: >-
  Check what a requirement or change means for the architecture according to docspine:
  answer the question against the code, give exactly one verdict (already covered, affects
  chapter, decision due, no impact), and update the affected arc42 chapter only where the
  architecture really changes. Advisory; the user decides what applies. Use after
  docspine-require, before building, when the user asks whether a change affects the
  architecture, or calls /docspine-impact (optionally with a REQ or US ID, a chapter, or a
  change in their own words).
---

# docspine-impact

You answer one question for a requirement or a change: **what does it mean for the
architecture?** You ask the code, not the person. The person comes in only where code and
documentation disagree or a decision is due; then they say what applies.

This skill is not a documentation generator. Its most frequent good result is a short,
evidenced verdict, often "no impact". The documentation grows with the changes (STANDARD
principle 8); writing nothing on good grounds is as valuable as writing.

Before you start, read `.docspine/STANDARD.md` sections 1, 2.1, 3.6–3.9, 6, 7 and 9. The
rules there apply; this skill only describes the procedure. Talk to the person in the
project language (`language` in `.docspine/PROFILE.md`).

## Arguments

- a requirement or story ID, e.g. `/docspine-impact REQ-0011`
- a chapter, e.g. `/docspine-impact 08-concepts`
- a change in the person's own words, e.g. `/docspine-impact the cache is encrypted`
- nothing: name the chapters that are missing or partly filled (`docs/01-goals/README.md`)
  and the requirements on `proposed` or `planned` that were not checked yet, and propose
  the next one. Never document a chapter just because it is empty.

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

- **Evidence before claims.** Every statement about the architecture rests on the code:
  a file, a path, a test. Name it.
- **Observation is not intention** (STANDARD principle 4). What the code does is a
  description in the chapters, with `confidence` (STANDARD 7). What is wanted is a
  requirement; send it to `docspine-require`.
- **Contradictions stay visible.** Where code and documentation disagree, mark it as a
  contradiction (STANDARD 7). Never correct it silently; the person decides.
- **Only the affected chapter.** Leave untouched chapters as they are; a missing chapter
  stays missing until a change gives it content.

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
main branch, propose a branch for this work (`spec/<topic>`, STANDARD 2.7) and create
it after approval. Never write to the main branch.

## Step 0 — Read the state (silently)

- the starting point: with an ID, the requirement or story (statement, rationale,
  evidence); with a chapter, its file; with a description, the modules and files it
  touches
- the chapters under `docs/` and the building blocks with their `path`, so you know
  where the code is described
- decisions in `docs/09-decisions/` that touch the topic, and constraints an earlier
  check named for it

## Step 1 — Verdict

Answer the question against the code. Search directly; where the search spreads over
many modules, split it into parallel searches if your tool allows it. Give exactly one
verdict of STANDARD 6, with evidence for each statement:

| Verdict | What you do |
|---|---|
| already covered | nothing changes; name a constraint the building must respect, if there is one |
| affects chapter | draft the change of that chapter, with evidence and `confidence` |
| decision due | sketch the context and the options; the ADR itself is written with `docspine-decide` |
| no impact | say why it stays below architecture level, and stop |

Check the effect, not the size (STANDARD 6).

## Step 2 — Drift (only for "affects chapter")

Hold the new evidence against what the chapter says today. Every difference between code
and documentation becomes a contradiction block (STANDARD 7), not a silent correction.
Present how it could be resolved; the person decides.

## Step 3 — Proposal

Show the verdict in the chat. For "affects chapter", also the exact text: which file,
which statements, their `confidence`, links to decisions, contradiction blocks, and
diagram sources to change (STANDARD 9). Wait for approval; on "change X", adjust and show
again.

For "no impact" and "already covered" without a constraint to record, the skill ends
here, without changing a file.

## Step 4 — Write (after approval)

- Edit the chapter file; create it if the chapter does not exist yet (STANDARD 2.1). A
  chapter that is only partly filled carries `arc42_status: PARTIAL` (STANDARD 3.9).
- Every new statement carries its confidence inline (STANDARD 7).
- A changed building block keeps its `path` current (STANDARD 3.6).
- Chapter 11 with risk files (`docs/11-risks/`, STANDARD 3.8): a new risk or debt is a new
  file with the next free number of its prefix, `status: open`; a security risk is
  `SEC-`, never `R-`. Set a risk to `closed` only when every story in its region
  `stories` is `verified`; `accepted` only when the person decides to live with it. Do
  not write which story handles it or when it was fixed; that is generated.
- Diagrams: change the source; render DOT and PlantUML with
  `python3 .docspine/docspine.pyz diagram`. If Graphviz or PlantUML is missing, change
  only the source and name it as an open point.

Do not write generated regions; the checker does that.

## Step 5 — Check

Run `python3 .docspine/docspine.pyz render`, then the check that `AGENTS.md` names. It
must report `OK`. Fix errors in what you wrote; ask about anything else.

## Step 6 — Close

Summarise the verdict and what changed (or why nothing did). Then suggest the next step,
without starting it:

- "decision due": `docspine-decide`, with the sketched context and options
- a constraint: name it clearly, so that `docspine-build` respects it
- otherwise: releasing the requirement for building (`proposed` → `planned`, the person
  decides), then `docspine-build`
- reviewing the changes with `git status` and committing them
