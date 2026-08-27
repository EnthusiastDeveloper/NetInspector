# Testing strategy and validation

The single source of truth for how this app is tested, functional and UI alike: what goes
in a JVM unit test versus an instrumented test versus a manual pass, the gesture and
orientation matrix for interactive controls, the corner cases each capability needs hit,
and the manual device matrix. `docs/design.md` §12 and `docs/feature-development.md` step 6
both point here instead of duplicating this content.

## 1. JVM unit tests

`:core:model`, `:core:common`, `:core:designsystem`, parsers:

- Frequency ↔ channel conversion across all three bands and both special cases.
- Channel span computation for every width constant including 80+80 and 320 MHz.
- Overlap and interference scoring, including the linear-power conversion.
- Security type set → display label, covering WPA2/WPA3 transition and OWE.
- Subnet math: prefix enumeration, network/broadcast, /31 and /32 edge cases.
- **Golden-file parser tests** for ping and traceroute output from both toybox and
  iputils, and for SSDP and NetBIOS responses. These are the highest-value tests in the
  project - the parsers are where silent inaccuracy hides.
- ICMP checksum and packet construction against known-good byte vectors.
- **A ViewModel action handler that gates a UI-visible state flag** (a refresh spinner, a
  disabled button, a countdown): every outcome branch the underlying call can return needs
  its own test, not just the happy path. `WifiViewModel.onRefresh` shipped a bug where the
  spinner flickered without animating specifically on the throttled/failed branches, and
  those branches had never been tested at all (`WifiViewModelTest`).
- **The governor or gate a bounded resource sits behind** (a throttle, a quota, a rate
  limiter) needs direct tests of its own, not just indirect coverage through whatever calls
  it. `ScanGovernor` is design §6.1's throttle and had none - `ScanGovernorTest` now covers
  the quota arithmetic, the reserved-token carve-out for user-initiated refreshes, and the
  specific property that made the above `onRefresh` bug possible: a throttled
  `requestScan` returns the moment it decides to throttle, never after waiting out the
  retry window.
- **Gesture math extracted to a plain value type** (a viewport, a hit-test): if a
  `pointerInput` handler's zoom/pan/tap arithmetic is complex enough to have its own
  clamping or geometry, extract it the way `AxisMapper`/`AxisViewport` are extracted for
  the channel occupancy graph, and unit-test the extraction directly rather than only
  through the composable. See §4 below for where this project currently does and doesn't
  follow its own pattern.

## 2. Boundary and negative-path coverage

A feature that clamps, throttles, or bounds something needs the boundary itself under
test, not just typical mid-range values. Three recurring shapes in this codebase:

- **A clamped/bounded interactive parameter** - anything with a `MAX_`/`MIN_` constant,
  such as the channel graph's `MAX_AXIS_ZOOM` or the network map's `MIN_SCALE`/`MAX_SCALE`.
  Test at the bound, not only in the middle of the range, and test whatever *consumes* the
  bounded value downstream, not only the function that does the clamping -
  `AxisViewport`'s zoom-ceiling test already covered the clamp itself, but nothing tested
  that `AxisMapper.xPx` stays consumable by the drawing code once a curve sits outside a
  zoomed-in viewport, which is exactly where a crash came from (`AxisMapperTest`,
  `ChannelOccupancyGraphRenderTest`).
- **A throttle, quota, or cooldown gate.** Test the denied path with the same care as the
  granted one, and confirm what the *caller* can assume about it - in particular, whether a
  denial can return before any real time has passed. A caller that assumes a gate always
  takes time to say no will misbehave the moment it doesn't (`ScanGovernorTest`).
