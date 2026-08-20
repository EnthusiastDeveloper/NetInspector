package dev.enthusiastdev.netinspector.core.model.lan

/** design §8.2 - one mDNS or SSDP service record for a host. `serviceType` is the mDNS
 * service type (e.g. `_http._tcp`) or the SSDP `ST`; `detail` carries whatever free-text
 * extra the protocol offered beyond a bare name (SSDP's `SERVER` header, when the LOCATION
 * XML fetch fails). `manufacturer`/`modelName` are SSDP's structured LOCATION-XML fields,
 * kept separate from `detail` so the UI can show them as their own rows rather than losing
 * one to a fallback when [name] is also present. `txtRecords` is mDNS's TXT-record key/value
 * set (RFC 6763 §6) - `NsdManager.resolveService` already fetches these as part of resolving
 * a service, so surfacing them costs no extra probing; a boolean (presence-only) key maps to
 * an empty string. */
data class DiscoveredService(
    val source: EvidenceSource,
    val serviceType: String?,
    val name: String?,
    val detail: String?,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val txtRecords: Map<String, String> = emptyMap(),
)
