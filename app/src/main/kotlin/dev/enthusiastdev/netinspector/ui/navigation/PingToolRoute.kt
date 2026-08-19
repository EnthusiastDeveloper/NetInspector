package dev.enthusiastdev.netinspector.ui.navigation

import kotlinx.serialization.Serializable

// Nested routes reachable from the Tools tab. Phase 7 populates the full nine-tool grid
// (design plan Phase 7); Ping is the only one built so far (Phase 2). `target` pre-fills the
// ping target for deep links from elsewhere in the app (design Phase 6 - the Devices detail
// pane's "Ping this host" action).
@Serializable data class PingToolRoute(
    val target: String? = null,
)
