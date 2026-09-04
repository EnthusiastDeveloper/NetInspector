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

/** Builds the "used for this lookup" indicator: `null` [explicitServer] means the query went
 * through the system resolver (the default, blank-server case in the DNS tool), which never
 * exposes its literal destination to the app - see [QueriedDnsServer.SystemResolver]'s doc. A
 * non-null [explicitServer] is the raw-socket path, where the destination is exactly what this
 * function was given, and can genuinely be checked against [networks]. */
fun queriedDnsServerOf(
    explicitServer: InetAddress?,
    networks: List<RegisteredDnsNetwork>,
    port: Int = DNS_PORT,
): QueriedDnsServer =
    if (explicitServer == null) {
        QueriedDnsServer.SystemResolver
    } else {
        QueriedDnsServer.Explicit(
            address = explicitServer,
            port = port,
            matchesRegistered = matchesAnyRegisteredServer(explicitServer, networks),
        )
    }
