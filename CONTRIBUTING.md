# Contributing

## Workflow

1. Branch off `main`:
   ```bash
   git checkout -b your-branch-name main
   ```
2. Make your change. Before pushing, run the same checks CI runs:
   ```bash
   ./gradlew ktlintCheck detekt test assembleDebug
   ```
3. Keep your branch current by rebasing onto `main` rather than merging it in:
   ```bash
   git fetch origin
   git rebase origin/main
   ```
4. Open a pull request against `main`.

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
