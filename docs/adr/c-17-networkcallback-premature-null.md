# C-17: A merged `NetworkCallback` flow can emit a premature `null`

Status: Accepted

**Symptom** A one-shot `Flow<ConnectionSnapshot?>.first()` intermittently returns `null`
for an already-connected network, even though the same flow displays correctly when
collected continuously (Connection dashboard, Phase 1).

**Cause** `ConnectivityDataSource.connectionSnapshots()` merges two independent
`NetworkCallback` methods - `onCapabilitiesChanged` and `onLinkPropertiesChanged` - into
one snapshot, emitting `null` whenever it has one half of the pair but not the other yet.
That's invisible to a continuously-observed `StateFlow` (the real snapshot arrives a
moment later and the UI just updates again), but a `.first()` call racing the two
callbacks can genuinely catch the intermediate `null` as its one and only emission.

**Mitigation** Any one-shot read of this flow (the LAN sweep needs a subnet exactly once
per sweep, not continuously) should wait for a specific non-null field rather than any
non-null value: `connectionSnapshot.first { it?.ipv4 != null }`. Caught during Phase 5
on-device verification - the sweep silently no-opped instead of crashing, which made it
easy to miss without diagnostic logging at each step.
