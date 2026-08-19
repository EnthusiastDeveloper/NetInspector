package dev.enthusiastdev.netinspector.core.model.wifi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelSpanTest {
    @Test
    fun `channelWidthMhz maps known platform values`() {
        assertThat(channelWidthMhz(0)).isEqualTo(20)
        assertThat(channelWidthMhz(1)).isEqualTo(40)
        assertThat(channelWidthMhz(2)).isEqualTo(80)
        assertThat(channelWidthMhz(3)).isEqualTo(160)
        assertThat(channelWidthMhz(4)).isEqualTo(80) // 80+80 MHz - each segment is 80 MHz
        assertThat(channelWidthMhz(5)).isEqualTo(320) // Wi-Fi 7, compared as a raw int (design §6.2)
    }

    @Test
    fun `channelWidthMhz defaults unknown values to 20`() {
        assertThat(channelWidthMhz(99)).isEqualTo(20)
    }

    @Test
    fun `lowMhz and highMhz bracket the span symmetrically around the center`() {
        val span = ChannelSpan(centerMhz = 5210, widthMhz = 80, primaryChannel = 36, band = Band.GHZ_5)
        assertThat(span.lowMhz).isEqualTo(5170)
        assertThat(span.highMhz).isEqualTo(5250)
    }
}
