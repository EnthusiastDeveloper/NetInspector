# Ideas backlog

Brainstormed feature and technique ideas for NetInspector that are not yet scoped enough
to be a concrete, actionable task. Concrete, actionable work (bugs, and ideas scoped down
to a specific implementable slice) is tracked as GitHub issues instead, not in this file,
see `docs/testing.md` for how bugs found during a validation pass get filed. Where an idea
below has had a specific slice scoped out into a GitHub issue, that's noted inline.

Two sections: feature/UX ideas, and device-identification technique ideas specifically
(narrower and more technical, split out because they share their own ranking rationale and
cross-reference each other heavily).

---

## Feature ideas

Ranked by ROI first, then by lowest additional-requirement count as a tiebreaker (cheaper,
more self-contained ideas rank above pricier ones of similar value). "Requirements" lists
what each idea needs beyond what the app already has.

### 1. Network hygiene score
**Status:** Implemented (PR #5, merged)

Turn the open-port risk heuristics you already compute per host into a single glanceable
score per host and per network, so users get an instant "is this okay?" read instead of
having to parse a raw port list.

**Requirements:**
- Aggregate `PortRiskHeuristics` output into a per-host and per-network score
- Score badge/card component (design-system addition)
- Wire into `DevicesDetailCards` / devices list

---

### 2. Hygiene score methodology explainer
**Status:** Implemented (PR #5, merged)

The score in #1 is currently opaque, a number and a rating with no visible explanation of
what's being measured or why. Surface the methodology in-app: what counts as a finding, the
severity tiers and their point deductions, and which ports are currently flagged, following
the same tap-to-expand pattern `DevicesDetailCards.kt`'s "why no MAC address?" dialog already
uses for a similar "explain a non-obvious number" need.

**Requirements:**
- Info/help affordance on the score badge or hygiene card
- Explanatory copy: severity tiers, per-tier penalty, and the current flagged-port list
- No new data or logic, purely surfaces what `HygieneScore`/`PortRiskHeuristics` already
  compute

---

### 3. Actionable remediation list from hygiene findings
**Status:** Implemented (PR #5, merged)

Turn a hygiene score's findings into a concrete "what to fix" checklist, per host and
network-wide, rather than leaving the user to infer action items from a raw score.

**Requirements:**
- A remediation suggestion per flagged port/protocol (extends the existing `PortRisk` map
  in `PortRiskHeuristics.kt` with a third field, reusing its established shape, e.g.
  "Telnet (23): disable it, use SSH instead")
- Checklist UI presenting findings + suggested fixes, per host and network-wide
- Tap-through from the network-level card to the specific host(s)/port(s) responsible

---

### 4. Dark / AMOLED true-black theme
**Status:** Implemented (PR #3, merged)

A pure-black theme option for OLED screens, cheap to add given `ThemeMode` already exists
as a settings concept.

**Requirements:**
- New `ThemeMode` value + true-black color set in `Theme.kt`/`Color.kt`
- Settings screen toggle

---

### 5. Configurable connection alert thresholds
**Status:** Implemented (PR #8, open for review)

Let the existing monitoring notification actually alert the user (not just display state)
when RSSI drops below a threshold or the connection drops/restores.

**Requirements:**
- Threshold settings (reuse `AppSettingsRepository` proto store)
- Comparison logic in `MonitoringService`'s connection collector
- Distinct alert notification channel/priority from the ongoing status notification

---

### 6. Scan session comparison view
**Status:** Implemented

Diff two persisted scan sessions side by side ("before/after I moved the router") using
data that's already stored.

**Requirements:**
- Session picker UI (two sessions from `ScanHistoryRepository`)
- Diff logic over `ScanSessionEntity`/`ScanObservationEntity`
- Diff results screen

---

### 7. Trend charts & historical channel congestion
Chart RSSI and channel-occupancy history over days/weeks instead of only per-session
snapshots, reuses the existing `RollingLineChart` and `ChannelOccupancyGraph` components.

A scoped slice of this (a history-backed occupancy chart specifically) is tracked as
GitHub issue #27, "Channel occupancy trend over time."

**Requirements:**
- Time-range query over `ScanHistoryRepository`
- New chart composable(s) built on existing chart primitives
- History screen entry point

---

### 8. Finish tablet/Chromebook two-pane layouts
The adaptive-UI foundation (window size classes, `DevicePosture`, `TabletopSplitLayout`)
already exists; apply it consistently to list-detail screens that don't use it yet
(Devices, Wi-Fi, History).

**Requirements:**
- Audit which screens lack a two-pane variant
- Wire existing adaptive components into those screens
- Large-screen/Chromebook manual test pass

---

### 9. Continuous ping with a live graph
**Status:** Implemented (PR #4, merged)

A "ping -t" style loop mode with a live-updating chart, built on the ping engine and
chart components that already exist.

**Requirements:**
- Loop-mode toggle in `PingScreen`/`PingViewModel`
- Live chart reusing `RollingLineChart`
- Cancellable loop coroutine in `PingRepository`

---

### 10. Device grouping/tagging
Manual labels ("IoT", "guest", "trusted") for LAN hosts, since OUI/mDNS guesses won't
always be right. The narrower "plain per-host nickname" half of this (see the device
identification ideas section, item D, implemented) already covers "which one is my
printer", what's left here is tag *categories* plus filter/sort by tag, not the underlying
storage or display-name override.

The icon-only slice of this idea is tracked as GitHub issue #29, "Custom device labels and
icons."

**Requirements:**
- Tag category field/table (`saved_host`'s `SavedHostEntity` already exists for the
  single-nickname case, extending it or adding a sibling table both work)
- Tag management UI
- Filter/sort by tag in `DevicesScreen`

---

### 11. AP capability diffing
**Status:** Implemented

Flag when a known AP's stored capabilities change (e.g. WPA3 to WPA2, 802.11ax dropped),
compares against data already stored in `KnownApEntity`.

**Requirements:**
- Comparison logic between current scan and stored `KnownApEntity`
- Change-flag UI on the Wi-Fi detail screen
- Optional notification on change

---

### 12. Logical network map
**Status:** Implemented (PR #6, merged)

A visual hub-and-spoke graph of discovered hosts around the gateway. Real switch-level
topology isn't discoverable without SNMP/LLDP, so this is framed honestly as a logical
map, not physical topology, pure visualization over data `LanDiscoveryRepository`
already has.

**Requirements:**
- Radial/force-directed layout composable
- Map view screen + nav entry
- Tap-through to existing device detail screen

---

### 13. DNS-over-HTTPS / DoT tester
Query a resolver over DoH and DoT and compare against plain DNS results, builds directly
on the existing `DnsWireCodec`/`DnsRepository`.

A scoped slice of this (adding a DoH transport option to the existing DNS tool) is tracked
as GitHub issue #31, "DNS-over-HTTPS/TLS query support."

**Requirements:**
- DoH client (HTTPS POST/GET wire format)
- DoT client (TLS socket on port 853)
- Comparison UI added to the existing DNS tool screen

---

### 14. TLS certificate inspector
Show cert chain, expiry, and common misconfigurations for a host, following the same
pattern as the existing HTTP header inspector.

**Requirements:**
- `SSLSocket` handshake-only client to fetch the cert chain
- Cert parsing/formatting (expiry, SAN, issuer)
- New screen following `HttpInspectorScreen`'s pattern

---

### 15. Bandwidth-aware host list
A rough per-host responsiveness indicator from periodic latency sampling, reusing the
sweep probes already built for LAN discovery.

**Requirements:**
- Periodic sampling loop reusing `IcmpSweepProbe`/`TcpSweepProbe`
- Rolling latency average storage per host
- Indicator in the devices list row

---

### 16. Dashboard/home screen
**Status:** Implemented (PR #7, merged)

A single "network health at a glance" screen combining Wi-Fi quality, device count, and
active diagnostics, rather than requiring navigation into each tool separately.

**Requirements:**
- New composable screen + nav destination
- ViewModel combining existing `ConnectionRepository`/`WifiScanRepository`/
  `LanDiscoveryRepository` flows
- Layout design (adaptive, per existing design-system conventions)

---

### 17. Search across tools & history
One search bar to jump to a host, past scan, or diagnostic run instead of navigating the
bottom nav tree.

**Requirements:**
- Search UI component
- Query layer across `ScanHistoryRepository`, `DiagnosticRunRepository`,
  `LanDiscoveryRepository`
- Result-to-destination routing

---

### 18. WPS/UPnP exposure flags
Surface when SSDP-discovered UPnP devices are exposing more services than a typical user
would expect, builds on the existing `SsdpProbe`.

**Requirements:**
- Fetch and parse UPnP device-description XML from the SSDP `LOCATION` URL
- Exposure heuristics (which service types to flag)
- Flag surfaced in device detail

---

### 19. Onboarding / first-run tour
A brief guided tour given how many tools are packed into the bottom nav.

**Requirements:**
- First-run flag in settings/DataStore
- Tour UI (overlay or stepped screens)
- Per-screen tour content

---

### 20. PDF/shareable report generation
A formatted network health report (Wi-Fi + LAN + diagnostics summary) for e.g. handing to
an ISP support line or landlord.

**Requirements:**
- Report template pulling from existing history repositories
- PDF generation (Android `PdfDocument` API)
- Share-intent integration

---

### 21. Opt-in local crash reporting
**Status:** Implemented (PR #11, open for review)

Capture uncaught exceptions to a local file the user can export, rather than a telemetry
SDK, consistent with the app's privacy stance. `ReleaseTree.kt` is an existing precedent
for structured local logging.

**Requirements:**
- Uncaught exception handler writing to local storage
- Redaction pass (strip local IPs/SSIDs before export)
- Export/share UI + settings opt-in toggle

---

### 22. In-app debug-bundle export
**Status:** Implemented (PR #11, open for review)

Bundle recent logs plus current scan/diagnostic state into a shareable file, so bug
reports don't require ADB. Shares infrastructure with #21.

**Requirements:**
- Log ring buffer
- Bundling + redaction of scan/diagnostic snapshot
- Zip + share-intent trigger

---

### 23. Scheduled/automatic periodic scans
**Status:** Implemented

Opt-in background snapshots so history builds up without manual runs, using the same
WorkManager pattern already used for `RetentionCleanupWorker`.

**Requirements:**
- Periodic `WorkManager` job respecting the existing scan-throttle (`ScanGovernor`)
- Settings toggle + interval choice
- Battery-impact messaging in the UI (Doze/background limits are real here)

---

### 24. New/vanished device alerts
**Status:** Implemented

Notify when an unrecognized MAC joins the LAN, or a normally-present device disappears.

**Requirements:**
- Background/periodic LAN sweep (depends on #23's scheduling work, or a dedicated job)
- Diffing logic against previously observed host sets
- Notification channel + false-positive tuning (DHCP churn, guest devices)

---

### 25. Rogue AP / evil-twin detection
Flag when a known SSID suddenly appears with a different BSSID, a downgraded security
type, or an unexpected vendor OUI.

**Requirements:**
- Detection logic layered on top of #11's AP-diffing work
- Alert UI/notification
- False-positive tuning (legitimate AP replacement, mesh roaming, guest APs)

---

### 26. Roaming/handoff tracker
For mesh networks, log which AP the phone is actually associated with over time and flag
roam events, useful for diagnosing sticky-client issues.

A scoped slice of this (persisting BSSID transition events specifically) is tracked as
GitHub issue #26, "Roaming / BSSID transition log."

**Requirements:**
- BSSID-change detection while SSID is stable in `ConnectionRepository`/
  `MonitoringService`
- New DB table for roam events
- Timeline UI

---

### 27. MTU / path MTU discovery
Binary-search packet sizes with the DF flag to find path MTU, useful for VPN/tunnel
troubleshooting.

A scoped slice of this is tracked as GitHub issue #30, "MTU / path MTU discovery tool."

**Requirements:**
- Confirm DF-flag/fragmentation-needed detection is achievable via the existing
  binary-ping shell-out approach (`PingBinaryEngine`) without root, technical risk, needs
  a spike before committing
- Binary-search algorithm over packet sizes
- Result UI

---

### 28. Default-credential hints
An informational nudge ("this device has an admin panel open") for recognized device
types with known default admin ports.

**Requirements:**
- A maintained device-type to default-port/path mapping (ongoing maintenance burden, not
  just a one-time build)
- Careful UI wording to stay clearly informational, not exploit-adjacent
- Accuracy/false-positive review before shipping

---

### 29. Home-screen & quick-settings widgets
Glanceable signal strength and LAN device count without opening the app.

Two scoped slices of this are tracked as GitHub issues: #28 ("Quick Settings tile for
gateway ping/traceroute") and #34 ("Home-screen widget for signal strength").

**Requirements:**
- Glance widget dependency + module wiring
- Widget layout(s) bound to `ConnectionRepository`/`LanDiscoveryRepository`
- Periodic update `Worker`
- Separate `TileService` if a quick-settings tile is included

---

### 30. Bufferbloat test
Compare idle vs. loaded latency to score bufferbloat, directly relevant to the app's
networking focus.

**Requirements:**
- Idle-latency baseline (reuse the ping engine)
- A load-generation mechanism significant enough to saturate the link (needs a data
  source, self-hosted endpoint, since the app currently has zero third-party service
  dependencies and that's a deliberate positioning choice)
- Under-load latency measurement + scoring
- New tool screen

---

### 31. Speed test integration
**Status:** Implemented, rescoped (PR #21, open for review)

Built as a **LAN-only throughput test**, not an internet speed test. The original framing
below called for a transfer endpoint, self-hosted, to avoid a third-party speed-test API,
but a self-hosted endpoint is still infrastructure this project doesn't operate and the
app's user doesn't have, which cuts against the same no-third-party-service positioning just
as much as a commercial API would. See
[ADR-0009](adr/0009-lan-throughput-icmp-burst-estimate.md) for the full reasoning and what
was built instead: throughput to a host already on the user's own LAN (the gateway, or any
discovered device), estimated from a burst of concurrent ICMP echo probes, no server
component anywhere, reusing the same unprivileged ICMP socket mechanism `PingRepository`
already relies on.

**What shipped:**
- LAN throughput test tool screen (`ui/screens/tools/throughput`), reachable from the Tools
  grid and, pre-filled, from a "LAN throughput" button on the Devices detail screen alongside
  Ping/Traceroute/Scan ports
- Free-text host/IP field plus a dropdown of known devices from the current sweep
  (`LanDiscoveryRepository.hosts`)
- `LanThroughputRepository` (`:data:diagnostics`), concurrent ICMP echo burst, round-trip
  Mbps estimate, packet loss, peak sample
- Correlation card: RSSI, channel, channel width and the count of other visible APs sharing
  that channel, captured from `ConnectionRepository`/`WifiScanRepository` at test start and
  end
- Copy throughout (tool label, on-screen disclaimer, ADR) explicitly signals "local network
  only, not your internet connection", never bare "Speed test"
- Not built: a bufferbloat-style idle-vs-loaded latency score, that's #30, a separate item

---

### 32. Benchmark/perf test suite for the LAN sweep pipeline
**Status:** Implemented (PR #20, open for review)

Dev-facing only, given how parallel and timing-sensitive host discovery is, a benchmark
harness would catch regressions, but it delivers no direct user value.

**Requirements:**
- Benchmark harness setup (e.g. JMH or a lightweight Kotlin equivalent)
- Baseline metrics capture
- CI integration

---

### 33. Wear OS / lock-screen glanceable
A companion signal-strength view on the wrist or lock screen. Related to #29's signal
widget above, but a separate platform surface, no GitHub issue filed for this one yet.

**Requirements:**
- Separate Wear OS module + build config
- Wearable Data Layer API sync from the phone app
- Companion Wear UI
- Dedicated Wear OS test device
- Niche value for a network-analysis use case, hardest to justify at this cost

---

### 34. iperf3-compatible throughput mode
Real throughput numbers against a companion `iperf3` server elsewhere on the LAN.

**Requirements:**
- iperf3 protocol client implementation (nontrivial binary protocol), no prebuilt Android
  binary to shell out to, unlike ping/traceroute's toybox/iputils precedent
- Requires the user to run a separate `iperf3` server themselves
- New tool screen + result visualization
- Highest implementation cost of any idea on this list, for a fairly technical audience

---

### 36. Adjustable UI/font scale
**Status:** Implemented (PR #19, open for review)

A settings slider that scales text and UI element sizing app-wide, independent of the
system's own accessibility font-size setting, surfaced after simulating several screen
densities during Phase 4 verification (`wm density`), where the smaller-density renders read
noticeably better for this app's information-dense screens (Devices list, network map) than
the default. Rather than only supporting that indirectly through the OS setting (which affects
every app, not just this one, and most users won't go find it in system settings for one app),
a first-party in-app slider is a cheap, self-contained way to offer the same benefit.

**Requirements:**
- A persisted scale factor (reuse `AppSettingsRepository`'s existing DataStore-backed pattern,
  same shape as `ThemeMode`)
- A `CompositionLocalProvider(LocalDensity provides ...)` wrapper applying the scale to
  `fontScale` at the app root, so every screen picks it up with no per-screen changes
- Settings screen slider control with a live preview
- A sane clamped range (too small becomes illegible, too large breaks layouts already tuned
  for a default scale, `NetworkMapLayout.kt`'s `nodeScaleFor` is a precedent for "how far can
  this go before it needs its own compensating logic")

---

### 37. F-Droid listing
Matches the app's existing no-telemetry/no-accounts positioning and Obtainium support;
mostly a packaging/process task, not new code.

**Requirements:**
- Reproducible-build verification
- Dependency audit (confirm everything in `libs.versions.toml` is FOSS-compatible)
- `fdroiddata` metadata submission (external repo, review process)

---

### 37. Favicon / web-UI hash fingerprinting
**Status:** Declined.

Considered as a way to identify router/NAS/camera web UIs by hashing their
`/favicon.ico` and matching against known-vendor hashes (the Shodan-style
technique). Declined because there's no verified reference hash database
available to match against, and fabricating one risks mislabeling devices,
directly against this app's own "no label without a basis" design principle
(`design.md`). The existing HTTP banner grab (`Server` header + `<title>`)
already covers most of the same value for web-UI identification without this risk.

**Requirements:** none planned. Would need a genuinely sourced, verified hash database
before reconsidering, not something to build speculatively.

---

## Device identification ideas

Brainstormed techniques to make LAN device labels more specific than the current
`ttlDeviceHint`/`portSignatureHint` fallbacks ("Linux/Android/iOS/macOS family", "Windows
family", "Network equipment", `DeviceHintHeuristics.kt`), inspired by how apps like Fing or
Network Analyzer produce concrete device names. Ranked by ROI first, then by lowest
additional-requirement count as a tiebreaker, same convention as the feature ideas section
above (that section's #10, device tagging, is the UX-only companion to this section, manual
labels for when *no* automated signal is right).

All active probing here is still subject to the scope-of-use note in
[`README.md`](README.md#a-note-on-scope-of-use): everything below only queries hosts on
networks the user administers, and adds no new probe traffic beyond what a compliant SSDP/
mDNS/NetBIOS/SNMP client would normally send.

**Note on MAC address / OUI vendor lookup:** this is a permanent, deliberate architectural
constraint (`docs/adr/c-01-arp-table-unreadable.md`), not unfinished work, and isn't
reopened by any idea below. Android 10+ blocks reading the ARP table for any unrooted,
non-system app, with no supported workaround. `Host.macAddress` and `Host.vendor` exist in
the model and stay `null` for most hosts by design so a future rooted/privileged build
could populate them without a model change, but that is out of scope for the current
unrooted target, per the "Root support: None" fixed decision in `docs/README.md`. Items A3
and C1 below are real but partial exceptions: they recover a MAC from an application-layer
payload the app already legitimately receives (NetBIOS NBSTAT, a router's own UPnP `Hosts:1`
service), not from the kernel ARP table, so they don't need root or a raw socket and don't
reopen C-01.

---

### A1. Feed SSDP/UPnP manufacturer+model into `DeviceHint`
**Status:** Implemented

`SsdpProbe.kt` already fetches the UPnP device-description XML from a responder's `LOCATION`
header and extracts `manufacturer`/`modelName` into `DiscoveredService`, but that data never
reached `DeviceHint`, it only showed up as a detail-screen field. A device's own declared
manufacturer/model is stronger evidence than an inferred port signature or TTL bucket, so it
now wins outright as a new top `Certainty.CONFIRMED` tier.

**Requirements:**
- A `Certainty.CONFIRMED` tier above `LIKELY`/`POSSIBLE`
- A hint-precedence merge in `HostMerge.mergeObservation` (previously "whichever `HostObservation`
  arrived most recently wins", which let a later Stage C TTL guess silently clobber an earlier,
  more specific Stage A SSDP hint)
- A pure `upnpDeviceHint(manufacturer, modelName)` builder alongside the existing
  `portSignatureHint`/`ttlDeviceHint` in `DeviceHintHeuristics.kt`

---

### A2. Feed mDNS service type + TXT records into `DeviceHint`
**Status:** Implemented

`MdnsProbe.kt` already resolves full TXT records (RFC 6763 §6) into `DiscoveredService.
txtRecords` but never used them, or the service type itself, as a `DeviceHint` signal. Two
sub-techniques:
- The **service type alone** is a decent LIKELY-tier signal, a host advertising
  `_airplay._tcp` is an Apple device, `_hap._tcp` is a HomeKit accessory, `_googlecast._tcp`
  is a Cast device, regardless of TTL.
- Several service types carry an **explicit model string in TXT**, which is CONFIRMED-tier
  evidence exactly like A1's UPnP manufacturer/model: `_device-info._tcp` (Apple's own info
  service, TXT `model=`, e.g. `J274AP`), `_googlecast._tcp` (TXT `md=`, a friendly model name),
  `_ipp._tcp`/`_printer._tcp` (TXT `ty=`, printer model).

**Requirements:**
- A `mdnsServiceHint(serviceType, txtRecords)` pure function next to A1's `upnpDeviceHint`
- A small service-type to generic-label table (same shape as `PORT_SIGNATURES`)
- A small TXT-key to model table for the services that expose one

---

### A3. Extract a real MAC address from NetBIOS NBSTAT responses
**Status:** Implemented

RFC 1002 §4.2.18's NBSTAT response STATISTICS field begins with a 6-byte UNIT_ID, the
responding NIC's actual MAC address. `NetBiosProbe.kt` already parses this exact packet but
discarded everything after the name array. This is a real MAC obtained from an application-
layer protocol payload the app is already legitimately receiving, not from the kernel ARP
table blocked by C-01, it doesn't need root, a raw socket, or any workaround the ADR's
"rejected alternatives" table considered. Coverage is narrow (only hosts that answer NBSTAT,
mainly Windows/Samba boxes and some NAS/print servers), so this is a real but partial
exception to "no MAC addresses for LAN hosts," not a general fix.

Once a real MAC exists, the OUI vendor table (previously wired only to Wi-Fi AP BSSIDs,
`VendorLookup.kt`) can resolve a vendor for these hosts too. Caveat carried over from that
table's own scope: it's deliberately curated to router/AP/NAS vendors and excludes general
client-device silicon (Intel, Realtek, Dell, etc. NIC vendors), so a NetBIOS-observed Windows
PC will often still get no vendor hit until the full Wireshark `manuf` registry (already
planned for `:data:persistence`) lands.

**Requirements:**
- Parse the STATISTICS field's UNIT_ID in `NetBiosProbe.kt`
- `macAddress`/`vendor` fields on `HostObservation`, merged the same way `icmpReplyTtl`
  already is (new non-null wins, doesn't get erased by a later null)
- Move `VendorLookup` (and its `oui_vendors.tsv` table) somewhere both `:data:wifi` and
  `:data:lan` can reach it without violating "a data module never depends on another data
  module", `:core:common`, switching from Android `AssetManager` to a plain JVM classpath
  resource so the module's "no `android.*` imports" rule still holds
- Update the "why no MAC address?" detail-screen copy (`DevicesDetailCards.kt`) to actually
  render a MAC/vendor when one is present, instead of asserting it's always unavailable

---

### B1. SNMP `sysDescr` query
**Status:** Implemented

Query OID `1.3.6.1.2.1.1.1.0` over UDP 161 with community string `public`. Printers, managed
switches, UPSes, and NAS boxes very often leave the default read-only community enabled and
return an exact model/firmware string, historically one of the highest-value single probes
for exactly the "Network equipment" bucket that's currently weakest. Also queries `sysName`
(OID `1.3.6.1.2.1.1.5.0`, an admin-set device name) in the same GET-request round trip, since
SNMP allows multiple varbinds per PDU at no extra network cost.

**Requirements:**
- A minimal SNMP v1/v2c GET-request encoder/decoder (BER/ASN.1 subset, no existing dependency
  for this, would need a small hand-rolled implementation matching the NetBIOS/SSDP probes'
  existing "hand-roll the wire format" precedent), `SnmpBer.kt`, scoped to exactly the tags a
  GET round trip uses, not a general ASN.1 codec
- Wire the result into `DeviceHint` at `CONFIRMED` tier (self-reported by the device),
  `snmpDeviceHint` in `DeviceHintHeuristics.kt`; `sysName` also feeds the hostname precedence
  ladder (`EvidenceSource.SNMP`, alongside `NETBIOS`/`UPNP_HOSTS`)
- New probe module under `:data:lan`, same shape as `NetBiosProbe`/`SsdpProbe`, `SnmpProbe.kt`,
  run from `HostEnricher` (Stage C) against every already-`CONFIRMED` host, same as the existing
  TTL/port enrichment

---

### B2. WS-Discovery probe
Multicast UDP 3702 to `239.255.255.250`, how ONVIF IP cameras and some Windows/print devices
announce themselves. Several cameras answer WS-Discovery but not SSDP, filling a gap in the
current port-554-only camera guess.

**Requirements:**
- WS-Discovery Probe/ProbeMatch SOAP-over-UDP messages (another hand-rolled wire format)
- XAddrs parsing for device metadata
- New probe module under `:data:lan`

---

### B3. TLS certificate inspection on admin-UI ports
**Status:** Implemented

`ExtendedPortProbe.kt` already opens a plain-HTTP banner grab on ports like 443/8443. A TLS
handshake-only client on those same ports can read the certificate CN/SAN/issuer, which
self-signed router/NAS/camera certs frequently set to the product name (`Synology Inc.`,
`ubnt`, `hikvision`, `RT-AX88U`).

**Requirements:**
- `SSLSocket` handshake-only client (shares the "connect, don't validate, just read the
  chain" pattern the standalone TLS inspector idea in the feature ideas section (#14) would
  also need, could share code with that if #14 ever lands), `TlsCertificateProbe.kt`, tried
  against 443 then 8443
- Cert subject/issuer parsing, just the CN component of the subject DN for now (SAN/issuer
  are visible on the detail screen's existing port/banner rows if a fuller cert viewer ever
  lands, e.g. via #14)
- Feed matched fields into `DeviceHint`, `tlsCertificateDeviceHint` in
  `DeviceHintHeuristics.kt`, `CONFIRMED` tier like B1's SNMP `sysDescr` (a tie between the two
  favors SNMP's usually more specific firmware string over a certificate's often generic
  company-name CN)
- A matching `EvidenceSource.TLS` entry on the detail screen's timeline, same as every other
  Stage A/C signal, verified live against a real TP-Link Deco mesh node (CN `tplinkdeco.net`)

---

### B4. HTTP banner/HTML signature table
`ExtendedPortProbe.kt` already grabs `Server:` header and `<title>` from HTTP banners but
never uses them for `DeviceHint`, just displays them raw. A small curated set of
regex-to-label rules (the same idea as Wappalyzer, scoped down to network-device admin UIs)
turns banners already being fetched into device labels.

**Requirements:**
- A signature table (ongoing maintenance burden, similar caveat to feature idea #28)
- Match logic feeding `DeviceHint`

---

### C1. UPnP IGD "Hosts" service for router-reported MAC+hostname
**Status:** Implemented

Some consumer routers (mainly those exposing full UPnP IGD v2) implement
`urn:schemas-upnp-org:service:Hosts:1`, which lets any LAN client SOAP-query the router's own
connected-device table, MAC *and* hostname, for every device on the LAN, all at once. This
is the single biggest lever on the MAC-address problem (C-01) beyond A3's narrow NetBIOS
exception, but coverage depends entirely on the router's firmware exposing this service, real
consumer routers vary widely, so treat the resulting hosts as a bonus on top of the rest of
Stage A, not a guaranteed win on any given network.

**Requirements:**
- Detect the `Hosts:1` service in the UPnP device description `SsdpProbe.kt` already fetches,
  done in `SsdpProbe.parseUpnpDeviceDescription`, resolving a relative `controlURL` against the
  device description's own `LOCATION` URL
- SOAP client for `GetHostNumberOfEntries`/`GetGenericHostEntry`, `UpnpHostsProbe.kt`, a
  hand-rolled SOAP envelope builder and a small regex-based response-field reader (not a full
  pull-parser: `android.util.Xml` is an unmocked Android stub in a plain JVM unit test, and
  every field read back here is a single leaf element with no nested markup)
- Runs after `SsdpProbe`'s UDP receive loop closes, not inside it, so the SOAP round-trip's
  own latency never risks missing another responder's M-SEARCH reply
- Each router-reported entry becomes its own `HostObservation` (`EvidenceSource.UPNP_HOSTS`,
  `ANNOUNCED` tier like NetBIOS, a third party reporting a host isn't that host's own direct
  response), keyed by IP, with a real MAC/vendor and hostname folded in the same way A3's
  NetBIOS observations are

---

### C2. Passive DHCP broadcast sniffing
Binding UDP port 67 and listening (not sending) picks up other devices' DHCP DISCOVER/REQUEST
broadcasts. Option 12 (hostname) and Option 60 (vendor class, e.g. `android-dhcp-13`,
`MSFT 5.0`) are immediate wins; the Option 55 parameter-request-list sequence is also a known
OS/device fingerprint (this is what Fingerbank's DHCP fingerprinting is built on).

**Requirements:**
- UDP broadcast listener on port 67 (may race the real DHCP server for the socket depending on
  OEM; some Android builds restrict raw broadcast binding, technical risk, needs a spike)
- DHCP packet parser (options 12/55/60)
- A maintained OS-fingerprint table for the Option 55 sequence if going beyond just
  hostname/vendor-class (ongoing maintenance burden, same caveat class as B4)

---

### D. Manual nickname/tag per device
**Status:** Implemented

Not a new identification *technique*, the fallback improvement independent of how good
automated ID gets. There was previously no way to override a `Host`'s displayed name at all
(see feature idea #10, which scopes the broader IoT/guest/trusted-style tagging idea, a plain
per-host nickname is the same underlying feature, narrower: no tag categories, no filter/sort
by tag, just a label that wins over every automated naming signal). Fixes "which one is my
printer" immediately regardless of automated accuracy.

**Requirements:**
- A `saved_host` Room table (`SavedHostEntity`: `key`, `nickname`), migration 2 to 3
- `Host.nicknameKey()` in `core/model`, MAC-based when available (A3), address+hostname
  otherwise, never plain IP alone (unstable across a DHCP lease change)
- `SavedHostRepository` joined into `DevicesViewModel`'s host flow, overlaying `Host.nickname`
  after every sweep merge (nicknames aren't part of the sweep pipeline itself)
- `displayName()` gives a nickname top precedence over hostname/device-hint/self/gateway
  labelling
- Edit affordance on the device detail screen's header (pencil icon leading to a dialog with a
  text field); saving a blank value clears the nickname

---

### E. Crowd-sourced/cloud fingerprint database
Fing's actual edge over anything in this list is Fingbank, a cloud database matching
OUI+ports+mDNS+DHCP-fingerprint combinations against millions of crowd-sourced devices.
Replicating the accuracy this buys offline means building and maintaining an equivalent
curated signature table by hand (everything above is exactly that data feed); calling an
external API instead means shipping local network topology off-device, which cuts directly
against this app's no-third-party-service, no-accounts positioning (feature ideas #30/#31
turn down third-party APIs for the same reason). Flagging this for completeness, not
recommending it, out of scope unless that privacy trade-off is explicitly wanted.
