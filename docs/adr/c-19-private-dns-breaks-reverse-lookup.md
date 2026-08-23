# C-19: Android's Private DNS breaks reverse-DNS for LAN hosts

Status: Accepted

**Symptom** With Android's "Private DNS" setting (Settings → Connections → More connection
settings → Private DNS) set to a specific hostname, every host in a LAN sweep loses its
reverse-DNS hostname and falls back to the coarse TTL/port `DeviceHint` guess - reproduced on
a Galaxy S23 Ultra with Private DNS pointed at `base.dns.mullvad.net`, versus a Galaxy S21
Ultra with it off (`private_dns_mode=null`), on the same network. On a network whose router
serves `hostname.lan` PTR records for DHCP leases (the common case, e.g. OpenWrt's dnsmasq),
reverse DNS turned out to be the *dominant* naming source in practice - more than mDNS, SSDP,
or NetBIOS combined - so losing it degrades every host's display name at once, not just a
few.

**Cause** `ReverseDnsProbe.resolve` (design §8.2 Stage C) uses `android.net.DnsResolver`,
which - like `InetAddress` and every other standard resolution API - honors Private DNS at
the OS/netd level, deliberately with no per-app opt-out (that's the point of the setting: a
user with Private DNS on expects *every* DNS query, from every app, to go through their
chosen encrypted resolver). Once Private DNS is active, a PTR query for a private-range
address like `50.8.168.192.in-addr.arpa` gets sent to that external resolver, which has no
route to or records for the querying device's own LAN - it doesn't even see the same address
space - so it always comes back empty or NXDOMAIN, regardless of what DNS server the LAN
itself provides.

**Mitigation** `ReverseDnsProbe.resolveViaGateway` sends the exact same `DnsPtrQuery`-encoded
question directly to the LAN gateway over a raw UDP socket, bypassing the system resolver (and
Private DNS with it) entirely - the same way `NetBiosProbe`/`SsdpProbe` already talk to the
LAN directly rather than through a platform convenience API. `HostEnricher` only reaches for
this once both system-resolver attempts have come back empty, so it never overrides a real
answer and adds no extra latency in the common case. This is a deliberate, narrow privacy
trade-off: it's local-network-only traffic (nothing leaves the LAN, and the address being
looked up is inherently visible to anyone on that LAN regardless), but it does mean the app
takes one extra concrete step around a setting the user turned on specifically to keep DNS off
a path they didn't choose - worth stating plainly rather than leaving implicit. It's also not
a complete fix: a gateway that isn't itself running a DNS server (e.g. a network using a
separate Pi-hole/NAS as the DNS server) yields nothing here either, same as a host with no PTR
record at all.
