# C-11: Broadcast receiver export flag (Android 14+)

Status: Accepted

**Symptom** `SecurityException` when registering a receiver at runtime on API 34+.

**Mitigation** Always pass `Context.RECEIVER_NOT_EXPORTED` when registering the
`SCAN_RESULTS_AVAILABLE_ACTION` receiver. None of this app's receivers should ever be
exported.
