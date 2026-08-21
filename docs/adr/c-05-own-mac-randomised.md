# C-05: Own MAC address is randomised and unreadable

Status: Accepted

**Symptom** `WifiInfo.getMacAddress()` returns `02:00:00:00:00:00`.

**Cause** Per-SSID MAC randomisation (default since Android 10); the real address needs
the privileged `LOCAL_MAC_ADDRESS` permission.

**Mitigation** Do not display or depend on the device's own MAC. Where a MAC is genuinely
required - Wake-on-LAN targets - the user enters it manually and it is saved.
