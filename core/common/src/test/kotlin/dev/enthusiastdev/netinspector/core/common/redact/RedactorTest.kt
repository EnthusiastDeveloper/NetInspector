package dev.enthusiastdev.netinspector.core.common.redact

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RedactorTest {
    @Test
    fun `redacts each RFC1918 range`() {
        assertThat(redactIps("host at 10.113.153.161 responded")).isEqualTo("host at <redacted-ip> responded")
        assertThat(redactIps("gateway 192.168.1.1")).isEqualTo("gateway <redacted-ip>")
        assertThat(redactIps("link-local 169.254.1.2")).isEqualTo("link-local <redacted-ip>")
        assertThat(redactIps("loopback 127.0.0.1")).isEqualTo("loopback <redacted-ip>")
    }

    @Test
    fun `redacts 172_16-31 slash12 but not adjacent addresses outside it`() {
        assertThat(redactIps("172.16.0.1")).isEqualTo("<redacted-ip>")
        assertThat(redactIps("172.31.255.254")).isEqualTo("<redacted-ip>")
        assertThat(redactIps("172.15.0.1")).isEqualTo("172.15.0.1")
        assertThat(redactIps("172.32.0.1")).isEqualTo("172.32.0.1")
    }

    @Test
    fun `leaves public IPs untouched`() {
        assertThat(redactIps("dns server 8.8.8.8")).isEqualTo("dns server 8.8.8.8")
    }

    @Test
    fun `redacts multiple occurrences`() {
        assertThat(redactIps("10.0.0.1 talked to 10.0.0.2"))
            .isEqualTo("<redacted-ip> talked to <redacted-ip>")
    }

    @Test
    fun `redacts known SSIDs longest match first to avoid partial leaks`() {
        val result = redactSsids("connected to Home 5G, seen Home earlier", setOf("Home", "Home 5G"))
        assertThat(result).isEqualTo("connected to <redacted-ssid>, seen <redacted-ssid> earlier")
    }

    @Test
    fun `ignores blank ssids`() {
        assertThat(redactSsids("hello world", setOf(""))).isEqualTo("hello world")
    }

    @Test
    fun `redact combines ip and ssid redaction`() {
        val result = redact("KIA NEW at 10.113.153.161", setOf("KIA NEW"))
        assertThat(result).isEqualTo("<redacted-ssid> at <redacted-ip>")
    }
}
