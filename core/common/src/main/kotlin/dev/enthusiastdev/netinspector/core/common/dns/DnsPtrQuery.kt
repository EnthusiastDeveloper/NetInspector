package dev.enthusiastdev.netinspector.core.common.dns

import java.net.Inet4Address

/**
 * design §9.4/§8.2 - a minimal, self-contained DNS wire-format PTR query/response codec.
 * `android.net.DnsResolver` (API 29+) has no reverse-lookup convenience: its typed `query()`
 * only returns A/AAAA answers, so a PTR lookup has to go through `rawQuery(byte[])`, which
 * means encoding the question and decoding the answer by hand. Deliberately narrow - just
 * enough for Stage C's reverse-DNS enrichment (design §8.2) - rather than the general
 * encoder/decoder design §9.4 describes for the standalone DNS tool, which lives in
 * `:data:diagnostics` and cannot be depended on from here (design §2.1's module graph forbids
 * one `:data:*` module depending on another).
 */
object DnsPtrQuery {
    private const val TYPE_PTR = 12
    private const val CLASS_IN = 1
    private const val HEADER_SIZE = 12
    private const val MAX_LABEL_POINTER_HOPS = 32
    private const val NAME_POINTER_FLAG = 0xC0
    private const val NAME_POINTER_MASK = 0x3FFF

    /** Builds a standard, recursion-desired PTR query for `address`'s `in-addr.arpa` name. */
    fun buildQuery(
        address: Inet4Address,
        queryId: Int,
    ): ByteArray {
        val name = reverseName(address)
        val question = encodeName(name) + writeUInt16(TYPE_PTR) + writeUInt16(CLASS_IN)
        val header =
            writeUInt16(queryId) +
                writeUInt16(RECURSION_DESIRED_FLAG) +
                writeUInt16(1) + // QDCOUNT
                writeUInt16(0) + // ANCOUNT
                writeUInt16(0) + // NSCOUNT
                writeUInt16(0) // ARCOUNT
        return header + question
    }

    /** Returns the first PTR record's target hostname, or `null` if the response is malformed,
     * carries no answer, or doesn't match `queryId`. */
    fun parseAnswer(
        response: ByteArray,
        queryId: Int,
    ): String? {
        val header = readHeader(response, queryId) ?: return null

        var offset = HEADER_SIZE
        repeat(header.questionCount) {
            offset = skipName(response, offset) ?: return null
            offset += 4 // QTYPE + QCLASS
        }
        return findPtrTarget(response, offset, header.answerCount)
    }

    private data class DnsHeader(
        val questionCount: Int,
        val answerCount: Int,
    )

    private fun readHeader(
        response: ByteArray,
        queryId: Int,
    ): DnsHeader? {
        if (response.size < HEADER_SIZE) return null
        val idMatches = readUInt16(response, 0) == queryId
        val noError = readUInt16(response, 2) and RCODE_MASK == 0 // non-zero RCODE - server-side error.
        val answerCount = readUInt16(response, 6)
        if (!idMatches || !noError || answerCount == 0) return null
        return DnsHeader(readUInt16(response, 4), answerCount)
    }

    private fun findPtrTarget(
        response: ByteArray,
        start: Int,
        answerCount: Int,
    ): String? {
        var offset = start
        repeat(answerCount) {
            val nameEnd = skipName(response, offset)
            if (nameEnd == null || nameEnd + 10 > response.size) return null
            val type = readUInt16(response, nameEnd)
            val rdLength = readUInt16(response, nameEnd + 8)
            val rdataOffset = nameEnd + 10
            if (rdataOffset + rdLength > response.size) return null
            if (type == TYPE_PTR) return readName(response, rdataOffset)?.first
            offset = rdataOffset + rdLength
        }
        return null
    }

    private fun reverseName(address: Inet4Address): String =
        address.address
            .map { it.toInt() and 0xFF }
            .reversed()
            .joinToString(".", postfix = ".in-addr.arpa")

    private fun encodeName(name: String): ByteArray {
        val bytes = mutableListOf<Byte>()
        name.split(".").filter { it.isNotEmpty() }.forEach { label ->
            bytes += label.length.toByte()
            bytes += label.toByteArray(Charsets.US_ASCII).toList()
        }
        bytes += 0
        return bytes.toByteArray()
    }

    /** Advances past a (possibly compressed) name without decoding it, for skipping the
     * question section and each answer's NAME field. */
    private fun skipName(
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

    /** Decodes a (possibly compressed) name starting at [start], following pointers per
     * RFC 1035 §4.1.4. Returns the dotted name and the offset immediately after it in the
     * *original* (non-followed) stream - the caller only needs that offset when the name
     * wasn't itself the field being skipped, which is why [skipName] stays separate. */
    private fun readName(
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
                    // Second check guards against a pointer loop.
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

    private fun writeUInt16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

    private fun readUInt16(
        buffer: ByteArray,
        offset: Int,
    ): Int = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private const val RECURSION_DESIRED_FLAG = 0x0100
    private const val RCODE_MASK = 0x000F
}
