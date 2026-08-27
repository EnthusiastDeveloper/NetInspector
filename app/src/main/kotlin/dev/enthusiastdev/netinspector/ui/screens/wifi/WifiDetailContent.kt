package dev.enthusiastdev.netinspector.ui.screens.wifi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.enthusiastdev.netinspector.core.common.wifi.rssiToQualityPercent
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoCard
import dev.enthusiastdev.netinspector.core.designsystem.component.InfoRow
import dev.enthusiastdev.netinspector.core.designsystem.gauge.RssiGauge
import dev.enthusiastdev.netinspector.core.model.settings.RssiDisplayUnit
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.InformationElementSummary
import dev.enthusiastdev.netinspector.data.persistence.scan.KnownApEntity
import dev.enthusiastdev.netinspector.ui.screens.connection.label

/** Owns fetching the on-demand [InformationElementSummary] for whichever BSSID the list-detail
 * scaffold currently has selected (design §6.2: parsed lazily, per-AP, not on every scan). */
@Composable
internal fun WifiDetailPane(
    bssid: String,
    accessPoints: List<AccessPoint>,
    apCapabilityChanges: Map<String, KnownApEntity>,
    informationElementsFor: (String) -> InformationElementSummary,
    rssiDisplayUnit: RssiDisplayUnit,
    modifier: Modifier = Modifier,
) {
    val accessPoint = accessPoints.firstOrNull { it.bssid == bssid }
    if (accessPoint == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("This network is no longer in range", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val informationElements = remember(bssid) { informationElementsFor(bssid) }
    WifiDetailContent(accessPoint, apCapabilityChanges[bssid], informationElements, rssiDisplayUnit, modifier)
}

@Composable
internal fun WifiDetailContent(
    accessPoint: AccessPoint,
    capabilityChange: KnownApEntity? = null,
    informationElements: InformationElementSummary,
    rssiDisplayUnit: RssiDisplayUnit = RssiDisplayUnit.DBM,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { WifiDetailHeader(accessPoint, rssiDisplayUnit) }
        if (capabilityChange != null) {
            item { WifiDetailCapabilityChangeSection(capabilityChange) }
        }
        item { WifiDetailRadioSection(accessPoint) }
        item { WifiDetailSecuritySection(accessPoint) }
        item { WifiDetailInformationElementsSection(informationElements) }
    }
}

@Composable
private fun WifiDetailHeader(
    accessPoint: AccessPoint,
    rssiDisplayUnit: RssiDisplayUnit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        RssiGauge(
            rssiDbm = accessPoint.rssiDbm,
            qualityPercent = rssiToQualityPercent(accessPoint.rssiDbm),
            showAsPercent = rssiDisplayUnit == RssiDisplayUnit.PERCENT,
        )
        Text(
            text = accessPoint.ssid.ifEmpty { "<hidden>" },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = accessPoint.bssid,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** ideas.md #11 - only rendered when [KnownApEntity.toCapabilityChangeDisplay]
 * returns non-null, i.e. there's a complete before/after snapshot and it's actually notable. */
@Composable
private fun WifiDetailCapabilityChangeSection(knownAp: KnownApEntity) {
    val display = knownAp.toCapabilityChangeDisplay() ?: return
    val previousSecurity = display.previous.security.parsedSecurityLabel()
    val currentSecurity = display.current.security.parsedSecurityLabel()
    val previousStandard = display.previous.standard.parsedStandardLabel()
    val currentStandard = display.current.standard.parsedStandardLabel()

    InfoCard(title = "Capability change detected") {
        if (display.change.securityChanged) {
            InfoRow("Security", "$previousSecurity → $currentSecurity")
        }
        if (display.change.standardChanged) {
            InfoRow("Standard", "$previousStandard → $currentStandard")
        }
        if (display.change.channelChanged) {
            InfoRow("Channel", "${display.previous.primaryChannel} → ${display.current.primaryChannel}")
        }
        InfoRow("Detected", display.changedAt.asChangeTimestamp())
    }
}

@Composable
private fun WifiDetailRadioSection(accessPoint: AccessPoint) {
    InfoCard(title = "Radio") {
        val span = accessPoint.span
        InfoRow("Band", span.band.label())
        InfoRow("Channel", span.primaryChannel.toString())
        InfoRow("Width", "${span.widthMhz} MHz")
        InfoRow("Center frequency", "${span.centerMhz} MHz")
        accessPoint.secondarySpan?.let { InfoRow("Secondary center", "${it.centerMhz} MHz (80+80)") }
        InfoRow("Standard", accessPoint.standard.label())
        if (accessPoint.isDfsChannel) InfoRow("DFS", "Yes - radar events can cause disconnections")
        if (accessPoint.is6GhzPsc) InfoRow("6 GHz PSC", "Yes - a preferred scanning channel")
    }
}

@Composable
private fun WifiDetailSecuritySection(accessPoint: AccessPoint) {
    InfoCard(title = "Security") {
        InfoRow("Type", accessPoint.security.label())
        InfoRow("Signal", "${accessPoint.rssiDbm} dBm (${rssiToQualityPercent(accessPoint.rssiDbm)}%)")
        InfoRow("Connected", if (accessPoint.isConnected) "Yes" else "No")
        InfoRow("Vendor", accessPoint.vendor ?: "Unknown")
    }
}

@Composable
private fun WifiDetailInformationElementsSection(informationElements: InformationElementSummary) {
    InfoCard(title = "Information elements") {
        InfoRow("Country code", informationElements.countryCode ?: "Not advertised")
        val rates =
            informationElements.supportedRatesMbps
                .takeIf { it.isNotEmpty() }
                ?.joinToString { rate -> "${if (rate == rate.toLong().toDouble()) rate.toLong() else rate} Mbps" }
                ?: "Not advertised"
        InfoRow("Supported rates", rates)
        InfoRow("WPS", if (informationElements.hasWps) "Yes" else "No")
    }
}
