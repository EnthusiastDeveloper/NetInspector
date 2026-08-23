# NetInspector - Technical Design

Version 1.1 · Target: Android 13 (API 33) through Android 15 (API 35) · Kotlin / Compose

---

## 1. Overview

NetInspector is a single-user, offline Android application with three capability groups:

1. **Radio environment** - enumerate visible access points, their bands, channels,
   channel widths, security, standards and signal strength; render channel occupancy
   and recommend a clear channel.
2. **Local network** - enumerate reachable hosts on the connected subnet, identify what
   they are, and expose per-host detail.
3. **Diagnostics** - ping, traceroute, DNS lookup, port scan, Wake-on-LAN, WHOIS,
   HTTP header inspection, subnet calculator, signal meter.

All processing is on-device. No network egress beyond the diagnostic operations the user
explicitly triggers, plus WHOIS (TCP 43) which by definition contacts an external server.

### 1.1 The shape of the problem

Nearly every difficulty in this app is a platform restriction rather than an algorithmic
one. Android has progressively locked down exactly the primitives a network analyser
wants: the ARP table, raw sockets, unthrottled Wi-Fi scanning, MAC addresses and
`/proc/net`. The design is therefore organised around a **constraint register**
(`adr/`, one file per constraint, IDs `C-01`-`C-18`) and a set of **tiered strategies** - each capability has a
preferred implementation, a fallback, and a defined degraded behaviour that is shown
honestly in the UI rather than papered over.

The `accuracy > battery > compatibility > speed` priority order resolves the trade-offs
these tiers create.

---

## 2. Architecture

### 2.1 Module graph

```
:app                    Compose UI, navigation, DI wiring, foreground service
 ├── :core:model        Pure Kotlin domain types, no Android imports
 ├── :core:common       Dispatchers, Result types, IP/subnet math, hex/checksum utils
 ├── :core:designsystem Theme, typography, reusable composables, chart primitives
 ├── :data:wifi         WifiManager, ConnectivityManager, scan throttle governor
 ├── :data:lan          Host discovery pipeline, mDNS/SSDP/NetBIOS probes
 ├── :data:diagnostics  ICMP engine, traceroute, DNS, port scan, WOL, WHOIS
 └── :data:persistence  Room database, DAOs, OUI lookup, DataStore preferences
```

Dependency rules, enforced by a Gradle convention plugin:

- `:core:model` depends on nothing. No `android.*` imports. Fully unit-testable on the JVM.
- `:data:*` modules may depend on `:core:model` and `:core:common`, never on each other.
- `:app` depends on everything; nothing depends on `:app`.
- Repositories return `Flow<T>` or `Result<T>`. They never expose `ScanResult`,
  `NetworkCapabilities` or any other framework type across a module boundary.

Rationale for splitting `:data` three ways rather than one: the three subsystems have
genuinely different threading models, failure modes and permission requirements, and the
LAN pipeline is the one most likely to be rewritten.

### 2.2 Layering

`Framework API → Data source → Repository → Use case → ViewModel → Composable`

Use cases exist only where logic spans repositories (e.g. *recommend a channel* needs
scan results plus the current connection; *discover hosts* needs the subnet from
connectivity plus the probe engines). Elsewhere ViewModels call repositories directly -
a pass-through use case is noise.

### 2.3 State and events

- Continuous observations (connection state, scan results, discovered hosts, RSSI) are
  **cold `Flow`s** in the data layer, converted with `stateIn(WhileSubscribed(5_000))` in
  ViewModels. The 5-second grace period prevents rebinding storms on rotation without
  keeping radios awake.
- One-shot operations (a traceroute run, a port scan) are **suspend functions returning a
  `Flow` of incremental results**, so partial output streams to the UI. A traceroute that
  dies at hop 12 must still show hops 1-11.
- User-visible errors are modelled as domain types (`ProbeOutcome.Unreachable`,
  `ScanOutcome.Throttled(retryAt)`), never as thrown exceptions crossing the UI boundary.

### 2.4 Dependency injection

Hilt. Scopes: `@Singleton` for repositories and the Room database, `@ViewModelScoped` for
per-screen coordinators. The `CoroutineScope` used by long-running collectors is provided
as a `@Singleton` `@ApplicationScope` with `SupervisorJob() + Dispatchers.Default`, so a
crashing probe does not tear down unrelated collectors.

---

## 3. Domain model

Defined in `:core:model`. Abbreviated to the fields that carry design decisions.

