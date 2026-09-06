# C-20: Private DNS strict mode and a raw UDP/53 socket to an explicit server

Status: Proposed

**Context** The DNS Lookup tool's "used for this lookup" indicator (design §9.4) needs to
say, with confidence, what server a lookup actually queried. For the system-resolver path
(blank server field) that's straightforward: the OS's `DnsResolver` is the one thing Private
DNS is documented to affect, so the indicator deliberately shows no guessed destination there
(see `QueriedDnsServer.SystemResolver`'s doc comment). For the custom-server path
(`DefaultDnsRepository.queryServer`), the tool opens a plain `DatagramSocket` and sends
straight to the address the user typed, on port 53 - the open question is whether Android's
Private DNS **strict mode** (a user-chosen hostname, not "automatic") blocks or redirects that
kind of raw traffic the same way it does for the system resolver, or leaves it alone.

**Evidence** `docs/adr/c-19-private-dns-breaks-reverse-lookup.md` (Accepted) already answers
this for the identical technique: `ReverseDnsProbe.resolveViaGateway` sends a raw UDP/53
packet to an explicit `InetAddress`, and was verified in production to work correctly on a
Galaxy S23 Ultra with Private DNS set to a specific hostname (`base.dns.mullvad.net`) - i.e.
strict mode. `DefaultDnsRepository.queryServer` is mechanically the same operation (a plain
`DatagramSocket.send` to an explicit address:53), just against an arbitrary user-supplied
server instead of the LAN gateway. There is no reason strict mode's netd-level interception
of the system resolver's traffic would distinguish between these two call sites: neither goes
anywhere near `DnsResolver`/`getaddrinfo`, which is specifically what strict mode redirects.

**Decision (pending confirmation)** Treat raw UDP/53 sockets to an explicit address as not
blocked or redirected by Private DNS strict mode, on the strength of C-19's existing
production evidence for the same mechanism. This is not yet a fresh, dedicated on-device
spike (the original request's proposed "Spike S-03") - that confirmation is the real-device
step in `docs/testing.md`'s DNS row (toggle strict mode via `adb shell settings put global
private_dns_mode hostname` + `private_dns_specifier`, run a custom-server lookup, confirm it
still returns real answers rather than a timeout) and has not been run yet as of this ADR.

**Consequences** The "used for this lookup" indicator ships now because it never actually
asserts this conclusion to the user - it reports the literal address:port the raw socket
targeted, which is true regardless of whether strict mode interferes, and a failure mode
(strict mode silently drops the packet) would already surface honestly as a timeout
`DnsQueryOutcome.Error`, not a false success. This ADR exists so that "why do we believe raw
sockets aren't affected" has one written answer instead of being re-derived per feature, and
so the pending on-device confirmation is tracked rather than silently assumed. Once run, this
status should move to Accepted (or the decision revisited) with the result recorded the way
C-08's spike outcome is.
