# C-15: `CHANNEL_WIDTH_320MHZ` is API 34

Status: Accepted

See also [ADR-0002](0002-sdk-version-floor-and-target.md).

**Symptom** Compile failure, or Wi-Fi 7 access points reported with an unknown channel
width.

**Cause** The Wi-Fi 7 width constant was added in API 34, one level above this `minSdk`.

**Mitigation** Compare `ScanResult.channelWidth` against the raw integer `5` rather than
referencing the constant. This is the **only remaining version-gated branch in the app**
- worth keeping the comment next to it saying so, since it will otherwise look arbitrary
to whoever reads it next.

**Resolved by the API 33 floor** `ScanResult.getSecurityTypes()` is API 33+ and is now
unconditional. The `capabilities`-string parser this constraint previously required is
deleted, along with the two bugs it invited: WPA2/WPA3 transition networks mislabelled as
WPA2, and OWE networks mislabelled as Open.
