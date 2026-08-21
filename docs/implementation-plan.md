# NetInspector - Implementation Plan

Nine phases, ordered so that each one ships something usable and each one de-risks the
next. Estimates are developer-days for one experienced Android developer, and include
tests. Total ≈ 39 days.

The ordering follows one rule: **build the things with no permission requirements first.**
Phases 1 and 2 need nothing beyond `INTERNET` and `ACCESS_NETWORK_STATE`, so the
permission machinery is not on the critical path for proving the architecture works.

---

## Phase 0 - Foundation

**Goal** An empty app that builds, with module boundaries and CI already enforced.
**Estimate** 3 days.

**Tasks**

- Gradle version catalog, convention plugins for the Android library / Compose / Hilt setups.
- Create all eight modules with the dependency rules from design §2.1 enforced by the
  convention plugin (a `:data` module importing another `:data` module fails the build).
- `minSdk 33`, `targetSdk 35`, `compileSdk 35`. Kotlin 2.x, K2, Compose compiler plugin.
- Hilt wiring, `@ApplicationScope` `CoroutineScope` with `SupervisorJob`.
- Room with `exportSchema = true`, schema directory committed.
- Base theme, typography, dark mode.
- **Adaptive shell**: `NavigationSuiteScaffold` with the four destinations, driven by
  `currentWindowAdaptiveInfo()` - bottom bar on compact width, navigation rail on medium
  and expanded. Verify rotation preserves state with no `configChanges` manifest entry.
- **Fold posture plumbing**: `WindowInfoTracker.windowLayoutInfo()` collected as a `Flow`,
  exposed as a `DevicePosture` domain type (`Normal`, `Book`, `Tabletop`) with hinge bounds
  translated from window to composable coordinates via `onGloballyPositioned`. Guard on
  `isSeparating` so a non-separating `HALF_OPENED` falls through to the size-class layout.
- Configure a resizable emulator AVD with fold-state controls as the layout test surface.
- Timber (or equivalent) logging with a release tree that drops verbose logs.
- **Release keystore**: generate once with long validity, store outside the repository,
  back up durably. Wire release signing config from a local properties file that is
  git-ignored. Do not ship anything signed with the debug keystore - its certificate
  expires after a year and the app then becomes un-updatable in place.
- `versionCode` and `versionName` driven from a single source in the version catalog, so a
  release tag cannot disagree with the manifest.
- CI: assemble, ktlint/detekt, JVM unit tests. No instrumented tests in CI (see C-13).

**Acceptance** A release-signed APK installs, and a second build with an incremented
`versionCode` installs over it without a signature mismatch. App navigates between four
placeholder screens. Rotating to
landscape switches the bottom bar to a navigation rail and preserves the selected
destination. On the resizable emulator, folding into book and tabletop posture emits the
correct `DevicePosture` with hinge bounds inside the composable's own coordinate space.
Adding an illegal cross-module dependency fails the build. `./gradlew test` passes.

---

## Phase 1 - Connection dashboard

**Goal** The Connection tab fully working. Proves the reactive architecture end to end
with zero runtime permissions.
**Estimate** 3 days.

**Tasks**

- `ConnectivityDataSource`: `NetworkCallback` with `FLAG_INCLUDE_LOCATION_INFO`, exposing
  a cold `Flow<ConnectionSnapshot>` built from `onCapabilitiesChanged` and
  `onLinkPropertiesChanged`. Handle `onLost` and `onUnavailable`.
- Subnet math in `:core:common`: parse `LinkAddress` into network/broadcast/prefix, host
  enumeration, `/31` and `/32` edge cases.
- Gateway extraction from `LinkProperties.routes` via `isDefaultRoute` (C-12).
- `freqToChannel` / `bandOf` / `ChannelSpan` derivation, with full unit test coverage.
- Dashboard UI: SSID, BSSID, band/channel/width/standard, RSSI gauge, tx/rx link speeds,
  IPv4 with prefix, IPv6 list (display only), gateway, DNS servers, validated-internet and
  captive-portal badges, metered flag.
- Live RSSI stream driving the gauge (design §5.1).

**Acceptance** Dashboard updates within ~1 s of switching networks or toggling Wi-Fi.
SSID and BSSID render correctly (not redacted). Prefix length is read, never assumed.
Unit tests cover channel and subnet math including all edge cases.

