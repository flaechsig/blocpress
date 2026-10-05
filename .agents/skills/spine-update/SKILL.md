---
name: spine-update
description: >-
  Finish an update of docspine in a project: translate the README again where its English
  source changed, remove files docspine no longer delivers, and run the checker. Use after
  running the docspine installation command again, or when the user calls /spine-update.
---

# spine-update

The installation command has just written a new version of docspine into `.docspine/`,
`.agents/skills/spine-*` and `.claude/skills`. You finish the update. Everything the
project wrote itself stays untouched, except where a step below says otherwise and the
person approves.

Talk to the person in the project language (`language` in `.docspine/PROFILE.md`).

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

## Branch

Before writing anything, check the current branch (`git branch --show-current`). On the
main branch, propose a branch for this work (`docs/<topic>`, STANDARD 2.7) and create
it after approval. Never write to the main branch.

## Step 1 — Read the state (silently)

1. The installed version: first line of `.docspine/STANDARD.md` (`<!-- docspine X · … -->`).
2. The README version: first line of `docs/README.md` (`<!-- docspine Y · … -->`).
3. Files from older versions, found by comparing the repository with
   `.docspine/MANIFEST`:
   - folders `.agents/skills/spine-*` that are not listed in the manifest
   - `docs/STANDARD.md` (before version 0.1 of the layout, the standard lived there)
   - `docs/PROFILE.md` (likewise for the profile)
   - `docs/STATUS.md` (before version 0.3; its content is now in `docs/01-goals/README.md`)
4. A field `docspine:` in the front matter of `.docspine/PROFILE.md` (no longer used).

## Step 2 — Propose

Tell the person what the update brought: the entries in `.docspine/CHANGELOG.md` that
are newer than the README's version. Then list what you would do:

- **README:** translate `.docspine/README.en.md` again into `docs/README.md` if its
  version differs from the installed version; otherwise "README is current".
- **Profile in the old place:** move `docs/PROFILE.md` to `.docspine/PROFILE.md` if only
  the old one exists.
- **Leftover files:** delete the files and skill folders found in step 1.3, except the
  profile.
- **Profile field:** remove `docspine:` from `.docspine/PROFILE.md` if present.

If there is nothing to do, say "Nothing to do" and continue with step 4.

Wait for approval.

## Step 3 — Carry out (after approval)

- Translate the README as STANDARD 2.4 describes, with the installed version in the first
  line.
- Move, delete and edit as approved. Delete with `git rm` where the file is tracked, so
  the deletion shows up in the next commit.

## Step 4 — Check

Run `python3 .docspine/docspine.pyz render`, then `python3 .docspine/docspine.pyz check`.
The check must report `OK`. Fix errors caused by the update; if an error points to the
project's own content, show it and ask.

## Step 5 — Close

Summarise in a few lines: old and new version, what changed in the project, and suggest
reviewing the changes with `git status` and committing them.
