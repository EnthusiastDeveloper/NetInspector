# C-06: Multicast is filtered without a lock

Status: Accepted

**Symptom** mDNS and SSDP discovery return nothing, on a network where other tools find
plenty.

**Cause** The Wi-Fi driver drops inbound multicast to save power unless a
`WifiManager.MulticastLock` is held.

**Mitigation** Acquire the lock before Stage A probes and release it in a `finally`
block. Requires `CHANGE_WIFI_MULTICAST_STATE` (install-time, no runtime prompt). A leaked
lock is a significant battery drain, so the lock is owned by the discovery scope and never
by a long-lived singleton.
