package dev.enthusiastdev.netinspector.data.lan.netbios

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Clock

/**
 * RFC 1002 §4.2.18 byte-offset math is fiddly and there's no guarantee a NetBIOS/SMB
 * responder is reachable in every environment this test runs in, so this builds a synthetic
 * NBSTAT response by hand rather than relying on a live one - see [NetBiosProbe]'s own doc
 * comment and docs/ideas.md A3.
 */
class NetBiosProbeTest {
    private val probe = NetBiosProbe(Clock.systemUTC())

    @Test
    fun `parseNbstatResponse extracts the workstation name and the STATISTICS field MAC`() {
        val mac = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
        val packet = nbstatResponse(name = "TESTPC", macAddress = mac)

        val result = probe.parseNbstatResponse(packet, packet.size)

        assertThat(result?.name).isEqualTo("TESTPC")
        assertThat(result?.macAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `parseNbstatResponse returns a null MAC when the STATISTICS field is zero-filled`() {
        val packet = nbstatResponse(name = "TESTPC", macAddress = ByteArray(6))

        val result = probe.parseNbstatResponse(packet, packet.size)

        assertThat(result?.name).isEqualTo("TESTPC")
        assertThat(result?.macAddress).isNull()
    }

    @Test
    fun `parseNbstatResponse skips a group name and reports only the unique workstation entry`() {
        val packet =
            nbstatResponse(
                name = "TESTPC",
                macAddress = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55),
                extraGroupNameFirst = true,
            )

        val result = probe.parseNbstatResponse(packet, packet.size)

        assertThat(result?.name).isEqualTo("TESTPC")
        assertThat(result?.macAddress).isEqualTo("00:11:22:33:44:55")
    }

    @Test
    fun `parseNbstatResponse returns null when ANCOUNT is zero`() {
        val packet = nbstatResponse(name = "TESTPC", macAddress = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55))
        packet[7] = 0x00 // ANCOUNT low byte -> 0

        assertThat(probe.parseNbstatResponse(packet, packet.size)).isNull()
    }

    @Test
    fun `parseNbstatResponse returns null for a truncated packet`() {
        val packet = ByteArray(HEADER_SIZE - 1)

        assertThat(probe.parseNbstatResponse(packet, packet.size)).isNull()
    }

    /**
     * Builds a minimal, well-formed NBSTAT response: a 12-byte header (ANCOUNT=1), an echoed
     * RR name of the RFC-mandated encoded length, then RDATA = NUM_NAMES + name entries +
     * STATISTICS (whose first 6 bytes are the MAC). Byte offsets mirror [NetBiosProbe]'s own
     * `HEADER_SIZE`/`ENCODED_NAME_LENGTH`/`NAME_ENTRY_SIZE` constants (RFC 1002 §4.2.18), not
     * re-derived independently, since this test is about the parsing math, not the RFC layout.
     */
    private fun nbstatResponse(
        name: String,
        macAddress: ByteArray,
        extraGroupNameFirst: Boolean = false,
    ): ByteArray {
        val numNames = if (extraGroupNameFirst) 2 else 1
        val rdataStart = HEADER_SIZE + (1 + ENCODED_NAME_LENGTH + 1) + 2 + 2 + 4 + 2
        val namesStart = rdataStart + 1
        val statisticsOffset = namesStart + numNames * NAME_ENTRY_SIZE
        val packet = ByteArray(statisticsOffset + macAddress.size)

        packet[7] = 0x01 // ANCOUNT = 1 (low byte; high byte already zero)
        packet[rdataStart] = numNames.toByte()

        var entryOffset = namesStart
        if (extraGroupNameFirst) {
            writeNameEntry(packet, entryOffset, "GROUP", type = 0x00, isGroup = true)
            entryOffset += NAME_ENTRY_SIZE
        }
        writeNameEntry(packet, entryOffset, name, type = 0x00, isGroup = false)

        macAddress.copyInto(packet, statisticsOffset)
        return packet
    }

    private fun writeNameEntry(
        packet: ByteArray,
        offset: Int,
        name: String,
        type: Int,
        isGroup: Boolean,
    ) {
        name.padEnd(NETBIOS_NAME_SIZE, ' ').toByteArray(Charsets.US_ASCII).copyInto(packet, offset)
        packet[offset + NETBIOS_NAME_SIZE] = type.toByte()
        packet[offset + NETBIOS_NAME_SIZE + 1] = (if (isGroup) 0x80 else 0x00).toByte()
    }

    private companion object {
        const val HEADER_SIZE = 12
        const val NETBIOS_NAME_SIZE = 15
        const val NAME_ENTRY_SIZE = 18
        const val ENCODED_NAME_LENGTH = 32
    }
}
