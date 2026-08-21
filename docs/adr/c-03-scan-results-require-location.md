# C-03: Scan results require location, not `NEARBY_WIFI_DEVICES`

Status: Accepted

**Symptom** `getScanResults()`/`startScan()` return an empty list / `false` with no
exception and no error - or, if `NEARBY_WIFI_DEVICES` is granted but location isn't,
they silently succeed anyway, which is what actually exposed this entry.

**Cause, corrected** The design's first draft assumed `NEARBY_WIFI_DEVICES` was the
`minSdk`-33+ gate for `getScanResults()`/`startScan()`. It is not, and this was caught
by testing on a physical device (Phase 3) with `NEARBY_WIFI_DEVICES` denied and
`ACCESS_FINE_LOCATION` granted (from the C-04 dashboard flow): the scan list populated
fully anyway. Traced to AOSP source for the exact OS build installed on the test device
(`android-16.0.0_r1`):

- Both `WifiServiceImpl.getScanResults()` and `.startScan()`
  ([source](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/tags/android-16.0.0_r1/service/java/com/android/server/wifi/WifiServiceImpl.java))
  call `WifiPermissionsUtil.enforceCanAccessScanResults()`, not any
  `NEARBY_WIFI_DEVICES`-checking method.
- `enforceCanAccessScanResults()`
  ([source](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/tags/android-16.0.0_r1/service/java/com/android/server/wifi/util/WifiPermissionsUtil.java))
  requires `LocationManager`-equivalent location mode to be enabled, then
  `checkCallersLocationPermission()` (fine, or coarse only if `targetSdk < Q` - not
  relevant here), then the `OPSTR_WIFI_SCAN` app-op. `NEARBY_WIFI_DEVICES` never
  appears in this path; it gates a separate, newer method
  (`enforceNearbyDevicesPermission`) used by different APIs this app doesn't call.

In short: this app's scan results go through the *exact same* location-permission gate
as C-04's connected-network `WifiInfo` - `ACCESS_FINE_LOCATION` plus system location
mode enabled. `NEARBY_WIFI_DEVICES` gates nothing this app actually uses and is not
declared.

**Mitigation** Check `ACCESS_FINE_LOCATION` + `LocationManager.isLocationEnabled()`
before scanning, identically to the dashboard's location-access flow (design §4.1a) -
the Wi-Fi screen's permission card is the same rationale/request flow, just scoped to
this screen. Because an empty list carries no error on its own, still always check
permission/location state before interpreting an empty result as "no networks nearby".
