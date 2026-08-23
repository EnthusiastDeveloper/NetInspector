# Architecture Decision Records

This folder records the decisions that shape NetInspector, in the standard
[ADR](https://adr.github.io/) spirit: one immutable file per decision, so the reasoning
behind a choice stays next to the code instead of living only in someone's memory or a
closed PR thread.

Two numbering schemes live side by side here, kept separate deliberately:

| Prefix | Covers | Example |
|---|---|---|
| `ADR-XXXX` | Project-level decisions: distribution, SDK floor, scope | `0001-distribution-github-releases-obtainium.md` |
| `C-XX` | Android platform constraints and their mitigations | `c-06-multicast-filtered-without-lock.md` |

The `C-XX` numbering is **not renumbered into the `ADR-XXXX` sequence**. Those IDs are
cited by ID (not file path) in ~265 code comments across the app (`design §8.2/C-06`,
`C-09`, etc.) - renumbering them would sever that traceability. If you add a new platform
constraint, give it the next `C-XX` number and cite it the same way in the code that works
around it. If you add a new project-level decision, give it the next `ADR-XXXX` number.

New ADRs: copy `template.md`. Status is one of `Proposed`, `Accepted`, `Superseded by
ADR-XXXX`. Once accepted, don't edit a record's Decision/Consequences to reflect a later
change of mind - write a new ADR that supersedes it and link both directions.

## Project-level decisions

| ADR | Decision |
|---|---|
| [0001](0001-distribution-github-releases-obtainium.md) | Distribution via GitHub Releases + Obtainium, no Play Store |
| [0002](0002-sdk-version-floor-and-target.md) | `minSdk` 33, `targetSdk` 35 |
| [0003](0003-no-native-code.md) | No NDK, no native code |
| [0004](0004-no-root-support.md) | No root support |
| [0005](0005-ipv4-only-active-tooling.md) | IPv4 only for active tooling; IPv6 is display-only |
| [0006](0006-priority-order-accuracy-battery-compatibility-speed.md) | Priority order: accuracy > battery > device compatibility > implementation speed |
| [0007](0007-adaptive-layout-from-day-one.md) | Window size classes and fold posture built in from day one |
| [0008](0008-reference-network-baseline.md) | Reference network baseline: 192.168.x.0/24 |

## Platform constraints

See [`../design.md`](../design.md) for the subsystems these constrain, and
[`../open-items.md`](../open-items.md) for constraints still awaiting a second-device
confirmation.

| ID | Constraint |
|---|---|
| [C-01](c-01-arp-table-unreadable.md) | ARP table is unreadable (Android 10+) |
| [C-02](c-02-wifi-scan-throttling.md) | Wi-Fi scan throttling (Android 9+) |
| [C-03](c-03-scan-results-require-location.md) | Scan results require location, not `NEARBY_WIFI_DEVICES` |
| [C-04](c-04-connection-info-deprecated-redacted.md) | `WifiManager.getConnectionInfo()` is deprecated and redacted (API 31+) |
| [C-05](c-05-own-mac-randomised.md) | Own MAC address is randomised and unreadable |
| [C-06](c-06-multicast-filtered-without-lock.md) | Multicast is filtered without a lock |
| [C-07](c-07-no-raw-sockets.md) | No raw sockets |
| [C-08](c-08-icmp-errors-traceroute-uncertain.md) | Reading ICMP errors for traceroute is uncertain through the `Os` API |
| [C-09](c-09-foreground-service-types.md) | Foreground service types (Android 14+) |
| [C-10](c-10-background-execution-and-doze.md) | Background execution and Doze |
| [C-11](c-11-broadcast-receiver-export-flag.md) | Broadcast receiver export flag (Android 14+) |
| [C-12](c-12-dhcpinfo-deprecated.md) | `dhcpInfo` is deprecated and often wrong |
| [C-13](c-13-emulator-cannot-scan-wifi.md) | Emulator cannot scan Wi-Fi |
| [C-14](c-14-oem-divergence.md) | OEM divergence |
| [C-15](c-15-channel-width-320mhz-api34.md) | `CHANNEL_WIDTH_320MHZ` is API 34 |
| [C-16](c-16-wifilock-needs-wake-lock.md) | `WifiManager.WifiLock` needs `WAKE_LOCK`, not a Wi-Fi permission |
| [C-17](c-17-networkcallback-premature-null.md) | A merged `NetworkCallback` flow can emit a premature `null` |
| [C-18](c-18-recvttl-no-constant.md) | Reading a received packet's TTL needs `recvmsg`, and `IP_RECVTTL` has no constant |
| [C-19](c-19-private-dns-breaks-reverse-lookup.md) | Android's Private DNS breaks reverse-DNS for LAN hosts |
