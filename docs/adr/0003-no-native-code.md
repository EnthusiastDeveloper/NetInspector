# ADR-0003: No NDK, no native code

Status: Accepted

## Context

Several of the app's diagnostic tools (ping, traceroute) would traditionally reach for
raw sockets, which on Android require `CAP_NET_RAW` - unavailable without root, and
usually implemented via native code even when it is available. Bringing in the NDK for
this would double the build/toolchain surface (native debugging, ABI splits, native crash
symbolication) for functionality unprivileged APIs can mostly cover.

## Decision

No NDK, no C/C++ in the app. All networking goes through JVM APIs, including
`android.system.Os` for the low-level socket operations diagnostics need.

## Consequences

- The ICMP engine is built on `Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP)` - unprivileged
  ping-mode datagram sockets - rather than raw sockets in native code. This works because
  Android's init sets `net.ipv4.ping_group_range` permissively; see
  [C-07](c-07-no-raw-sockets.md).
- Reading inbound TTL and ICMP errors off those sockets needs `recvmsg`/ancillary data
  support that `android.system.Os` exposes but doesn't fully name-constant - see
  [C-08](c-08-icmp-errors-traceroute-uncertain.md) and [C-18](c-18-recvttl-no-constant.md).
  Both were solved without native code, at the cost of passing a couple of raw Linux UAPI
  integers where `OsConstants` has no named equivalent.
- No SYN scanning is possible (that needs raw sockets); port scanning uses TCP connect
  and is labelled as such in the UI so results aren't mistaken for a SYN scan.
- The build stays pure-JVM: no ABI splits, no native crash symbolication, no NDK version
  pinning.