```kotlin
// ---------- Wi-Fi environment ----------

enum class Band { GHZ_2_4, GHZ_5, GHZ_6, UNKNOWN }

data class ChannelSpan(
    val centerMhz: Int,
    val widthMhz: Int,          // 20, 40, 80, 160, 320
    val primaryChannel: Int,
    val band: Band,
) {
    val lowMhz get() = centerMhz - widthMhz / 2
    val highMhz get() = centerMhz + widthMhz / 2
}

enum class SecurityType { OPEN, OWE, WEP, WPA, WPA2, WPA3, WPA2_WPA3, EAP, UNKNOWN }

enum class WifiStandard { LEGACY, N, AC, AX, BE, UNKNOWN }

data class AccessPoint(
    val bssid: String,                  // always available from scan results
    val ssid: String?,                  // null when hidden
    val rssiDbm: Int,
    val span: ChannelSpan,
    val secondarySpan: ChannelSpan?,    // 80+80 MHz only
    val security: Set<SecurityType>,
    val standard: WifiStandard,
    val vendor: String?,                // OUI lookup on BSSID - see §7.5
    val isConnected: Boolean,
    val is6GhzPsc: Boolean,
    val isDfsChannel: Boolean,
    val lastSeen: Instant,
    val firstSeen: Instant,
)

// ---------- Current connection ----------

data class ConnectionSnapshot(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val txLinkSpeedMbps: Int?,
    val rxLinkSpeedMbps: Int?,
    val span: ChannelSpan?,
    val standard: WifiStandard,
    val ipv4: LinkAddressInfo?,
    val ipv6: List<LinkAddressInfo>,    // display only
    val gateway: Inet4Address?,
    val dnsServers: List<InetAddress>,
    val domains: String?,
    val hasInternet: Boolean,           // NET_CAPABILITY_VALIDATED
    val isCaptivePortal: Boolean,
    val isMetered: Boolean,
)

// ---------- LAN hosts ----------

enum class EvidenceSource { ICMP, TCP_CONNECT, MDNS, SSDP, NETBIOS, REVERSE_DNS, GATEWAY, SELF }

enum class HostConfidence {
    CONFIRMED,   // responded directly to at least one of our probes
    ANNOUNCED,   // advertised itself via mDNS/SSDP but never answered a probe
    STALE,       // seen in a previous sweep, absent from the current one
}

data class Evidence(
    val source: EvidenceSource,
    val observedAt: Instant,
    val detail: String? = null,
)

data class OpenPort(val port: Int, val serviceGuess: String?, val banner: String?)

data class Host(
    val address: Inet4Address,
    val confidence: HostConfidence,
    val evidence: List<Evidence>,
    val hostnames: Map<EvidenceSource, String>,   // provenance-tagged, never merged blindly
    val macAddress: String?,                      // null in practice - see C-01
    val vendor: String?,
    val deviceHint: DeviceHint?,                  // inferred class, always with a reason
    val openPorts: List<OpenPort>,
    val services: List<DiscoveredService>,        // mDNS/SSDP records
    val icmpReplyTtl: Int?,                       // OS-class fingerprint input
    val rttMedianMs: Double?,
    val isGateway: Boolean,
    val isSelf: Boolean,
)

data class DeviceHint(val label: String, val basis: String, val certainty: Certainty)
enum class Certainty { LIKELY, POSSIBLE }
```

Two conventions in the model that exist specifically to serve the accuracy priority:

- **`hostnames` is a map keyed by source, not a single string.** mDNS, NetBIOS and
  reverse DNS routinely disagree. The UI shows the highest-priority one but the detail
  screen lists all of them with their origin.
- **`DeviceHint` carries its own `basis` string.** Nothing in the UI ever asserts "this
  is a Samsung TV" without being able to say why ("SSDP `friendlyName` header").

---

## 4. Permission model

`minSdk 33` does **not** remove the location-permission branch for Wi-Fi info the way
this design originally assumed. Originally drafted as "the app never requests location
permission at all," then revised to "except for the connected network's SSID/BSSID"
after Phase 1 testing - and revised again here after Phase 3 testing showed the
*AP scan list* goes through the identical location gate, not `NEARBY_WIFI_DEVICES` as
first assumed. Two separate corrections, two separate device-testing sessions, the same
root cause both times: trusting an assumption about which permission an API checks
instead of reading the platform source. See C-04 and C-03 for the full AOSP trails.

```xml
<!-- Required on every supported level -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

<!-- Unredacts the connected network's own SSID/BSSID (C-04) AND gates the AP scan
     list (C-03) - the same permission covers both, contrary to this design's first two
     drafts. Requested lazily, the first time either screen needs it, not at first launch. -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Optional continuous monitoring service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**`NEARBY_WIFI_DEVICES` is not declared.** It was in the first two drafts of this
manifest, on the theory that it gated `getScanResults()`/`startScan()`. Phase 3 device
testing (permission denied, scan list populated anyway) and the AOSP source for
`WifiPermissionsUtil.enforceCanAccessScanResults()` both confirm it gates neither of the
scan APIs this app calls - see C-03. It may still turn out to matter for the mDNS/SSDP
multicast probes (Phase 4/5, not yet built); that claim from the original draft hasn't
been re-verified and should be checked against source before being trusted, the same way
this entry was.

`POST_NOTIFICATIONS` is a runtime permission at every supported level and is requested
only when the user starts the optional monitoring service.

`CHANGE_WIFI_MULTICAST_STATE` is a normal (install-time) permission and is mandatory -
without a held `MulticastLock` the Wi-Fi driver filters inbound multicast and mDNS/SSDP
discovery silently returns nothing. This is one of the most common causes of "device
discovery finds only the gateway".

**Permission gating is per-capability, not global.** All diagnostic tools and the LAN
unicast sweep need none of the above beyond `ACCESS_NETWORK_STATE`/`INTERNET`. The
connection dashboard works fully without `ACCESS_FINE_LOCATION` - SSID/BSSID are the
only two fields that need it, and only to reveal those two fields; the rest of the
dashboard (band, channel, link speed, IPv4/IPv6, gateway, DNS,
validated/captive-portal/metered) needs nothing beyond `ACCESS_NETWORK_STATE`. The Wi-Fi
scan screen needs it for the whole AP list, not just individual fields, since the
permission gates `getScanResults()` itself. A user who denies it still gets a fully
working dashboard (minus two fields), ping, traceroute and host sweep - just not the
AP scanner.

### 4.1 Requesting flow (SSID/BSSID and AP scanning - the same flow)

Deliberately **not** part of first-run - requested lazily the first time either screen
would otherwise show `<permission required>` or an empty AP list, with an inline card
(not a blocking dialog) explaining why an OS-level Wi-Fi restriction needs a location
permission for a feature that has nothing to do with location. Two independent failure
states, both surfaced distinctly rather than collapsed into one "unknown":

1. **Permission not granted** - inline card with a rationale and a "Grant location
   access" button. `shouldShowRequestPermissionRationale` returning `false` after a
   request means permanent denial; the button becomes "Open app settings" instead of
   re-requesting (which would silently no-op).
2. **Permission granted, location services off** - a different message ("turn on
   location services") with a button deep-linking to
   `Settings.ACTION_LOCATION_SOURCE_SETTINGS`, not the permission dialog.

Both checks are re-evaluated on `ON_RESUME`, since granting via the system Settings app
(rather than the in-app prompt) doesn't fire the permission-result callback. The
dashboard and the Wi-Fi screen each run this flow independently (separate cards, separate
`ON_RESUME` checks) even though they end up checking the same permission - a grant on one
screen unblocks the other the next time its own check runs, but there's no cross-screen
signal that forces an immediate refresh.

---

## 5. Connectivity and the current network

`WifiManager.getConnectionInfo()` is deprecated as of API 31 and returns redacted values.
The only correct source is `ConnectivityManager`.

```kotlin
val request = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .build()

