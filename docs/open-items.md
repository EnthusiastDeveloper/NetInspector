# NetInspector - Open Items

Everything left outstanding after Phases 0-8 of `implementation-plan.md` shipped.
The phased plan itself is complete: Room persistence, the monitoring foreground
service, history screens, CSV/JSON export, settings, and the accessibility/adaptive
layout passes are all in place, and no phase has unchecked tasks. What follows is
smaller, targeted work identified after the fact - mostly from a Devices-tab
debugging session that fixed several real bugs along the way (see git log for
specifics: the scan progress counter, reverse DNS never resolving, and the
follow-on concurrency fix are all already committed, not listed here as open).

---

## 1. Physical foldable verification session

**Status: still open, explicitly called out in `implementation-plan.md`'s "Still
open" section.**

Book and tabletop posture layouts were built and verified on the resizable
emulator only, which the design doc treats as a valid surface for layout work (the
one exception to C-13, since layout needs no radio). Real hinge dimensions and
aspect ratios vary by manufacturer, so one verification session on an actual
foldable device is still needed before calling this done.

**Action**: book device time on a real foldable, run through both list-detail
panes (Wi-Fi, Devices) in book and tabletop posture, confirm hinge-aware pane
placement matches what the emulator showed.

---

## 2. Router DHCP lease-table integration (needs a decision, not just work)

**Status: not started - and in tension with a documented non-goal.**

The single most useful remaining lever for reducing "Unknown device" further:
every DHCP client sends its hostname to the router at lease time (option 12),
which is usually the most complete and reliable name source on a home network -
better than mDNS/SSDP/NetBIOS/reverse-DNS combined, since virtually every client
sends it. A phone app can't see this passively (DHCP negotiations between other
devices and the router aren't delivered to a regular app without root), but could
query it if given the router's admin credentials. Some routers expose a
documented API for this (AVM Fritz!Box's TR-064, for example); others only via
scraping an HTML admin page, which is fragile and a separate integration per
router vendor.

**The catch**: `docs/README.md`'s Non-goals section explicitly lists "Router
administration via vendor web UIs or TR-064" as out of scope. Pursuing this
would mean deliberately reversing that decision, not just building a new
feature - it also means storing the user's router credentials locally, which is
a real trust/security surface this app has otherwise avoided entirely (no
accounts, no cloud sync, no telemetry, per the same non-goals list).

**Action before writing any code**: explicit go/no-go decision on whether to
reverse the TR-064 non-goal. If yes, scope it to read-only lease-table queries
for whichever router the user actually has, not a general router-admin feature.

---

## 3. Raw mDNS fallback (bypass `NsdManager`)

**Status: contingency, not confirmed necessary.**

`design.md`'s own risk notes flag `NsdManager` as "historically flaky across
OEMs" and name a hand-rolled raw UDP 5353 mDNS client as a contingency - but only
if `NsdManager` proves unreliable in practice, with an estimated +2 days if it's
needed. No evidence yet that it's actually a problem: reverse DNS now covers most
of what used to show as "Unknown device" (see the concurrency fix above), so the
practical impact of any remaining `NsdManager` flakiness is unclear.

**Action**: hold off. Revisit only if specific mDNS-only devices (AirPlay,
HomeKit, Chromecasts, printers) are observed missing from scans despite
announcing on the network - check via `NsdManager`'s own discovery log or a
third-party mDNS browser tool for comparison before committing to a raw
implementation.

---

## 4. Favicon / web-UI hash fingerprinting

**Status: declined.**

Considered as a way to identify router/NAS/camera web UIs by hashing their
`/favicon.ico` and matching against known-vendor hashes (the Shodan-style
technique). Declined because there's no verified reference hash database
available to match against, and fabricating one risks mislabeling devices -
directly against this app's own "no label without a basis" design principle
(`design.md` §11.3). The existing HTTP banner grab (`Server` header + `<title>`)
already covers most of the same value for web-UI identification without this risk.

**Action**: none planned. Would need a genuinely sourced, verified hash database
before reconsidering - not something to build speculatively.

---

## 5. New-device-joined notification

**Status: not started.**

The sweep pipeline already produces confirmed hosts and the app already persists
`known_ap`/`saved_host` records, but nothing diffs a fresh sweep against what was
seen before. A background or monitoring-service check that raises a notification
when an address not in the known set turns up would turn "who's on my Wi-Fi"
from a manual re-scan into a passive alert - the single highest-value use of
data the app already collects but doesn't act on.

**Action**: extend `MonitoringService`/`MonitoringController` to diff each sweep
against `known_ap`/`saved_host`, with an opt-in setting (default off, to avoid
surprising a user who just added a device on purpose).

---

## 6. Scheduled / continuous ping monitor

**Status: not started.**

Single ping runs miss intermittent drops. The `MonitoringService` foreground-
service pattern already built for continuous Wi-Fi observation is directly
reusable for a long-running ping against a user-chosen target (typically the
gateway), charting packet loss and RTT over hours rather than seconds.

**Action**: add a monitoring mode that reuses the tier-1 ICMP engine on an
interval, persists results the same way `diagnostic_run` does, and surfaces a
loss-rate notification/history chart.

---

## 7. Roaming / BSSID transition log

**Status: not started.**

`AccessPoint` already tracks `firstSeen`/`lastSeen`/`isConnected` per BSSID.
Logging every handoff between BSSIDs sharing an SSID (with RSSI at the moment
of each transition) would directly diagnose flaky mesh/multi-AP roaming - a
common real-world complaint this app is otherwise well-positioned to explain
but currently doesn't record as a timeline.

**Action**: persist a transition event whenever `ConnectionSnapshot.bssid`
changes while `ssid` stays constant; surface as a simple timeline on the
Connection screen.

---

## 8. Channel occupancy trend over time

**Status: not started.**

`scan_session`/`scan_observation` are already retained for 30 days but only
ever rendered as the current-moment occupancy graph (§7.1). A time-series view
("channel 6 congestion over the last week") would use data already being
collected without adding any new persistence.

**Action**: add a history-backed chart to the Wi-Fi history screen, bucketed
by channel and time, reusing `ScanHistoryRepository`.

---

## 9. Quick Settings tile for gateway ping/traceroute

**Status: not started.**

One-tap triage when someone says "is the Wi-Fi down" - a Quick Settings tile
that launches straight into a ping against the current gateway, without
opening the app and navigating to Tools first.

**Action**: `TileService` that reads the cached gateway from
`ConnectionRepository` and deep-links into the Ping tool pre-filled, matching
the existing host-row-to-tool deep link pattern (§11.1).

---

## 10. Custom device labels and icons

**Status: not started.**

`saved_host` already supports a label, but there's no category icon, so the
Devices list still reads as an undifferentiated address list once a network
has more than a handful of pinned hosts. Letting a user pick an icon (TV,
printer, phone, NAS, camera, …) alongside the label would make the list
scannable at a glance rather than requiring a tap into each detail screen.

**Action**: add an icon field to `saved_host`/`SavedHostEntity`-equivalent and
a small fixed icon set to the device detail edit flow.

---

## 11. Diff view between two saved scan sessions

**Status: not started.**

Both Wi-Fi and Devices history are persisted, but there's no "what changed
since yesterday" view - only single-session chart/list rendering. A diff
between two sessions (APs/hosts appeared, disappeared, or moved channel/RSSI
materially) would answer the most common troubleshooting question users bring
to a network analyser without requiring any new capture logic.

**Action**: add a session-picker diff screen to `ScanHistoryScreen` and the
Devices history equivalent.

---

## 12. LAN throughput test against a self-hosted or LAN target

**Status: not started - distinct from the declined non-goal.**

`docs/README.md`'s non-goals list rules out "speed testing against third-party
servers." A LAN-only throughput test (iperf3-style, against a NAS or another
instance of this app on the same subnet) doesn't touch that non-goal - it stays
within "networks you administer" - and fills a real gap: the app can discover
and ping a host but never says how fast it can actually talk to it.

