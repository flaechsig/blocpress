---
name: docspine-decide
description: >-
  Record an architecture decision as an ADR according to docspine: sharpen the context,
  lay out the options, let the user decide, and write the decision with its rationale and
  consequences. Advisory; the user decides what applies. Use when docspine-impact found a
  decision due, when the user wants to record or supersede a decision, set an ADR from
  proposed to accepted, or calls /docspine-decide (optionally with a topic or an ADR ID).
---

# docspine-decide

You help the person record a **due decision** as an ADR: which options there were, what
was decided, why, and what follows. You sharpen the context, propose wording and write
the file after approval. You do not decide what applies; the person does.

An ADR is a decision, not a requirement and not a description. What the system shall do
goes to `docspine-require`; what the code does goes into the chapters with `docspine-impact`.
The ADR records **why** the architecture is the way it is.

Before you start, read `.docspine/STANDARD.md` sections 1, 3.5, 4, 5 and 6. The rules
there apply; this skill only describes the procedure. Talk to the person in the project
language (`language` in `.docspine/PROFILE.md`).

## Arguments

- a topic in the person's own words, e.g. `/docspine-decide authorisation of the import`
- a decision handed over from `docspine-impact` (context and options sketched): take the
  sketch up and sharpen it instead of asking again
- an ADR ID, e.g. `/docspine-decide ADR-0019`: complete a proposed ADR, set it to
  `accepted`, or supersede it
- nothing: ask which decision is to be recorded; never create an ADR unasked

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

- **The person decides.** If several options are viable, lay them out with a
  recommendation; the choice is theirs.
- **The rationale is the core**, together with the rejected options. Do not invent a
  plausible reason; ask.
- **The current state rests on the code.** The context of the ADR names the files and
  paths that show the starting point.
- **Honest status.** `proposed` while the direction is open or not confirmed; `accepted`
  only when the person confirms it, asked for separately.
- **Accepted means immutable** (STANDARD 3.5, 5). A changed decision is a new ADR that
  supersedes the old one.

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

- the decisions in `docs/09-decisions/` with title and status, and the next free
  `ADR-NNNN`
- whether this decision, or an overlapping one, exists already: then it is a completion
  (only while `proposed`), a supersession, or genuinely new
- the requirements and stories the decision touches, and contradictions in the chapters
  it could resolve

## Step 1 — Dialogue

Short and to the point; only as much as a clean ADR needs. Clarify:

1. **Is it architecture?** Worth an ADR is what is hard to reverse and shapes several
   building blocks, cross-cutting concepts or operation (STANDARD 6). A question local
   to one element is not.
2. **Context:** which problem or force makes a decision necessary; the current state
   with evidence from the code.
3. **Options:** the alternatives actually considered, each with its pros and cons.
4. **Decision:** which option applies. If it is still open, ask (rule "the person
   decides").
5. **Rationale:** why this option, and why not the others.
6. **Consequences:** new dependencies, migration, operation, affected requirements and
   stories, contradictions it resolves, follow-up work. The inconvenient ones too.
7. **Testable consequences:** requirements that follow from the decision go into
   `requires` (STANDARD 3.5, 4). New ones are written with `docspine-require`.
8. **Relation to existing ADRs:** does it refine one, or supersede it?

## Step 2 — Proposal

Show the complete draft in the chat: number, title, status, date, front matter and the
sections of STANDARD 3.5, plus every change to other files (a superseded ADR, a
superseded requirement or story). If the direction is open, the choice between the
options is part of this step. Wait for approval.

## Step 3 — Write (after approval)

- `docs/09-decisions/ADR-NNNN.md` with the front matter and sections of STANDARD 3.5;
  the date of the decision, never guessed. Rejected options stay in the decision as
  history.
- **Superseding a decision:** in the old ADR set only `status: superseded` and
  `superseded_by`; the new one names it in `supersedes`.
- **A decision that replaces a requirement or story:** set `status: superseded` and
  `superseded_by: ADR-NNNN` there (STANDARD 5); nothing else in it changes.
- **Contradictions** stay in place until the building actually resolves them; the ADR
  names them.
- **Status change of an existing ADR** (`proposed` → `accepted`): only after the person
  confirmed it explicitly. Until then, a proposed ADR may still be edited.

Create referenced files before the files that refer to them. Do not write generated
regions; the overview lists open decisions itself.

## Step 4 — Check

Run `python3 .docspine/docspine.pyz render`, then the check that `AGENTS.md` names. It
must report `OK`. Fix errors in what you wrote; ask about anything else.

## Step 5 — Close

Summarise number, status and what changed. Then suggest the next step, without starting
it:

- new behaviour the decision requires: `docspine-require`
- chapters the decision changes beyond a link: `docspine-impact`
- a decision on `proposed`: setting it to `accepted` once the person decides
- reviewing the changes with `git status` and committing them
