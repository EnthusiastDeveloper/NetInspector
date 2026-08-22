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

    @Test
    fun `portRiskRemediation suggests SSH in place of telnet`() {
        assertThat(portRiskRemediation(23)).contains("SSH")
    }

    @Test
    fun `portRiskRemediation returns null for a port with no notable risk`() {
        assertThat(portRiskRemediation(8009)).isNull()
    }

    @Test
    fun `allFlaggedPorts covers every port portRiskNote flags, sorted ascending`() {
        val ports = allFlaggedPorts()
        assertThat(ports.map { it.first }).isEqualTo(ports.map { it.first }.sorted())
        assertThat(ports.map { it.first }).containsExactly(21, 23, 25, 110, 143, 1723, 3389, 5900)
        ports.forEach { (port, risk) ->
            assertThat(portRiskNote(port)).isEqualTo("${risk.protocol} - ${risk.reason}")
            assertThat(portRiskSeverity(port)).isEqualTo(risk.severity)
            assertThat(portRiskRemediation(port)).isEqualTo(risk.remediation)
        }
    }
}