val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
        val wifiInfo = caps.transportInfo as? WifiInfo ?: return
        // ssid, bssid, rssi, linkSpeed, rxLinkSpeedMbps, frequency, wifiStandard
    }
    override fun onLinkPropertiesChanged(network: Network, props: LinkProperties) {
        // linkAddresses -> IP + prefix length; routes -> gateway; dnsServers; domains
    }
    override fun onLost(network: Network) { /* clear snapshot */ }
}
```

Three details that are easy to get wrong:

- **`FLAG_INCLUDE_LOCATION_INFO` (API 31+) is mandatory.** Without it `ssid` is
  `<unknown ssid>` and `bssid` is `02:00:00:00:00:00`. The flag is only honoured if the
  app **additionally** holds `ACCESS_FINE_LOCATION` with system location mode enabled -
  not `NEARBY_WIFI_DEVICES`, which this design originally (and incorrectly) assumed was
  sufficient. See C-04 for the AOSP source trail and §4.1 for the request flow.
- **The gateway comes from `LinkProperties.routes`**, by finding the route where
  `isDefaultRoute` is true and reading its gateway. `WifiManager.dhcpInfo` is deprecated,
  IPv4-only, and wrong on networks using non-DHCP configuration.
- **The subnet prefix comes from `linkAddresses`**, not from assuming /24. Assuming /24
  is the single most common bug in this app category and produces both missed hosts on
  /23 networks and pointless scanning on /28 networks.

`NET_CAPABILITY_VALIDATED` distinguishes "connected" from "actually has working
internet"; `NET_CAPABILITY_CAPTIVE_PORTAL` detects a portal. Both are surfaced on the
dashboard because they explain the majority of "Wi-Fi says connected but nothing works"
situations.

### 5.1 RSSI stream

The signal meter is driven by `onCapabilitiesChanged`, which fires on RSSI change for the
connected network. This costs **zero scan budget** and negligible battery, and is more
frequent and more accurate than deriving RSSI from scan results. Scan results are used
only for *other* networks.

---

## 6. Wi-Fi scanning

### 6.1 The throttle governor

`WifiManager.startScan()` is deprecated but has no replacement for third-party apps. It
is rate-limited to **4 calls per 2-minute rolling window** in the foreground (1 per 30
minutes in the background). Exceeding it makes `startScan()` return `false` with no
further signal.

The `ScanGovernor` is a singleton that owns all scanning:

```kotlin
class ScanGovernor(
    private val wifiManager: WifiManager,
    private val clock: Clock,
) {
    private val window = ArrayDeque<Instant>()   // timestamps of our own startScan() calls
    private val quota = 4
    private val windowLength = 2.minutes

    fun budget(): ScanBudget           // remaining calls + instant of next availability
    suspend fun requestScan(): ScanOutcome
    val results: Flow<ScanSnapshot>    // driven by broadcast, not by our requests
}
```

Behaviours:

- **Passive harvesting is the primary source.** A receiver for
  `SCAN_RESULTS_AVAILABLE_ACTION` consumes every scan completion, including scans
  triggered by the system or other apps. On a normally-behaving device this yields
  updates roughly every 15-30 seconds for free. `results` emits from the broadcast, never
  from a `startScan()` return value.
- **Active scans are spent deliberately.** One on screen entry, one on explicit pull-to-
  refresh, and none otherwise. Two of the four tokens are always held in reserve for user-
  initiated refreshes.
- **`WifiManager.isScanThrottleEnabled()` (API 30+) is checked at startup.** If the user
  has disabled throttling in Developer Options, the governor raises its cadence to one
  scan every 5 seconds while the scanner screen is foregrounded. This is the single
  largest accuracy improvement available and costs nothing to support.
- **The UI always shows result age.** Every AP row carries a "last seen" and the screen
  header shows "results as of HH:MM:SS" plus, when throttled, a countdown to the next
  available refresh. Stale data presented as live is an accuracy failure.
- **Registration is lifecycle-scoped.** The receiver is registered on `STARTED` and
  unregistered on `STOPPED`, with `RECEIVER_NOT_EXPORTED` (mandatory on API 34+).

### 6.2 Parsing a `ScanResult`

**Frequency to channel.** Covers 2.4 GHz, 5 GHz and 6 GHz including the two special cases
that trip up naive implementations (channel 14, and 6 GHz channel 2 at 5935 MHz):

```kotlin
fun freqToChannel(mhz: Int): Int? = when (mhz) {
    2484 -> 14
    in 2412..2472 -> (mhz - 2407) / 5
    in 5160..5895 -> (mhz - 5000) / 5
    5935 -> 2
    in 5955..7115 -> (mhz - 5950) / 5
    else -> null
}

