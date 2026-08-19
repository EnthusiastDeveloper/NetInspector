package dev.enthusiastdev.netinspector.ui.screens.wifi

import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.Band

internal enum class WifiSortOrder { SIGNAL, NAME, CHANNEL }

/** design §6.3 - one entry per SSID, expandable to each BSSID; a hidden network (empty SSID)
 * is never grouped with other hidden networks, since an empty label doesn't mean "the same
 * network," only keyed by BSSID so each stays its own single-member group. */
internal data class WifiGroup(
    val ssid: String,
    val members: List<AccessPoint>,
)

internal fun List<AccessPoint>.toGroups(
    sortOrder: WifiSortOrder,
    bandFilter: Set<Band>,
): List<WifiGroup> {
    val filtered = if (bandFilter.isEmpty()) this else filter { it.span.band in bandFilter }
    val groups =
        filtered
            .groupBy { if (it.ssid.isNotEmpty()) "ssid:${it.ssid}" else "bssid:${it.bssid}" }
            .map { (_, members) ->
                WifiGroup(ssid = members.first().ssid, members = members.sortedByDescending { it.rssiDbm })
            }

    return when (sortOrder) {
        WifiSortOrder.SIGNAL -> groups.sortedByDescending { group -> group.members.maxOf { it.rssiDbm } }
        WifiSortOrder.NAME -> groups.sortedBy { it.ssid.ifEmpty { "￿" } } // hidden sorts last
        WifiSortOrder.CHANNEL -> groups.sortedBy { group -> group.members.minOf { it.span.primaryChannel } }
    }
}
