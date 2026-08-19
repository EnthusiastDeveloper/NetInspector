package dev.enthusiastdev.netinspector.core.common.icmp

/**
 * ICMP echo request/reply framing (design §9.1) - 8-byte header (type, code, checksum,
 * identifier, sequence) plus payload. On Linux ping sockets the kernel rewrites the
 * identifier and recomputes the checksum on send, so replies must be matched on sequence
 * number (and source address, at the caller) rather than the identifier we set here.
 */
object IcmpPacket {
    const val HEADER_SIZE = 8
    const val TYPE_ECHO_REPLY = 0
    const val TYPE_ECHO_REQUEST = 8
    private const val CODE_ECHO = 0

    fun buildEchoRequest(
        identifier: Int,
        sequence: Int,
        payload: ByteArray,
    ): ByteArray {
        val packet = ByteArray(HEADER_SIZE + payload.size)
        packet[0] = TYPE_ECHO_REQUEST.toByte()
        packet[1] = CODE_ECHO.toByte()
        // packet[2], packet[3] (checksum) filled in below, once the rest of the packet exists.
        writeUInt16(packet, 4, identifier)
        writeUInt16(packet, 6, sequence)
        payload.copyInto(packet, HEADER_SIZE)

        val checksum = internetChecksum(packet)
        writeUInt16(packet, 2, checksum)
        return packet
    }

    fun parseEchoReply(
        buffer: ByteArray,
        length: Int,
    ): IcmpEchoReply? {
        if (length < HEADER_SIZE) return null
        return IcmpEchoReply(
            type = buffer[0].toInt() and 0xFF,
            code = buffer[1].toInt() and 0xFF,
            identifier = readUInt16(buffer, 4),
            sequence = readUInt16(buffer, 6),
        )
    }

    /**
     * RFC 1071 Internet checksum: 16-bit one's-complement sum of the message (checksum
     * field itself treated as zero), folded and complemented.
     */
    fun internetChecksum(data: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < data.size) {
            sum += readUInt16(data, i)
            i += 2
        }
        if (i < data.size) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun writeUInt16(
        buffer: ByteArray,
        offset: Int,
        value: Int,
    ) {
        buffer[offset] = (value shr 8).toByte()
        buffer[offset + 1] = value.toByte()
    }

    private fun readUInt16(
        buffer: ByteArray,
        offset: Int,
    ): Int = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
}

data class IcmpEchoReply(
    val type: Int,
    val code: Int,
    val identifier: Int,
    val sequence: Int,
) {
    val isEchoReply: Boolean get() = type == IcmpPacket.TYPE_ECHO_REPLY
}
