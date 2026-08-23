# Contributing

## Setup

Once per clone, install the git hooks that enforce the checks below automatically:

```bash
./scripts/install-git-hooks.sh
```

This installs a `commit-msg` hook (writing conventions, see below) and a `pre-push` hook
(the full build gate). Both also run as plain scripts if you'd rather invoke them by hand
or don't want them wired into git.

## Workflow

For a full feature - not a one-line fix - see
[`docs/feature-development.md`](docs/feature-development.md) for the complete process
(requirements through PR, including UI validation across window size classes, fold
posture, and a real device). It's also available as the `/feature-dev` skill in Claude
Code. The short version for everything else:

1. Branch off `main`:
   ```bash
   git checkout -b your-branch-name main
   ```
2. Make your change. Before pushing, run the same checks CI runs:
   ```bash
   ./scripts/verify.sh
   ```
3. Keep your branch current by rebasing onto `main` rather than merging it in:
   ```bash
   git fetch origin
   git rebase origin/main
   ```
4. Open a pull request against `main`. `./scripts/pr-preflight.sh` checks the branch is
   rebased and that no commit message violates the writing conventions below.

## Writing conventions

Applies to commit messages, PR titles/descriptions, code comments, string literals, and
documentation - everywhere, not just where the `commit-msg` hook checks:

- No em-dash or en-dash. Use a comma, colon, period, or a plain hyphen instead.
- No `Co-Authored-By` trailer.
- No AI-attribution footer ("Generated with Claude Code" or similar).
- Commit messages are concise and explain *why*, not *what* - the diff already shows what
  changed.

## What's required before merging

`main` is a protected branch:

- The `build` check (ktlint, detekt, unit tests, `assembleDebug` - see
  [`.github/workflows/ci.yml`](.github/workflows/ci.yml)) must pass on a
  branch that's up to date with `main`.
- At least one approving review.
- No merge commits - `main`'s history is linear, so PRs are merged via
  squash or rebase (both enabled; merge commits are disabled at the repo
  level). Keep your branch rebased on `main` rather than merging `main`
  into it, so the PR merges cleanly.
- Force-pushes and deletions are blocked on `main` itself; your feature
  branch is auto-deleted after merge.

Module boundaries and other structural conventions are enforced at build
time (see `build-logic/convention/`), not by hand-review, so the CI check
above is the actual gate.

## Releases

Contributions land on `main`; cutting an actual release (version bump, tag,
signed build) is a separate, maintainer-only process - see
[docs/releasing.md](docs/releasing.md).
