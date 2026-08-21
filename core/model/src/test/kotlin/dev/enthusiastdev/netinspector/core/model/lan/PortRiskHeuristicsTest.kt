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

    @Test
    fun `portRiskSeverity rates telnet and VNC as CRITICAL - commonly no auth barrier at all`() {
        assertThat(portRiskSeverity(23)).isEqualTo(PortRiskSeverity.CRITICAL)
        assertThat(portRiskSeverity(5900)).isEqualTo(PortRiskSeverity.CRITICAL)
    }

    @Test
    fun `portRiskSeverity rates FTP, PPTP and RDP as HIGH`() {
        assertThat(portRiskSeverity(21)).isEqualTo(PortRiskSeverity.HIGH)
        assertThat(portRiskSeverity(1723)).isEqualTo(PortRiskSeverity.HIGH)
        assertThat(portRiskSeverity(3389)).isEqualTo(PortRiskSeverity.HIGH)
    }

    @Test
    fun `portRiskSeverity rates the mail protocols as MODERATE`() {
        assertThat(portRiskSeverity(25)).isEqualTo(PortRiskSeverity.MODERATE)
        assertThat(portRiskSeverity(110)).isEqualTo(PortRiskSeverity.MODERATE)
        assertThat(portRiskSeverity(143)).isEqualTo(PortRiskSeverity.MODERATE)
    }

    @Test
    fun `portRiskSeverity returns null for a port with no notable risk`() {
        assertThat(portRiskSeverity(8009)).isNull()
    }
}
