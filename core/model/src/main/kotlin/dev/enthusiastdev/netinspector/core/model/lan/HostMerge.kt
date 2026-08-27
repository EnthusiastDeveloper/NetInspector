package dev.enthusiastdev.netinspector.core.model.lan

import java.net.Inet4Address

/** design §8.3 - CONFIRMED if any evidence source proves the host actually answered
 * (ICMP, TCP_CONNECT) or is structurally known-correct (GATEWAY, SELF); ANNOUNCED if it was
 * only advertised by mDNS/SSDP/NetBIOS. */
fun confidenceOf(evidence: List<Evidence>): HostConfidence =
    if (evidence.any { it.source in DIRECT_EVIDENCE_SOURCES }) HostConfidence.CONFIRMED else HostConfidence.ANNOUNCED

/**
 * design §8.2 - folds one freshly-arrived [HostObservation] into the live host map, called as
 * each probe result streams in so the UI populates progressively rather than waiting for the
 * whole sweep to finish. Evidence and hostname variants accumulate; a hostname re-reported by
 * the same source simply overwrites its own entry (fresher wins), never another source's
 * (design §8.3 - "conflicting evidence is never silently resolved").
 */
fun mergeObservation(
    current: Map<Inet4Address, Host>,
    observation: HostObservation,
): Map<Inet4Address, Host> {
    val existing = current[observation.address]
    val evidence = existing?.evidence.orEmpty() + observation.evidence
    val rttSamples = observation.rttSamplesMs.takeIf { it.isNotEmpty() }
    val merged =
        Host(
            address = observation.address,
            confidence = confidenceOf(evidence),
            evidence = evidence,
            hostnames = existing?.hostnames.orEmpty() + observation.hostnames,
            macAddress = observation.macAddress ?: existing?.macAddress,
            vendor = observation.vendor ?: existing?.vendor,
            deviceHint = preferredHint(existing?.deviceHint, observation.deviceHint),
            openPorts = mergeOpenPorts(existing?.openPorts.orEmpty(), observation.openPorts),
            services = mergeServices(existing?.services.orEmpty(), observation.services),
            icmpReplyTtl = observation.icmpReplyTtl ?: existing?.icmpReplyTtl,
            rttMedianMs = rttSamples?.let { median(it) } ?: existing?.rttMedianMs,
            isGateway = observation.isGateway || (existing?.isGateway == true),
            isSelf = observation.isSelf || (existing?.isSelf == true),
        )
    return current + (observation.address to merged)
}

/**
 * design §8.3 - STALE transition, applied once a sweep's probes have all completed. A host
 * not observed this sweep is greyed (marked STALE) the first time, and dropped entirely the
 * next time it's still absent - "kept visible for one sweep, greyed, then dropped."
 */
fun finalizeSweep(
    current: Map<Inet4Address, Host>,
    observedThisSweep: Set<Inet4Address>,
): Map<Inet4Address, Host> =
    current
        .mapNotNull { (address, host) ->
            when {
                address in observedThisSweep -> address to host
                host.confidence == HostConfidence.STALE -> null
                else -> address to host.copy(confidence = HostConfidence.STALE)
            }
        }.toMap()

/** docs/ideas.md A1 - probes fire across stages in no guaranteed order
 * (Stage A's SSDP/mDNS hints can arrive before or after Stage C's port/TTL hint), so "last
 * observation wins" would let a coarse TTL guess overwrite a device's own self-reported
 * manufacturer/model just because it happened to resolve second. The more certain hint always
 * wins instead; a tie (e.g. two LIKELY port signatures) goes to the incoming one, matching the
 * "fresher wins" rule the rest of this file uses for hostnames. */
private fun preferredHint(
    existing: DeviceHint?,
    incoming: DeviceHint?,
): DeviceHint? =
    when {
        incoming == null -> existing
        existing == null -> incoming
        incoming.certainty <= existing.certainty -> incoming
        else -> existing
    }

private fun mergeServices(
    existing: List<DiscoveredService>,
    incoming: List<DiscoveredService>,
): List<DiscoveredService> = (existing + incoming).distinct()

/** design §8.4 - a re-reported port (e.g. a banner arriving after the bare open/closed
 * result) overwrites its own entry by port number; the result stays sorted for a stable
 * detail-screen order. */
private fun mergeOpenPorts(
    existing: List<OpenPort>,
    incoming: List<OpenPort>,
): List<OpenPort> =
    (existing.associateBy { it.port } + incoming.associateBy { it.port })
        .values
        .sortedBy { it.port }

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
}
