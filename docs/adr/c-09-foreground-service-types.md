# C-09: Foreground service types (Android 14+)

Status: Accepted

**Symptom** `startForeground()` throws `MissingForegroundServiceTypeException` or a
`SecurityException` on API 34+.

**Mitigation** Declare `android:foregroundServiceType="connectedDevice"` in the manifest
and pass the matching constant to `startForeground()`. The `connectedDevice` type is
satisfied by `CHANGE_WIFI_STATE`, which the app already holds, and requires
`FOREGROUND_SERVICE_CONNECTED_DEVICE` to be declared. Also request `POST_NOTIFICATIONS`
at runtime (API 33+) or the service notification is silently suppressed.
