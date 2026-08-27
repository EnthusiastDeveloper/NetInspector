# Feature development process

The end-to-end path from a requirement to a merged PR, and which parts are scripted versus
which need judgment. The `/feature-dev` Claude Code skill
(`.claude/skills/dev-feature/SKILL.md`) drives this for an AI-assisted session, but every
script it calls also works by hand - nothing here depends on being run by Claude.

## One-time setup

```bash
./scripts/install-git-hooks.sh
```

Installs two git hooks from `githooks/` into `.git/hooks/` (git does not version that
folder, so this has to be run once per clone):

- `commit-msg` - rejects a commit whose message violates the writing conventions below.
- `pre-push` - runs the full build gate (`scripts/verify.sh`) before anything leaves the
  machine, so a failure is caught locally instead of after a round trip through CI.

## Stages

### 1. Requirements

A feature starts from a direct request, or from a backlog item: a GitHub issue for
concrete, scoped work, or an entry in the ranked ideas backlog (`docs/ideas.md`) for a
larger or less-scoped idea. Either way, place it in the architecture first: which stage of the pipeline does it belong
to (see `docs/design.md` §8.2), does it touch a fixed decision recorded in `docs/adr/`, does
it change a row in `docs/testing.md`'s device matrix or corner-case checklist. Restate scope
and flag open questions before writing code.

### 2. Design

For anything beyond a one-file fix, use Claude Code's plan mode to settle the approach
before touching code. A plan is not just file-by-file mechanics - hold it to the same bar
as review:

- **Minimal duplication.** Check for an existing helper before writing a new one. Three
  similar lines beat a premature abstraction, but a fourth copy of the same logic doesn't.
