---
name: docspine-require
description: >-
  Turn a need into testable artifacts according to docspine: a story with one or more
  requirements (EARS), under an existing or new epic. Works in dialogue; the user approves
  before anything is written. Use when the user wants to add or change a requirement or a
  story, refine an existing story into requirements, or calls /docspine-require
  (optionally with a need in their own words or a story ID such as US-0001).
---

# docspine-require

You help the person turn a need into artifacts of the description hierarchy:
**epic ⊃ story ⊃ requirement**. You propose wording; the person decides what applies.
You write only after approval. This is specification work: you never write code
(STANDARD 3.4, "Specifying and building").

Before you start, read `.docspine/STANDARD.md` sections 1, 3.2–3.4 and 5. The rules there
apply; this skill only describes the procedure. Talk to the person in the project
language (`language` in `.docspine/PROFILE.md`).

## Arguments

- a need in the person's own words, e.g. `/docspine-require the greeting must be configurable`
- a story ID, e.g. `/docspine-require US-0001`: refine this story into requirements
- nothing: ask what it is about

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

- the epics in `docs/01-goals/epics/` with title and purpose
- the stories in `docs/01-goals/stories/` with title, epic, status and requirements
- the next free IDs for `US-NNNN` and `REQ-NNNN` (IDs are never reused; on a branch
  they are reservations that become final on the main branch, STANDARD 2.7)
- `sources` and `statement_language` from `.docspine/PROFILE.md`
- with a story ID: that story and its requirements

## Step 1 — Dialogue

Propose instead of interrogating: draft each point from what you know and let the person
confirm or correct it. At most two rounds. Clarify:

1. **Level.** Is this a theme (epic), a benefit for users (story) or a testable rule for
   the system (requirement)? Usually it is a story with one or more requirements under an
   existing epic. Something that cannot be tested yet stays a story with
   `requirements: []`.
2. **Actor and trigger** → the EARS pattern (STANDARD 3.4): always valid, on an event
   (WHEN), in a state (WHILE), unwanted case (IF … THEN), optional feature (WHERE). One
   requirement states exactly one rule; split anything larger.
3. **Obligation:** MUST, SHOULD or WILL.
4. **Source:** an external norm listed in the profile's `sources`, or empty for an own
   requirement. A new norm must first be added to the profile; ask before doing so.
5. **Rationale:** why this rule, and what happens without it. This is the most important
   field. Do not invent one; ask.
6. **How fulfilment is checked:** a test, or another kind of proof. Write it into
   `verification` where no test will cover it.
7. **Epic:** which existing theme. A new epic only for a genuinely new theme.
8. **New or change:** does it change an existing requirement? Then it is a new
   requirement that supersedes the old one (STANDARD 5); the old one is never rewritten.
9. **Risk or debt:** does the story work on a risk or debt in `docs/11-risks/`
   (`R-`, `SEC-`, `TD-`)? Then it names it in `addresses` (STANDARD 3.8).

## Step 2 — Proposal

Show the complete draft in the chat: every file that would be created or changed, with
front matter and body. Wait for approval. On "change X", adjust and show again.

## Step 3 — Write (after approval)

- **Requirement** `docs/01-goals/requirements/REQ-NNNN.md` (STANDARD 3.4): `statement` in
  `statement_language`, `obligation`, `status: proposed` (always; the release for
  building is a separate decision); `source`; `rationale` in the project
  language; `verification` where needed; no `evidence` yet. Body as STANDARD 3.4
  describes.
- **Story** `docs/01-goals/stories/US-NNNN.md` (STANDARD 3.3): new, or the existing one
  with the new IDs added to `requirements`. Keep its status unless the person decides
  otherwise. A risk or debt it works on goes into `addresses`; never write the risk ID
  into the text as a back reference, and never write the progress of a fix ("fixed on
  …") into the story. The risk shows its stories and their status itself.
- **Epic** `docs/01-goals/epics/E-<NAME>.md`: only if a new theme was agreed.
- **Superseding:** in the old requirement set only `status: superseded` and
  `superseded_by`; the new one names it in `supersedes`.

Create referenced files before the files that refer to them. Do not write generated
regions; the checker does that.

## Step 4 — Check

Run `python3 .docspine/docspine.pyz render`, then `python3 .docspine/docspine.pyz check`.
The check must report `OK`. Fix errors in what you wrote; ask about anything else.

## Step 5 — Close

Summarise what was written, and suggest the next step. Stay in the specification
unless the person asks otherwise:

- by default, the next story or requirement, naming the stories that still have none
- checking the effect on the architecture (STANDARD 6) with `docspine-impact`
- reviewing the changes with `git status` and committing them

Mention as one option among these that a requirement can be released for building
(`proposed` → `planned`) once the person decides so. Do not start building.
