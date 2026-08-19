package dev.enthusiastdev.netinspector.data.diagnostics.dns

import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecord
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType

/**
 * design §9.4 - a small self-contained DNS wire-format encoder/decoder for querying a
 * user-specified server directly: the system resolver ([android.net.DnsResolver]) cannot be
 * redirected to a chosen server, so redirecting means building and parsing UDP-53 packets by
 * hand. Deliberately separate from [dev.enthusiastdev.netinspector.core.common.dns.DnsPtrQuery]
 * (module boundaries forbid `:data:diagnostics` depending on `:core:common`'s narrow PTR-only
 * codec's internals in the other direction anyway) - this one is general over every record type
 * the DNS tool supports, that one exists only for Stage C's reverse-DNS enrichment. Byte-level
 * primitives live in [DnsWireBytes] and RDATA formatting in [DnsRdataFormatter], kept separate
 * so this object's own concern - header and question framing - stays what it actually is.
 */
object DnsWireCodec {
    private const val HEADER_SIZE = 12
    private const val CLASS_IN = 1
    private const val RECURSION_DESIRED_FLAG = 0x0100
    private const val RCODE_MASK = 0x000F

    fun buildQuery(
        name: String,
        type: DnsRecordType,
        queryId: Int,
    ): ByteArray {
        val question = encodeName(name) + DnsWireBytes.writeUInt16(type.code) + DnsWireBytes.writeUInt16(CLASS_IN)
        val header =
            DnsWireBytes.writeUInt16(queryId) +
                DnsWireBytes.writeUInt16(RECURSION_DESIRED_FLAG) +
                DnsWireBytes.writeUInt16(1) + // QDCOUNT
                DnsWireBytes.writeUInt16(0) + // ANCOUNT
                DnsWireBytes.writeUInt16(0) + // NSCOUNT
                DnsWireBytes.writeUInt16(0) // ARCOUNT
        return header + question
    }

    /** `null` only for a malformed or mismatched response - the caller distinguishes "no
     * records" (a valid, empty-answer response) from "couldn't parse this at all". */
    fun parseResponse(
        response: ByteArray,
        queryId: Int,
    ): List<DnsRecord>? {
        val header = validateHeader(response, queryId) ?: return null
        val answersStart = skipQuestions(response, header.questionCount) ?: return null
        return parseAnswers(response, answersStart, header.answerCount)
    }

    private data class Header(
        val questionCount: Int,
        val answerCount: Int,
    )

    private fun validateHeader(
        response: ByteArray,
        queryId: Int,
    ): Header? {
        if (response.size < HEADER_SIZE) return null
        if (DnsWireBytes.readUInt16(response, 0) != queryId) return null
        val rcode = DnsWireBytes.readUInt16(response, 2) and RCODE_MASK
        if (rcode != 0) return null
        return Header(DnsWireBytes.readUInt16(response, 4), DnsWireBytes.readUInt16(response, 6))
    }

    private fun skipQuestions(
        response: ByteArray,
        questionCount: Int,
    ): Int? {
        var offset = HEADER_SIZE
        repeat(questionCount) {
            offset = DnsWireBytes.skipName(response, offset) ?: return null
            offset += 4 // QTYPE + QCLASS
        }
        return offset
    }

    private fun parseAnswers(
        response: ByteArray,
        start: Int,
        answerCount: Int,
    ): List<DnsRecord>? {
        var offset = start
        val records = mutableListOf<DnsRecord>()
        repeat(answerCount) {
            val (name, nameEnd) = DnsWireBytes.readName(response, offset) ?: return null
            if (nameEnd + 10 > response.size) return null
            val typeCode = DnsWireBytes.readUInt16(response, nameEnd)
            val ttl = DnsWireBytes.readUInt32(response, nameEnd + 4)
            val rdLength = DnsWireBytes.readUInt16(response, nameEnd + 8)
            val rdataOffset = nameEnd + 10
            if (rdataOffset + rdLength > response.size) return null

            val type = DnsRecordType.fromCode(typeCode)
            val data = DnsRdataFormatter.format(response, type, rdataOffset, rdLength)
            records += DnsRecord(name, type, typeCode, ttl, data)
            offset = rdataOffset + rdLength
        }
        return records
    }

    private fun encodeName(name: String): ByteArray {
        val bytes = mutableListOf<Byte>()
        name.split(".").filter { it.isNotEmpty() }.forEach { label ->
            bytes += label.length.toByte()
            bytes += label.toByteArray(Charsets.US_ASCII).toList()
        }
        bytes += 0
        return bytes.toByteArray()
    }
}

/** in-addr.arpa reverse-lookup name for a dotted-quad IPv4 address string (design §9.4). */
fun reverseDnsName(ipv4Address: String): String =
    ipv4Address.split(".").reversed().joinToString(".", postfix = ".in-addr.arpa")
