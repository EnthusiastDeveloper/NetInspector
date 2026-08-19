package dev.enthusiastdev.netinspector.core.common.net

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WakeOnLanTest {
    @Test
    fun `builds a 102-byte packet with a 6-byte sync stream and the MAC repeated 16 times`() {
        val packet = WakeOnLan.buildMagicPacket("AA:BB:CC:DD:EE:FF")!!

        assertThat(packet).hasLength(102)
        assertThat(packet.copyOfRange(0, 6).toList()).isEqualTo(List(6) { 0xFF.toByte() })
        val macBytes = listOf(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF).map { it.toByte() }
        for (rep in 0 until 16) {
            val start = 6 + rep * 6
            assertThat(packet.copyOfRange(start, start + 6).toList()).isEqualTo(macBytes)
        }
    }

    @Test
    fun `accepts hyphen-separated MACs`() {
        assertThat(WakeOnLan.buildMagicPacket("aa-bb-cc-dd-ee-ff")).isNotNull()
    }

    @Test
    fun `rejects a malformed MAC`() {
        assertThat(WakeOnLan.buildMagicPacket("not-a-mac")).isNull()
        assertThat(WakeOnLan.buildMagicPacket("AA:BB:CC:DD:EE")).isNull()
        assertThat(WakeOnLan.buildMagicPacket("GG:BB:CC:DD:EE:FF")).isNull()
    }
}
