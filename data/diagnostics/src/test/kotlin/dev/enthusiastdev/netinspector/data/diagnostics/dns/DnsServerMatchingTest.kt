package dev.enthusiastdev.netinspector.data.diagnostics.dns

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.connection.NetworkTransport
import dev.enthusiastdev.netinspector.core.model.diagnostics.QueriedDnsServer
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

private fun ip(text: String): InetAddress = InetAddress.getByName(text)

class DnsServerMatchingTest {
    @Test
    fun `networkTransportOf prefers wifi over ethernet and cellular`() {
        assertThat(networkTransportOf(isWifi = true, isCellular = true, isEthernet = true))
            .isEqualTo(NetworkTransport.WIFI)
    }

    @Test
    fun `networkTransportOf prefers ethernet over cellular`() {
        assertThat(networkTransportOf(isWifi = false, isCellular = true, isEthernet = true))
            .isEqualTo(NetworkTransport.ETHERNET)
    }

    @Test
    fun `networkTransportOf returns cellular alone`() {
        assertThat(networkTransportOf(isWifi = false, isCellular = true, isEthernet = false))
            .isEqualTo(NetworkTransport.CELLULAR)
    }

    @Test
    fun `networkTransportOf returns null for an unrecognised transport`() {
        assertThat(networkTransportOf(isWifi = false, isCellular = false, isEthernet = false)).isNull()
    }

    @Test
    fun `registeredDnsNetworkOf splits servers by address family`() {
        val network =
            registeredDnsNetworkOf(
                transport = NetworkTransport.WIFI,
                dnsServers = listOf(ip("192.168.1.1"), ip("2001:4860:4860::8888")),
                isPrivateDnsActive = false,
                privateDnsServerName = null,
            )
        assertThat(network.ipv4Servers).containsExactly(ip("192.168.1.1"))
        assertThat(network.ipv6Servers).containsExactly(ip("2001:4860:4860::8888"))
    }

    @Test
    fun `registeredDnsNetworkOf handles no configured servers`() {
        val network =
            registeredDnsNetworkOf(
                transport = NetworkTransport.CELLULAR,
                dnsServers = emptyList(),
                isPrivateDnsActive = false,
                privateDnsServerName = null,
            )
        assertThat(network.ipv4Servers).isEmpty()
        assertThat(network.ipv6Servers).isEmpty()
    }

    @Test
    fun `registeredDnsNetworkOf carries private dns strict mode hostname`() {
        val network =
            registeredDnsNetworkOf(
                transport = NetworkTransport.WIFI,
                dnsServers = emptyList(),
                isPrivateDnsActive = true,
                privateDnsServerName = "dns.google",
            )
        assertThat(network.isPrivateDnsActive).isTrue()
        assertThat(network.privateDnsServerName).isEqualTo("dns.google")
    }

    @Test
    fun `matchesAnyRegisteredServer finds an ipv4 match on any network`() {
        val networks =
            listOf(
                registeredDnsNetworkOf(NetworkTransport.CELLULAR, listOf(ip("10.0.0.1")), false, null),
                registeredDnsNetworkOf(NetworkTransport.WIFI, listOf(ip("192.168.1.1")), false, null),
            )
        assertThat(matchesAnyRegisteredServer(ip("192.168.1.1"), networks)).isTrue()
    }

    @Test
    fun `matchesAnyRegisteredServer finds an ipv6 match`() {
        val networks =
            listOf(registeredDnsNetworkOf(NetworkTransport.WIFI, listOf(ip("2001:4860:4860::8888")), false, null))
        assertThat(matchesAnyRegisteredServer(ip("2001:4860:4860::8888"), networks)).isTrue()
    }

    @Test
    fun `matchesAnyRegisteredServer returns false when nothing matches`() {
        val networks = listOf(registeredDnsNetworkOf(NetworkTransport.WIFI, listOf(ip("192.168.1.1")), false, null))
        assertThat(matchesAnyRegisteredServer(ip("8.8.8.8"), networks)).isFalse()
    }

    @Test
    fun `matchesAnyRegisteredServer returns false against an empty network list`() {
        assertThat(matchesAnyRegisteredServer(ip("8.8.8.8"), emptyList())).isFalse()
    }

    @Test
    fun `queriedDnsServerOf returns SystemResolver when no explicit server was given`() {
        val networks = listOf(registeredDnsNetworkOf(NetworkTransport.WIFI, listOf(ip("192.168.1.1")), false, null))
        assertThat(queriedDnsServerOf(explicitServer = null, networks = networks))
            .isEqualTo(QueriedDnsServer.SystemResolver)
    }

    @Test
    fun `queriedDnsServerOf flags a match against a registered server`() {
        val networks = listOf(registeredDnsNetworkOf(NetworkTransport.WIFI, listOf(ip("192.168.1.1")), false, null))
        val result = queriedDnsServerOf(ip("192.168.1.1") as Inet4Address, networks)
        assertThat(result).isEqualTo(QueriedDnsServer.Explicit(ip("192.168.1.1"), DNS_PORT, matchesRegistered = true))
    }

    @Test
    fun `queriedDnsServerOf flags a custom server that matches nothing registered`() {
        val result = queriedDnsServerOf(ip("8.8.8.8") as Inet4Address, emptyList())
        assertThat(result).isEqualTo(QueriedDnsServer.Explicit(ip("8.8.8.8"), DNS_PORT, matchesRegistered = false))
    }
}
