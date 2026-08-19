package dev.enthusiastdev.netinspector.core.common.dns

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

private fun addr(host: String) = InetAddress.getByName(host) as Inet4Address

private fun label(text: String): ByteArray = byteArrayOf(text.length.toByte()) + text.toByteArray(Charsets.US_ASCII)

private fun uint16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

private fun uint32(value: Int): ByteArray =
    byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

/** Builds a minimal, well-formed response header for the given question/answer counts, RCODE
 * (0 = no error) and query id, matching the wire format [DnsPtrQuery] decodes. */
private fun header(
    queryId: Int,
    rcode: Int = 0,
    questionCount: Int = 1,
    answerCount: Int,
): ByteArray =
    uint16(queryId) +
        uint16(0x8100 or rcode) + // QR=1 (response), RA=1, RCODE as given.
        uint16(questionCount) +
        uint16(answerCount) +
        uint16(0) +
        uint16(0)

private val PTR_QUESTION_NAME =
    label("5") + label("1") + label("168") + label("192") + label("in-addr") + label("arpa") + byteArrayOf(0)
private val PTR_QUESTION = PTR_QUESTION_NAME + uint16(12) + uint16(1) // TYPE=PTR, CLASS=IN

private fun ptrAnswerRecord(rdata: ByteArray): ByteArray =
    byteArrayOf(0xC0.toByte(), 0x0C) + // NAME - a pointer back to the question at offset 12.
        uint16(12) + // TYPE=PTR
        uint16(1) + // CLASS=IN
        uint32(3600) + // TTL
        uint16(rdata.size) +
        rdata

class DnsPtrQueryTest {
    @Test
    fun `buildQuery encodes the reversed octets under in-addr-arpa`() {
        val query = DnsPtrQuery.buildQuery(addr("192.168.1.5"), queryId = 0x1234)

        assertThat(query.copyOfRange(12, query.size - 4)).isEqualTo(PTR_QUESTION_NAME)
        // QTYPE = PTR (12), QCLASS = IN (1), immediately after the name's terminator.
        assertThat(query.copyOfRange(query.size - 4, query.size)).isEqualTo(uint16(12) + uint16(1))
    }

    @Test
    fun `buildQuery sets the query id and a single question`() {
        val query = DnsPtrQuery.buildQuery(addr("10.0.0.1"), queryId = 0xABCD)

        assertThat(query.copyOfRange(0, 2)).isEqualTo(uint16(0xABCD))
        assertThat(query.copyOfRange(4, 6)).isEqualTo(uint16(1)) // QDCOUNT
    }

    @Test
    fun `parseAnswer decodes an uncompressed PTR target`() {
        val rdata = label("printer") + label("lan") + byteArrayOf(0)
        val response = header(0x1234, answerCount = 1) + PTR_QUESTION + ptrAnswerRecord(rdata)

        assertThat(DnsPtrQuery.parseAnswer(response, queryId = 0x1234)).isEqualTo("printer.lan")
    }

    @Test
    fun `parseAnswer follows a compression pointer inside the PTR target`() {
        // RDATA is "nas." followed by a pointer back into the question's "arpa" label chain -
        // real resolvers do exactly this to avoid repeating a common suffix. Question layout is
        // 1('5') 1('1') 3('168') 3('192') 7('in-addr') 4('arpa') 0, starting at offset 12; the
        // "arpa" label sits at 12 + 2+2+4+4+8 = 32.
        val arpaLabelOffset = 12 + 2 + 2 + 4 + 4 + 8
        assertThat(PTR_QUESTION[arpaLabelOffset - 12].toInt() and 0xFF).isEqualTo(4) // sanity: length byte for "arpa"

        val rdata = label("nas") + byteArrayOf(0xC0.toByte(), arpaLabelOffset.toByte())
        val response = header(0x1234, answerCount = 1) + PTR_QUESTION + ptrAnswerRecord(rdata)

        assertThat(DnsPtrQuery.parseAnswer(response, queryId = 0x1234)).isEqualTo("nas.arpa")
    }

    @Test
    fun `parseAnswer returns null for a non-zero RCODE`() {
        val response = header(0x1234, rcode = 3, answerCount = 0) + PTR_QUESTION // RCODE 3 = NXDOMAIN.

        assertThat(DnsPtrQuery.parseAnswer(response, queryId = 0x1234)).isNull()
    }

    @Test
    fun `parseAnswer returns null when there is no answer`() {
        val response = header(0x1234, answerCount = 0) + PTR_QUESTION

        assertThat(DnsPtrQuery.parseAnswer(response, queryId = 0x1234)).isNull()
    }

    @Test
    fun `parseAnswer returns null when the response id does not match the query`() {
        val rdata = label("printer") + label("lan") + byteArrayOf(0)
        val response = header(0x1234, answerCount = 1) + PTR_QUESTION + ptrAnswerRecord(rdata)

        assertThat(DnsPtrQuery.parseAnswer(response, queryId = 0x9999)).isNull()
    }

    @Test
    fun `parseAnswer returns null for a truncated response`() {
        assertThat(DnsPtrQuery.parseAnswer(byteArrayOf(1, 2, 3), queryId = 0x1234)).isNull()
    }
}
