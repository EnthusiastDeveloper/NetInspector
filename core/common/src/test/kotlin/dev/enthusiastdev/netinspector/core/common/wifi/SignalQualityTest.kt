package dev.enthusiastdev.netinspector.core.common.wifi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignalQualityTest {
    @Test
    fun `maps range endpoints to 0 and 100`() {
        assertThat(rssiToQualityPercent(-100)).isEqualTo(0)
        assertThat(rssiToQualityPercent(-30)).isEqualTo(100)
    }

    @Test
    fun `clamps values outside the defined range`() {
        assertThat(rssiToQualityPercent(-120)).isEqualTo(0)
        assertThat(rssiToQualityPercent(0)).isEqualTo(100)
    }

    @Test
    fun `interpolates linearly between endpoints`() {
        assertThat(rssiToQualityPercent(-65)).isEqualTo(50)
    }
}
