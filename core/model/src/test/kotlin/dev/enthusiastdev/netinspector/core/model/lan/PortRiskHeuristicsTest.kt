package dev.enthusiastdev.netinspector.core.model.lan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PortRiskHeuristicsTest {
    @Test
    fun `portRiskNote flags telnet as an unencrypted-credentials risk`() {
        assertThat(portRiskNote(23)).contains("plaintext")
    }

    @Test
    fun `portRiskNote flags VNC as commonly unauthenticated`() {
        assertThat(portRiskNote(5900)).contains("unauthenticated")
    }

    @Test
    fun `portRiskNote returns null for a port with no notable risk`() {
        assertThat(portRiskNote(8009)).isNull()
    }
}
