package dev.enthusiastdev.netinspector.data.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.connection.LinkAddressInfo
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.bandOf
import dev.enthusiastdev.netinspector.core.model.wifi.freqToChannel
import dev.enthusiastdev.netinspector.core.model.wifi.wifiStandardOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.net.Inet6Address
import javax.inject.Inject

/**
 * Wraps `ConnectivityManager.NetworkCallback` (design §5) as a cold `Flow`. `null` means "not
 * currently connected to Wi-Fi" - distinct from a `ConnectionSnapshot` with null fields, which
 * means "connected, but a specific field couldn't be read."
 */
class ConnectivityDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun connectionSnapshots(): Flow<ConnectionSnapshot?> =
            callbackFlow {
                val connectivityManager =
                    requireNotNull(context.getSystemService(ConnectivityManager::class.java)) {
                        "ConnectivityManager unavailable"
                    }

                var lastCapabilities: NetworkCapabilities? = null
                var lastLinkProperties: LinkProperties? = null

                fun emitMerged() {
                    val caps = lastCapabilities
                    val props = lastLinkProperties
                    trySend(if (caps != null && props != null) caps.toConnectionSnapshot(props) else null)
                }

                val callback =
                    object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            lastCapabilities = networkCapabilities
                            emitMerged()
                        }

                        override fun onLinkPropertiesChanged(
                            network: Network,
                            linkProperties: LinkProperties,
                        ) {
                            lastLinkProperties = linkProperties
                            emitMerged()
                        }

                        override fun onLost(network: Network) {
                            lastCapabilities = null
                            lastLinkProperties = null
                            trySend(null)
                        }

                        override fun onUnavailable() {
                            trySend(null)
                        }
                    }

                val request =
                    NetworkRequest
                        .Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()

                connectivityManager.registerNetworkCallback(request, callback)

                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }
    }

private fun NetworkCapabilities.toConnectionSnapshot(linkProperties: LinkProperties): ConnectionSnapshot {
    val wifiInfo = transportInfo as? WifiInfo

    val addresses = linkProperties.linkAddresses
    val ipv4 =
        addresses
            .firstOrNull { it.address is Inet4Address }
            ?.let { LinkAddressInfo(it.address, it.prefixLength) }
    val ipv6 =
        addresses
            .filter { it.address is Inet6Address }
            .map { LinkAddressInfo(it.address, it.prefixLength) }
    // A dual-stack network has both an IPv4 and an IPv6 default route; without the type
    // filter, whichever happens to come first in the list wins and an IPv6 gateway silently
    // discards a perfectly good IPv4 one via the `as?` cast below.
    val gateway =
        linkProperties.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway as? Inet4Address

    return ConnectionSnapshot(
        ssid = wifiInfo?.ssid?.normalizedSsid(),
        bssid = wifiInfo?.bssid?.normalizedBssid(),
        rssiDbm = wifiInfo?.rssi,
        txLinkSpeedMbps = wifiInfo?.txLinkSpeedMbps,
        rxLinkSpeedMbps = wifiInfo?.rxLinkSpeedMbps,
        span = wifiInfo?.frequency?.let(::primaryChannelSpan),
        standard = wifiStandardOf(wifiInfo?.wifiStandard ?: -1),
        ipv4 = ipv4,
        ipv6 = ipv6,
        gateway = gateway,
        dnsServers = linkProperties.dnsServers,
        domains = linkProperties.domains,
        hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        isCaptivePortal = hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
        isMetered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
}

/**
 * `WifiInfo.getFrequency()` is the primary 20 MHz channel's own frequency, which is genuinely
 * accurate regardless of bonded width - but the *bonded* channel width (40/80/160/320 MHz)
 * isn't derivable from `WifiInfo` alone, only from cross-referencing a `ScanResult` (Phase 3).
 * `widthMhz = 20` here describes the primary channel itself, not a guess at the bonded width.
 */
private fun primaryChannelSpan(frequencyMhz: Int): ChannelSpan? {
    val channel = freqToChannel(frequencyMhz) ?: return null
    return ChannelSpan(
        centerMhz = frequencyMhz,
        widthMhz = 20,
        primaryChannel = channel,
        band = bandOf(frequencyMhz),
    )
}

/** `WifiInfo.getSSID()` returns `<unknown ssid>` without NEARBY_WIFI_DEVICES (C-03), and
 * legitimate SSIDs come quoted unless non-UTF-8. Both are normalized away here so the UI layer
 * only has to reason about "known SSID" vs "unknown" (permission vs. hidden is a UI concern -
 * see the Phase 1 risk note in the implementation plan). */
private fun String.normalizedSsid(): String? =
    when {
        this == "<unknown ssid>" -> null
        length >= 2 && startsWith("\"") && endsWith("\"") -> substring(1, length - 1)
        else -> this
    }

private fun String.normalizedBssid(): String? = if (this == "02:00:00:00:00:00") null else this
