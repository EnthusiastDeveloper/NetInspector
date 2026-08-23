---
name: dev-feature
description: Full NetInspector feature lifecycle - requirements through a merged PR, including test coverage, the build gate, an emulator UI validation sweep, a real-device functional pass, a documentation pass, and PR creation. Use when starting non-trivial feature work in this repo, not for a one-line fix.
---

Full write-up, rationale, and the commands below in more detail: `docs/feature-development.md`.
Read it once per session if anything here is unclear - do not duplicate its content back to
the user, just follow it.

## Stage order

1. **Requirements.** Restate scope from the prompt or the named backlog item
   (`docs/improvement-ideas.md`, `docs/device-identification-ideas.md`,
   `docs/open-items.md`). Place it against `docs/design.md` §8.2's pipeline stages and
   `docs/adr/` before writing anything.

2. **Plan.** For anything beyond a one-file fix, use plan mode. The plan must explicitly
   address: minimal duplication (reuse existing helpers), reusability (pure functions for
   parsing/scoring/precedence logic, not just I/O), testability (keep such logic in plain
   `internal` functions/objects so it's JVM-unit-testable with no Android stubs), and
   debugability (does a new signal surface in the UI's `Evidence` timeline / uncertainty
   presentation, or is it a silent black box - see design.md §11.3).

3. **Implement**, scoped to the plan.

4. **Tests.** Land unit tests with the code, per design.md §12's split between JVM unit
   tests, instrumented tests, and the manual device matrix.

5. **Build gate.** Run `./scripts/verify.sh`. Do not proceed past a failure - fix it, don't
   route around it (no skipping ktlint/detekt rules, no `--no-verify`).

6. **UI validation.**
   - Layout (rotation, window size class, fold posture) needs no radio (ADR `C-13`) and is
     fully scripted: `./scripts/ui-matrix.sh boot <NetInspector_Resizable|NetInspector_Fold76>`,
     install the debug APK, navigate to each touched screen and run
     `./scripts/ui-matrix.sh sweep <serial> <outdir> <label>` per screen, review the
     screenshots, `./scripts/ui-matrix.sh kill <serial>` when done.
   - Anything radio-dependent (scanning, RSSI, ping, real hosts) needs a real device.
     **Before touching one, ask the user which device to use via AskUserQuestion, unless
     the prompt already named it.** List `adb devices -l` output as the options. Do not
     assume a device from prior context (memory, an earlier message) is still free -
     another agent or the user may be using it right now, and wireless-ADB addresses drift
     between sessions anyway.
   - If the feature adds or changes screen-level Compose state, verify it survives an
     actual rotation on the real device, not just the emulator sweep - see the rotation
     note in `docs/feature-development.md` stage 6.

7. **Bug-fix loop.** Back to steps 3-6 as needed; both `verify.sh` and `ui-matrix.sh sweep`
   are safe to re-run.

8. **Documentation - mandatory gate, not optional.** Before moving to step 9, walk
   `docs/feature-development.md` stage 8's table against the actual diff and update every
   row that applies (design.md, an ADR, a backlog doc's status, installing.md,
   troubleshooting.md, releasing.md, root README.md, CONTRIBUTING.md). If truly nothing
   applies, that must be a deliberate conclusion stated to the user, not a default skipped
   silently.

9. **PR.** Run `./scripts/pr-preflight.sh` first - it fails the branch if it isn't rebased
   onto `origin/main`, or if any commit message violates the writing conventions below, and
   prints a docs-touched summary as a last check against step 8. Then write the PR body
   (summary + test plan, same as any PR) and run `gh pr create`.

## Writing conventions (apply everywhere, not just where the hook checks)

- No em-dash or en-dash, anywhere: commits, PR text, code comments, string literals, docs.
- No `Co-Authored-By` trailer.
- No AI-attribution footer in commits or PR descriptions.
- Concise commit messages that explain why, not what.

## One-time repo setup (mention if the hooks aren't installed yet)

`./scripts/install-git-hooks.sh` installs `commit-msg` (writing-convention check) and
`pre-push` (full build gate) into `.git/hooks/`. Git doesn't version that folder, so a fresh
clone needs this run once.
