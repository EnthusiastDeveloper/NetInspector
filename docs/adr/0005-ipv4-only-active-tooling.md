# ADR-0005: IPv4 only for active tooling; IPv6 is display-only

Status: Accepted

## Context

Home and small-office networks - the app's primary target - are still overwhelmingly
IPv4-addressed for LAN-local traffic, even on networks with IPv6 WAN connectivity. Building
full dual-stack active tooling (sweep ranges, port scanning, traceroute) doubles the
surface area of every probe for a case that's rare in practice on the LAN side.

## Decision

All active tooling - LAN sweep, traceroute, port scanning - operates on IPv4 only. IPv6
addresses are read and displayed where the platform surfaces them (e.g. link properties)
but nothing is actively probed over IPv6.

## Consequences

- Sweep range derivation, timing budgets, and the subnet confirmation gate are all IPv4
  concepts only - no dual-stack branching in the sweep pipeline.
- On an IPv6-only network, LAN discovery has materially less to work with; this is an
  accepted gap, not silently masked - see [C-12](c-12-dhcpinfo-deprecated.md) for how
  link properties are read without assuming a particular address family.
- Extending to active IPv6 tooling later is possible without a redesign - the connection
  model already carries IPv6 addresses - but was deliberately deferred rather than
  half-implemented.