fun bandOf(mhz: Int): Band = when (mhz) {
    in 2400..2500 -> Band.GHZ_2_4
    in 5150..5900 -> Band.GHZ_5
    in 5925..7125 -> Band.GHZ_6
    else -> Band.UNKNOWN
}
```

**Channel span.** `ScanResult.channelWidth` gives the width constant; `centerFreq0` gives
the true centre for 40/80/160 MHz allocations (which is *not* the primary channel
frequency); `centerFreq1` is populated only for 80+80 MHz. Width constant 5
(`CHANNEL_WIDTH_320MHZ`, Wi-Fi 7) is API 34 - still above this `minSdk`, so compare the
raw int rather than referencing the constant. This is now the only remaining
version-gated branch in the scan-parsing code.

**Security.** `ScanResult.getSecurityTypes()` (API 33+) is authoritative and, at this
`minSdk`, unconditional. It returns a set of `WifiInfo.SECURITY_TYPE_*` constants that
map directly onto `SecurityType`, so there is no string parsing anywhere in the app.

This removes what would otherwise have been one of the highest-risk correctness areas in
the codebase: hand-parsing the `capabilities` string reliably mislabels two cases -
WPA2/WPA3 transition networks (advertising both `RSN-SAE` and `RSN-PSK`), and OWE
networks, which are unauthenticated but *encrypted* and must never be displayed as
"Open". The API returns both correctly. Both cases still need explicit UI treatment: a
network with both `SECURITY_TYPE_PSK` and `SECURITY_TYPE_SAE` renders as "WPA2/WPA3
transition", and `SECURITY_TYPE_OWE` renders as "Open (encrypted)".

**Standard.** `ScanResult.getWifiStandard()` (API 30+) maps directly to `WifiStandard`.

**DFS and PSC.** Both are computed from the channel number rather than read from the API:
5 GHz DFS covers channels 52-64 and 100-144 in most regulatory domains (region-dependent;
the country code from the AP's information elements is used when present, defaulting to
"unknown, flagged conservatively"). 6 GHz Preferred Scanning Channels are 5, 21, 37, …,
i.e. every 16th channel starting at 5.

**Information elements** (`getInformationElements()`, API 30+) provide the country code,
WPS state, supported rates and vendor-specific elements. Parsed lazily, on the AP detail
screen only - parsing every IE of every AP on every scan is wasted work.

### 6.3 Hidden and multi-BSSID networks

APs with an empty SSID are hidden and rendered as `<hidden>` with the BSSID as the
primary identifier. Multiple BSSIDs sharing an SSID are grouped in the list view
(expandable to show each radio) - a single tri-band mesh AP otherwise fills the screen
with what looks like six separate networks.

---

## 7. Channel analysis

### 7.1 Occupancy graph

The classic overlapping-parabola chart, one per band, as three tabs. X axis is frequency
in MHz (not channel number - channel numbering is non-linear across bands and drawing by
channel misrepresents 40/80/160 MHz overlap). Y axis is RSSI from −100 to −30 dBm.

Each AP is drawn as a symmetric curve spanning `span.lowMhz`…`span.highMhz`, peaking at
`centerMhz` at its RSSI. 80+80 MHz APs draw two curves joined by a thin connector.

Rendered with Compose `Canvas`, not a charting library: the shape is unusual enough that
every library needs fighting, and the drawing logic is about 80 lines.

### 7.2 Recommendation algorithm

For each candidate primary channel in a band, compute an interference score:

```
score(candidate) = Σ over all visible APs:
    overlapFactor(candidateSpan, apSpan) × linearPower(ap.rssiDbm) × bandPenalty
