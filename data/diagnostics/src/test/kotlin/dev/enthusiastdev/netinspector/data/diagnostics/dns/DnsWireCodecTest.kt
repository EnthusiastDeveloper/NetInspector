package dev.enthusiastdev.netinspector.data.diagnostics.dns

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecord
import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType
import org.junit.Test

private fun label(text: String): ByteArray = byteArrayOf(text.length.toByte()) + text.toByteArray(Charsets.US_ASCII)

private fun uint16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

private fun uint32(value: Long): ByteArray =
    byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

private fun header(
    queryId: Int,
    rcode: Int = 0,
    answerCount: Int,
): ByteArray = uint16(queryId) + uint16(0x8100 or rcode) + uint16(1) + uint16(answerCount) + uint16(0) + uint16(0)

// Question section for "example.com" A/IN, starting at offset 12.
private val QUESTION_NAME = label("example") + label("com") + byteArrayOf(0)
private val QUESTION = QUESTION_NAME + uint16(1) + uint16(1)
private const val NAME_POINTER_TO_QUESTION_START = 0xC0
private const val QUESTION_START_OFFSET = 12

private fun answerRecord(
    type: Int,
    rdata: ByteArray,
    ttl: Long = 300,
): ByteArray =
    byteArrayOf(NAME_POINTER_TO_QUESTION_START.toByte(), QUESTION_START_OFFSET.toByte()) +
        uint16(type) +
        uint16(1) +
        uint32(ttl) +
        uint16(rdata.size) +
        rdata

class DnsWireCodecTest {
    @Test
    fun `buildQuery encodes name, type and class`() {
        val query = DnsWireCodec.buildQuery("example.com", DnsRecordType.MX, queryId = 0x1234)

        assertThat(query.copyOfRange(0, 2)).isEqualTo(uint16(0x1234))
        assertThat(query.copyOfRange(12, query.size - 4)).isEqualTo(QUESTION_NAME)
        assertThat(query.copyOfRange(query.size - 4, query.size)).isEqualTo(uint16(15) + uint16(1))
    }

    @Test
    fun `parses an A record`() {
        val ipBytes = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)
        val response = header(0x1234, answerCount = 1) + QUESTION + answerRecord(1, ipBytes)

        val records = DnsWireCodec.parseResponse(response, queryId = 0x1234)

        assertThat(records).containsExactly(
            DnsRecord(
                name = "example.com",
                type = DnsRecordType.A,
                rawTypeCode = 1,
                ttlSeconds = 300,
                data = "93.184.216.34",
            ),
        )
    }

    @Test
    fun `parses a CNAME record that points back into the question name via compression`() {
        val rdata = label("www") + byteArrayOf(0xC0.toByte(), QUESTION_START_OFFSET.toByte())
        val response = header(0x1234, answerCount = 1) + QUESTION + answerRecord(5, rdata)

        val records = DnsWireCodec.parseResponse(response, queryId = 0x1234)

        assertThat(records!!.single().data).isEqualTo("www.example.com")
        assertThat(records.single().type).isEqualTo(DnsRecordType.CNAME)
    }

    @Test
    fun `parses an MX record as priority and exchange`() {
        val rdata = uint16(10) + label("mail") + byteArrayOf(0xC0.toByte(), QUESTION_START_OFFSET.toByte())
        val response = header(0x1234, answerCount = 1) + QUESTION + answerRecord(15, rdata)

        val records = DnsWireCodec.parseResponse(response, queryId = 0x1234)

        assertThat(records!!.single().data).isEqualTo("10 mail.example.com")
    }

    @Test
    fun `parses a TXT record as quoted strings`() {
        val rdata = label("v=spf1") + label("include:_spf.example.com")
        val response = header(0x1234, answerCount = 1) + QUESTION + answerRecord(16, rdata)

        val records = DnsWireCodec.parseResponse(response, queryId = 0x1234)

        assertThat(records!!.single().data).isEqualTo("\"v=spf1\" \"include:_spf.example.com\"")
    }

    @Test
    fun `parses an SRV record as priority weight port target`() {
        val target = label("sip") + byteArrayOf(0xC0.toByte(), QUESTION_START_OFFSET.toByte())
        val rdata = uint16(10) + uint16(20) + uint16(5060) + target
        val response = header(0x1234, answerCount = 1) + QUESTION + answerRecord(33, rdata)

        val records = DnsWireCodec.parseResponse(response, queryId = 0x1234)

        assertThat(records!!.single().data).isEqualTo("10 20 5060 sip.example.com")
    }

    @Test
    fun `parses an AAAA record`() {
        val ipv6Bytes = byteArrayOf(0x20, 0x01, 0x0d, 0xb8.toByte(), *ByteArray(12))
        val response = header(0x1234, answerCount = 1) + QUESTION + answerRecord(28, ipv6Bytes)

        val records = DnsWireCodec.parseResponse(response, queryId = 0x1234)

        // java.net.Inet6Address doesn't collapse zero runs into "::" the way RFC 5952 textual
        // form does - a purely cosmetic difference from what Android's own formatter might show.
        assertThat(records!!.single().data).isEqualTo("2001:db8:0:0:0:0:0:0")
    }

    @Test
    fun `returns an empty list when there are no answers`() {
        val response = header(0x1234, answerCount = 0) + QUESTION

        assertThat(DnsWireCodec.parseResponse(response, queryId = 0x1234)).isEmpty()
    }

    @Test
    fun `returns null for a non-zero RCODE`() {
        val response = header(0x1234, rcode = 3, answerCount = 0) + QUESTION

        assertThat(DnsWireCodec.parseResponse(response, queryId = 0x1234)).isNull()
    }

    @Test
    fun `returns null when the response id does not match the query`() {
        val response = header(0x1234, answerCount = 0) + QUESTION

        assertThat(DnsWireCodec.parseResponse(response, queryId = 0x9999)).isNull()
    }

    @Test
    fun `reverseDnsName builds the in-addr-arpa name`() {
        assertThat(reverseDnsName("192.168.1.5")).isEqualTo("5.1.168.192.in-addr.arpa")
    }
}