**Risk, resolved during implementation** SSID and BSSID are redacted if
`FLAG_INCLUDE_LOCATION_INFO` is missing *or* `ACCESS_FINE_LOCATION` has not been granted
*or* system location mode is off - **not** `NEARBY_WIFI_DEVICES` as originally assumed
here; caught by testing on a physical device and traced to AOSP source (design C-04).
The dashboard requests `ACCESS_FINE_LOCATION` lazily, from the dashboard itself, with a
rationale explaining the OS-level restriction (design §4.1a) - every other field works
with no runtime permission at all, and this is the one place in the app that asks for
location, scoped to exactly these two fields.

---

## Phase 2 - ICMP engine and ping

**Goal** A validated ICMP engine, which everything in Phase 7 depends on.
**Estimate** 5 days, including the spike.

### Spike S-01 - unprivileged ICMP datagram sockets (1 day, do this first)

Write a throwaway activity that calls
`Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP)`, sends an echo request to the gateway and
to `8.8.8.8`, and reads the reply. Run it on **all three devices in the matrix.**

- **Pass** → proceed with tier 1 as the primary engine.
- **Fail on some devices** → implement tier 1 with runtime capability detection and tier 2
  (`/system/bin/ping` exec) as the automatic fallback. Budget +1.5 days.
- **Fail everywhere** → tier 2 becomes primary. Budget +2 days and revisit S-02 immediately,
  since traceroute then has no tier-1 path either.

Record the outcome in `docs/adr/c-07-no-raw-sockets.md`.

**Tasks**

- ICMP packet builder: 8-byte header, internet checksum, payload, sequence tracking.
  Unit-tested against known-good byte vectors.
- `IcmpEngine`: socket lifecycle, `SO_RCVTIMEO`, `IP_TTL`, `nanoTime` measurement,
  cancellation-safe (`Os.close` in `finally`, cooperative cancellation between probes).
- Tier 2 fallback: `/system/bin/ping` exec with tolerant, locale-independent parsers.
  Golden-file tests for both toybox and iputils output.
- Tier 3: TCP connect RTT, explicitly labelled in the result model.
- Ping tool UI: target input, count/interval/size/timeout/TTL/DF options, streaming
  per-probe log, live RTT sparkline, summary with min/avg/max/**median**/stddev/jitter/loss.

**Acceptance** Ping to the gateway and to a public host works on both deployment devices.
Cancelling mid-run releases the socket immediately (verify no FD leak over 100 runs).
RTTs are within ~10% of `ping` run in a terminal on the same device. Median and jitter are
computed correctly against a fixture dataset.

---

## Phase 3 - Wi-Fi scanning

**Goal** The AP list, with the throttle governor behaving well.
**Estimate** 3.5 days (was 4 at `minSdk 31`; the version-branched permission flow and the
`capabilities`-string parser are both gone, offset by the list-detail pane).

**Tasks**

