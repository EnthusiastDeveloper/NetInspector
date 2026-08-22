package dev.enthusiastdev.netinspector.core.model.lan

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.Instant

private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

private fun evidence(source: EvidenceSource) = Evidence(source, Instant.EPOCH, null)

private fun hostWithPorts(
    address: String,
    vararg ports: Int,
) = mergeObservation(
    emptyMap(),
    HostObservation(
        addr(address),
        listOf(evidence(EvidenceSource.ICMP)),
        openPorts = ports.map { OpenPort(it, null, null) },
    ),
).getValue(addr(address))

class HygieneScoreTest {
    @Test
    fun `hostHygieneScore is the CLEAN 100 with no findings for a host with no risky open ports`() {
        val host = hostWithPorts("192.168.1.5", 80, 443)

        val score = hostHygieneScore(host)

        assertThat(score).isEqualTo(HygieneScore.CLEAN)
        assertThat(score.rating).isEqualTo(HygieneRating.EXCELLENT)
    }

    @Test
    fun `hostHygieneScore is unaffected by ports that carry no risk note`() {
        val withBenignPorts = hostHygieneScore(hostWithPorts("192.168.1.5", 80, 443, 8009))

        assertThat(withBenignPorts.value).isEqualTo(100)
    }

    @Test
    fun `hostHygieneScore deducts the CRITICAL penalty for a single unauthenticated-by-default port`() {
        val host = hostWithPorts("192.168.1.5", 23)

        val score = hostHygieneScore(host)

        assertThat(score.value).isEqualTo(60)
        assertThat(score.rating).isEqualTo(HygieneRating.FAIR)
        assertThat(score.findings).containsExactly(HygieneFinding(23, PortRiskSeverity.CRITICAL, "192.168.1.5"))
    }

    @Test
    fun `hostHygieneScore sums penalties across every risky open port`() {
        // Telnet (CRITICAL, -40) + FTP (HIGH, -20) + SMTP (MODERATE, -10) = -70
        val host = hostWithPorts("192.168.1.5", 23, 21, 25)

        val score = hostHygieneScore(host)

        assertThat(score.value).isEqualTo(30)
        assertThat(score.rating).isEqualTo(HygieneRating.POOR)
        assertThat(score.findings).hasSize(3)
    }

    @Test
    fun `hostHygieneScore floors at 0 rather than going negative`() {
        // Telnet + VNC + RDP + PPTP is -40 - 40 - 20 - 20 = -120, well past the floor.
        val host = hostWithPorts("192.168.1.5", 23, 5900, 3389, 1723)

        val score = hostHygieneScore(host)

        assertThat(score.value).isEqualTo(0)
        assertThat(score.rating).isEqualTo(HygieneRating.CRITICAL)
    }

    @Test
    fun `networkHygieneScore for a single host matches that host's own hostHygieneScore`() {
        val host = hostWithPorts("192.168.1.5", 23)

        assertThat(networkHygieneScore(listOf(host))).isEqualTo(hostHygieneScore(host))
    }

    @Test
    fun `networkHygieneScore combines findings across hosts rather than scoring each in isolation`() {
        val clean = hostWithPorts("192.168.1.5", 80)
        val risky = hostWithPorts("192.168.1.6", 23)

        val score = networkHygieneScore(listOf(clean, risky))

        assertThat(score.value).isEqualTo(60)
        assertThat(score.findings).containsExactly(HygieneFinding(23, PortRiskSeverity.CRITICAL, "192.168.1.6"))
    }

    @Test
    fun `networkHygieneScore drops further when several hosts each carry one risky port`() {
        // One host with Telnet+FTP scores the same as two hosts carrying one each: -40 + -20 = -60.
        val single = hostWithPorts("192.168.1.5", 23, 21)
        val combined = networkHygieneScore(listOf(hostWithPorts("192.168.1.5", 23), hostWithPorts("192.168.1.6", 21)))

        assertThat(combined.value).isEqualTo(hostHygieneScore(single).value)
    }

    @Test
    fun `networkHygieneScore is CLEAN for an empty host list`() {
        assertThat(networkHygieneScore(emptyList())).isEqualTo(HygieneScore.CLEAN)
    }
}
