package dev.enthusiastdev.netinspector.ui.screens.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

/** design plan Phase 7 - the nine diagnostic tools (design §9), each its own nested route.
 * Phase 8 folds the two History viewers in alongside them: neither is a diagnostic tool a user
 * runs, but design §11.1's bottom nav table has no fifth slot for them, and Wake-on-LAN already
 * established that this grid isn't reserved for diagnostics-only entries. */
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
    SCAN_HISTORY("Wi-Fi history", Icons.Filled.Timeline),
    DIAGNOSTIC_HISTORY("Diagnostic history", Icons.Filled.History),
}
