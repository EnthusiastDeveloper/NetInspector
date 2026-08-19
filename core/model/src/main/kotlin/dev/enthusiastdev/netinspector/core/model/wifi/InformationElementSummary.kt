package dev.enthusiastdev.netinspector.core.model.wifi

/** design §6.2 - parsed lazily, only for the AP the detail screen is currently showing. */
data class InformationElementSummary(
    val countryCode: String?,
    val supportedRatesMbps: List<Double>,
    val hasWps: Boolean,
)