- **An async action with a minimum-duration UI affordance** (a spinner sized to "how long
  this usually takes," not to the actual result). Test every outcome the underlying call can
  return, since the fast, unhappy outcomes are exactly where a hardcoded duration assumption
  breaks (`WifiViewModelTest`).

## 3. Instrumented tests

`ACCESS_FINE_LOCATION` permission state machine (granted, denied, permanently denied),
Room migrations, vendor lookup correctness and performance - including longest-prefix
precedence, where a 36-bit entry must win over a 24-bit entry covering the same address -
`NetworkCallback` lifecycle. **Compose `Canvas`/`DrawScope` rendering logic** belongs here
too, not only in the manual screenshot sweep below: a pure-math unit test on the values fed
into a draw call (an `AxisMapper`, a viewport transform) cannot prove the draw call itself
survives those values, since `DrawScope` extension functions need a real composition to
run. `ChannelOccupancyGraphRenderTest` renders the graph directly rather than only
screenshotting it, specifically so a crash - not just a layout mistake - gets caught by a
test rather than by a user's phone.

## 4. UI interaction and gesture coverage

The full inventory of gesture-capable controls in the app, confirmed against source (not
assumed): everything else in the UI is standard tap/scroll/text-input, with no
swipe-to-dismiss, sliders, or drag-reorder anywhere in the codebase.

| Control | File | Gestures | Coverage today |
|---|---|---|---|
| Channel occupancy graph | `core/designsystem/.../chart/ChannelOccupancyGraph.kt` | Pinch-zoom + pan (frequency axis), tap-to-highlight a curve | Zoom/pan clamp is extracted and tested (`AxisViewport` / `AxisViewportTest`). Tap-to-highlight (`hitTestCurve`, `ChannelOccupancyGraphDrawing.kt`) is a pure function with **zero test coverage**. |
| Network map graph | `core/designsystem/.../graph/NetworkMapGraph.kt` | Pinch-zoom + pan, tap-to-select a node | Tap-to-select is extracted and tested (`hitTestNode` / `NetworkMapHitTestTest`). Zoom/pan clamp (`MIN_SCALE`/`MAX_SCALE`, the `maxOffsetX`/`maxOffsetY` pan bound) is inline in the composable's `detectTransformGestures` lambda, **not extracted, zero test coverage**. |
| Wi-Fi screen refresh | `app/.../ui/screens/wifi/WifiScreen.kt` | Pull-to-refresh - two separate `PullToRefreshBox` instances, one per adaptive layout (compact single-pane, expanded two-pane) | The ViewModel branch it drives is covered (`WifiViewModelTest`'s `onRefresh` outcomes); the gesture itself, and specifically the second `PullToRefreshBox` instance, is manual-only. |
| Every list screen | `DashboardScreen`, `DevicesScreen`, `WifiScreen`, the tool screens, both history screens | Scroll (`LazyColumn`/`LazyRow`) | Manual only - this is Compose's own scroll handling, not app logic, so no dedicated test is expected. |
| Tool forms | `app/.../ui/screens/tools/*` (currently 13, grouped Diagnostics/Utilities/History - check `ToolsScreen.kt`/`Tool.kt` for the current count rather than trusting a number here) | Text input, IME actions, button press | Manual only. |

Both graphs deliberately have no `onDoubleTap` (documented in their own code comments: a
registered `onDoubleTap` would delay every single tap by the double-tap timeout, which is
unacceptable for an interaction meant to feel immediate). That is a considered trade-off,
not a bug - but it means a rapid double-tap needs to be manually confirmed to behave as two
independent single taps, since nothing exercises that path today.

**Specifically check these** during a manual pass - combinations a normal tap-through
tends to skip:

- Rapid double-tap on either graph (see above - confirm two independent taps, not one
  swallowed or duplicated event).
- Fingers still down mid-pinch or mid-pan when the device rotates.
- Pinch-zoom or pan immediately followed by rotation, before lifting fingers.
- Pull-to-refresh triggered, then the device rotated before the refresh completes - this
  crosses from one `PullToRefreshBox` instance to the other mid-gesture.
- A tap on a list row that reorders or grows between tap-down and tap-up (the LAN host list
  during progressive population, the AP list during a live scan).
- Back gesture or back button during an in-flight scan or diagnostic run.

## 5. Corner-case checklist by capability

*When* in a capability's interaction lifecycle to exercise it - not *what* the correct
behavior is, which is already documented where cited. Every capability, at minimum:
empty/zero results, exactly one result, a large/max-realistic result set, permission
revoked mid-run, radio or airplane-mode toggled mid-run, network changed mid-run, the app
backgrounded and then killed by the system mid-run (process death - see §7, this is a
different guarantee than surviving a rotation), and the device rotated mid-run.

| Capability | Screen(s) | Also check | Correct-behavior reference |
|---|---|---|---|
| Dashboard | Home | Every card's own empty state (no Wi-Fi, no devices, no active diagnostics) rendered together on first launch | design §11 |
| Wi-Fi scan + channel graph | Wi-Fi | Throttled/denied refresh; scan mid-pinch-zoom | design §6.1 (throttle governor), §7 |
| LAN discovery + network map, hygiene score | Devices | Discovery mid-progressive-population; host vanishes mid-scan | design §8.2 (three-stage pipeline), §8.3 (evidence merging) |
| Background scanning | (none - runs headless) | Doze interruption, a scan window straddling a Doze entry | design §8.5 |
| Ping | Tools → Ping | ICMP unavailable, falls back to TCP RTT | design §9.1, §9.2, §11.3 (degraded-mode labeling) |
| Traceroute | Tools → Traceroute | A hop that never responds (timeout row), max hop count | design §9.3 |
| DNS | Tools → DNS | NXDOMAIN (clean "no records"), a genuinely malformed wire response from a real-world resolver (distinct from NXDOMAIN - confirmed reproducible against `.invalid` TLDs on at least one ISP resolver) | design §9.4 |
| Port scanner | Tools → Port Scanner | Zero open ports, every port open, scan cancelled mid-run | design §9.5, §11.4 (first-run acknowledgement gate) |
| LAN throughput test | Tools → LAN throughput test | Target with no responder (degrades to a low-throughput/high-loss result rather than hanging or erroring - confirm this reads as a result, not a false "it works"); host picked from the pre-populated dropdown vs. typed manually | design §9.6 if present, otherwise flag as undocumented (this tool landed after the design doc's tool list was last updated) |
| Wake-on-LAN | Tools → WoL | Malformed MAC input, send failure | design §9.6 |
| Subnet calculator | Tools → Subnet Calculator | /31 and /32 edge cases, invalid CIDR input | design §9.6 |
| HTTP inspector | Tools → HTTP Inspector | Non-HTTP response, connection refused, redirect chain, a self-signed/untrusted TLS certificate (confirmed reproducible against a LAN device's own HTTPS port) | design §9.6 |
| Signal meter | Tools → Signal Meter | RSSI stream interruption (Wi-Fi disconnects mid-read) | design §5.1 |
| Permissions | Any radio-dependent screen | Granted → denied → permanently-denied transitions, mid-scan revocation | design §4.1 |
| First-run acknowledgement | Devices (before first LAN sweep) | Dismiss without acknowledging, rotate while the dialog is open | design §11.4 |
| Scan history / export | Tools → Wi-Fi history, Diagnostic history | Empty history, export with zero/partial permissions | design §10 |
| Wi-Fi changes diff | Tools → Wi-Fi changes | No scans yet to compare, only one scan ever recorded | design §10 |
| Settings / appearance | Settings | Every theme option (System/Light/Dark/AMOLED) rendered on at least one data-heavy screen, not just Settings itself | design §11 |

## 6. Manual device matrix

Physical devices only, since the emulator cannot scan Wi-Fi at all (C-13). The primary
targets are the two owned Android 15 devices; the third row is optional but is the only
way to catch OEM divergence (C-14) before it bites. **The primary and secondary rows now
also carry the §4 gesture pass and the §5 corner-case checklist**, not just reference
behavior - both graphs' gesture targets (device count, AP count) depend on live
radio-driven data the layout-only emulator can't produce.

| Device class | API | Specifically tests |
|---|---|---|
| Primary device | 35 | Reference behaviour, FGS typing, 6 GHz if available, both spikes, full §4/§5 pass |
| Secondary device | 35 | Cross-checks the spikes; catches per-device kernel differences; full §4/§5 pass |
| Resizable emulator | 33-35 | **Layout only**: window size classes, book and tabletop posture. No radio, so no scanning, sweeping, gestures against live data, or diagnostics |
| Third-party OEM *(optional)* | 33-34 | The API 33 floor itself, vendor Wi-Fi stack, aggressive background limits |
| Physical foldable *(one session)* | any | Real hinge dimensions and aspect ratio against the emulator-developed layouts |

The two owned devices are the deployment targets, so they define "working." The resizable
emulator is the exception to C-13: layout needs no Wi-Fi radio, so it is a fully valid test
surface for everything in design §11.2 and nothing else. The optional third-party device
exists purely to validate that the API 33 floor is real rather than theoretical - if the
app is never installed below 35, consider raising `minSdk` and deleting the last version
branch (design §6.2).

## 7. UI validation process

How to actually run a validation pass, split by what needs a radio versus what doesn't
(same split as §6, because the emulator has no Wi-Fi radio - C-13).

**Layout** (window size classes, fold posture, rotation) needs no radio and is fully
scriptable:

```bash
# Boot a layout AVD (NetInspector_Resizable or NetInspector_Fold76) and get its serial
serial=$(./scripts/ui-matrix.sh boot NetInspector_Resizable)
./scripts/ui-matrix.sh install "$serial" app/build/outputs/apk/debug/app-debug.apk
# Navigate to the screen under test, then, for that one screen:
./scripts/ui-matrix.sh sweep "$serial" /tmp/ui-matrix wifi-screen
./scripts/ui-matrix.sh kill "$serial"
```

`sweep` captures one screenshot per rotation (0/90/180/270), per width size class
(compact/medium/expanded, computed from the device's actual density), and - on a
foldable-capable target - per posture (closed/half-opened/opened), then restores every
override. Run it once per screen **family** the change touches - every screen reachable
from the one the diff edits, not only the specific sub-view the plan called out - review
the resulting screenshots, repeat for the next screen. This replaces tapping through each
combination live over adb, but it only takes static screenshots - it never taps, drags, or
pinches anything, so it cannot exercise §4's gesture matrix on its own.

Sweep by blast radius, not by diff lines: three bugs shipped in a Wi-Fi screen change that
never touched the code they lived in, because that code sat in the same screen family but
outside the specific sub-view the plan named, so it was never navigated to during
validation. A change to any file under a screen's package puts the whole screen back in
scope for this step.

Reviewing the screenshots is also where a missing affordance gets caught, not just a
layout mistake - check the screen actually exposes, visibly, every capability
`docs/design.md` claims for it. A gesture-only action (pull-to-refresh, a hidden swipe)
with no visible control is a discoverability bug even when it works and even when nothing
overflows.

**Gesture pass** - for any screen family whose diff touches a file listed in §4's table,
manually exercise every gesture that control supports on the real-device pass below, not
just tap navigation to reach the screen. Work through §4's "specifically check these" list
for that control, not just a single tap/pinch/pan to confirm the control responds at all.

**Anything radio-dependent** (scanning, RSSI, ping, real host data) needs a real device.
**Ask which physical device to use before touching one**, unless the request already named
it - `adb devices -l` lists what's attached, but a listed device can already be in use by
another agent or the user themselves, and wireless-ADB endpoints drift between sessions.
Never assume a remembered address is still free. This is also where §5's corner-case
checklist gets exercised, since most of those cases (permission revocation, radio toggles,
network changes) need real radio state to produce.

**State survival** - two separate checks, not one:

- **Rotation.** State lives in ViewModels behind `stateIn(WhileSubscribed)` (design §11.2),
  so a configuration change should cost nothing. An emulator's programmatic rotate can
  recreate the Activity differently than a real device's sensor-driven rotation does, so
  this needs one real rotate-and-back on physical hardware, not just the emulator's
  scripted sweep above.
- **Process death.** A different guarantee from rotation, and not automatically covered by
  `stateIn(WhileSubscribed)` - that survives recomposition, not the process being reclaimed
  by the system while backgrounded. Confirm state that matters (an in-progress scan's
  partial results, scroll position, an open dialog) survives a real process kill: enable
  "Don't keep activities" in Developer Options, or background the app long enough for the
  system to reclaim it, then return. Treat this as **confirming** the behavior, not
  asserting it already works - it may be a genuine gap, since it depends on `SavedStateHandle`
  usage that hasn't been specifically audited.

**Accessibility and presentation** - cheap to check on any device pass, not a full
localization effort: system font scale at a large step, and dark/light theme. RTL layout
mirroring is explicitly out of scope - the app doesn't target RTL locales as a shipped
feature, so this isn't a gap being silently skipped, just a genuinely unneeded axis today.

## 8. Bug-fix cycles

Loop back through implementation, then re-run whatever combination of §§1-7 the fix
touches. `scripts/verify.sh` and `scripts/ui-matrix.sh sweep` are both idempotent, so
re-run them after each fix rather than re-deriving results by hand.
