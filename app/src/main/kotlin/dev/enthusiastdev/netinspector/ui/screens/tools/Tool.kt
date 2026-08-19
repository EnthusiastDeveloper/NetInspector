package dev.enthusiastdev.netinspector.ui.screens.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.ui.graphics.vector.ImageVector

/** design plan Phase 7 - the nine diagnostic tools (design §9), each its own nested route. */
enum class Tool(
    val label: String,
    val icon: ImageVector,
) {
    PING("Ping", Icons.Filled.NetworkPing),
    TRACEROUTE("Traceroute", Icons.Filled.Router),
    DNS("DNS lookup", Icons.Filled.Dns),
    PORT_SCANNER("Port scanner", Icons.Filled.Radar),
    WAKE_ON_LAN("Wake-on-LAN", Icons.Filled.SettingsEthernet),
    WHOIS("WHOIS", Icons.Filled.Http),
    HTTP_INSPECTOR("HTTP headers", Icons.Filled.Http),
    SUBNET_CALCULATOR("Subnet calculator", Icons.Filled.Calculate),
    SIGNAL_METER("Signal meter", Icons.Filled.SignalCellularAlt),
}