- Permission layer: `ACCESS_FINE_LOCATION` request (C-03, corrected from the original
  `NEARBY_WIFI_DEVICES` assumption - device testing showed `getScanResults()`/
  `startScan()` are gated by location, identically to C-04's dashboard fields), rationale
  sheet, permanent-denial handling with a settings deep link, plus the location-services
  check the original plan assumed away.
- `ScanGovernor`: token-bucket budget, `SCAN_RESULTS_AVAILABLE_ACTION` receiver registered
  with `RECEIVER_NOT_EXPORTED` (C-11), lifecycle-scoped registration,
  `isScanThrottleEnabled()` detection.
- `ScanResult` → `AccessPoint` mapping: channel span from `channelWidth` + `centerFreq0`/
  `centerFreq1` (raw-int check for 320 MHz per C-15), security via the unconditional
  `getSecurityTypes()`, standard, DFS and 6 GHz PSC flags.
- Vendor database: shipped scoped-down rather than the full Room-backed plan below -
  a ~2.8k-entry flat asset (`oui_vendors.tsv`, 24-bit prefixes only) filtered from a
  freshly-fetched Wireshark `manuf` dataset down to consumer/SMB Wi-Fi router, mesh and
  AP vendors, excluding general client-device silicon vendors. Locally-administered bit
  checked before lookup so randomised BSSIDs never match a bogus vendor. The full
  36→28→24-bit longest-prefix `.db` asset via `:data:persistence`, covering the entire
  registry, is deferred to whichever later phase actually stands up that module.
- AP list UI: sort by RSSI/SSID/channel, filter by band/security, group by SSID with
  expandable BSSIDs, per-row age, header showing data age and throttle countdown.
- AP list and detail built as a single `ListDetailPaneScaffold` destination, not two
  navigation destinations - two panes on expanded width, push navigation on compact.
- AP detail pane: all fields, RSSI history sparkline, lazy information-element parsing.

**Acceptance** List populates within 2 s of screen entry. Refresh is disabled with a
visible countdown when the budget is exhausted, and never silently no-ops. Passive updates
arrive without user action. Denying the permission leaves the rest of the app fully
functional. WPA2/WPA3 transition and OWE networks are labelled distinctly - OWE never
reads as "Open". Verified on both deployment devices, including at least one with 6 GHz.

---

## Phase 4 - Channel analysis

**Goal** The occupancy graph and the recommendation engine.
**Estimate** 5 days.

**Tasks**

- Overlap and interference scoring in `:core:model`: linear-power conversion, overlap
  fraction, the 1.5× partial-overlap multiplier, 2.4 GHz 1/6/11 preference. Pure functions,
  heavily unit-tested.
- Compose `Canvas` channel graph: frequency-based X axis, RSSI Y axis, per-AP parabolas,
  80+80 handling, band tabs, tap-to-highlight, label collision avoidance. **Tick spacing
  and label density derive from measured width**, not fixed values - landscape roughly
  triples the horizontal space on the axis that carries the information, and this is the
  one screen where adaptivity needs real design rather than a scaffold swap.
- **Tabletop layout**: graph in the upper display, band tabs / legend / recommendation card
  in the lower. Nothing drawn across the hinge. This is the flagship posture screen.
- Recommendation card: ranked channels per band with score, contributing AP count, plain-
  language rationale, DFS and non-PSC flags.
- Multi-sample requirement: refuse to recommend from a single scan; show how many samples
  the recommendation is based on.

**Acceptance** Graph renders correctly with 40+ visible APs without frame drops. A 160 MHz
AP visually spans eight 20 MHz channels. Recommendation output is reproducible for a fixed
fixture input and matches hand-computed expected scores.

---

## Phase 5 - LAN discovery

**Goal** The Devices tab: the three-stage pipeline with progressive results.
**Estimate** 6 days. The largest and riskiest phase.

**Tasks**

- Pipeline scaffolding: structured-concurrency scope, bounded dispatchers (64 sweep / 8
  exec), `WifiLock` in `WIFI_MODE_FULL_LOW_LATENCY` acquired and released around active
  sweeps, full cancellation on lifecycle stop.
- Stage A probes, all under a single `MulticastLock` acquired/released in `finally` (C-06):
  - mDNS via `NsdManager`, including the `_services._dns-sd._udp` meta-query.
  - SSDP M-SEARCH ×3, plus `LOCATION` XML fetch for `friendlyName`/`manufacturer`/`modelName`.
  - NetBIOS node-status query to the broadcast address, UDP 137.
- Stage B: three-pass sweep (ICMP → ICMP retry → TCP connect fallback, with pass 3
  probing all ports for a host concurrently), prefix-derived ranges, confirmation gate for
  prefixes shorter than /22.
- Evidence model and merge rules (design §8.3): confidence assignment, provenance-tagged
  hostname map, conflict preservation, `STALE` handling across consecutive sweeps.
- Devices UI: progressive population, per-host confidence indicator, scan progress with
  addresses-probed count, cancel button that actually cancels.
- First-run acknowledgement dialog (design §11.4).

**Acceptance** On a reference network with a known device inventory, the sweep finds
**every** host that responds to `ping` or `nmap -sT` from a laptop on the same network.
Hosts that only announce via mDNS appear as `ANNOUNCED`, not silently dropped or falsely
promoted. Cancelling stops all probes within 1 s. Backgrounding the app terminates the
sweep. A /24 sweep completes in under 25 s (design §8.2 budgets ~21 s; if it exceeds 25 s,
tune pass timeouts before dropping pass 2). No `MulticastLock` or `WifiLock` survives the
sweep - verify with `dumpsys wifi`.

**Risks**

- mDNS on `NsdManager` is historically flaky across OEMs. If it proves unreliable on the
  matrix, budget +2 days to implement a direct mDNS query/parse over UDP 5353 rather than
  fighting the framework wrapper.
- Sweep timing on congested Wi-Fi may push the /24 target; tune per-pass timeouts before
  reducing accuracy by dropping pass 2.

---

## Phase 6 - Device identification

**Goal** Turn IP addresses into recognisable devices, without MAC addresses.
**Estimate** 4.5 days.

**Tasks**

- Stage C enrichment: reverse DNS via `DnsResolver`, extended ~30-port probe, HTTP
  `Server`/`<title>` and SSH banner grabs.
- ICMP reply TTL fingerprint → OS class (64 / 128 / 255), recorded as a `DeviceHint` with
  its basis string and `POSSIBLE` certainty.
- Port-signature heuristics: 62078 → iOS, 5555 → ADB, 9100 → printer, 8009 → Chromecast,
  445+139 → Windows/Samba, 32400 → Plex, and so on. Each mapping carries its own basis
  string; none is asserted without one.
- Metadata join from Stage A by IP, with the hostname precedence rules.
- Host list and detail as a single `ListDetailPaneScaffold` destination: all evidence with
  sources and timestamps, all hostname variants, open ports with banners, discovered
  services, deep links into ping and port scan pre-filled with the address.
- Explicit UI treatment for the MAC gap (C-01): no empty row, and a short explanation
  available on tap rather than a mysterious absence.

**Acceptance** On the reference network, at least 70% of hosts receive a `DeviceHint`, and
**every** hint displays a basis when tapped. No hint is shown without a supporting evidence
entry. Reverse DNS never blocks list rendering.

---

## Phase 7 - Remaining tools

**Goal** The Tools tab complete.
**Estimate** 5 days.

> **Blocking gate - port preset review.** Before implementing the port scanner, revisit
> the default ~30-port service set. The Phase 5 fallback set (80, 443, 22, 445, 139, 8009,
> 62078, 5555) was chosen for *discovery*, not for scanning, and the scanner preset should
> reflect what actually runs on the reference network. Deferred from planning by explicit
> decision; do not implement the default set without this review.

### Spike S-02 - ICMP error queue for traceroute (0.5 day, do this first)

Extend the S-01 harness: enable `IP_RECVERR`, set `IP_TTL` to 1, send an echo to a public
host, and attempt `Os.recvfrom(..., MSG_ERRQUEUE, ...)`. Confirm the source address field
yields the first-hop router on both deployment devices.

- **Pass** → implement traceroute on tier 1.
- **Fail** → fall back to the `ping -t <ttl>` TTL walk (C-08). Budget +1 day for the dual
  toybox/iputils parser and its golden-file tests.

**Tasks**

- Traceroute: 3 probes per hop, max 30 hops, stop on target or 5 consecutive dead hops,
  async per-hop reverse DNS, per-hop min/avg/max, `*` for non-responders, partial results
  preserved on failure.
- DNS tool: `DnsResolver` for system-resolver queries; a self-contained DNS wire-format
  encoder/decoder for querying a user-specified server. A, AAAA, CNAME, MX, NS, TXT, SOA,
  PTR, SRV. Reverse lookup via `in-addr.arpa`.
- Port scanner: connect scan with configurable range/preset, concurrency and timeout, a
  non-configurable rate floor, banner grab, and a UI note that this is a connect scan not
  a SYN scan.
- Wake-on-LAN: magic packet to UDP 9 broadcast, manual MAC entry, saved targets in Room.
- WHOIS: TCP 43 with referral chasing capped at 3 hops.
- HTTP header inspector: redirects disabled, full header list, redirect chain, TLS
  certificate subject/issuer/validity.
- Subnet calculator: offline CIDR ↔ mask, network/broadcast/usable range, host count, VLSM
  splitting.
- Signal meter: live RSSI from the Phase 1 stream, 60 s rolling chart, dBm and quality,
  link speeds. Consumes no scan budget.
- Apply a centred `widthIn(max = 600.dp)` constraint to all nine tool forms so inputs do
  not stretch across a tablet or landscape display.
- **Tabletop layouts** for the three live-output tools: ping, traceroute and signal meter
  put streaming output in the upper display and controls in the lower.

**Acceptance** Traceroute to a public host reaches the target and matches a desktop
`traceroute` hop-for-hop on the same network, allowing for load-balanced paths. DNS
queries against a user-specified server return correct records for all supported types.
WOL wakes a configured machine. All tools stream partial results and cancel cleanly.

---

## Phase 8 - Monitoring, history, export, polish

**Goal** Everything that makes it a tool rather than a demo.
**Estimate** 4 days.

**Tasks**

- Foreground service for continuous monitoring: `connectedDevice` type, runtime
  `POST_NOTIFICATIONS`, persistent notification with live RSSI, explicit user start/stop
  (C-09, C-10).
- Room persistence of scan sessions, observations, known APs, diagnostic runs; retention
  policies with a periodic `WorkManager` cleanup.
- History screens: AP-seen-over-time, RSSI history per BSSID, past diagnostic runs.
- Export to CSV and JSON via `ACTION_CREATE_DOCUMENT`.
- Settings: scan cadence, sweep concurrency and timeouts, port presets, retention, theme,
  units.
- Accessibility pass: content descriptions on all charts and gauges, TalkBack navigation,
  minimum touch targets, dynamic type.
- Adaptive layout verification pass: every screen checked at compact, medium and expanded
  width, plus book and tabletop posture on the resizable emulator. No new adaptive work
  should be needed here - this is a regression check on what Phases 0, 3, 4, 6 and 7
  already built.
- Full matrix regression across all three devices.

**Acceptance** Monitoring survives 30 minutes with the screen off and produces a
timestamped series with visible gaps rather than interpolated values. Export opens
correctly in a spreadsheet. Room migration from a seeded v1 database succeeds.

---

## Spike summary

| ID | Question | Phase | Cost | Fallback if it fails |
|---|---|---|---|---|
| S-01 | Do unprivileged ICMP datagram sockets work via `android.system.Os`? | 2 | 1 d | `/system/bin/ping` exec, +1.5-2 d |
| S-02 | Does the socket error queue surface TTL-exceeded hop addresses? | 7 | 0.5 d | `ping -t` TTL walk, +1 d |

Both spikes must run on **both** deployment devices, not just the primary development
device - kernel behaviour around ICMP sockets and the socket error queue can differ
between devices on the same Android version.

---

## Estimate summary

| Phase | Days | Size class | Fold posture |
|---|---|---|---|
| 0 - Foundation | 3 | +0.5 nav suite | +0.5 posture plumbing |
| 1 - Connection dashboard | 3 | - | - |
| 2 - ICMP engine and ping (incl. S-01) | 5 | - | - |
| 3 - Wi-Fi scanning | 3.5 | +0.5 AP list-detail | free via scaffold |
| 4 - Channel analysis | 5 | +0.5 responsive graph | +0.5 tabletop graph |
| 5 - LAN discovery | 6 | - | - |
| 6 - Device identification | 4.5 | +0.5 host list-detail | free via scaffold |
| 7 - Remaining tools (incl. S-02) | 5 | +0.5 form widths | +0.5 tabletop tools |
| 8 - Monitoring, history, polish | 4 | verification | verification |
| **Total** | **39** | **+2.5** | **+1.5** |

Adaptive layout is distributed across phases rather than being a phase of its own,
because retrofitting `ListDetailPaneScaffold` onto screens already built as separate
navigation destinations costs roughly 5-6 days instead of 2.5.

Book posture costs nothing beyond the Phase 0 plumbing: `calculatePaneScaffoldDirective()`
consumes the posture from `currentWindowAdaptiveInfo()` and places the pane gap over the
hinge automatically, so both list-detail screens are hinge-aware for free. The 1.5 days is
almost entirely tabletop posture, which has no framework equivalent.

Add ~4 days of contingency if either spike fails, and ~2 days if `NsdManager` needs
replacing with a hand-rolled mDNS implementation.

---

## Definition of done, per phase

Every phase is complete only when all of the following hold:

1. Feature works on both deployment devices.
2. New pure logic has JVM unit tests; new parsers have golden-file tests.
3. No lock (`MulticastLock`, `WifiLock`, wake lock) outlives its scope - verified with
   `dumpsys`.
4. All long-running work is cancellable and is actually cancelled on lifecycle stop.
5. Every degraded or unavailable state is visible in the UI with an honest label - no
   silent empty lists, no plausible-looking defaults substituted for missing data.
6. New platform limitations discovered during the phase get a new ADR under
   `docs/adr/` (next `C-XX` number).

---

## Resolved decisions

| # | Question | Resolution |
|---|---|---|
| 1 | App name and package ID | `NetInspector`; package `dev.enthusiastdev.netinspector` |
| 2 | Sweep threshold for large subnets | Confirmed. Reference network is a 192.168.x.0/24, so the /22 gate never fires in normal use; retained for other networks |
| 4 | History retention | 30 days for scan sessions, 90 for diagnostic runs, both user-adjustable in settings |
| 5 | Adaptive layouts | Window size classes **and** fold posture from day one, distributed across phases (+4 days total). Book posture is free via the pane scaffold; tabletop is built deliberately - see design §11.2 |

## Still open

**Port preset contents** - deferred by decision to Phase 7, where it is a blocking gate
before the port scanner is implemented. See the callout at the head of that phase.

**Physical foldable verification session** - book and tabletop layouts are developed and
verified on the resizable emulator, which is a valid surface because layout needs no radio
(the exception to C-13). Real hinge dimensions and aspect ratios vary by manufacturer, so
book one session on the actual device before it is given away.
