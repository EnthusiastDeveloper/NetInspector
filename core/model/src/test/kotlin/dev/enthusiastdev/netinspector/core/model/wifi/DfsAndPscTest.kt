package dev.enthusiastdev.netinspector.core.model.wifi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DfsAndPscTest {
    @Test
    fun `isDfsChannel flags both 5GHz DFS ranges`() {
        assertThat(isDfsChannel(Band.GHZ_5, 52)).isTrue()
        assertThat(isDfsChannel(Band.GHZ_5, 64)).isTrue()
        assertThat(isDfsChannel(Band.GHZ_5, 100)).isTrue()
        assertThat(isDfsChannel(Band.GHZ_5, 144)).isTrue()
    }

    @Test
    fun `isDfsChannel is false for non-DFS 5GHz channels and the gap between ranges`() {
        assertThat(isDfsChannel(Band.GHZ_5, 36)).isFalse() // UNII-1, non-DFS
        assertThat(isDfsChannel(Band.GHZ_5, 165)).isFalse() // UNII-3, non-DFS
        assertThat(isDfsChannel(Band.GHZ_5, 68)).isFalse() // gap between the two DFS ranges
    }

    @Test
    fun `isDfsChannel is false outside the 5GHz band even for numerically matching channels`() {
        assertThat(isDfsChannel(Band.GHZ_2_4, 52)).isFalse()
        assertThat(isDfsChannel(Band.GHZ_6, 100)).isFalse()
    }

    @Test
    fun `is6GhzPsc flags every 16th channel starting at 5`() {
        assertThat(is6GhzPsc(Band.GHZ_6, 5)).isTrue()
        assertThat(is6GhzPsc(Band.GHZ_6, 21)).isTrue()
        assertThat(is6GhzPsc(Band.GHZ_6, 37)).isTrue()
        assertThat(is6GhzPsc(Band.GHZ_6, 229)).isTrue() // 5 + 14*16
    }

    @Test
    fun `is6GhzPsc is false for non-PSC 6GHz channels`() {
        assertThat(is6GhzPsc(Band.GHZ_6, 1)).isFalse()
        assertThat(is6GhzPsc(Band.GHZ_6, 6)).isFalse()
        assertThat(is6GhzPsc(Band.GHZ_6, 20)).isFalse()
    }

    @Test
    fun `is6GhzPsc is false outside the 6GHz band even for numerically matching channels`() {
        assertThat(is6GhzPsc(Band.GHZ_5, 21)).isFalse()
    }
}
