# ADR-0008: Reference network baseline - 192.168.x.0/24

Status: Accepted

## Context

The LAN sweep's timing budget has to be set against some assumed network size. Home and
small-office networks are overwhelmingly /24s in practice; designing the default timing
budget around a much larger enterprise subnet would make the common case slower than it
needs to be for a rare one.

## Decision

192.168.x.0/24 is the reference network the sweep timing budget is tuned against. Larger
subnets are still supported, gated behind a defensive confirmation step (the /22
confirmation gate) rather than swept unconditionally.

## Consequences

- Default sweep timing is tuned for ~254 hosts, not the thousands a /16 could contain -
  keeps the common case fast.
- A user on a genuinely larger subnet sees a confirmation prompt before a sweep that could
  otherwise take an excessive amount of time or trip Wi-Fi scan/multicast throttling; the
  gate is defensive, not a hard cap.
- `ConnectivityManager.getLinkProperties()` is still used to read the real prefix length
  rather than assuming /24 outright - see [C-12](c-12-dhcpinfo-deprecated.md) - so this
  ADR sets the *tuning target*, not a hardcoded assumption baked into the sweep logic.
