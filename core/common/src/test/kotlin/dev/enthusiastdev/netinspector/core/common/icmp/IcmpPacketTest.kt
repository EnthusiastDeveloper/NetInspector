package dev.enthusiastdev.netinspector.core.common.icmp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IcmpPacketTest {
    @Test
    fun `internetChecksum matches the textbook echo-request example`() {
        // type=8, code=0, checksum=0, id=0, seq=0 -> checksum 0xF7FF (the canonical
        // worked example for the Internet checksum algorithm applied to an ICMP header).
        val header = byteArrayOf(0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        assertThat(IcmpPacket.internetChecksum(header)).isEqualTo(0xF7FF)
    }

    @Test
    fun `internetChecksum handles an odd-length buffer by padding the last byte`() {
        // A single 0xFF byte, treated as the high byte of a padded 16-bit word (0xFF00).
        val checksum = IcmpPacket.internetChecksum(byteArrayOf(0xFF.toByte()))

        assertThat(checksum).isEqualTo(0x00FF)
    }

    @Test
    fun `internetChecksum folds carries out of the 16-bit accumulator`() {
        // Two words that individually overflow 16 bits when summed: 0xFFFF + 0xFFFF = 0x1FFFE,
        // which folds to 0xFFFE + 1 = 0xFFFF, then complements to 0x0000 (the one's-complement
        // "negative zero" case - mathematically correct, distinct from the on-the-wire
        // convention of substituting 0xFFFF for a computed-zero checksum).
        val data = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

        assertThat(IcmpPacket.internetChecksum(data)).isEqualTo(0x0000)
    }

    @Test
    fun `buildEchoRequest produces the correct header layout and checksum`() {
        val packet = IcmpPacket.buildEchoRequest(identifier = 0, sequence = 0, payload = ByteArray(0))

        assertThat(packet).hasLength(IcmpPacket.HEADER_SIZE)
        assertThat(packet[0]).isEqualTo(IcmpPacket.TYPE_ECHO_REQUEST.toByte()) // type
        assertThat(packet[1]).isEqualTo(0.toByte()) // code
        assertThat(packet[2]).isEqualTo(0xF7.toByte()) // checksum high byte
        assertThat(packet[3]).isEqualTo(0xFF.toByte()) // checksum low byte
    }

    @Test
    fun `buildEchoRequest encodes identifier, sequence and payload`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val packet = IcmpPacket.buildEchoRequest(identifier = 0x1234, sequence = 0x0007, payload = payload)

        assertThat(packet).hasLength(IcmpPacket.HEADER_SIZE + payload.size)
        assertThat(packet[4]).isEqualTo(0x12.toByte())
        assertThat(packet[5]).isEqualTo(0x34.toByte())
        assertThat(packet[6]).isEqualTo(0x00.toByte())
        assertThat(packet[7]).isEqualTo(0x07.toByte())
        assertThat(packet.copyOfRange(8, 12)).isEqualTo(payload)
    }

    @Test
    fun `buildEchoRequest checksum verifies as zero when checksummed with itself`() {
        // Internet checksum property: summing a message that already contains its own
        // correct checksum yields zero (before complementing back).
        val packet = IcmpPacket.buildEchoRequest(identifier = 0xABCD, sequence = 42, payload = byteArrayOf(9, 9, 9))

        assertThat(IcmpPacket.internetChecksum(packet)).isEqualTo(0)
    }

    @Test
    fun `parseEchoReply reads type, code, identifier and sequence`() {
        val buffer = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x12, 0x34, 0x00, 0x07)

        val reply = IcmpPacket.parseEchoReply(buffer, buffer.size)

        assertThat(reply).isEqualTo(IcmpEchoReply(type = 0, code = 0, identifier = 0x1234, sequence = 7))
        assertThat(reply!!.isEchoReply).isTrue()
    }

    @Test
    fun `parseEchoReply returns null for a buffer shorter than the header`() {
        assertThat(IcmpPacket.parseEchoReply(byteArrayOf(1, 2, 3), 3)).isNull()
    }
}
