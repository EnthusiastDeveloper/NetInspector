package dev.enthusiastdev.netinspector.core.common.wifi

import dev.enthusiastdev.netinspector.core.model.wifi.InformationElementSummary

private const val ELEMENT_ID_SUPPORTED_RATES = 1
private const val ELEMENT_ID_COUNTRY = 7
private const val ELEMENT_ID_EXTENDED_SUPPORTED_RATES = 50
private const val ELEMENT_ID_VENDOR_SPECIFIC = 221
private const val RATE_MASK = 0x7F
private const val RATE_UNIT_MBPS = 0.5
private const val WPS_VENDOR_TYPE: Byte = 0x04
private val MICROSOFT_OUI = byteArrayOf(0x00, 0x50.toByte(), 0xF2.toByte())

/**
 * design §6.2 - plain IEEE 802.11 element IDs and layout, not device- or vendor-specific, so
 * (unlike the toybox/iputils ping parsers) hand-built byte arrays are a legitimate test
 * fixture here rather than a real capture standing in for an undocumented format.
 *
 * Rate bytes carry a high "basic rate" bit (§7.3.2.2) that isn't surfaced here - the detail
 * screen shows *what rates the AP supports*, not which are mandatory for association.
 */
fun summarizeInformationElements(elements: List<Pair<Int, ByteArray>>): InformationElementSummary {
    val countryCode =
        elements
            .firstOrNull { it.first == ELEMENT_ID_COUNTRY }
            ?.second
            ?.takeIf { it.size >= 2 }
            ?.let { String(it, 0, 2, Charsets.US_ASCII) }

    val supportedRates =
        elements
            .filter { it.first == ELEMENT_ID_SUPPORTED_RATES || it.first == ELEMENT_ID_EXTENDED_SUPPORTED_RATES }
            .flatMap { it.second.toList() }
            .map { (it.toInt() and RATE_MASK) * RATE_UNIT_MBPS }
            .sorted()

    val hasWps =
        elements.any { (id, bytes) ->
            id == ELEMENT_ID_VENDOR_SPECIFIC &&
                bytes.size >= MICROSOFT_OUI.size + 1 &&
                bytes.copyOfRange(0, MICROSOFT_OUI.size).contentEquals(MICROSOFT_OUI) &&
                bytes[MICROSOFT_OUI.size] == WPS_VENDOR_TYPE
        }

    return InformationElementSummary(countryCode, supportedRates, hasWps)
}
