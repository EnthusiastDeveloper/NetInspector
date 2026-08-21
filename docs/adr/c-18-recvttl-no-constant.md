# C-18: Reading a received packet's TTL needs `recvmsg`, and `IP_RECVTTL` has no constant

Status: Accepted

**Symptom** Stage C's ICMP-reply TTL fingerprint (design §8.2) needs the *inbound* IP TTL
of an echo reply. `Os.getsockoptInt(fd, IPPROTO_IP, IP_TTL)` only reads the socket's own
outbound TTL setting, not anything about a received packet, and the plain `Os.recvfrom`
used everywhere else in the ICMP sweep has no path to ancillary data at all.

**Cause** The Linux kernel only reports a received packet's TTL as ancillary (`cmsg`)
data attached to a `recvmsg` call, requested in advance via the `IP_RECVTTL` sockopt.
`android.system.OsConstants` exposes `IP_TTL` (the *output* option) but has no constant
for `IP_RECVTTL` at all, even though `Os.recvmsg`/`StructMsghdr`/`StructCmsghdr` - the
machinery needed to consume the cmsg once it arrives - are all present.

**Mitigation** `IP_RECVTTL` is a stable Linux UAPI constant (`12`, defined in
`include/uapi/linux/in.h`, identical across every architecture Android ships on), so
`IcmpSweepProbe` passes the raw int `12` to `setsockoptInt` directly rather than waiting
for a named constant that may never appear. The whole read - enabling the option, then
parsing the `IPPROTO_IP`/`IP_TTL`-tagged `StructCmsghdr` back out of `recvmsg`'s
`msg_control` - is wrapped so that any failure (an OEM kernel rejecting the option, or a
missing cmsg) degrades to "no TTL for this host" rather than losing the RTT reply the
rest of the sweep depends on. Verified on the Galaxy S21 Ultra (One UI 7.0 / Android 15):
`recvmsg` returns a populated `IPPROTO_IP`/`IP_TTL` cmsg for ICMP echo replies, and the
Devices detail pane correctly shows the resulting `DeviceHint` (e.g. "IP TTL 64 (~64) →
Linux/Android/iOS/macOS family") end to end. Still only one device in the matrix - confirm
on the other deployment device before relying on this path everywhere; if `recvmsg` proves
unreliable there, the fallback is dropping the TTL fingerprint and keeping only the
port-signature half of Stage C's `DeviceHint` (design §3), which needs no packet-level
access at all.
