package dev.enthusiastdev.netinspector.data.diagnostics.dns

import dev.enthusiastdev.netinspector.core.model.connection.NetworkTransport
import dev.enthusiastdev.netinspector.core.model.diagnostics.QueriedDnsServer
import dev.enthusiastdev.netinspector.core.model.diagnostics.RegisteredDnsNetwork
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** design §9.4 - the port [RegisteredDnsServersDataSource] and [DefaultDnsRepository.queryServer]
 * both use. Kept separate from `DefaultDnsRepository`'s own private constant of the same value:
 * one literal, two call sites that live in different files for different reasons. */
const val DNS_PORT = 53

/** Pure transport classification, extracted from `NetworkCapabilities.hasTransport` so the
 * decision is JVM-testable without mocking an Android framework type. `null` means the network
 * doesn't match any of the three transports this app enumerates (VPN, Bluetooth PAN, etc.). */
fun networkTransportOf(
    isWifi: Boolean,
    isCellular: Boolean,
    isEthernet: Boolean,
): NetworkTransport? =
    when {
        isWifi -> NetworkTransport.WIFI
        isEthernet -> NetworkTransport.ETHERNET
        isCellular -> NetworkTransport.CELLULAR
        else -> null
    }

/** Builds a [RegisteredDnsNetwork] from `LinkProperties`' plain-value getters, splitting
 * [dnsServers] by runtime type - Android commonly returns both IPv4 and IPv6 servers for one
 * network, and the acceptance criteria calls for labeling them separately. */
fun registeredDnsNetworkOf(
    transport: NetworkTransport,
    dnsServers: List<InetAddress>,
    isPrivateDnsActive: Boolean,
    privateDnsServerName: String?,
): RegisteredDnsNetwork =
    RegisteredDnsNetwork(
        transport = transport,
        ipv4Servers = dnsServers.filter { it is Inet4Address },
        ipv6Servers = dnsServers.filter { it is Inet6Address },
        isPrivateDnsActive = isPrivateDnsActive,
        privateDnsServerName = privateDnsServerName,
    )

/** True if [queried] appears in any [networks]' registered server list, IPv4 or IPv6. */
fun matchesAnyRegisteredServer(
    queried: InetAddress,
    networks: List<RegisteredDnsNetwork>,
): Boolean = networks.any { queried in it.ipv4Servers || queried in it.ipv6Servers }

/** design §9.4 - the server a blank server field aims at: the first one the active network has
 * registered (IPv4 preferred - the raw-socket path is a plain UDP datagram, no scope-id
 * handling). `null` means fall back to the system resolver - either the active network has no
 * registered server to point at, or Private DNS is active and a cleartext UDP/53 query would
 * silently bypass the DoT the user configured (see
 * `docs/adr/c-20-private-dns-strict-mode-and-raw-sockets.md`). */
fun firstRegisteredDnsServer(
    activeTransport: NetworkTransport?,
    networks: List<RegisteredDnsNetwork>,
): InetAddress? {
    val network = networks.firstOrNull { it.transport == activeTransport } ?: return null
    if (network.isPrivateDnsActive) return null
    return network.ipv4Servers.firstOrNull() ?: network.ipv6Servers.firstOrNull()
}

/** Builds the "used for this lookup" indicator: `null` [queriedServer] means the query went
 * through the system resolver, which never exposes its literal destination to the app - see
 * [QueriedDnsServer.SystemResolver]'s doc. A non-null [queriedServer] is the raw-socket path,
 * where the destination is exactly what this function was given and can genuinely be checked
 * against [networks]. [autoSelected] distinguishes a server the user typed from one
 * [firstRegisteredDnsServer] picked for a blank field. */
fun queriedDnsServerOf(
    queriedServer: InetAddress?,
    networks: List<RegisteredDnsNetwork>,
    port: Int = DNS_PORT,
    autoSelected: Boolean = false,
): QueriedDnsServer =
    if (queriedServer == null) {
        QueriedDnsServer.SystemResolver
    } else {
        QueriedDnsServer.Explicit(
            address = queriedServer,
            port = port,
            matchesRegistered = matchesAnyRegisteredServer(queriedServer, networks),
            autoSelected = autoSelected,
        )
    }
