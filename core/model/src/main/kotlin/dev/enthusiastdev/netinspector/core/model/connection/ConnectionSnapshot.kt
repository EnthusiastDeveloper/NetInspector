package dev.enthusiastdev.netinspector.core.model.connection

import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.WifiStandard
import java.net.Inet4Address
import java.net.InetAddress

data class ConnectionSnapshot(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val txLinkSpeedMbps: Int?,
    val rxLinkSpeedMbps: Int?,
    val span: ChannelSpan?,
    val standard: WifiStandard,
    val ipv4: LinkAddressInfo?,
    val ipv6: List<LinkAddressInfo>, // display only
    val gateway: Inet4Address?,
    val dnsServers: List<InetAddress>,
    val domains: String?,
    val hasInternet: Boolean, // NET_CAPABILITY_VALIDATED
    val isCaptivePortal: Boolean,
    val isMetered: Boolean,
)
