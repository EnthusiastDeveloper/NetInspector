# Improvement ideas

Brainstormed feature/UX ideas for NetInspector, ranked by ROI first, then by lowest
additional-requirement count as a tiebreaker (cheaper, more self-contained ideas rank
above pricier ones of similar value). "Requirements" lists what each idea needs beyond
what the app already has.

---

## 1. Network hygiene score
**Status:** Implemented (PR #5, open for review)

Turn the open-port risk heuristics you already compute per host into a single glanceable
score per host and per network, so users get an instant "is this okay?" read instead of
having to parse a raw port list.

**Requirements:**
- Aggregate `PortRiskHeuristics` output into a per-host and per-network score
- Score badge/card component (design-system addition)
- Wire into `DevicesDetailCards` / devices list

---

## 2. Hygiene score methodology explainer
The score in #1 is currently opaque - a number and a rating with no visible explanation of
what's being measured or why. Surface the methodology in-app: what counts as a finding, the
severity tiers and their point deductions, and which ports are currently flagged, following
the same tap-to-expand pattern `DevicesDetailCards.kt`'s "why no MAC address?" dialog already
uses for a similar "explain a non-obvious number" need.

**Requirements:**
- Info/help affordance on the score badge or hygiene card
- Explanatory copy: severity tiers, per-tier penalty, and the current flagged-port list
- No new data or logic - purely surfaces what `HygieneScore`/`PortRiskHeuristics` already
  compute

---

## 3. Actionable remediation list from hygiene findings
Turn a hygiene score's findings into a concrete "what to fix" checklist, per host and
network-wide, rather than leaving the user to infer action items from a raw score.

**Requirements:**
- A remediation suggestion per flagged port/protocol (extends the existing `PortRisk` map
  in `PortRiskHeuristics.kt` with a third field, reusing its established shape - e.g.
  "Telnet (23): disable it, use SSH instead")
- Checklist UI presenting findings + suggested fixes, per host and network-wide
- Tap-through from the network-level card to the specific host(s)/port(s) responsible

---

## 4. Dark / AMOLED true-black theme
**Status:** Implemented (PR #3, merged)

A pure-black theme option for OLED screens, cheap to add given `ThemeMode` already exists
as a settings concept.

**Requirements:**
- New `ThemeMode` value + true-black color set in `Theme.kt`/`Color.kt`
- Settings screen toggle

---

## 5. Configurable connection alert thresholds
**Status:** Implemented (PR #8, open for review)

Let the existing monitoring notification actually alert the user (not just display state)
when RSSI drops below a threshold or the connection drops/restores.

**Requirements:**
- Threshold settings (reuse `AppSettingsRepository` proto store)
- Comparison logic in `MonitoringService`'s connection collector
- Distinct alert notification channel/priority from the ongoing status notification

---

## 6. Scan session comparison view
Diff two persisted scan sessions side by side ("before/after I moved the router") using
data that's already stored.

**Requirements:**
- Session picker UI (two sessions from `ScanHistoryRepository`)
- Diff logic over `ScanSessionEntity`/`ScanObservationEntity`
- Diff results screen

---

## 7. Trend charts & historical channel congestion
Chart RSSI and channel-occupancy history over days/weeks instead of only per-session
snapshots - reuses the existing `RollingLineChart` and `ChannelOccupancyGraph` components.

**Requirements:**
- Time-range query over `ScanHistoryRepository`
- New chart composable(s) built on existing chart primitives
- History screen entry point

---

## 8. Finish tablet/Chromebook two-pane layouts
The adaptive-UI foundation (window size classes, `DevicePosture`, `TabletopSplitLayout`)
already exists; apply it consistently to list-detail screens that don't use it yet
(Devices, Wi-Fi, History).

**Requirements:**
- Audit which screens lack a two-pane variant
- Wire existing adaptive components into those screens
- Large-screen/Chromebook manual test pass

---

## 9. Continuous ping with a live graph
**Status:** Implemented (PR #4, merged)

A "ping -t" style loop mode with a live-updating chart, built on the ping engine and
chart components that already exist.

**Requirements:**
- Loop-mode toggle in `PingScreen`/`PingViewModel`
- Live chart reusing `RollingLineChart`
- Cancellable loop coroutine in `PingRepository`

---

## 10. Device grouping/tagging
Manual labels ("IoT", "guest", "trusted") for LAN hosts, since OUI/mDNS guesses won't
always be right.

**Requirements:**
- DB migration adding a tag/group field (or table) to the host schema
- Tag management UI
- Filter/sort by tag in `DevicesScreen`

---

## 11. AP capability diffing
Flag when a known AP's stored capabilities change (e.g. WPA3 → WPA2, 802.11ax dropped) -
compares against data already stored in `KnownApEntity`.

**Requirements:**
- Comparison logic between current scan and stored `KnownApEntity`
- Change-flag UI on the Wi-Fi detail screen
- Optional notification on change

---

## 12. Logical network map
**Status:** Implemented (PR #6, open for review)

A visual hub-and-spoke graph of discovered hosts around the gateway. Real switch-level
topology isn't discoverable without SNMP/LLDP, so this is framed honestly as a logical
map, not physical topology - pure visualization over data `LanDiscoveryRepository`
already has.

**Requirements:**
- Radial/force-directed layout composable
- Map view screen + nav entry
- Tap-through to existing device detail screen

---

## 13. DNS-over-HTTPS / DoT tester
Query a resolver over DoH and DoT and compare against plain DNS results - builds directly
on the existing `DnsWireCodec`/`DnsRepository`.

**Requirements:**
- DoH client (HTTPS POST/GET wire format)
- DoT client (TLS socket on port 853)
- Comparison UI added to the existing DNS tool screen

---

## 14. TLS certificate inspector
Show cert chain, expiry, and common misconfigurations for a host, following the same
pattern as the existing HTTP header inspector.

**Requirements:**
- `SSLSocket` handshake-only client to fetch the cert chain
- Cert parsing/formatting (expiry, SAN, issuer)
- New screen following `HttpInspectorScreen`'s pattern

---

## 15. Bandwidth-aware host list
A rough per-host responsiveness indicator from periodic latency sampling, reusing the
sweep probes already built for LAN discovery.

**Requirements:**
- Periodic sampling loop reusing `IcmpSweepProbe`/`TcpSweepProbe`
- Rolling latency average storage per host
- Indicator in the devices list row

---

## 16. Dashboard/home screen
A single "network health at a glance" screen combining Wi-Fi quality, device count, and
active diagnostics, rather than requiring navigation into each tool separately.

**Requirements:**
- New composable screen + nav destination
- ViewModel combining existing `ConnectionRepository`/`WifiScanRepository`/
  `LanDiscoveryRepository` flows
- Layout design (adaptive, per existing design-system conventions)

---

## 17. Search across tools & history
One search bar to jump to a host, past scan, or diagnostic run instead of navigating the
bottom nav tree.

**Requirements:**
- Search UI component
- Query layer across `ScanHistoryRepository`, `DiagnosticRunRepository`,
  `LanDiscoveryRepository`
- Result-to-destination routing

---

## 18. WPS/UPnP exposure flags
Surface when SSDP-discovered UPnP devices are exposing more services than a typical user
would expect - builds on the existing `SsdpProbe`.

**Requirements:**
- Fetch and parse UPnP device-description XML from the SSDP `LOCATION` URL
- Exposure heuristics (which service types to flag)
- Flag surfaced in device detail

---

## 19. Onboarding / first-run tour
A brief guided tour given how many tools are packed into the bottom nav.

**Requirements:**
- First-run flag in settings/DataStore
- Tour UI (overlay or stepped screens)
- Per-screen tour content

---

## 20. PDF/shareable report generation
A formatted network health report (Wi-Fi + LAN + diagnostics summary) for e.g. handing to
an ISP support line or landlord.

**Requirements:**
- Report template pulling from existing history repositories
- PDF generation (Android `PdfDocument` API)
- Share-intent integration

---

## 21. Opt-in local crash reporting
Capture uncaught exceptions to a local file the user can export, rather than a telemetry
SDK - consistent with the app's privacy stance. `ReleaseTree.kt` is an existing precedent
for structured local logging.

**Requirements:**
- Uncaught exception handler writing to local storage
- Redaction pass (strip local IPs/SSIDs before export)
- Export/share UI + settings opt-in toggle

---

## 22. In-app debug-bundle export
Bundle recent logs plus current scan/diagnostic state into a shareable file, so bug
reports don't require ADB. Shares infrastructure with #21.

**Requirements:**
- Log ring buffer
- Bundling + redaction of scan/diagnostic snapshot
- Zip + share-intent trigger

---

## 23. Scheduled/automatic periodic scans
Opt-in background snapshots so history builds up without manual runs, using the same
WorkManager pattern already used for `RetentionCleanupWorker`.

**Requirements:**
- Periodic `WorkManager` job respecting the existing scan-throttle (`ScanGovernor`)
- Settings toggle + interval choice
- Battery-impact messaging in the UI (Doze/background limits are real here)

---

## 24. New/vanished device alerts
Notify when an unrecognized MAC joins the LAN, or a normally-present device disappears.

**Requirements:**
- Background/periodic LAN sweep (depends on #23's scheduling work, or a dedicated job)
- Diffing logic against previously observed host sets
- Notification channel + false-positive tuning (DHCP churn, guest devices)

---

## 25. Rogue AP / evil-twin detection
Flag when a known SSID suddenly appears with a different BSSID, a downgraded security
type, or an unexpected vendor OUI.

**Requirements:**
- Detection logic layered on top of #11's AP-diffing work
- Alert UI/notification
- False-positive tuning (legitimate AP replacement, mesh roaming, guest APs)

---

## 26. Roaming/handoff tracker
For mesh networks, log which AP the phone is actually associated with over time and flag
roam events - useful for diagnosing sticky-client issues.

**Requirements:**
- BSSID-change detection while SSID is stable in `ConnectionRepository`/
  `MonitoringService`
- New DB table for roam events
- Timeline UI

---

## 27. MTU / path MTU discovery
Binary-search packet sizes with the DF flag to find path MTU - useful for VPN/tunnel
troubleshooting.

**Requirements:**
- Confirm DF-flag/fragmentation-needed detection is achievable via the existing
  binary-ping shell-out approach (`PingBinaryEngine`) without root - technical risk, needs
  a spike before committing
- Binary-search algorithm over packet sizes
- Result UI

---

## 28. Default-credential hints
An informational nudge ("this device has an admin panel open") for recognized device
types with known default admin ports.

**Requirements:**
- A maintained device-type → default-port/path mapping (ongoing maintenance burden, not
  just a one-time build)
- Careful UI wording to stay clearly informational, not exploit-adjacent
- Accuracy/false-positive review before shipping

---

## 29. Home-screen & quick-settings widgets
Glanceable signal strength and LAN device count without opening the app.

**Requirements:**
- Glance widget dependency + module wiring
- Widget layout(s) bound to `ConnectionRepository`/`LanDiscoveryRepository`
- Periodic update `Worker`
- Separate `TileService` if a quick-settings tile is included

---

## 30. Bufferbloat test
Compare idle vs. loaded latency to score bufferbloat - directly relevant to the app's
networking focus.

**Requirements:**
- Idle-latency baseline (reuse the ping engine)
- A load-generation mechanism significant enough to saturate the link (needs a data
  source - self-hosted endpoint, since the app currently has zero third-party service
  dependencies and that's a deliberate positioning choice)
- Under-load latency measurement + scoring
- New tool screen

---

## 31. Speed test integration
Simple throughput test correlated against RSSI/channel.

**Requirements:**
- A transfer endpoint (self-hosted, since using a third-party speed-test API cuts against
  the app's no-accounts/no-third-party-service stance)
- Throughput measurement + calculation
- Correlation UI against `WifiScanRepository` data
- New tool screen

---

## 32. Benchmark/perf test suite for the LAN sweep pipeline
Dev-facing only - given how parallel and timing-sensitive host discovery is, a benchmark
harness would catch regressions, but it delivers no direct user value.

**Requirements:**
- Benchmark harness setup (e.g. JMH or a lightweight Kotlin equivalent)
- Baseline metrics capture
- CI integration

---

## 33. Wear OS / lock-screen glanceable
A companion signal-strength view on the wrist or lock screen.

**Requirements:**
- Separate Wear OS module + build config
- Wearable Data Layer API sync from the phone app
- Companion Wear UI
- Dedicated Wear OS test device
- Niche value for a network-analysis use case - hardest to justify at this cost

---

## 34. iperf3-compatible throughput mode
Real throughput numbers against a companion `iperf3` server elsewhere on the LAN.

**Requirements:**
- iperf3 protocol client implementation (nontrivial binary protocol) - no prebuilt Android
  binary to shell out to, unlike ping/traceroute's toybox/iputils precedent
- Requires the user to run a separate `iperf3` server themselves
- New tool screen + result visualization
- Highest implementation cost of any idea on this list, for a fairly technical audience

---

## 35. F-Droid listing
Matches the app's existing no-telemetry/no-accounts positioning and Obtainium support;
mostly a packaging/process task, not new code.

**Requirements:**
- Reproducible-build verification
- Dependency audit (confirm everything in `libs.versions.toml` is FOSS-compatible)
- `fdroiddata` metadata submission (external repo, review process)
