# C-02: Wi-Fi scan throttling (Android 9+)

Status: Accepted

**Symptom** `WifiManager.startScan()` returns `false`; results stop updating.

**Cause** Foreground apps are limited to 4 `startScan()` calls per rolling 2-minute
window. Background apps get 1 per 30 minutes.

**Mitigation** The `ScanGovernor` (design §6.1):
- Treat `SCAN_RESULTS_AVAILABLE_ACTION` as the primary data source - it fires for system
  and other-app scans too, giving free updates roughly every 15-30 s.
- Spend active scans only on screen entry and explicit refresh; reserve two tokens.
- Check `WifiManager.isScanThrottleEnabled()` (API 30+) and raise cadence when the user
  has disabled throttling in Developer Options.
- Always display data age and, when throttled, a countdown to the next refresh.

**Do not attempt** to call `setScanThrottleEnabled()` - it requires `NETWORK_SETTINGS`,
a privileged permission unavailable to installed apps.
