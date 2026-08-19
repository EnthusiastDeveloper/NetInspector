package dev.enthusiastdev.netinspector.core.model.lan

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Instant

private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

private fun evidence(
    source: EvidenceSource,
    at: Instant = Instant.EPOCH,
    detail: String? = null,
) = Evidence(source, at, detail)

class HostMergeTest {
    @Test
    fun `confidenceOf is CONFIRMED for a directly-answering or structurally-known source`() {
        assertThat(confidenceOf(listOf(evidence(EvidenceSource.ICMP)))).isEqualTo(HostConfidence.CONFIRMED)
        assertThat(confidenceOf(listOf(evidence(EvidenceSource.TCP_CONNECT)))).isEqualTo(HostConfidence.CONFIRMED)
        assertThat(confidenceOf(listOf(evidence(EvidenceSource.GATEWAY)))).isEqualTo(HostConfidence.CONFIRMED)
        assertThat(confidenceOf(listOf(evidence(EvidenceSource.SELF)))).isEqualTo(HostConfidence.CONFIRMED)
    }

    @Test
    fun `confidenceOf is ANNOUNCED when only advertised, never answered`() {
        assertThat(confidenceOf(listOf(evidence(EvidenceSource.MDNS)))).isEqualTo(HostConfidence.ANNOUNCED)
        assertThat(
            confidenceOf(listOf(evidence(EvidenceSource.SSDP), evidence(EvidenceSource.NETBIOS))),
        ).isEqualTo(HostConfidence.ANNOUNCED)
    }

    @Test
    fun `mergeObservation creates a new CONFIRMED host from a single ICMP reply`() {
        val observation = HostObservation(addr("192.168.1.5"), listOf(evidence(EvidenceSource.ICMP)))
        val hosts = mergeObservation(emptyMap(), observation)

        val host = hosts.getValue(addr("192.168.1.5"))
        assertThat(host.confidence).isEqualTo(HostConfidence.CONFIRMED)
        assertThat(host.evidence).containsExactly(evidence(EvidenceSource.ICMP))
    }

    @Test
    fun `mergeObservation accumulates evidence across multiple observations for the same address`() {
        val address = addr("192.168.1.5")
        val first = mergeObservation(emptyMap(), HostObservation(address, listOf(evidence(EvidenceSource.MDNS))))
        val second = mergeObservation(first, HostObservation(address, listOf(evidence(EvidenceSource.ICMP))))

        val host = second.getValue(address)
        assertThat(host.evidence).containsExactly(evidence(EvidenceSource.MDNS), evidence(EvidenceSource.ICMP))
        // Upgraded from ANNOUNCED to CONFIRMED once a direct-answer source arrives.
        assertThat(host.confidence).isEqualTo(HostConfidence.CONFIRMED)
    }

    @Test
    fun `mergeObservation preserves conflicting hostnames from different sources`() {
        val address = addr("192.168.1.5")
        val first =
            mergeObservation(
                emptyMap(),
                HostObservation(
                    address,
                    listOf(evidence(EvidenceSource.MDNS)),
                    hostnames =
                        mapOf(
                            EvidenceSource.MDNS to "kitchen-tv",
                        ),
                ),
            )
        val second =
            mergeObservation(
                first,
                HostObservation(
                    address,
                    listOf(evidence(EvidenceSource.NETBIOS)),
                    hostnames = mapOf(EvidenceSource.NETBIOS to "DESKTOP-1"),
                ),
            )

        val host = second.getValue(address)
        assertThat(
            host.hostnames,
        ).containsExactly(EvidenceSource.MDNS, "kitchen-tv", EvidenceSource.NETBIOS, "DESKTOP-1")
        assertThat(host.primaryHostname).isEqualTo("kitchen-tv") // MDNS precedes NETBIOS (design §11.3)
    }

    @Test
    fun `mergeObservation overwrites a hostname re-reported by the same source`() {
        val address = addr("192.168.1.5")
        val first =
            mergeObservation(
                emptyMap(),
                HostObservation(
                    address,
                    listOf(evidence(EvidenceSource.MDNS)),
                    hostnames =
                        mapOf(
                            EvidenceSource.MDNS to "old-name",
                        ),
                ),
            )
        val second =
            mergeObservation(
                first,
                HostObservation(
                    address,
                    listOf(evidence(EvidenceSource.MDNS)),
                    hostnames =
                        mapOf(
                            EvidenceSource.MDNS to "new-name",
                        ),
                ),
            )

        assertThat(second.getValue(address).hostnames).containsExactly(EvidenceSource.MDNS, "new-name")
    }

    @Test
    fun `mergeObservation computes the median RTT and keeps it when a later observation has no samples`() {
        val address = addr("192.168.1.5")
        val withSamples =
            mergeObservation(
                emptyMap(),
                HostObservation(
                    address,
                    listOf(evidence(EvidenceSource.ICMP)),
                    rttSamplesMs = listOf(10.0, 30.0, 20.0),
                ),
            )
        assertThat(withSamples.getValue(address).rttMedianMs).isWithin(1e-9).of(20.0)

        val withoutSamples =
            mergeObservation(withSamples, HostObservation(address, listOf(evidence(EvidenceSource.ICMP))))
        assertThat(withoutSamples.getValue(address).rttMedianMs).isWithin(1e-9).of(20.0)
    }

