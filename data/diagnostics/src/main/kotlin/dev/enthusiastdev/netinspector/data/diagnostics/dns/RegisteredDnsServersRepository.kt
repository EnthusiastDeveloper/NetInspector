package dev.enthusiastdev.netinspector.data.diagnostics.dns

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.core.model.connection.NetworkTransport
import dev.enthusiastdev.netinspector.core.model.diagnostics.RegisteredDnsNetwork
import javax.inject.Inject

/**
 * design §9.4 - the DNS tool's "registered on device" indicator, distinct from the DNS query
 * paths in [DnsRepository]. Unlike `data:wifi`'s `ConnectivityDataSource` (a continuous
 * Wi-Fi-only `NetworkCallback` flow, used for the live signal meter), [snapshot] and
 * [activeTransport] are one-shot reads across *all* networks - Wi-Fi, cellular and Ethernet can
 * legitimately be up at once (design's foldable/dual-network case), and a DNS lookup is a
 * point-in-time action, not something that needs a live subscription.
 */
interface RegisteredDnsServersRepository {
    /** One [RegisteredDnsNetwork] per currently-up Wi-Fi/cellular/Ethernet network. A network
     * with an unrecognised transport (VPN, Bluetooth PAN, etc.) or that returns null
     * capabilities/link properties (already torn down) is silently skipped. */
    fun snapshot(): List<RegisteredDnsNetwork>

    /** The transport of `activeNetwork` at the moment this is called - design's "which
     * network's config was active at query time, when multiple networks are up." */
    fun activeTransport(): NetworkTransport?
}

class DefaultRegisteredDnsServersRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RegisteredDnsServersRepository {
        override fun snapshot(): List<RegisteredDnsNetwork> {
            val connectivityManager = connectivityManagerOrNull() ?: return emptyList()
            return connectivityManager.allNetworks.mapNotNull { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
                val transport = capabilities.toNetworkTransport() ?: return@mapNotNull null
                val linkProperties = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
                registeredDnsNetworkOf(
                    transport = transport,
                    dnsServers = linkProperties.dnsServers,
                    isPrivateDnsActive = linkProperties.isPrivateDnsActive,
                    privateDnsServerName = linkProperties.privateDnsServerName,
                )
            }
        }

        override fun activeTransport(): NetworkTransport? {
            val connectivityManager = connectivityManagerOrNull() ?: return null
            val active = connectivityManager.activeNetwork ?: return null
            return connectivityManager.getNetworkCapabilities(active)?.toNetworkTransport()
        }

        private fun connectivityManagerOrNull(): ConnectivityManager? =
            context.getSystemService(ConnectivityManager::class.java)

        private fun NetworkCapabilities.toNetworkTransport(): NetworkTransport? =
            networkTransportOf(
                isWifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                isCellular = hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                isEthernet = hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            )
    }
