package dev.enthusiastdev.netinspector.core.common.net

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

private fun ip(text: String) = InetAddress.getByName(text) as Inet4Address

class VlsmTest {
    @Test
    fun `allocates decreasing-size blocks sequentially without overlap`() {
        val pool = Ipv4Subnet(ip("192.168.1.0"), 24)

        val allocations = pool.splitForHostCounts(listOf(100, 50, 20, 2))!!

        // A request for exactly 2 hosts fits a /31 (RFC 3021 point-to-point, both addresses
        // usable) at least as tightly as a /30, so /31 - the smaller block - wins.
        assertThat(allocations.map { it.subnet.prefixLength }).containsExactly(25, 26, 27, 31).inOrder()
        assertThat(allocations[0].subnet.networkAddress).isEqualTo(ip("192.168.1.0")) // /25: .0-.127
        assertThat(allocations[1].subnet.networkAddress).isEqualTo(ip("192.168.1.128")) // /26: .128-.191
        assertThat(allocations[2].subnet.networkAddress).isEqualTo(ip("192.168.1.192")) // /27: .192-.223
        assertThat(allocations[3].subnet.networkAddress).isEqualTo(ip("192.168.1.224")) // /31: .224-.225
    }

    @Test
    fun `returns null when the pool is exhausted`() {
        val pool = Ipv4Subnet(ip("192.168.1.0"), 28) // 14 usable hosts

        assertThat(pool.splitForHostCounts(listOf(10, 10))).isNull()
    }

    @Test
    fun `a single request exactly fills a slash-24`() {
        val pool = Ipv4Subnet(ip("10.0.0.0"), 24)

        val allocations = pool.splitForHostCounts(listOf(254))!!

        assertThat(allocations.single().subnet).isEqualTo(pool)
    }
}