```

where:

- `linearPower(dbm) = 10^(dbm / 10)` - RSSI must be converted out of the log domain
  before summing. Averaging dBm values directly is wrong and is a common bug.
- `overlapFactor` is the fraction of the candidate's span covered by the AP's span, with
  a **partial-overlap multiplier of 1.5**. This is the counter-intuitive part: on 2.4 GHz,
  a co-channel neighbour is *less* harmful than a partially-overlapping one, because
  co-channel stations share the medium through CSMA/CA while partial overlap appears as
  raw noise that cannot be decoded or deferred to.
- `bandPenalty` slightly favours the 1/6/11 non-overlapping set on 2.4 GHz.

The output is a ranked list per band with the score, the number of contributing APs, and
plain-language rationale. DFS channels are flagged (radar events cause disconnections);
6 GHz non-PSC channels are flagged (slower client discovery).

The recommendation is explicitly labelled as based on *this device's vantage point at
this moment* - an AP's own view differs. Given the accuracy priority, the screen also
shows how many scans the recommendation is based on and refuses to recommend from a
single sample.

---

## 8. LAN host discovery

### 8.1 The MAC address problem

**This must be understood before designing anything else in this section.** Since Android
10, SELinux policy blocks untrusted apps from reading `/proc/net/arp`. There is no
supported API replacement. On an unrooted device - which is what we are building -
**MAC addresses of other hosts on the network are unavailable**, and by extension so are
OUI-based vendor lookups for those hosts.

Rejected alternatives, for the record:

| Approach | Why rejected |
|---|---|
| Read `/proc/net/arp` | Returns empty on API 29+; SELinux denial |
| `NetworkInterface.getHardwareAddress()` | Returns `null` for non-own interfaces, and own MAC is randomised |
| Root shell `ip neigh` | Root explicitly out of scope |
| Send ARP frames directly | Requires a raw `AF_PACKET` socket, which needs `CAP_NET_RAW` - root only |
| Query the router's UI/API | Vendor-specific, credential-requiring, out of scope |

The design consequence: **host identification is built on service discovery and
behavioural fingerprinting, not on MAC vendors.** The `Host.macAddress` field exists and
stays null for most hosts, so a future rooted or privileged build can populate it without a
model change. The UI does not show an empty "MAC" row; it shows the identification signals it
actually has.

**One narrow, deliberate exception** (docs/device-identification-ideas.md A3): a host that
answers a NetBIOS NBSTAT query includes its adapter's real MAC in the response's STATISTICS
field (RFC 1002 §4.2.18) - an application-layer payload the app is already receiving
legitimately, not the ARP table. `NetBiosProbe` extracts it and runs it through the OUI table
below. Coverage is limited to hosts that speak NetBIOS (mostly Windows/Samba, some NAS/print
servers), so this doesn't change the blanket statement above for the general case.

One place OUI lookup *does* work reliably: **BSSIDs from Wi-Fi scan results are real MAC
addresses**, so access point vendor identification is fully supported. The bundled OUI
database (`VendorLookup`, in `:core:common` so both `:data:wifi` and `:data:lan` can reach it
without violating the "data modules never depend on each other" rule in §2.1) earns its keep
there, and now also serves the NetBIOS-derived MACs above - with the caveat that its
AP-oriented vendor scope means client-device NIC vendors often won't resolve.

### 8.2 Three-stage pipeline

Stage ordering is driven by cost and by the accuracy priority: cheap passive listening
first (it also seeds hostnames used later), then the active sweep, then enrichment of
confirmed hosts only.

**Stage A - passive and broadcast (≈3 s, runs concurrently)**

| Probe | Mechanism | Yields |
|---|---|---|
| mDNS | `NsdManager` browse, plus a meta-query for `_services._dns-sd._udp` to enumerate service types before browsing each | Hostnames, service types, device models (Apple, printers, Chromecast, NAS) |
| SSDP | UDP M-SEARCH ×3 to `239.255.255.250:1900`, `ST: ssdp:all`, MX 2 | `SERVER`, `LOCATION`; fetching the LOCATION XML yields `friendlyName`, `manufacturer`, `modelName` |
| NetBIOS | UDP node-status query to the broadcast address on port 137 | Windows/Samba names and workgroup |
| Known hosts | Gateway from `LinkProperties`, self from `linkAddresses` | Two guaranteed-correct entries |

All three network probes require a held `MulticastLock`. It is acquired at stage start and
released in a `finally` block - a leaked multicast lock is a serious battery drain.

**Stage B - active sweep (bounded, IPv4 only)**

1. Enumerate host addresses from the actual prefix length. Refuse prefixes shorter than
   /22 without explicit confirmation (a /16 is 65,534 probes). On the reference /24 this
   gate never fires; it exists for the networks the device will meet elsewhere - corporate
   /16s, VPN tunnels, and captive-portal networks with unusual addressing.
2. **Pass 1**: ICMP echo to every address, 1 probe, 1 s timeout, 64-way concurrency.
3. **Pass 2**: re-probe every non-responder once, 2 s timeout, 32-way concurrency. This
   recovers hosts lost to transient wireless loss and is a direct consequence of ranking
   accuracy above battery.
4. **Pass 3**: for addresses still silent, attempt TCP connect to a small port set
   (80, 443, 22, 445, 139, 8009, 62078, 5555) with a 400 ms timeout. Catches hosts with
   ICMP disabled - notably many Windows installs and IoT devices. **All ports for a given
   host are probed concurrently**, so one host costs one 400 ms round rather than eight
   sequential ones. Probing them serially turns pass 3 from ~2 s into ~12 s on a /24 and
   is the difference between the sweep meeting its budget and missing it.

Results stream to the UI as they arrive; the list populates progressively rather than
appearing at the end.

**Timing budget for a /24** (254 addresses, the reference case):

| Pass | Probes | Concurrency | Timeout | Worst case |
|---|---|---|---|---|
| 1 - ICMP | 254 | 64 | 1 s | ~4 s |
| 2 - ICMP retry | ~240 non-responders | 32 | 2 s | ~15 s |
| 3 - TCP fallback | ~240 hosts × 8 ports, ports parallel per host | 64 hosts | 400 ms | ~2 s |
| | | | **Total** | **~21 s** |

Pass 2 dominates, and it exists purely to serve the accuracy priority - it recovers hosts
lost to transient wireless loss. It is the first thing to make optional if the sweep feels
slow in practice; dropping it takes a /24 to roughly 6 seconds at the cost of occasional
false negatives on a congested network.

**Stage C - enrichment (confirmed hosts only)**

- Reverse DNS via `DnsResolver`, async, never blocking the list.
- Extended port probe over a ~30-port service set.
- Banner grab: HTTP `Server` header and `<title>`, SSH version string.
- **ICMP reply TTL fingerprint**: an initial TTL of 64 implies Linux/Android/iOS/macOS,
  128 implies Windows, 255 implies network equipment. Combined with the hop count this is
  a cheap and reasonably reliable OS class hint - recorded as a `DeviceHint` with basis
  `"IP TTL 128 → Windows family"` and certainty `POSSIBLE`.
- Join in Stage A metadata by IP.

### 8.3 Evidence merging and confidence

Every probe appends an `Evidence` entry rather than overwriting fields. Merge rules:

- A host is `CONFIRMED` if any of ICMP, TCP_CONNECT, or a directly-addressed response was
  received. `ANNOUNCED` if only mDNS/SSDP/NetBIOS advertised it. `STALE` if present in the
  previous sweep and absent now - kept visible for one sweep, greyed, then dropped.
- Hostname precedence for the primary display name: mDNS → SSDP `friendlyName` → NetBIOS
  → reverse DNS. All variants remain visible on the detail screen with their source.
- Conflicting evidence is never silently resolved. If mDNS and NetBIOS disagree, both are
  shown.

### 8.4 Concurrency and resource control

```kotlin
private val sweepDispatcher = Dispatchers.IO.limitedParallelism(64)
private val execDispatcher  = Dispatchers.IO.limitedParallelism(8)   // process-spawning probes
```

Bounded parallelism, not raw thread spawning. A `WifiManager.WifiLock` in
`WIFI_MODE_FULL_LOW_LATENCY` is held for the duration of an active sweep only - this
prevents power-save mode from inflating RTTs and producing false negatives, and it is
released immediately on completion or cancellation. The whole pipeline is a single
structured-concurrency scope tied to the screen lifecycle, so backgrounding the app
cancels every in-flight probe.

---

## 9. Diagnostics

### 9.1 ICMP engine - the core decision

With the NDK excluded, the naive path is to shell out to `/system/bin/ping` and parse
stdout. That works, but it is fragile (toybox and iputils produce different output),
locale-sensitive, and cannot report per-probe detail.

**Android exposes unprivileged ICMP datagram sockets through `android.system.Os`.** The
kernel parameter `net.ipv4.ping_group_range` is set permissively by Android's init, so any
app holding `INTERNET` can create `SOCK_DGRAM`/`IPPROTO_ICMP` sockets without root and
without native code.

```kotlin
val fd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, ttl)
Os.setsockoptTimeval(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO,
                     StructTimeval.fromMillis(timeoutMs))
