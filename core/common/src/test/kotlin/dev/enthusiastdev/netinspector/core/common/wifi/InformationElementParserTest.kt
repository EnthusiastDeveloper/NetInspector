package dev.enthusiastdev.netinspector.core.common.wifi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InformationElementParserTest {
    @Test
    fun `country element yields the two-letter code, ignoring the regulatory indicator byte`() {
        val elements = listOf(7 to byteArrayOf('U'.code.toByte(), 'S'.code.toByte(), ' '.code.toByte()))
        assertThat(summarizeInformationElements(elements).countryCode).isEqualTo("US")
    }

    @Test
    fun `supported rates and extended supported rates are combined, masked and sorted`() {
        // 0x82=1.0 (basic), 0x84=2.0 (basic), 0x0B=5.5, 0x16=11.0 - mixed across both elements.
        val elements =
            listOf(
                1 to byteArrayOf(0x82.toByte(), 0x84.toByte()),
                50 to byteArrayOf(0x0B, 0x16),
            )
        assertThat(summarizeInformationElements(elements).supportedRatesMbps)
            .containsExactly(1.0, 2.0, 5.5, 11.0)
            .inOrder()
    }

    @Test
    fun `a Microsoft-OUI vendor element with WPS type 4 is detected as WPS`() {
        val wpsElement = byteArrayOf(0x00, 0x50, 0xF2.toByte(), 0x04, 0x10, 0x4A)
        val elements = listOf(221 to wpsElement)
        assertThat(summarizeInformationElements(elements).hasWps).isTrue()
    }

    @Test
    fun `a vendor element with a different OUI is not detected as WPS`() {
        val otherVendor = byteArrayOf(0x00, 0x17, 0xF2.toByte(), 0x04)
        val elements = listOf(221 to otherVendor)
        assertThat(summarizeInformationElements(elements).hasWps).isFalse()
    }

    @Test
    fun `a Microsoft-OUI vendor element with a different type is not detected as WPS`() {
        val nonWpsMicrosoft = byteArrayOf(0x00, 0x50, 0xF2.toByte(), 0x02)
        val elements = listOf(221 to nonWpsMicrosoft)
        assertThat(summarizeInformationElements(elements).hasWps).isFalse()
    }

    @Test
    fun `missing elements yield nulls and empty lists rather than throwing`() {
        val summary = summarizeInformationElements(emptyList())
        assertThat(summary.countryCode).isNull()
        assertThat(summary.supportedRatesMbps).isEmpty()
        assertThat(summary.hasWps).isFalse()
    }
}