**Action**: needs a go/no-go decision (it's a new protocol surface, possibly
requiring a companion listener), then scope to LAN-only targets explicitly.

---

## 13. MTU / path MTU discovery tool

**Status: not started.**

A small, natural extension of the existing ICMP engine: sweep DF-flagged echo
payload sizes to find the real path MTU. Directly useful for diagnosing
VPN/PPPoE-related fragmentation issues, which "ping works, some sites don't
load" users hit often and have no good on-device way to diagnose today.

**Action**: add an `IP_DONTFRAG`/DF-bit option to `IcmpSocketEngine` (it
already sets `IP_TTL` the same way) and a binary-search sweep UI similar to
the existing Ping tool.

---

## 14. DNS-over-HTTPS/TLS query support

**Status: not started.**

The DNS tool already supports querying the system resolver or a specific
server over plain UDP 53. Adding DoH/DoT would let a user compare what a
DoH resolver returns against the system resolver - a direct diagnostic for
DNS-based filtering or captive-portal DNS hijacking, which the connection
dashboard already flags as a category of problem (§captive portal detection)
but the DNS tool can't currently help diagnose the cause of.

**Action**: extend `DnsRepository`/`DnsWireCodec` with an HTTPS transport
option; reuse the existing wire-format encoder/decoder over a TLS/HTTPS
connection instead of raw UDP.

---

## 15. Quick-peek reachability check for the Devices tab

**Status: not started.**

The full three-stage sweep (§8.2) takes up to ~21 seconds on a /24, which is
the right trade-off for a full discovery pass but is overkill when a user
just wants to confirm one already-known host is up. A lightweight mode that
pings only `saved_host`/previously-confirmed addresses would answer "is my
server up" in under a second instead of a full re-sweep.

**Action**: add a "quick check" action on the Devices screen that probes only
already-known addresses via the existing ICMP tier, skipping Stage B/C.

---

## 16. Co-channel neighbor count as a glanceable indicator

**Status: not started.**

