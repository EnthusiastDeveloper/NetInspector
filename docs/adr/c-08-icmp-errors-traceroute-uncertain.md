# C-08: Reading ICMP errors for traceroute is uncertain through the `Os` API

Status: Accepted

**Symptom** TTL-exceeded responses from intermediate hops are not delivered to the ping
socket by a plain `recvfrom`.

**Cause** Linux delivers ICMP errors on ping sockets via the socket error queue, which
requires `IP_RECVERR` and `recvfrom(..., MSG_ERRQUEUE, ...)`. Whether the offender address
is reliably surfaced through Android's `android.system.Os` wrapper across OEM kernels is
not something to assume.

**Spike S-02 outcome (Phase 7): Pass.** Verified end to end on the S21 Ultra (Android 15,
One UI 7.0) via the real `TracerouteSocketEngine` (`:data:diagnostics`), not a throwaway
harness - `poll()` on the ICMP datagram socket correctly reports `POLLERR` when a "Time
Exceeded" lands on the error queue, and `recvmsg(..., MSG_ERRQUEUE)` yields a populated
`IPPROTO_IP`/`IP_RECVERR` cmsg whose trailing `sockaddr_in` decodes to the true offending
router at every hop. A live trace to `8.8.8.8` resolved all 8 hops correctly (LAN gateway,
five WAN routers, then the target itself via a normal echo reply - no error-queue entry -
correctly recognised as "reached" and stopping the walk) with per-hop RTTs matching the
route's real latency profile. Tier 1 is the primary engine; no fallback needed on this
device. Neither `IP_RECVERR` nor `MSG_ERRQUEUE` has a named `OsConstants` constant - same
gap as C-18's `IP_RECVTTL` - so both are passed as the raw Linux UAPI ints (`11` and
`0x2000`). **Not yet run on a secondary device**, same caveat as S-01 (C-07): confirm on
the second deployment device before treating this as unconditionally safe everywhere.

**Fallback if the spike fails on another device** TTL walk driven by the system binary:
`/system/bin/ping -c 1 -W 1 -t <ttl> <target>`, parsing the `From <ip> ... Time to live
exceeded` line. Verified against a real toybox capture on the S21 Ultra - the exact phrasing
is `From <ip>: icmp_seq=N Time to live exceeded` (colon directly after the address, no
space) - plus a reference iputils capture without the colon; the parser
(`TracerouteBinaryOutputParser`) matches on the IP address and numeric `icmp_seq` rather
than the surrounding wording, so both variants parse. Unlike a normal reply, this line
carries no `time=` field in either build, so the fallback engine times the RTT from
wall-clock around the whole process instead - less accurate (it includes process spawn
overhead), labelled as such in the UI (design §11.3) whenever this tier is in use.
