package dev.enthusiastdev.netinspector.core.model.lan

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

private fun confirmedHost(address: String) =
    Host(
        address = addr(address),
        confidence = HostConfidence.CONFIRMED,
        evidence = emptyList(),
        hostnames = emptyMap(),
        macAddress = null,
        vendor = null,
        deviceHint = null,
        openPorts = emptyList(),
        services = emptyList(),
        icmpReplyTtl = null,
        rttMedianMs = null,
        isGateway = false,
        isSelf = false,
    )

private fun record(
    key: String,
    consecutiveMissedSweeps: Int = 0,
    vanishedAlertSent: Boolean = false,
) = KnownHostRecord(
    key = key,
    displayName = null,
    firstSeenMillis = 0L,
    lastSeenMillis = 0L,
    consecutiveMissedSweeps = consecutiveMissedSweeps,
    vanishedAlertSent = vanishedAlertSent,
)

class LanPresenceDiffTest {
    @Test
    fun `first run (no prior records) records every current host but reports none as new`() {
        val host = confirmedHost("192.168.1.5")

        val diff =
            diffLanPresence(
                previousRecords = emptyMap(),
                currentConfirmedHosts = listOf(host),
                nowMillis = 100L,
                knownDeviceKeys = emptySet(),
            )

        assertThat(diff.newHosts).isEmpty()
        assertThat(diff.updatedRecords.map { it.key }).containsExactly(host.nicknameKey())
    }

    @Test
    fun `a genuinely new key is reported once`() {
        val existingHost = confirmedHost("192.168.1.1")
        val newHost = confirmedHost("192.168.1.5")
        // Not the bootstrap case - there's already prior state for a different host.
        val previous = mapOf(existingHost.nicknameKey() to record(existingHost.nicknameKey()))

        val diff =
            diffLanPresence(
                previousRecords = previous,
                currentConfirmedHosts = listOf(existingHost, newHost),
                nowMillis = 100L,
                knownDeviceKeys = emptySet(),
            )

        assertThat(diff.newHosts.map { it.key }).containsExactly(newHost.nicknameKey())
    }

    @Test
    fun `a single missed sweep does not alert`() {
        val existingKey = confirmedHost("192.168.1.1").nicknameKey()
        val key = confirmedHost("192.168.1.5").nicknameKey()
        // Non-empty prior state unrelated to `key`, so this isn't the bootstrap case either.
        val previous = mapOf(existingKey to record(existingKey), key to record(key))

        val diff =
            diffLanPresence(
                previousRecords = previous,
                currentConfirmedHosts = listOf(confirmedHost("192.168.1.1")),
                nowMillis = 100L,
                knownDeviceKeys = emptySet(),
            )

        assertThat(diff.vanishedHosts).isEmpty()
        assertThat(diff.updatedRecords.first { it.key == key }.consecutiveMissedSweeps).isEqualTo(1)
    }

    @Test
    fun `a two-sweep miss alerts exactly once and not again on a third miss`() {
        val key = confirmedHost("192.168.1.5").nicknameKey()
        val onceMissed = mapOf(key to record(key, consecutiveMissedSweeps = 1))

        val secondMiss =
            diffLanPresence(
                previousRecords = onceMissed,
                currentConfirmedHosts = emptyList(),
                nowMillis = 200L,
                knownDeviceKeys = emptySet(),
            )
        assertThat(secondMiss.vanishedHosts.map { it.key }).containsExactly(key)

        val thirdMiss =
            diffLanPresence(
                previousRecords = secondMiss.updatedRecords.associateBy { it.key },
                currentConfirmedHosts = emptyList(),
                nowMillis = 300L,
                knownDeviceKeys = emptySet(),
            )
        assertThat(thirdMiss.vanishedHosts).isEmpty()
    }

    @Test
    fun `reappearance after a real vanish alert is reported once`() {
        val host = confirmedHost("192.168.1.5")
        val key = host.nicknameKey()
        val previous = mapOf(key to record(key, consecutiveMissedSweeps = 2, vanishedAlertSent = true))

        val diff =
            diffLanPresence(
                previousRecords = previous,
                currentConfirmedHosts = listOf(host),
                nowMillis = 400L,
                knownDeviceKeys = emptySet(),
            )

        assertThat(diff.reappearedHosts.map { it.key }).containsExactly(key)
        assertThat(diff.updatedRecords.single().vanishedAlertSent).isFalse()
    }

    @Test
    fun `reappearance without a prior vanish alert is not reported`() {
        val host = confirmedHost("192.168.1.5")
        val key = host.nicknameKey()
        val previous = mapOf(key to record(key, consecutiveMissedSweeps = 1, vanishedAlertSent = false))

        val diff =
            diffLanPresence(
                previousRecords = previous,
                currentConfirmedHosts = listOf(host),
                nowMillis = 400L,
                knownDeviceKeys = emptySet(),
            )

        assertThat(diff.reappearedHosts).isEmpty()
    }

    @Test
    fun `a known-device key never appears in vanishedHosts or reappearedHosts`() {
        val host = confirmedHost("192.168.1.5")
        val key = host.nicknameKey()
        val onceMissed = mapOf(key to record(key, consecutiveMissedSweeps = 1))

        val vanishAttempt =
            diffLanPresence(
                previousRecords = onceMissed,
                currentConfirmedHosts = emptyList(),
                nowMillis = 200L,
                knownDeviceKeys = setOf(key),
            )
        assertThat(vanishAttempt.vanishedHosts).isEmpty()
        // Threshold-crossing bookkeeping still happens even though the alert is suppressed.
        assertThat(vanishAttempt.updatedRecords.single().vanishedAlertSent).isTrue()

        val reappearAttempt =
            diffLanPresence(
                previousRecords = vanishAttempt.updatedRecords.associateBy { it.key },
                currentConfirmedHosts = listOf(host),
                nowMillis = 300L,
                knownDeviceKeys = setOf(key),
            )
        assertThat(reappearAttempt.reappearedHosts).isEmpty()
    }

    @Test
    fun `clearing the known-device flag resumes normal alerting`() {
        val key = confirmedHost("192.168.1.5").nicknameKey()
        val onceMissed = mapOf(key to record(key, consecutiveMissedSweeps = 1))

        val diff =
            diffLanPresence(
                previousRecords = onceMissed,
                currentConfirmedHosts = emptyList(),
                nowMillis = 200L,
                knownDeviceKeys = emptySet(),
            )

        assertThat(diff.vanishedHosts.map { it.key }).containsExactly(key)
    }
}
