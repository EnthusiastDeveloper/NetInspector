package dev.enthusiastdev.netinspector.ui.screens.tools.throughput

import dev.enthusiastdev.netinspector.core.model.diagnostics.ThroughputResult

/** One entry in the "known devices" dropdown (requirement A) - [address] is what actually goes
 * into the target field on selection, [label] is a human name for the menu row. */
data class HostOption(
    val address: String,
    val label: String,
)

/** design §5.1/§7 correlation snapshot (improvement-ideas.md #31's differentiator), captured
 * once at test start and once at completion so a result can be read against *both* - a test
 * that starts on a strong signal and ends on a weak one (walking away from the AP mid-test)
 * should not be misread as "this network is always this fast." `null` fields mean the app
 * wasn't connected to Wi-Fi at that moment, not an omitted measurement. */
data class WifiCorrelationSnapshot(
    val ssid: String?,
    val bssid: String?,
    val rssiDbm: Int?,
    val channel: Int?,
    val widthMhz: Int?,
    val overlappingApCount: Int?,
)

data class ThroughputUiState(
    val target: String = "",
    val hostOptions: List<HostOption> = emptyList(),
    val isRunning: Boolean = false,
    val mbpsSamples: List<Float> = emptyList(),
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val result: ThroughputResult? = null,
    val correlationAtStart: WifiCorrelationSnapshot? = null,
    val correlationAtEnd: WifiCorrelationSnapshot? = null,
    val errorMessage: String? = null,
)
