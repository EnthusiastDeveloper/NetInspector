package dev.enthusiastdev.netinspector.core.designsystem.graph

/** One node on a [NetworkMapGraph]. [id] is opaque to this module (callers key it however their
 * own data model does - an IP string, a BSSID, ...) and is exactly what [NetworkMapGraph]'s tap
 * callback hands back, so the caller can look the underlying entity up itself. */
data class NetworkMapNode(
    val id: String,
    val label: String,
    val isSelf: Boolean = false,
    val isAtRisk: Boolean = false,
)
