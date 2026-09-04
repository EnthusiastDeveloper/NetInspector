package dev.enthusiastdev.netinspector.core.model.diagnostics

import dev.enthusiastdev.netinspector.core.model.connection.NetworkTransport
import java.net.InetAddress

/** design §9.4 - what a single `ConnectivityManager` network has registered for DNS.
 * [ipv4Servers]/[ipv6Servers] are `List<InetAddress>` rather than the statically-typed
 * `Inet4Address`/`Inet6Address` purely so matching against a queried [InetAddress] doesn't
 * fight Kotlin's generic variance - both lists are already split by runtime type when built. */
data class RegisteredDnsNetwork(
    val transport: NetworkTransport,
    val ipv4Servers: List<InetAddress>,
    val ipv6Servers: List<InetAddress>,
    val isPrivateDnsActive: Boolean,
    val privateDnsServerName: String?,
)

/** design §9.4 - the server a specific DNS lookup actually queried, distinct from
 * [RegisteredDnsNetwork] which describes what the OS has configured. [SystemResolver] carries
 * no address: the literal destination `DnsResolver` used is not observable from the app (it's
 * resolved inside netd), so showing a guessed one would violate design §11.3's "absent data
 * reads unknown, never a plausible-looking default." */
sealed interface QueriedDnsServer {
    data class Explicit(
        val address: InetAddress,
        val port: Int,
        val matchesRegistered: Boolean,
    ) : QueriedDnsServer

    data object SystemResolver : QueriedDnsServer
}
