# C-16: `WifiManager.WifiLock` needs `WAKE_LOCK`, not a Wi-Fi permission

Status: Accepted

**Symptom** `WifiLock.acquire()` throws `SecurityException: Neither user … nor current
process has android.permission.WAKE_LOCK` - a runtime crash, not a lint or compile error,
since `WifiLock` is declared in `android.net.wifi` and every other permission the LAN
sweep needs (`CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`) is a Wi-Fi permission,
which is what the manifest had until this was caught on-device (Phase 5 verification,
Galaxy S21 Ultra).

**Cause** `WifiLock` is a thin wrapper around the same underlying wake-lock mechanism as
`PowerManager.WakeLock`, so it needs the general-purpose `WAKE_LOCK` permission
(install-time, no runtime prompt) despite living in the Wi-Fi package.

**Mitigation** `<uses-permission android:name="android.permission.WAKE_LOCK" />` alongside
the other Wi-Fi permissions. Caught only by running the actual sweep on a physical device
- the emulator (C-13) can't exercise this at all, and nothing at compile or install time
flags a missing `WAKE_LOCK` declaration.
