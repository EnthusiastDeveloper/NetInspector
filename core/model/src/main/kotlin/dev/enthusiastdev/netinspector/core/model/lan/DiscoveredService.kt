package dev.enthusiastdev.netinspector.core.model.lan

/** design §8.2 - one mDNS or SSDP service record for a host. `serviceType` is the mDNS
 * service type (e.g. `_http._tcp`) or the SSDP `ST`; `detail` carries whatever the protocol
 * offered beyond a bare name - SSDP's `manufacturer`/`modelName` from the LOCATION XML, for
 * instance. */
data class DiscoveredService(
    val source: EvidenceSource,
    val serviceType: String?,
    val name: String?,
    val detail: String?,
)
