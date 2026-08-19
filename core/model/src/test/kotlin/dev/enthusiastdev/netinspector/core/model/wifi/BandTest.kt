package dev.enthusiastdev.netinspector.core.model.wifi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BandTest {
    @Test
    fun `bandOf classifies 2point4, 5 and 6 GHz correctly`() {
        assertThat(bandOf(2412)).isEqualTo(Band.GHZ_2_4)
        assertThat(bandOf(2484)).isEqualTo(Band.GHZ_2_4)
        assertThat(bandOf(5180)).isEqualTo(Band.GHZ_5)
        assertThat(bandOf(5825)).isEqualTo(Band.GHZ_5)
        assertThat(bandOf(5955)).isEqualTo(Band.GHZ_6)
        assertThat(bandOf(7115)).isEqualTo(Band.GHZ_6)
    }

    @Test
    fun `bandOf returns UNKNOWN outside defined ranges`() {
        assertThat(bandOf(1000)).isEqualTo(Band.UNKNOWN)
        assertThat(bandOf(5920)).isEqualTo(Band.UNKNOWN) // gap between 5 GHz and 6 GHz bands
        assertThat(bandOf(8000)).isEqualTo(Band.UNKNOWN)
    }

    @Test
    fun `freqToChannel maps standard 2point4GHz channels`() {
        assertThat(freqToChannel(2412)).isEqualTo(1)
        assertThat(freqToChannel(2437)).isEqualTo(6)
        assertThat(freqToChannel(2472)).isEqualTo(13)
    }

    @Test
    fun `freqToChannel handles channel 14 special case`() {
        assertThat(freqToChannel(2484)).isEqualTo(14)
    }

    @Test
    fun `freqToChannel maps standard 5GHz channels`() {
        assertThat(freqToChannel(5180)).isEqualTo(36)
        assertThat(freqToChannel(5825)).isEqualTo(165)
    }

    @Test
    fun `freqToChannel handles 6GHz channel 2 special case at 5935 MHz`() {
        assertThat(freqToChannel(5935)).isEqualTo(2)
    }

    @Test
    fun `freqToChannel maps standard 6GHz channels`() {
        assertThat(freqToChannel(5955)).isEqualTo(1)
        assertThat(freqToChannel(7115)).isEqualTo(233)
    }

    @Test
    fun `freqToChannel returns null for frequencies outside any Wi-Fi band`() {
        assertThat(freqToChannel(1000)).isNull()
        assertThat(freqToChannel(5920)).isNull()
    }

    @Test
    fun `wifiStandardOf maps known platform values`() {
        assertThat(wifiStandardOf(1)).isEqualTo(WifiStandard.LEGACY)
        assertThat(wifiStandardOf(4)).isEqualTo(WifiStandard.N)
        assertThat(wifiStandardOf(5)).isEqualTo(WifiStandard.AC)
        assertThat(wifiStandardOf(6)).isEqualTo(WifiStandard.AX)
        assertThat(wifiStandardOf(8)).isEqualTo(WifiStandard.BE)
    }

    @Test
    fun `wifiStandardOf maps unknown and unhandled values to UNKNOWN`() {
        assertThat(wifiStandardOf(0)).isEqualTo(WifiStandard.UNKNOWN)
        assertThat(wifiStandardOf(7)).isEqualTo(WifiStandard.UNKNOWN) // 11AD / 60 GHz WiGig - out of scope
        assertThat(wifiStandardOf(99)).isEqualTo(WifiStandard.UNKNOWN)
    }
}
