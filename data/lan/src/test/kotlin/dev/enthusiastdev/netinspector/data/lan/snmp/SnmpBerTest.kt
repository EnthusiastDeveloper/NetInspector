package dev.enthusiastdev.netinspector.data.lan.snmp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [SnmpBer.buildGetRequest] is verified against a byte-for-byte hand-computed SNMPv2c
 * GET-request (RFC 1157/3416 BER encoding), the same way [dev.enthusiastdev.netinspector.data
 * .lan.netbios.NetBiosProbeTest] verifies NBSTAT parsing against a hand-built packet.
 * [SnmpBer.parseGetResponse] is verified against a synthetic GetResponse-PDU assembled from the
 * same encode primitives (a different PDU type than [SnmpBer.buildGetRequest] produces, so this
 * isn't a self-fulfilling round trip).
 */
class SnmpBerTest {
    @Test
    fun `buildGetRequest encodes a single-OID v2c GET-request byte for byte`() {
        // community="public", requestId=1, OID 1.3.6.1.2.1.1.1.0 (sysDescr) - each nested TLV
        // is spelled out as its own tag+length+content group, matching SnmpBer's own encode
        // helpers, so the expected bytes read as wire structure rather than an opaque hex blob.
        val version = byteArrayOf(0x02, 0x01, 0x01)
        val community = byteArrayOf(0x04, 0x06) + "public".toByteArray(Charsets.US_ASCII)
        val oid = byteArrayOf(0x06, 0x08, 0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00)
        val nullValue = byteArrayOf(0x05, 0x00)
        val varBind = byteArrayOf(0x30, 0x0C) + oid + nullValue
        val varBindList = byteArrayOf(0x30, 0x0E) + varBind
        val requestId = byteArrayOf(0x02, 0x01, 0x01)
        val errorStatus = byteArrayOf(0x02, 0x01, 0x00)
        val errorIndex = byteArrayOf(0x02, 0x01, 0x00)
        val pdu = byteArrayOf(0xA0.toByte(), 0x19) + requestId + errorStatus + errorIndex + varBindList
        val expected = byteArrayOf(0x30, 0x26) + version + community + pdu

        val actual = SnmpBer.buildGetRequest("public", requestId = 1, oids = listOf("1.3.6.1.2.1.1.1.0"))

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `parseGetResponse extracts multiple OCTET STRING varbinds by OID`() {
        val response =
            getResponsePdu(
                varBind(oid = "1.3.6.1.2.1.1.1.0", value = octetString("TestDevice")),
                varBind(oid = "1.3.6.1.2.1.1.5.0", value = octetString("TestName")),
            )

        val values = SnmpBer.parseGetResponse(response, response.size)

        assertThat(values["1.3.6.1.2.1.1.1.0"]).isEqualTo("TestDevice")
        assertThat(values["1.3.6.1.2.1.1.5.0"]).isEqualTo("TestName")
    }

    @Test
    fun `parseGetResponse omits a varbind whose value is a v2c exception type`() {
        val response =
            getResponsePdu(
                varBind(oid = "1.3.6.1.2.1.1.1.0", value = byteArrayOf(0x80.toByte(), 0x00)), // noSuchObject
                varBind(oid = "1.3.6.1.2.1.1.5.0", value = octetString("TestName")),
            )

        val values = SnmpBer.parseGetResponse(response, response.size)

        assertThat(values).doesNotContainKey("1.3.6.1.2.1.1.1.0")
        assertThat(values["1.3.6.1.2.1.1.5.0"]).isEqualTo("TestName")
    }

    @Test
    fun `parseGetResponse returns an empty map for a truncated or malformed packet`() {
        assertThat(SnmpBer.parseGetResponse(byteArrayOf(), 0)).isEmpty()
        val truncated = byteArrayOf(0x30, 0x7F, 0x02, 0x01)
        assertThat(SnmpBer.parseGetResponse(truncated, truncated.size)).isEmpty()
    }

    // -- fixture builders, using the same TLV shapes buildGetRequest's worked example derives --

    private fun tlv(
        tag: Int,
        content: ByteArray,
    ): ByteArray = byteArrayOf(tag.toByte()) + byteArrayOf(content.size.toByte()) + content

    private fun octetString(value: String) = tlv(0x04, value.toByteArray(Charsets.US_ASCII))

    private fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toInt() }
        val bytes = mutableListOf(arcs[0] * 40 + arcs[1])
        bytes.addAll(arcs.drop(2))
        return tlv(0x06, bytes.map { it.toByte() }.toByteArray())
    }

    private fun varBind(
        oid: String,
        value: ByteArray,
    ) = tlv(0x30, oid(oid) + value)

    private fun getResponsePdu(vararg varBinds: ByteArray): ByteArray {
        val varBindList = tlv(0x30, varBinds.fold(ByteArray(0)) { acc, v -> acc + v })
        val requestId = tlv(0x02, byteArrayOf(0x01))
        val errorStatus = tlv(0x02, byteArrayOf(0x00))
        val errorIndex = tlv(0x02, byteArrayOf(0x00))
        val pdu = tlv(0xA2, requestId + errorStatus + errorIndex + varBindList)
        val message = tlv(0x02, byteArrayOf(0x01)) + octetString("public") + pdu
        return tlv(0x30, message)
    }
}
