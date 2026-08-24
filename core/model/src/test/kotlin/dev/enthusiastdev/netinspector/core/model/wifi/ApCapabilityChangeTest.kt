package dev.enthusiastdev.netinspector.core.model.wifi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private val DEFAULT_SNAPSHOT = ApCapabilitySnapshot(security = "WPA2", standard = "AC", primaryChannel = 36)

class ApCapabilityChangeTest {
    @Test
    fun `identical capabilities are not notable`() {
        val change = diffApCapabilities(DEFAULT_SNAPSHOT, DEFAULT_SNAPSHOT)

        assertThat(change.securityChanged).isFalse()
        assertThat(change.standardChanged).isFalse()
        assertThat(change.channelChanged).isFalse()
        assertThat(change.isNotable).isFalse()
    }

    @Test
    fun `a security change is notable and only flags security`() {
        val change = diffApCapabilities(DEFAULT_SNAPSHOT, DEFAULT_SNAPSHOT.copy(security = "WPA3"))

        assertThat(change.securityChanged).isTrue()
        assertThat(change.standardChanged).isFalse()
        assertThat(change.channelChanged).isFalse()
        assertThat(change.isNotable).isTrue()
    }

    @Test
    fun `a standard change is notable and only flags standard`() {
        val change = diffApCapabilities(DEFAULT_SNAPSHOT, DEFAULT_SNAPSHOT.copy(standard = "AX"))

        assertThat(change.securityChanged).isFalse()
        assertThat(change.standardChanged).isTrue()
        assertThat(change.channelChanged).isFalse()
        assertThat(change.isNotable).isTrue()
    }

    @Test
    fun `a channel change is notable and only flags channel`() {
        val change = diffApCapabilities(DEFAULT_SNAPSHOT, DEFAULT_SNAPSHOT.copy(primaryChannel = 40))

        assertThat(change.securityChanged).isFalse()
        assertThat(change.standardChanged).isFalse()
        assertThat(change.channelChanged).isTrue()
        assertThat(change.isNotable).isTrue()
    }

    @Test
    fun `multiple fields can change at once`() {
        val change =
            diffApCapabilities(
                DEFAULT_SNAPSHOT,
                ApCapabilitySnapshot(security = "WPA3", standard = "AX", primaryChannel = 6),
            )

        assertThat(change.securityChanged).isTrue()
        assertThat(change.standardChanged).isTrue()
        assertThat(change.channelChanged).isTrue()
        assertThat(change.isNotable).isTrue()
    }
}
