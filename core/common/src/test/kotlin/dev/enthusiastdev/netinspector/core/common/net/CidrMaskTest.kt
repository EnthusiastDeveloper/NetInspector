package dev.enthusiastdev.netinspector.core.common.net

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

private fun ip(text: String) = InetAddress.getByName(text) as Inet4Address

class CidrMaskTest {
    @Test
    fun `prefixLengthToNetmask covers common prefixes`() {
        assertThat(prefixLengthToNetmask(24)).isEqualTo(ip("255.255.255.0"))
        assertThat(prefixLengthToNetmask(30)).isEqualTo(ip("255.255.255.252"))
        assertThat(prefixLengthToNetmask(0)).isEqualTo(ip("0.0.0.0"))
        assertThat(prefixLengthToNetmask(32)).isEqualTo(ip("255.255.255.255"))
    }

    @Test
    fun `netmaskToPrefixLength round-trips with prefixLengthToNetmask`() {
        for (prefix in 0..32) {
            assertThat(netmaskToPrefixLength(prefixLengthToNetmask(prefix))).isEqualTo(prefix)
        }
    }

    @Test
    fun `netmaskToPrefixLength rejects a non-contiguous mask`() {
        assertThat(netmaskToPrefixLength(ip("255.0.255.0"))).isNull()
    }
}
