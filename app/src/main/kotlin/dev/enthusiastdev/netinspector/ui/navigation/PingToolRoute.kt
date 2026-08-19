package dev.enthusiastdev.netinspector.ui.navigation

import kotlinx.serialization.Serializable

// Nested routes reachable from the Tools tab. Phase 7 populates the full nine-tool grid
// (design plan Phase 7); Ping is the only one built so far (Phase 2).
@Serializable data object PingToolRoute
