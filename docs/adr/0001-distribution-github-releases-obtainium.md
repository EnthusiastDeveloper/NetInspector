# ADR-0001: Distribution via GitHub Releases + Obtainium, no Play Store

Status: Accepted

## Context

NetInspector needs a distribution channel. A Play Store listing brings policy review,
an account, and ongoing compliance surface for an app whose entire value proposition is
"no accounts, no telemetry" - that tension pushed toward a lighter channel from the start.

## Decision

Ship as tagged GitHub Releases with an attached APK. [Obtainium](https://github.com/ImranR98/Obtainium)
is the supported update mechanism, watching the releases feed for new tags and offering
one-tap install. No store listing, no policy compliance process, no license obligation to
a store operator, no public support surface beyond the repo itself.

## Consequences

This is cheap as long as two things are handled correctly from the start, and painful to
fix later:

- **A real release keystore, generated once and backed up outside the repo.** Android
  refuses to install an update signed with a different key than the installed version -
  the failure is a signature mismatch, and the only fix is uninstalling, which destroys
  the app's data. The debug keystore is the trap: its certificate is valid for one year,
  so an app shipped debug-signed becomes un-updatable roughly twelve months later.
- **Monotonically increasing `versionCode`**, driven from a single source of truth in the
  version catalog. Obtainium compares release tags, and Android refuses to install a lower
  `versionCode` over a higher one - a tagged release cannot be allowed to disagree with the
  manifest.

In exchange, there is no store review latency, no policy risk from the port-scanning/LAN
discovery features (which some store policies treat cautiously), and no requirement to
collect any account or analytics data to satisfy a store's developer console.
