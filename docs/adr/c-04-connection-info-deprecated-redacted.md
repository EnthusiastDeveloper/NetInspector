# C-04: `WifiManager.getConnectionInfo()` is deprecated and redacted (API 31+)

Status: Accepted

**Symptom** SSID reads `<unknown ssid>`, BSSID reads `02:00:00:00:00:00`, even with
`NEARBY_WIFI_DEVICES` granted.

**Cause, corrected** The design's first draft assumed `NEARBY_WIFI_DEVICES` (C-03) was
sufficient to unredact `WifiInfo` obtained via `NetworkCapabilities.getTransportInfo()`.
It is not, and this was caught by testing on a physical device (Phase 1), not by reading
documentation. Traced to AOSP source for the exact OS build installed on the test device
(`android-16.0.0_r1`):

- `WifiInfo.getSSID()`/`getBSSID()` read fields that were redacted per-observer *before*
  the object reached the app - see `WifiInfo.shouldRedactLocationSensitiveFields()`
  ([source](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/tags/android-16.0.0_r1/framework/java/android/net/wifi/WifiInfo.java)),
  gated on the `NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION` bit.
- That bit is cleared only if the receiving app passed `FLAG_INCLUDE_LOCATION_INFO`
  *and* `LocationPermissionChecker.checkCallersLocationPermission()` succeeds - which
  checks `ACCESS_FINE_LOCATION` (or `ACCESS_COARSE_LOCATION` for `targetSdk < 29`, not
  relevant here) *and* that system location mode is enabled
  ([source](https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/tags/android-16.0.0_r1/staticlibs/framework/com/android/net/module/util/LocationPermissionChecker.java)).
  `NEARBY_WIFI_DEVICES` never appears in this check.

In short: `NEARBY_WIFI_DEVICES` unlocks `getScanResults()` (C-03) and nothing else. The
*connected* network's own SSID/BSSID goes through the pre-API-33 location-permission
path unconditionally, on every Android version checked including current (16).

**Consequence for the design** §4's "the app never requests location permission" no
longer holds without qualification. Resolved (see design §4.1a): the app requests
`ACCESS_FINE_LOCATION` for the single purpose of unredacting the dashboard's SSID/BSSID,
with a rationale explaining why, and requests nothing else location-related. AP scanning
(Phase 3) is unaffected and stays on `NEARBY_WIFI_DEVICES` alone.

**Mitigation** Obtain `WifiInfo` from `NetworkCapabilities.getTransportInfo()` inside a
`ConnectivityManager.NetworkCallback` constructed with `FLAG_INCLUDE_LOCATION_INFO`
(API 31+). Unredacting SSID/BSSID additionally needs `ACCESS_FINE_LOCATION` granted and
`LocationManager.isLocationEnabled()` true - both are independent gates and must be
surfaced as distinct states in the UI (permission missing vs. permission granted but
location services off), not collapsed into one "unknown" state.
