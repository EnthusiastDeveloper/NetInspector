package dev.enthusiastdev.netinspector.ui.screens.devices

import dev.enthusiastdev.netinspector.core.designsystem.graph.NetworkMapNode
import dev.enthusiastdev.netinspector.core.model.lan.Host

/** [hub] is the gateway host, if one was identified this sweep; [spokes] is every other host,
 * in the order they should be laid out around it. */
internal data class DevicesNetworkMapData(
    val hub: NetworkMapNode?,
    val spokes: List<NetworkMapNode>,
)

/** design idea #10 - a logical hub-and-spoke view over whatever `LanDiscoveryRepository` already
 * found, keyed identically to [HostCard]'s click target (the dotted-quad address string) so a
 * tapped map node reuses the exact same [DevicesDetailPane] navigation as the list. */
internal fun List<Host>.toNetworkMapData(): DevicesNetworkMapData {
    val hub = firstOrNull { it.isGateway }
    val spokes = filterNot { it.isGateway }
    return DevicesNetworkMapData(
        hub = hub?.toNetworkMapNode(),
        spokes = spokes.map { it.toNetworkMapNode() },
    )
}

private fun Host.toNetworkMapNode(): NetworkMapNode =
    NetworkMapNode(
        id = address.addressString,
        label = mapLabel(),
        isSelf = isSelf,
        isAtRisk = openPorts.isNotEmpty(),
    )

/** [displayName] is too wide for a node packed among several others at the map's scale - this
 * favors the last IP octet for a regular host, since that's still enough to tell nodes apart at
 * a glance, and "You"/the full gateway label for the two nodes the map singles out anyway. */
internal fun Host.mapLabel(): String =
    when {
        isGateway -> displayName()
        isSelf -> "You"
        else -> address.addressString.substringAfterLast('.')
    }
