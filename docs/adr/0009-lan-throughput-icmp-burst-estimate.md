# ADR-0009: LAN throughput test uses an ICMP echo burst, not a transfer endpoint

Status: Accepted

## Context

`docs/improvement-ideas.md` #31 originally scoped a "speed test integration": throughput
measurement correlated against RSSI/channel, using a self-hosted transfer endpoint as the
data source. That framing has two problems against this app's own positioning:

- [ADR-0001](0001-distribution-github-releases-obtainium.md)'s no-accounts,
  no-third-party-service stance rules out a third-party speed-test API outright.
- A *self-hosted* endpoint still means infrastructure the app's user doesn't have and this
  project doesn't operate - a server component, somewhere, that has to be reachable from the
  phone. That's a materially different commitment than everything else this app does, which
  talks only to hosts already on the user's own LAN (design §8, §9) with no server-side
  component anywhere.

Meanwhile the app already has, and this feature can reuse without adding anything new to the
trust surface: unprivileged ICMP echo sockets (design §9.1) that work against *any* host that
answers ping - which on a home LAN is effectively every device, no server software required
on the target at all.

## Decision

The LAN throughput test estimates round-trip throughput by running several concurrent
worker sockets (`IcmpSocketEngine.openSocket`/`probeOnSocket`, `LanThroughputRepository`),
each pipelining near-MTU-sized ICMP echo requests back-to-back against the chosen host for a
fixed duration. The observed rate - `(request bytes + reply bytes) x successful probes /
elapsed time` - is reported as an estimated Mbps figure, alongside packet loss and a peak
sample.

This is explicitly a **round-trip** estimate, not a one-directional download or upload
number the way speedtest.net-style tools report one, and the UI says so: the tool is named
"LAN throughput test" (never bare "Speed test"), states in its own copy that it measures a
device on the local network rather than the internet connection, and its result is captured
alongside a `WifiScanRepository`/`ConnectionRepository` snapshot (SSID, RSSI, channel,
channel-width, count of other APs sharing the channel) so a low number can be read against
*why* - the correlation improvement-ideas.md #31 actually wanted - rather than reported as an
unexplained score.

No transfer-endpoint mechanism (self-hosted or otherwise) was built. There is correspondingly
no tier-2/3 fallback the way `PingRepository` has one for ICMP: a device where unprivileged
ICMP sockets aren't supported (design §9.1's capability check) gets a clear "not supported"
result rather than a TCP-connect substitute, because a bare TCP connect transfers no data to
time and would silently under-report.

## Consequences

- No new infrastructure, no new third-party dependency, no server component anywhere - the
  decision this ADR is really about is staying inside the constraint the earlier framing
  broke.
- The reported number is a real but rough estimate: it reflects both directions combined, is
  bounded by how many worker sockets and how much CPU the test can spend pipelining probes
  rather than by genuinely saturating the link the way a purpose-built transfer would, and
  says nothing about sustained large-file transfer behaviour (TCP slow start, congestion
  control) since ICMP echo sockets carry none of that. This is a known, accepted gap - the
  UI's own copy exists specifically so it's never mistaken for an internet-speed-test-grade
  measurement.
- Any host that blocks ICMP (some hardened IoT devices, a host with a host-based firewall)
  reads as "not supported" or near-zero throughput, not distinguished from "genuinely slow
  link" - same class of ambiguity `IcmpSweepProbe`/`TcpSweepProbe` already live with
  elsewhere in this codebase.
- If a genuine one-directional bandwidth measurement is wanted later, it still needs
  something to transfer real payload against - this ADR doesn't rule that out, it just
  records why it wasn't the answer here, and that revisiting it means confronting the same
  infrastructure question again.
