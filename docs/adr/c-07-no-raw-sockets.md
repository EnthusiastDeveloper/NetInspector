# C-07: No raw sockets

Status: Accepted

See also [ADR-0003](0003-no-native-code.md).

**Symptom** `SOCK_RAW` fails with `EPERM`.

**Cause** Raw sockets require `CAP_NET_RAW`.

**Impact** No SYN scanning, no ARP frames, no custom IP headers, no classic UDP-based
traceroute with hand-set TTL through a raw socket.

**Mitigation** Unprivileged ICMP **datagram** sockets are permitted:
`Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP)`, because Android's init sets
`net.ipv4.ping_group_range` permissively. This covers ping and TTL-walk traceroute
without root or native code. Port scanning uses TCP connect and is labelled as such in
the UI so results are not mistaken for a SYN scan.

**Caveat** On ping sockets the kernel rewrites the ICMP identifier and recomputes the
checksum. Match replies on sequence number and source address, not on your own identifier.

**Spike S-01 outcome (Phase 2): Pass.** Verified on the primary deployment device (Samsung
Galaxy S23 Ultra, Android 16 / API 36, OneUI 8.5) via the real `IcmpSocketEngine`
(`:data:diagnostics`), not a throwaway harness - round-tripped both the LAN gateway and a
public host (8.8.8.8) with RTTs matching a concurrent terminal `ping` within normal Wi-Fi
jitter. Tier 1 is the primary engine; no fallback needed on this device. **Not yet run on a
secondary device** - the implementation plan's device matrix calls for confirming on both
deployment devices since kernel behaviour around ping sockets can differ, so treat this as
provisionally passed pending that second run.

One real transient failure observed and diagnosed during testing: a single ping run to
8.8.8.8 returned 100% loss while a terminal `ping` to the same host succeeded seconds
before and after. Likely cause: this device has both Wi-Fi and cellular data active
simultaneously with automatic network switching (Samsung's Wi-Fi/mobile-data hand-off),
and `IcmpSocketEngine` doesn't bind its socket to a specific `Network` - an unbound socket
follows whatever Android's process-default network happens to be at send time, and mobile
carriers commonly filter or rate-limit outbound ICMP. A retry immediately succeeded. Not
fixed here - Phase 5's "Pass 2: re-probe every non-responder once" already covers exactly
this failure mode for the LAN sweep, and it's an argument for ping supporting a
manually-triggered re-run rather than a code change to this engine; revisit if it recurs.
