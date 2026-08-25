package dev.enthusiastdev.netinspector.ui.screens.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

/** Grouping for the tools grid. A flat grid of a dozen equally-weighted tiles gave the eye
 * nothing to anchor on - three short, labelled runs turn "find the tool I want" into scanning
 * one group of three or four rather than the whole screen. */
enum class ToolCategory(
    val label: String,
) {
    DIAGNOSTICS("Diagnostics"),
    UTILITIES("Utilities"),
    HISTORY("History"),
}

/** design plan Phase 7 - the nine diagnostic tools (design §9), each its own nested route.
 * Phase 8 folds the two History viewers in alongside them: neither is a diagnostic tool a user
 * runs, but design §11.1's bottom nav table has no fifth slot for them, and Wake-on-LAN already
 * established that this grid isn't reserved for diagnostics-only entries. Settings used to sit
 * here too and no longer does - it configures the app rather than producing a result, so it now
 * has its own top-level destination.
 *
 * Entries are declared in the order they appear on screen, grouped by [category]. */
enum class Tool(
    val label: String,
    val icon: ImageVector,
    val category: ToolCategory,
) {
    PING("Ping", Icons.Filled.NetworkPing, ToolCategory.DIAGNOSTICS),
    TRACEROUTE("Traceroute", Icons.Filled.Router, ToolCategory.DIAGNOSTICS),
    DNS("DNS lookup", Icons.Filled.Dns, ToolCategory.DIAGNOSTICS),
    PORT_SCANNER("Port scanner", Icons.Filled.Radar, ToolCategory.DIAGNOSTICS),

    // "LAN throughput test," never bare "Speed test" - docs/improvement-ideas.md #31's rescope
    // exists specifically to avoid reading as an internet speed-test app (see the tool screen's
    // own explanatory copy and docs/adr/0009-lan-throughput-icmp-burst-estimate.md).
    LAN_THROUGHPUT("LAN throughput test", Icons.Filled.NetworkCheck, ToolCategory.DIAGNOSTICS),
    HTTP_INSPECTOR("HTTP headers", Icons.Filled.Http, ToolCategory.DIAGNOSTICS),
    WHOIS("WHOIS", Icons.Filled.Public, ToolCategory.DIAGNOSTICS),
    SUBNET_CALCULATOR("Subnet calculator", Icons.Filled.Calculate, ToolCategory.UTILITIES),
    SIGNAL_METER("Signal meter", Icons.Filled.SignalCellularAlt, ToolCategory.UTILITIES),
    WAKE_ON_LAN("Wake-on-LAN", Icons.Filled.SettingsEthernet, ToolCategory.UTILITIES),
    SCAN_HISTORY("Wi-Fi history", Icons.Filled.Timeline, ToolCategory.HISTORY),
    DIAGNOSTIC_HISTORY("Diagnostic history", Icons.Filled.History, ToolCategory.HISTORY),
    WIFI_CHANGES("Wi-Fi changes", Icons.Filled.Compare, ToolCategory.HISTORY),
}