    @Test
    fun `mergeObservation dedups identical services across observations`() {
        val address = addr("192.168.1.5")
        val service =
            DiscoveredService(EvidenceSource.SSDP, "urn:schemas-upnp-org:device:MediaServer", "Living Room", null)
        val first =
            mergeObservation(
                emptyMap(),
                HostObservation(address, listOf(evidence(EvidenceSource.SSDP)), services = listOf(service)),
            )
        val second =
            mergeObservation(
                first,
                HostObservation(address, listOf(evidence(EvidenceSource.SSDP)), services = listOf(service)),
            )

        assertThat(second.getValue(address).services).containsExactly(service)
    }

    @Test
    fun `mergeObservation folds in Stage C enrichment fields`() {
        val address = addr("192.168.1.5")
        val confirmed = mergeObservation(emptyMap(), HostObservation(address, listOf(evidence(EvidenceSource.ICMP))))

        val enriched =
            mergeObservation(
                confirmed,
                HostObservation(
                    address,
                    listOf(evidence(EvidenceSource.REVERSE_DNS)),
                    openPorts = listOf(OpenPort(80, "http", "nginx")),
                    deviceHint = DeviceHint("Network printer", "Open port 9100 → Network printer", Certainty.LIKELY),
                    icmpReplyTtl = 64,
                ),
            )

        val host = enriched.getValue(address)
        assertThat(host.openPorts).containsExactly(OpenPort(80, "http", "nginx"))
        assertThat(host.deviceHint?.label).isEqualTo("Network printer")
        assertThat(host.icmpReplyTtl).isEqualTo(64)
    }

    @Test
    fun `mergeObservation keeps existing enrichment fields when a later observation carries none`() {
        val address = addr("192.168.1.5")
        val enriched =
            mergeObservation(
                emptyMap(),
                HostObservation(
                    address,
                    listOf(evidence(EvidenceSource.ICMP)),
                    icmpReplyTtl = 64,
                ),
            )

        val again = mergeObservation(enriched, HostObservation(address, listOf(evidence(EvidenceSource.ICMP))))

        assertThat(again.getValue(address).icmpReplyTtl).isEqualTo(64)
    }

    @Test
    fun `mergeObservation overwrites an open port re-reported with a banner`() {
        val address = addr("192.168.1.5")
        val first =
            mergeObservation(
                emptyMap(),
                HostObservation(
                    address,
                    listOf(evidence(EvidenceSource.TCP_CONNECT)),
                    openPorts = listOf(OpenPort(22, "ssh", null)),
                ),
            )
        val second =
            mergeObservation(
                first,
                HostObservation(
                    address,
                    listOf(evidence(EvidenceSource.TCP_CONNECT)),
                    openPorts = listOf(OpenPort(22, "ssh", "SSH-2.0-OpenSSH_9.6")),
                ),
            )

        assertThat(second.getValue(address).openPorts).containsExactly(OpenPort(22, "ssh", "SSH-2.0-OpenSSH_9.6"))
    }

    @Test
    fun `finalizeSweep marks an unobserved host STALE the first time it is missing`() {
        val address = addr("192.168.1.5")
        val current = mergeObservation(emptyMap(), HostObservation(address, listOf(evidence(EvidenceSource.ICMP))))

        val finalized = finalizeSweep(current, observedThisSweep = emptySet())

        assertThat(finalized.getValue(address).confidence).isEqualTo(HostConfidence.STALE)
    }

    @Test
    fun `finalizeSweep drops a host that was already STALE and is still missing`() {
        val address = addr("192.168.1.5")
        val confirmed = mergeObservation(emptyMap(), HostObservation(address, listOf(evidence(EvidenceSource.ICMP))))
        val staleOnce = finalizeSweep(confirmed, observedThisSweep = emptySet())

        val staleTwice = finalizeSweep(staleOnce, observedThisSweep = emptySet())

        assertThat(staleTwice).doesNotContainKey(address)
    }

    @Test
    fun `finalizeSweep leaves an observed host untouched`() {
        val address = addr("192.168.1.5")
        val current = mergeObservation(emptyMap(), HostObservation(address, listOf(evidence(EvidenceSource.ICMP))))

        val finalized = finalizeSweep(current, observedThisSweep = setOf(address))

        assertThat(finalized.getValue(address).confidence).isEqualTo(HostConfidence.CONFIRMED)
    }

    @Test
    fun `finalizeSweep re-confirms a previously STALE host observed again`() {
        val address = addr("192.168.1.5")
        val confirmed = mergeObservation(emptyMap(), HostObservation(address, listOf(evidence(EvidenceSource.ICMP))))
        val stale = finalizeSweep(confirmed, observedThisSweep = emptySet())
        val reobserved = mergeObservation(stale, HostObservation(address, listOf(evidence(EvidenceSource.ICMP))))

        assertThat(reobserved.getValue(address).confidence).isEqualTo(HostConfidence.CONFIRMED)
    }
}
