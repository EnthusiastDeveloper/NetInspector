package dev.enthusiastdev.netinspector.data.diagnostics.dns

/** design §9.4 - the byte-level primitives [DnsWireCodec] and [DnsRdataFormatter] both need:
 * big-endian integers and RFC 1035 §4.1.4 (possibly compressed) name decoding. Split out purely
 * to keep each of those two objects' own function count down to their actual concern (framing,
 * RDATA formatting) rather than for any layering reason. */
internal object DnsWireBytes {
    const val NAME_POINTER_FLAG = 0xC0
    const val NAME_POINTER_MASK = 0x3FFF
    private const val MAX_LABEL_POINTER_HOPS = 32

    fun writeUInt16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

    fun readUInt16(
        buffer: ByteArray,
        offset: Int,
    ): Int = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    fun readUInt32(
        buffer: ByteArray,
        offset: Int,
    ): Long =
        ((buffer[offset].toLong() and 0xFF) shl 24) or
            ((buffer[offset + 1].toLong() and 0xFF) shl 16) or
            ((buffer[offset + 2].toLong() and 0xFF) shl 8) or
            (buffer[offset + 3].toLong() and 0xFF)

    fun skipName(
        buffer: ByteArray,
        start: Int,
    ): Int? {
        var offset = start
        while (offset < buffer.size) {
            val lengthByte = buffer[offset].toInt() and 0xFF
            when {
                lengthByte == 0 -> return offset + 1
                lengthByte and NAME_POINTER_FLAG == NAME_POINTER_FLAG -> return offset + 2
                else -> offset += 1 + lengthByte
            }
        }
        return null
    }

    /** Decodes a (possibly compressed) name per RFC 1035 §4.1.4. Returns the dotted name and the
     * offset immediately after it in the original stream. */
    fun readName(
        buffer: ByteArray,
        start: Int,
    ): Pair<String, Int>? {
        val labels = mutableListOf<String>()
        var offset = start
        var endOffset = -1
        var hops = 0
        while (offset < buffer.size) {
            val lengthByte = buffer[offset].toInt() and 0xFF
            when {
                lengthByte == 0 -> {
                    if (endOffset == -1) endOffset = offset + 1
                    return labels.joinToString(".") to endOffset
                }
                lengthByte and NAME_POINTER_FLAG == NAME_POINTER_FLAG -> {
                    hops++
                    if (offset + 1 >= buffer.size || hops > MAX_LABEL_POINTER_HOPS) return null
                    if (endOffset == -1) endOffset = offset + 2
                    val highBits = lengthByte and (NAME_POINTER_MASK shr 8)
                    val lowBits = buffer[offset + 1].toInt() and 0xFF
                    offset = (highBits shl 8) or lowBits
                }
                else -> {
                    val labelStart = offset + 1
                    val labelEnd = labelStart + lengthByte
                    if (labelEnd > buffer.size) return null
                    labels += String(buffer, labelStart, lengthByte, Charsets.US_ASCII)
                    offset = labelEnd
                }
            }
        }
        return null
    }
}
