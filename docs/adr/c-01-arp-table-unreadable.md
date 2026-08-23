# C-01: ARP table is unreadable (Android 10+)

Status: Accepted

Assumes `minSdk 33`, `targetSdk 35`, unrooted, no NDK (see [ADR-0002](0002-sdk-version-floor-and-target.md), [ADR-0003](0003-no-native-code.md), [ADR-0004](0004-no-root-support.md)).

**Symptom** `/proc/net/arp` reads as empty or throws; no MAC addresses for LAN peers.

**Cause** SELinux policy introduced in Android 10 blocks untrusted apps from reading
`/proc/net/*`. There is no API replacement, and none is planned.

**Impact** No MAC addresses, no OUI vendor lookup, and no ARP-based host discovery for
any host other than this device.

**Mitigation** Identify hosts from service discovery and behaviour instead: mDNS, SSDP,
NetBIOS, open-port fingerprints, and ICMP reply TTL for OS class. `Host.macAddress`
remains in the model as a nullable field so a privileged build could populate it later.
The UI omits the field rather than showing it empty.

**Narrow exception (docs/device-identification-ideas.md A3)** A host that answers a NetBIOS
NBSTAT query carries its real MAC in the response's STATISTICS field (RFC 1002 §4.2.18) - an
application-layer payload, not the blocked ARP table - so `NetBiosProbe` populates
`Host.macAddress` for those hosts specifically. This doesn't reopen the constraint generally:
it only covers hosts that speak NetBIOS (mostly Windows/Samba, some NAS/print servers).

**Do not attempt** raw `AF_PACKET` sockets (needs `CAP_NET_RAW`), `ip neigh` via shell
(needs root), or `NetworkInterface.getHardwareAddress()` on foreign interfaces (returns
null by design).
