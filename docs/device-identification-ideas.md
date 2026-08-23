# Device identification ideas

Brainstormed techniques to make LAN device labels more specific than the current
`ttlDeviceHint`/`portSignatureHint` fallbacks ("Linux/Android/iOS/macOS family", "Windows
family", "Network equipment" - `DeviceHintHeuristics.kt`), inspired by how apps like Fing or
Network Analyzer produce concrete device names. Ranked by ROI first, then by lowest
additional-requirement count as a tiebreaker, same convention as
[`improvement-ideas.md`](improvement-ideas.md) (that file's #10, device tagging, is the UX-only
companion to this one - manual labels for when *no* automated signal is right).

All active probing here is still subject to the scope-of-use note in
[`README.md`](README.md#a-note-on-scope-of-use): everything below only queries hosts on
networks the user administers, and adds no new probe traffic beyond what a compliant SSDP/
mDNS/NetBIOS/SNMP client would normally send.

---

## A1. Feed SSDP/UPnP manufacturer+model into `DeviceHint`
**Status:** Implemented

`SsdpProbe.kt` already fetches the UPnP device-description XML from a responder's `LOCATION`
header and extracts `manufacturer`/`modelName` into `DiscoveredService`, but that data never
reached `DeviceHint` - it only showed up as a detail-screen field. A device's own declared
manufacturer/model is stronger evidence than an inferred port signature or TTL bucket, so it
now wins outright as a new top `Certainty.CONFIRMED` tier.

**Requirements:**
- A `Certainty.CONFIRMED` tier above `LIKELY`/`POSSIBLE`
- A hint-precedence merge in `HostMerge.mergeObservation` (previously "whichever `HostObservation`
  arrived most recently wins", which let a later Stage C TTL guess silently clobber an earlier,
  more specific Stage A SSDP hint)
- A pure `upnpDeviceHint(manufacturer, modelName)` builder alongside the existing
  `portSignatureHint`/`ttlDeviceHint` in `DeviceHintHeuristics.kt`

---

## A2. Feed mDNS service type + TXT records into `DeviceHint`
**Status:** Implemented

`MdnsProbe.kt` already resolves full TXT records (RFC 6763 §6) into `DiscoveredService.
txtRecords` but never used them, or the service type itself, as a `DeviceHint` signal. Two
sub-techniques:
- The **service type alone** is a decent LIKELY-tier signal - a host advertising
  `_airplay._tcp` is an Apple device, `_hap._tcp` is a HomeKit accessory, `_googlecast._tcp`
  is a Cast device, regardless of TTL.
- Several service types carry an **explicit model string in TXT**, which is CONFIRMED-tier
  evidence exactly like A1's UPnP manufacturer/model: `_device-info._tcp` (Apple's own info
  service, TXT `model=`, e.g. `J274AP`), `_googlecast._tcp` (TXT `md=`, a friendly model name),
  `_ipp._tcp`/`_printer._tcp` (TXT `ty=`, printer model).

**Requirements:**
- A `mdnsServiceHint(serviceType, txtRecords)` pure function next to A1's `upnpDeviceHint`
- A small service-type → generic-label table (same shape as `PORT_SIGNATURES`)
- A small TXT-key → model table for the services that expose one

---

## A3. Extract a real MAC address from NetBIOS NBSTAT responses
**Status:** Implemented

RFC 1002 §4.2.18's NBSTAT response STATISTICS field begins with a 6-byte UNIT_ID - the
responding NIC's actual MAC address. `NetBiosProbe.kt` already parses this exact packet but
discarded everything after the name array. This is a real MAC obtained from an application-
layer protocol payload the app is already legitimately receiving, not from the kernel ARP
table blocked by C-01 - it doesn't need root, a raw socket, or any workaround the ADR's
"rejected alternatives" table considered. Coverage is narrow (only hosts that answer NBSTAT -
mainly Windows/Samba boxes and some NAS/print servers), so this is a real but partial
exception to "no MAC addresses for LAN hosts," not a general fix.

Once a real MAC exists, the OUI vendor table (previously wired only to Wi-Fi AP BSSIDs -
`VendorLookup.kt`) can resolve a vendor for these hosts too. Caveat carried over from that
table's own scope: it's deliberately curated to router/AP/NAS vendors and excludes general
client-device silicon (Intel, Realtek, Dell, etc. NIC vendors), so a NetBIOS-observed Windows
PC will often still get no vendor hit until the full Wireshark `manuf` registry (already
planned for `:data:persistence`, per `implementation-plan.md` Phase 3) lands.

**Requirements:**
- Parse the STATISTICS field's UNIT_ID in `NetBiosProbe.kt`
- `macAddress`/`vendor` fields on `HostObservation`, merged the same way `icmpReplyTtl`
  already is (new non-null wins, doesn't get erased by a later null)
- Move `VendorLookup` (and its `oui_vendors.tsv` table) somewhere both `:data:wifi` and
  `:data:lan` can reach it without violating "a data module never depends on another data
  module" (design §2.1) - `:core:common`, switching from Android `AssetManager` to a plain
  JVM classpath resource so the module's "no `android.*` imports" rule still holds
- Update the "why no MAC address?" detail-screen copy (`DevicesDetailCards.kt`) to actually
  render a MAC/vendor when one is present, instead of asserting it's always unavailable

---

## B1. SNMP `sysDescr` query
Query OID `1.3.6.1.2.1.1.1.0` over UDP 161 with community string `public`. Printers, managed
switches, UPSes, and NAS boxes very often leave the default read-only community enabled and
return an exact model/firmware string - historically one of the highest-value single probes
for exactly the "Network equipment" bucket that's currently weakest.

**Requirements:**
- A minimal SNMP v1/v2c GET-request encoder/decoder (BER/ASN.1 subset - no existing dependency
  for this, would need a small hand-rolled implementation matching the NetBIOS/SSDP probes'
  existing "hand-roll the wire format" precedent)
- Wire the result into `DeviceHint` at `CONFIRMED` tier (self-reported by the device)
- New probe module under `:data:lan`, same shape as `NetBiosProbe`/`SsdpProbe`

---

## B2. WS-Discovery probe
Multicast UDP 3702 to `239.255.255.250` - how ONVIF IP cameras and some Windows/print devices
announce themselves. Several cameras answer WS-Discovery but not SSDP, filling a gap in the
current port-554-only camera guess.

**Requirements:**
- WS-Discovery Probe/ProbeMatch SOAP-over-UDP messages (another hand-rolled wire format)
- XAddrs parsing for device metadata
- New probe module under `:data:lan`

---

## B3. TLS certificate inspection on admin-UI ports
`ExtendedPortProbe.kt` already opens a plain-HTTP banner grab on ports like 443/8443. A TLS
handshake-only client on those same ports can read the certificate CN/SAN/issuer, which
self-signed router/NAS/camera certs frequently set to the product name (`Synology Inc.`,
`ubnt`, `hikvision`, `RT-AX88U`).

**Requirements:**
- `SSLSocket` handshake-only client (shares the "connect, don't validate, just read the
  chain" pattern the standalone TLS inspector idea in `improvement-ideas.md` #14 would also
  need - could share code with that if #14 ever lands)
- Cert subject/issuer parsing
- Feed matched fields into `DeviceHint`

---

## B4. HTTP banner/HTML signature table
`ExtendedPortProbe.kt` already grabs `Server:` header and `<title>` from HTTP banners but
never uses them for `DeviceHint` - just displays them raw. A small curated set of
regex-to-label rules (the same idea as Wappalyzer, scoped down to network-device admin UIs)
turns banners already being fetched into device labels.

**Requirements:**
- A signature table (ongoing maintenance burden, similar caveat to `improvement-ideas.md` #28)
- Match logic feeding `DeviceHint`

---

## C1. UPnP IGD "Hosts" service for router-reported MAC+hostname
Some consumer routers (mainly those exposing full UPnP IGD v2) implement
`urn:schemas-upnp-org:service:Hosts:1`, which lets any LAN client SOAP-query the router's own
connected-device table - MAC *and* hostname, for every device on the LAN, all at once. This
would be the single biggest lever on the MAC-address problem (C-01) beyond A3's narrow NetBIOS
exception, but coverage depends entirely on the router's firmware exposing this service -
needs an on-network spike to see how common it actually is before committing engineering time.

**Requirements:**
- Detect the `Hosts:1` service in the UPnP device description `SsdpProbe.kt` already fetches
- SOAP client for `GetHostNumberOfEntries`/`GetGenericHostEntry`
- Feasibility spike: survey how many real consumer routers actually expose this (anecdotally
  inconsistent across vendors/firmware versions)

---

## C2. Passive DHCP broadcast sniffing
Binding UDP port 67 and listening (not sending) picks up other devices' DHCP DISCOVER/REQUEST
broadcasts. Option 12 (hostname) and Option 60 (vendor class - e.g. `android-dhcp-13`,
`MSFT 5.0`) are immediate wins; the Option 55 parameter-request-list sequence is also a known
OS/device fingerprint (this is what Fingerbank's DHCP fingerprinting is built on).

**Requirements:**
- UDP broadcast listener on port 67 (may race the real DHCP server for the socket depending on
  OEM; some Android builds restrict raw broadcast binding - technical risk, needs a spike)
- DHCP packet parser (options 12/55/60)
- A maintained OS-fingerprint table for the Option 55 sequence if going beyond just
  hostname/vendor-class (ongoing maintenance burden, same caveat class as B4)

---

## D. Manual nickname/tag per device
**Status:** Implemented

Not a new identification *technique* - the fallback improvement independent of how good
automated ID gets. There was previously no way to override a `Host`'s displayed name at all
(see `improvement-ideas.md` #10, which scopes the broader IoT/guest/trusted-style tagging idea
- a plain per-host nickname is the same underlying feature, narrower: no tag categories, no
filter/sort by tag, just a label that wins over every automated naming signal). Fixes "which
one is my printer" immediately regardless of automated accuracy.

**Requirements:**
- A `saved_host` Room table (`SavedHostEntity`: `key`, `nickname`), migration 2→3
- `Host.nicknameKey()` in `core/model` - MAC-based when available (A3), address+hostname
  otherwise, never plain IP alone (unstable across a DHCP lease change)
- `SavedHostRepository` joined into `DevicesViewModel`'s host flow, overlaying `Host.nickname`
  after every sweep merge (nicknames aren't part of the sweep pipeline itself)
- `displayName()` gives a nickname top precedence over hostname/device-hint/self/gateway
  labelling
- Edit affordance on the device detail screen's header (pencil icon → dialog with a text
  field); saving a blank value clears the nickname

---

## E. Crowd-sourced/cloud fingerprint database
Fing's actual edge over anything in this list is Fingbank - a cloud database matching
OUI+ports+mDNS+DHCP-fingerprint combinations against millions of crowd-sourced devices.
Replicating the accuracy this buys offline means building and maintaining an equivalent
curated signature table by hand (everything above is exactly that data feed); calling an
external API instead means shipping local network topology off-device, which cuts directly
against this app's no-third-party-service, no-accounts positioning
(`improvement-ideas.md` #30/#31 turn down third-party APIs for the same reason). Flagging this
for completeness, not recommending it - out of scope unless that privacy trade-off is
explicitly wanted.