The channel recommendation algorithm (§7.2) already computes an interference
score per candidate channel. Surfacing "your current channel has N
overlapping networks" as a persistent notification or home-screen widget
would be a cheaper, more passive way to notice growing congestion than
opening the app and re-running the Wi-Fi scan screen.

**Action**: a small widget/notification reading the last computed
recommendation-algorithm score for the currently connected channel; no new
scanning, since passive harvesting (§6.1) already keeps this current.

---

## 17. Home-screen widget for signal strength

**Status: not started.**

The signal meter is driven by `onCapabilitiesChanged` and costs zero scan
budget (§5.1/§9.6), so a widget reading the same stream would be free to run
continuously - a glanceable RSSI/link-speed readout without opening the app,
useful while physically walking around a home to find dead zones.

**Action**: `GlanceAppWidget` bound to the same `ConnectionRepository` flow
the Connection dashboard already uses.

---

## 18. QR-code export/import of saved hosts and WOL targets

**Status: not started.**

`saved_host` and WOL targets already export to CSV/JSON (§10), but moving a
device list to a second phone (e.g., a household with two owned devices, per
this project's own device matrix) currently means manually re-entering hosts
or shuffling files through a file manager. A QR-code export/import keeps the
existing no-accounts, no-cloud-sync posture intact while making multi-device
use materially easier.

**Action**: encode the existing JSON export format into a QR code (chunked if
needed) for the saved-hosts and WOL-target lists specifically.

---

## 19. Automation intents (Shortcuts / Tasker-style)

**Status: not started - power-user niche, lowest priority of this batch.**

Exposing a small set of explicit intents (e.g., "ping this host", "run this
saved port scan") would let power users trigger tools from Android Shortcuts
or Tasker without opening the app. Lower end-user benefit than the rest of
this list since it serves a narrower audience, but cheap to add on top of
existing ViewModels and doesn't imply any accounts, telemetry, or cloud
surface.

**Action**: define a minimal set of `Intent` actions routed through the
existing tool ViewModels; no new business logic required.

---

## 20. Bottom navigation label wraps mid-word at the current six-destination width

**Status: confirmed bug, not started.**

Found running `docs/testing.md`'s new corner-case checklist against a physical device
(Galaxy S21 Ultra, 2026-08-25). With six bottom-nav destinations (Home, Connection, Wi-Fi,
Devices, Tools, Settings), the "Connection" and "Settings" labels wrap mid-word
("Connecti"/"on", "Setting"/"s") in the default portrait compact layout - not just at an
unusual font scale. It reproduces at the system default font scale and gets visibly worse
at a large font scale (1.3x), confirmed via `adb shell settings put system font_scale 1.3`.
Present in both dark and light theme, so it's a layout/sizing issue, not a color-contrast
one.

**Action**: shorten the two longest labels (e.g. "Conn." is a poor fix; consider whether
`NavigationSuiteScaffold` has a two-line label mode, or whether six destinations at
compact width needs `maxLines`/`softWrap` tuning, or a shorter label string) and re-verify
with `scripts/ui-matrix.sh sweep` plus a real-device large-font-scale check before closing.

---

## 21. HTTP inspector leaks a raw exception message for TLS failures

**Status: confirmed bug, not started.**

Found in the same pass as item 20. Inspecting an HTTPS URL whose certificate isn't
trusted (reproduced against a LAN device's own self-signed HTTPS port) surfaces the raw
`java.security.cert.CertPathValidatorException: Trust anchor for certification path not
found.` string, both live on the HTTP inspector screen and persisted verbatim into
Diagnostic history. Every other tool in the app (Ping's TCP-RTT fallback, DNS's
NXDOMAIN/malformed-response split) follows `design.md` §11.3's "degraded modes are named"
convention with a human-readable label instead of a raw stack-trace-style string; HTTP
inspector is the one tool that doesn't.

**Action**: catch `CertPathValidatorException` (and likely `SSLHandshakeException`
generally) in the HTTP inspector's request path and map it to a named degraded-mode
message (e.g. "Certificate not trusted") consistent with how the other tools present
failure, per `docs/testing.md` §5's HTTP inspector row.

---

## Not an open item: MAC address / OUI vendor lookup for LAN hosts

Recorded here only to head off it being re-raised as a gap. This is a permanent,
deliberate architectural constraint (`docs/adr/c-01-arp-table-unreadable.md`),
not unfinished work: Android 10+ blocks reading the ARP table for any unrooted,
non-system app, with no supported workaround. `Host.macAddress` and `Host.vendor`
exist in the model and stay `null` for most hosts by design so a future rooted/privileged
build could populate them without a model change - but that is out of scope for the
current unrooted target, per the "Root support: None" fixed decision in
`docs/README.md`.

The one exception - a real MAC recovered from a NetBIOS NBSTAT response's STATISTICS field
(docs/device-identification-ideas.md A3) - doesn't reopen this: it's an application-layer
payload the app already receives, not an ARP-table or root workaround, and it only covers
hosts that speak NetBIOS. Broader MAC coverage (e.g. a router's UPnP `Hosts:1` service,
docs/device-identification-ideas.md C1) is still a genuinely open idea, not a settled gap.
