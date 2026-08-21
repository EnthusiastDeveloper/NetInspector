# ADR-0004: No root support

Status: Accepted

## Context

Root access would unlock a materially better LAN discovery experience - a readable ARP
table gives real MAC addresses for every host on the subnet, not just inferred hints. But
designing for an optional root path means every discovery feature needs two implementations
and two sets of tested behavior, and the overwhelming majority of installs will be
unrooted.

## Decision

No root support, on any code path. The app targets unprivileged Android APIs exclusively.

## Consequences

- LAN host MAC addresses are unavailable for any host other than the device itself; see
  [C-01](c-01-arp-table-unreadable.md). Host identification instead relies on mDNS, SSDP,
  NetBIOS, open-port fingerprints, and ICMP reply TTL for OS class - each host gets a
  confidence label rather than a certain identity.
- `Host.macAddress` stays in the domain model as a nullable field so a privileged build
  could populate it later without a schema change, but nothing in the shipped app expects
  it to be non-null.
- No raw `AF_PACKET` sockets, no `ip neigh` via shell, no privileged `LOCAL_MAC_ADDRESS` -
  none of the root-only shortcuts get half-built as fallbacks.
- This keeps the single-codebase promise: one implementation, tested the way the vast
  majority of users will actually run it.