- **Reusability.** Pure, small functions for anything that is really a decision (parsing,
  scoring, precedence) rather than I/O - this project already does this consistently
  (`DeviceHintHeuristics.kt`, `SnmpBer`, `Host.kt`'s `HOSTNAME_PRECEDENCE`), and it is what
  makes the next point possible.
- **Testability.** A function that needs no Android framework class can be a plain
  `internal` top-level function or object, unit-tested directly on the JVM with no
  instrumentation and no mocking. Keep wire-format parsing (regex or hand-rolled binary,
  see `UpnpHostsProbe`, `SnmpBer`) out of anything that touches `android.util.Xml`-style
  unmocked stubs, for exactly this reason.
- **Debugability.** Every new signal that can identify or explain a host should be visible
  to the user, not just folded into a score - this project's `Evidence` timeline exists
  specifically so a confirmed `DeviceHint` is never a black box (§11.3, "Presenting
  uncertainty"). If you add a probe or heuristic, ask where its result surfaces in the UI.

### 3. Implementation

Write the code. Keep changes scoped to what the plan called for.

### 4. Test coverage

Unit tests land with the code that needs them, not after. `docs/testing.md` lists what
belongs in JVM unit tests versus instrumented tests versus the manual device matrix -
golden-file parser tests are called out there as the highest-value tests in the project.
Its "Boundary and negative-path coverage" list is a checklist, not just prose: if the
diff touches a clamped/bounded parameter, a throttle or quota gate, or an async action with
a minimum-duration UI affordance, the boundary or the non-happy-path branch needs a test of
its own, not just the typical-case path. If the diff touches a gesture-capable control
(a `pointerInput` handler, a pull-to-refresh), check `docs/testing.md` §4 for whether its
math is already extracted and tested the way `AxisMapper`/`AxisViewport` are - if not, that
extraction is part of the same change, not a follow-up.

**Changing code that existing tests already cover updates those tests, not just the code.**
A test left asserting stale behaviour is worse than no test - it passes while lying about
what the code does. If a fix or a refactor changes what a covered function returns or how
it behaves, the test for it is part of the same change, not a follow-up.

### 5. Build gate

```bash
./scripts/verify.sh
```

Runs `ktlintCheck detekt test assembleDebug` - the same steps, same order, as
`.github/workflows/ci.yml`. A pass here means CI passes. This also runs automatically on
`git push` once the hooks are installed.

### 6. UI validation

The full process - the scriptable layout sweep, the gesture pass, the corner-case
checklist, and the state-survival checks - lives in `docs/testing.md` §7. Quick summary,
split the way the device matrix splits it because the emulator has no Wi-Fi radio (ADR
`C-13`):

- **Layout** needs no radio and is fully scriptable via `scripts/ui-matrix.sh sweep`,
  run once per screen **family** the diff touches (every screen reachable from the one
  edited, not only the specific sub-view the plan called out - see `docs/testing.md` §7 for
  why blast radius matters more than diff lines here). Reviewing the screenshots is also
  where a missing affordance gets caught: check the screen actually exposes, visibly, every
  capability `docs/design.md` claims for it - a gesture-only action with no visible control
  is a discoverability bug even when it works.
- **The gesture pass** (pinch-zoom, pan, tap, pull-to-refresh - `docs/testing.md` §4's
  table) and **anything radio-dependent** (scanning, RSSI, ping, real host data,
  `docs/testing.md` §5's corner-case checklist) both need a real device. **Ask which
  physical device to use before touching one**, unless the request already named it -
  `adb devices -l` lists what's attached, but a listed device can already be in use by
  another agent or the user themselves, and wireless-ADB endpoints drift between sessions.
  Never assume a remembered address is still free.
- **State survival** is two separate checks, not one: rotation (an emulator's programmatic
  rotate can recreate the Activity differently than a real device's sensor-driven rotation
  does, so this needs one real rotate-and-back on physical hardware) and process death (a
  different guarantee - see `docs/testing.md` §7 for how to check it and why
  `stateIn(WhileSubscribed)` alone doesn't cover it).

### 7. Bug-fix cycles

Loop back to steps 3-6. `verify.sh` and `ui-matrix.sh sweep` are both idempotent, so re-run
them after each fix rather than re-deriving results by hand.

### 8. Documentation

Before opening a PR, walk `docs/README.md`'s table and update whatever the change actually
touches - do not skip this because the change "is just code":

| If the change... | Update |
|---|---|
| Adds/changes a subsystem, probe, or pipeline stage | `docs/design.md` |
| Makes or reveals a project-level decision, or hits a new Android platform constraint | New file in `docs/adr/` (copy `template.md`) |
| Finishes a backlog item | Close the GitHub issue (or mark the `docs/ideas.md` entry `Implemented`, noting the PR) |
| Changes install/sideload steps | `docs/installing.md` |
| Changes what a crash report or debug bundle captures | `docs/troubleshooting.md` |
| Changes the release process | `docs/releasing.md` |
| Changes a user-visible capability | Root `README.md` |
| Changes the contributor workflow itself | `CONTRIBUTING.md` |

A change with no matching row in that table genuinely needs no documentation update - but
that should be a conclusion, not a default.

### 9. Pull request

```bash
./scripts/pr-preflight.sh
```

Confirms the branch is rebased onto `origin/main` (required for the linear-history rule in
`CONTRIBUTING.md`), scans every commit message in the branch for the writing-convention
violations below, and prints which source paths changed without a matching docs change as a
final nudge for step 8. It does not write the PR description - that still takes judgment -
only `gh pr create` with a summary and test plan, same as any other PR.

## Writing conventions

Enforced by `scripts/check-banned-text.sh` (and the `commit-msg` hook, once installed) for
commit messages; apply them by hand to PR descriptions, comments, and docs prose too, since
those aren't hook-checked:

- No em-dash or en-dash, anywhere - commit messages, PR descriptions, code comments, string
  literals, documentation. Use a comma, colon, period, or a plain hyphen instead.
- No `Co-Authored-By` trailer.
- No AI-attribution footer ("Generated with Claude Code" or similar) in commits or PR
  descriptions.
- Commit messages are concise and explain *why*, not *what* - the diff already shows what
  changed.
