# ADR-0002: `minSdk` 33, `targetSdk` 35

Status: Accepted

## Context

The app's core value depends on APIs that only exist, or only behave usefully, from
specific Android versions onward: `NetworkCapabilities.getTransportInfo()` with
`FLAG_INCLUDE_LOCATION_INFO`, `ScanResult.getSecurityTypes()`, and modern foreground
service typing all have real version floors. Supporting older versions would mean
maintaining parallel code paths for a strictly worse experience.

## Decision

`minSdk` 33 (Android 13), `targetSdk` 35 (Android 15).

## Consequences

- The whole permission matrix is written against post-33 behavior - no `targetSdk < 29`
  coarse-location fallback paths to maintain (see [C-03](../adr/c-03-scan-results-require-location.md),
  [C-04](../adr/c-04-connection-info-deprecated-redacted.md)).
- `ScanResult.getSecurityTypes()` (API 33+) is available unconditionally, eliminating the
  capabilities-string parser an older floor would have required - and the two bugs that
  parser tends to invite (WPA2/WPA3 transition networks mislabelled as WPA2, OWE networks
  mislabelled as Open). See [C-15](c-15-channel-width-320mhz-api34.md).
- `targetSdk` 35 requires foreground service typing (Android 14+, [C-09](c-09-foreground-service-types.md))
  and the runtime `POST_NOTIFICATIONS` permission - both must be handled correctly for the
  monitoring service to work at all, not treated as optional hardening.
- The tradeoff: no install base below Android 13. Given this is a sideloaded,
  power-user-oriented tool rather than a mass-market app, that's an acceptable floor.