Os.sendto(fd, echoRequest, 0, echoRequest.size, 0, target, 0)
Os.recvfrom(fd, buffer, 0, buffer.size, 0, fromAddress)
```

Notes for the implementer:

- Build an 8-byte ICMP echo header (type 8, code 0, checksum, identifier, sequence) plus
  payload. On ping sockets the **kernel rewrites the identifier and recomputes the
  checksum**, so do not rely on echoing back your own identifier; match on sequence number
  and source address instead.
- Timestamp immediately before `sendto` and immediately after `recvfrom` using
  `System.nanoTime()`.

**Tiering** (documented so the fallback is a design decision, not a panic response):

| Tier | Implementation | When used |
|---|---|---|
| 1 | `android.system.Os` ICMP datagram socket | Default; full per-probe control and accurate RTT |
| 2 | `exec("/system/bin/ping")` + tolerant output parsing | If tier 1 fails at socket creation on a given device |
| 3 | TCP connect RTT, clearly labelled "TCP, not ICMP" in the UI | Both above unavailable |

Tier 1 is validated by a Phase 2 spike across the device matrix before the rest of the
diagnostics work is built on it (see `implementation-plan.md`, S-01).

### 9.2 Ping

Streams per-probe results. Reports min/avg/max, **median**, standard deviation, jitter
(mean absolute successive difference) and loss percentage. Median is included because the
mean is badly distorted by a single power-save-induced outlier on Wi-Fi, and this app
ranks accuracy first.

Configurable: count (or continuous), interval, payload size, timeout, TTL, don't-fragment
(useful for MTU discovery). A live sparkline of RTT sits above the log.

### 9.3 Traceroute

TTL walk using the tier-1 socket: set `IP_TTL` to *n*, send an echo, and read the
resulting ICMP Time Exceeded from an intermediate router.

Reading that reply requires the socket error queue: enable `IP_RECVERR` and call
`recvfrom` with `MSG_ERRQUEUE`, from which the source address field yields the hop that
generated the error. **This is the one part of the design carrying real technical
uncertainty** and is covered by spike S-02. If the error queue proves unreachable through
the `Os` API on the device matrix, the fallback is a TTL walk driven by the ping binary:

```
/system/bin/ping -c 1 -W 1 -t <ttl> <target>
```

parsing `From <ip> ... Time to live exceeded`. The parser must tolerate both toybox and
iputils phrasing, and must be locale-independent - match on the IP address and the
numeric fields, not on English words where avoidable. Golden-file tests cover both.

Behaviour: 3 probes per hop, max 30 hops, stop on target reached or 5 consecutive fully-
timed-out hops. Reverse DNS per hop resolved asynchronously and filled in as it arrives.
Per-hop min/avg/max shown. Non-responding hops render as `*` and never abort the run.

### 9.4 DNS

`DnsResolver` (API 29+) supports asynchronous queries with cancellation and arbitrary
record types via `rawQuery`. Supports A, AAAA, CNAME, MX, NS, TXT, SOA, PTR, SRV.

The user can query the system resolver (default) or a specific server. Querying a chosen
server requires constructing and parsing DNS wire format over UDP 53 directly - the
system resolver cannot be redirected. This is implemented as a small self-contained
encoder/decoder in `:data:diagnostics`; it is about 200 lines and avoids adding a
dependency for one feature.

Reverse lookups build the `in-addr.arpa` name and issue a PTR query.

### 9.5 Port scanner

TCP connect scan - SYN scanning needs raw sockets and is therefore impossible here, which
is worth stating in the UI so results aren't misread. Configurable range or preset service
set, adjustable concurrency (default 64) and timeout (default 500 ms). Banner grab on
connect where the protocol offers one.

Rate limiting is deliberate and non-configurable below a floor: an unthrottled scan
trips IDS on managed networks and can destabilise cheap consumer routers.

### 9.6 Remaining tools

- **Wake-on-LAN** - magic packet (6×`0xFF` + 16× target MAC) via UDP broadcast to port 9.
  The MAC must be entered manually or picked from a saved-hosts list, since we cannot read
  it from the network (§8.1). Saved WOL targets persist in Room.
- **WHOIS** - plain TCP to port 43. Referral chasing (IANA → RIR → registrar), capped at
  3 hops.
- **HTTP header inspector** - `HttpURLConnection` with redirects disabled, showing the
  status line, all response headers, the redirect chain, and TLS certificate subject,
  issuer and validity for HTTPS.
- **Subnet calculator** - offline; CIDR ↔ mask, network/broadcast/usable range, host
  count, VLSM splitting.
- **Signal meter** - live RSSI from the `NetworkCallback` stream (§5.1) with a rolling
  60-second chart, dBm and derived quality percentage, plus link speed. No scan budget
  consumed, so this can run continuously.

---

## 10. Persistence

Room, schema version 1, with `exportSchema = true` and schema files committed for future
migrations.

| Entity | Purpose | Retention |
|---|---|---|
| `scan_session` | One row per scan snapshot: timestamp, connected BSSID, location-free | 30 days, configurable |
| `scan_observation` | AP observation joined to a session: BSSID, RSSI, channel, width | Cascade with session |
| `known_ap` | Stable per-BSSID record: SSID, vendor, first/last seen, best RSSI | Indefinite |
| `saved_host` | User-pinned LAN host: label, IP, MAC (manual, for WOL), notes | Indefinite |
| `diagnostic_run` | Ping/traceroute/scan runs with parameters and serialised results | 90 days |
| `oui` | Prepopulated MAC prefix → vendor, variable prefix length | Static, bundled |

The vendor table ships as a prepopulated database asset (`createFromAsset`) rather than
being parsed at first launch - parsing tens of thousands of rows at runtime adds a visible
cold-start delay.

**Source: Wireshark's `manuf` dataset**, not the raw IEEE registry. Three reasons, in
order of practical weight:

1. **One file covers every prefix length.** The IEEE publishes MA-L (24-bit) as `oui.csv`
   but MA-M (28-bit) and MA-S (36-bit) as separate downloads. Taking only `oui.csv` gives
   silent 24-bit-only coverage; smaller registrants resolve to nothing. `manuf` merges all
   of them with explicit mask notation.
2. **Curated short names.** `manuf` carries both a short name and the registrant's legal
   name - "Cisco" alongside "Cisco Systems, Inc". The short name is what belongs in a
   constrained AP list row.
3. **Normalisation and well-known ranges.** Manual corrections are applied on top of the
   IEEE data, and the file includes multicast and reserved ranges (`01:00:5E`, `33:33`,
   `01:80:C2`, broadcast) that a network analyser will encounter and the IEEE registry
   does not contain.

Because prefix lengths vary, **lookup is longest-prefix-first**: try 36 bits, then 28,
then 24, returning the first hit. The schema is therefore `(prefix_bits, prefix_value,
short_name, long_name)` with a composite index, not a single 24-bit key. The
locally-administered bit is checked *before* any lookup, so randomised MACs are reported
as "randomised" rather than matched to a bogus vendor.

**Scope note.** Per §8.1 this table is consulted in exactly one place: BSSIDs from Wi-Fi
scan results. LAN host MACs are unavailable, so the small-registrant long tail that MA-S
coverage unlocks is largely invisible here - AP vendors are overwhelmingly large companies
holding MA-L blocks. The variable-length support is worth having, but the short names are
what earn the dataset choice day to day.

> Wireshark restructured how this data ships during the 4.x series. Confirm the current
> file location and format before writing the parser rather than assuming the historic
> tab-separated layout.

DataStore (Proto) holds preferences: scan cadence, sweep concurrency and timeouts, port
presets, theme, units, and the one-time scanning acknowledgement flag.

Export: scan sessions and diagnostic runs export to CSV and JSON via `ACTION_CREATE_DOCUMENT`.
No storage permissions needed.

---

## 11. User interface

### 11.1 Navigation

Bottom navigation with four destinations, plus detail routes:

| Destination | Content |
|---|---|
| **Connection** | Current network dashboard: SSID/BSSID, band/channel/width/standard, RSSI gauge, link speeds, IPv4 and IPv6 addresses, gateway, DNS servers, internet-validated and captive-portal state |
| **Wi-Fi** | AP list (sortable by RSSI/SSID/channel, groupable by SSID, filterable by band/security), channel graph tabs, channel recommendation card, AP detail route |
| **Devices** | LAN host list with progressive population and a scan-progress indicator, host detail route |
| **Tools** | Grid of the nine diagnostic tools, each its own route |

Compose Navigation with type-safe routes. Deep link from a host row into the ping or port
scanner tool pre-filled with that address - the single most useful piece of cross-screen
plumbing in the app.

### 11.2 Adaptive layout

Built in from Phase 0 rather than retrofitted, because the expensive part of adaptivity is
not styling - it is that **list-detail pairs must be modelled as one destination with two
panes rather than two navigation destinations.** Converting the latter into the former
later means rewriting navigation, back-stack handling and state hoisting for every
affected screen. Doing it up front costs roughly 2.5 days; retrofitting in Phase 8 costs
5-6.

Driven by `WindowSizeClass` from `androidx.compose.material3.adaptive`, using
`currentWindowAdaptiveInfo()`. Three width classes:

| Width class | Typical | Navigation | Content |
|---|---|---|---|
| Compact | Phone portrait | Bottom bar | Single pane |
| Medium | Phone landscape, small tablet, unfolded inner display | Navigation rail | Single pane, wider gutters |
| Expanded | Tablet, large unfolded display | Navigation rail | Two panes where the screen has a list-detail shape |

Implementation notes:

- **`NavigationSuiteScaffold`** switches between bottom bar and navigation rail
  automatically from the window size class. This is close to free and removes the most
  visible "phone app stretched sideways" tell.
- **`ListDetailPaneScaffold`** for the two screens that are genuinely list-detail: the AP
  list → AP detail, and the host list → host detail. On expanded width both panes show at
  once; on compact it degrades to the standard push navigation with correct back
  behaviour. This is the single biggest usability win and the main reason to do this early.
- **The channel graph is the standout beneficiary.** It is a frequency-axis chart squeezed
  into ~380 dp on a phone; in landscape it gets roughly triple the horizontal space, which
  is exactly the axis that carries information. The `Canvas` must derive label density and
  tick spacing from the measured width rather than using fixed values - this is the one
  place where adaptivity requires real thought rather than a scaffold swap.
- **Tool screens are forms** and need only a `widthIn(max = 600.dp)` centred constraint so
  text fields do not stretch across a tablet. Half a day for all nine.
- **Configuration changes are already handled.** State lives in ViewModels behind
  `stateIn(WhileSubscribed)`, so rotation costs nothing and no `configChanges` manifest
  hack is needed. This falls out of the Phase 0 architecture for free.

#### Fold posture

Window size classes handle a foldable's *unfolded* inner display correctly on their own,
but they say nothing about the hinge. Posture handling covers two further cases.

Posture comes from `WindowInfoTracker.windowLayoutInfo(activity)`, a `Flow<WindowLayoutInfo>`
carrying zero or more `FoldingFeature`s. The three fields that matter:

| Field | Values | Meaning |
|---|---|---|
| `state` | `FLAT`, `HALF_OPENED` | Whether the device is bent |
| `orientation` | `VERTICAL`, `HORIZONTAL` | Hinge runs down the screen (book) or across it (tabletop) |
| `isSeparating` | boolean | Whether the fold splits the display into logically distinct areas |

**Book posture (`HALF_OPENED` + `VERTICAL`)** - the hinge runs vertically, splitting the
display left and right. This is handled almost entirely for free: `currentWindowAdaptiveInfo()`
returns a `WindowAdaptiveInfo` containing both the size class *and* the posture, and
`calculatePaneScaffoldDirective()` uses the posture to place the pane gap directly over
the hinge. Because §11.2 already routes the AP and host list-detail screens through
`ListDetailPaneScaffold`, both become hinge-aware with no additional layout code - the
list lands on one half, the detail on the other, and nothing is drawn across the crease.

**Tabletop posture (`HALF_OPENED` + `HORIZONTAL`)** - the device sits half-open like a
laptop, with a horizontal upper display and lower display. This is the case worth building
deliberately, because it maps naturally onto three screens:

| Screen | Upper half | Lower half |
|---|---|---|
| Channel graph | The graph itself | Band tabs, legend, recommendation card |
| Signal meter | Rolling RSSI chart | Numeric readouts, link speeds |
| Ping / traceroute | Live streaming output | Target input and run controls |

The pattern is the same in each: **continuously-updating output above, controls below.**
The lower display sits at a natural angle for touch input while the upper stays readable
at a glance, which is exactly the ergonomics of watching a live ping run.

Implementation notes:

- `FoldingFeature.bounds` is reported in **window coordinates**. Translating to composable
  coordinates requires the composable's position from `onGloballyPositioned`. Getting this
  wrong places the split in visibly the wrong place, and it is the most common bug in
  posture-aware layouts.
- Treat posture as an enhancement layered on the size class, never a replacement.
  `HALF_OPENED` with `isSeparating == false` (some devices, some angles) must fall through
  to the normal size-class layout rather than producing a split with nothing to split
  around.
- Posture must be a `Flow` collected in composition, not read once - the user can fold and
  unfold mid-session and the layout should follow without losing scroll position or
  in-flight work.

**On testing without owning the hardware.** Every other capability in this app is blocked
on physical devices because the emulator has no Wi-Fi radio (C-13). Layout is the
exception: it needs no radio at all. The resizable emulator in Android Studio exposes fold
state and posture directly, so book and tabletop layouts can be developed and verified
without a foldable in hand. The remaining risk is cosmetic - real hinge dimensions and
aspect ratios vary by manufacturer - which is worth one verification session on the actual
device before it becomes a gift.

### 11.3 Presenting uncertainty

Given the accuracy priority, the UI has explicit conventions:

- Every list carries a timestamp of the data it is showing.
- Inferred values are visually distinct from measured ones, and their basis is available
  on tap.
- Absent data reads "unknown", never a plausible-looking default. In particular the host
  detail screen does not show a MAC row it cannot populate.
- Degraded modes are named. If ping fell back to TCP RTT, the results header says so.

### 11.4 First-run acknowledgement

Before the first LAN sweep, a one-time dialog states plainly that active host discovery
and port scanning should only be run against networks the user owns or administers, and
requires explicit acknowledgement. Stored in DataStore. Not skippable, not repeated.

---

## 12. Testing strategy

**JVM unit tests** (`:core:model`, `:core:common`, parsers):

- Frequency ↔ channel conversion across all three bands and both special cases.
- Channel span computation for every width constant including 80+80 and 320 MHz.
- Overlap and interference scoring, including the linear-power conversion.
- Security type set → display label, covering WPA2/WPA3 transition and OWE.
- Subnet math: prefix enumeration, network/broadcast, /31 and /32 edge cases.
- **Golden-file parser tests** for ping and traceroute output from both toybox and
  iputils, and for SSDP and NetBIOS responses. These are the highest-value tests in the
  project - the parsers are where silent inaccuracy hides.
- ICMP checksum and packet construction against known-good byte vectors.

**Instrumented tests**: `ACCESS_FINE_LOCATION` permission state machine (granted, denied,
permanently denied), Room migrations,
vendor lookup correctness and performance - including longest-prefix precedence, where a
36-bit entry must win over a 24-bit entry covering the same address -
`NetworkCallback` lifecycle.

**Manual device matrix** - physical devices only, since the emulator cannot scan Wi-Fi at
all (C-13). The primary targets are the two owned Android 15 devices; the third row is
optional but is the only way to catch OEM divergence (C-14) before it bites.

| Device class | API | Specifically tests |
|---|---|---|
| Primary device | 35 | Reference behaviour, FGS typing, 6 GHz if available, both spikes |
| Secondary device | 35 | Cross-checks the spikes; catches per-device kernel differences |
| Resizable emulator | 33-35 | **Layout only**: window size classes, book and tabletop posture. No radio, so no scanning, sweeping or diagnostics |
| Third-party OEM *(optional)* | 33-34 | The API 33 floor itself, vendor Wi-Fi stack, aggressive background limits |
| Physical foldable *(one session)* | any | Real hinge dimensions and aspect ratio against the emulator-developed layouts |

The two owned devices are the deployment targets, so they define "working". The resizable
emulator is the exception to C-13: layout needs no Wi-Fi radio, so it is a fully valid
test surface for everything in §11.2 and nothing else. The optional
third exists purely to validate that the API 33 floor is real rather than theoretical - if
the app is never installed below 35, consider raising `minSdk` and deleting the last
version branch (§6.2).

---

## 13. Known degradations

Recorded so they are decisions rather than surprises. Full detail in
`adr/` (`C-01`-`C-18`).

| Capability | Degradation | Mitigation |
|---|---|---|
| LAN host MAC / vendor | Unavailable | Identification via mDNS/SSDP/NetBIOS/ports/TTL |
| Own device MAC | Returns `02:00:00:00:00:00` | Not displayed |
| Scan refresh rate | 4 per 2 min | Passive harvesting; dev-options detection; visible countdown |
| Background scanning | 1 per 30 min, stops in Doze | Continuous monitoring is foreground-service-only and says so |
| SYN scanning | Impossible | Connect scan, labelled as such |
| Traceroute error queue | Uncertain via `Os` API | Spike S-02; ping-binary fallback |
| IPv6 | Display only | Stated in the UI; active tooling is IPv4 |
