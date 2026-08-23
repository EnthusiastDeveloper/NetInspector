package dev.enthusiastdev.netinspector.data.lan.snmp

import java.io.ByteArrayOutputStream

/**
 * docs/device-identification-ideas.md B1 - the minimal BER/ASN.1 subset [SnmpProbe] needs:
 * encoding one SNMPv2c GET-request PDU (RFC 1157/3416) with N object identifiers, and decoding
 * a GetResponse-PDU's variable-bindings back into an OID -> value map. This is not a
 * general-purpose ASN.1 codec, only the handful of tags an SNMP GET round-trip ever uses - the
 * same "hand-roll only what's needed" precedent as [dev.enthusiastdev.netinspector.data.lan
 * .netbios.NetBiosProbe]'s NBSTAT parsing.
 */
internal object SnmpBer {
    private const val TAG_INTEGER = 0x02
    private const val TAG_OCTET_STRING = 0x04
    private const val TAG_NULL = 0x05
    private const val TAG_OID = 0x06
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_GET_REQUEST_PDU = 0xA0
    private const val TAG_GET_RESPONSE_PDU = 0xA2
    private const val SNMP_VERSION_V2C = 1

    fun buildGetRequest(
        community: String,
        requestId: Int,
        oids: List<String>,
    ): ByteArray {
        val varBinds = oids.map { oid -> tlv(TAG_SEQUENCE, oidBytes(oid) + tlv(TAG_NULL, ByteArray(0))) }
        val varBindList = tlv(TAG_SEQUENCE, varBinds.fold(ByteArray(0)) { acc, bytes -> acc + bytes })
        val pdu =
            tlv(
                TAG_GET_REQUEST_PDU,
                integerBytes(requestId) + integerBytes(0) + integerBytes(0) + varBindList,
            )
        val message = integerBytes(SNMP_VERSION_V2C) + octetStringBytes(community.toByteArray(Charsets.US_ASCII)) + pdu
        return tlv(TAG_SEQUENCE, message)
    }

    /** Returns an OID -> decoded-OCTET-STRING-value map. A varbind whose value comes back as a
     * v2c exception type (`noSuchObject`/`noSuchInstance`/`endOfMibView` - context tags
     * 0x80-0x82) or any other non-OCTET-STRING type is simply omitted rather than guessed at,
     * since `sysDescr`/`sysName` are always OCTET STRING per RFC 1213 when actually present. */
    fun parseGetResponse(
        buffer: ByteArray,
        length: Int,
    ): Map<String, String> {
        val pdu = readResponsePdu(buffer, length) ?: return emptyMap()
        val pduReader = BerReader(pdu.content, pdu.content.size)
        pduReader.readTlv() // request-id
        pduReader.readTlv() // error-status
        pduReader.readTlv() // error-index
        val varBindList = pduReader.readTlv() ?: return emptyMap()
        val listReader = BerReader(varBindList.content, varBindList.content.size)
        val result = mutableMapOf<String, String>()
        while (true) {
            val varBind = listReader.readTlv() ?: break
            val varBindReader = BerReader(varBind.content, varBind.content.size)
            val oid = varBindReader.readTlv()
            val value = varBindReader.readTlv()
            if (oid != null && value != null && value.tag == TAG_OCTET_STRING) {
                result[decodeOid(oid.content)] = String(value.content, Charsets.US_ASCII)
            }
        }
        return result
    }

    /** The outer `SEQUENCE { version, community, data }` down to the `GetResponse-PDU` itself -
     * split out of [parseGetResponse] purely to keep that function's own return-statement count
     * within the project's [io.gitlab.arturbosch.detekt.rules.ReturnCount] limit. */
    private fun readResponsePdu(
        buffer: ByteArray,
        length: Int,
    ): BerTlv? {
        val message = BerReader(buffer, length).readTlv() ?: return null
        val messageReader = BerReader(message.content, message.content.size)
        messageReader.readTlv() // version
        messageReader.readTlv() // community
        val pdu = messageReader.readTlv() ?: return null
        return pdu.takeIf { it.tag == TAG_GET_RESPONSE_PDU }
    }

    // -- encoding helpers - each returns a complete tag+length+content TLV, ready to concatenate --

    private fun tlv(
        tag: Int,
        content: ByteArray,
    ): ByteArray = byteArrayOf(tag.toByte()) + lengthBytes(content.size) + content

    private fun lengthBytes(length: Int): ByteArray {
        if (length < 0x80) return byteArrayOf(length.toByte())
        val bytes = mutableListOf<Byte>()
        var remaining = length
        while (remaining > 0) {
            bytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining shr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun integerBytes(value: Int): ByteArray {
        var remaining = value
        val bytes = mutableListOf<Byte>()
        do {
            bytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining shr 8
        } while (remaining != 0 && remaining != -1)
        if (bytes[0].toInt() and 0x80 != 0 && value >= 0) bytes.add(0, 0)
        return tlv(TAG_INTEGER, bytes.toByteArray())
    }

    private fun octetStringBytes(bytes: ByteArray) = tlv(TAG_OCTET_STRING, bytes)

    private fun oidBytes(oid: String): ByteArray {
        val arcs = oid.split('.').map { it.toInt() }
        val output = ByteArrayOutputStream()
        output.write(arcs[0] * 40 + arcs[1])
        for (arc in arcs.drop(2)) output.write(encodeBase128(arc))
        return tlv(TAG_OID, output.toByteArray())
    }

    private fun encodeBase128(value: Int): ByteArray {
        var remaining = value
        val groups = mutableListOf(remaining and 0x7F)
        remaining = remaining shr 7
        while (remaining > 0) {
            groups.add(0, (remaining and 0x7F) or 0x80)
            remaining = remaining shr 7
        }
        return groups.map { it.toByte() }.toByteArray()
    }

    private fun decodeOid(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val first = bytes[0].toInt() and 0xFF
        val arcs = mutableListOf(first / 40, first % 40)
        var value = 0
        for (i in 1 until bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F)
            if (b and 0x80 == 0) {
                arcs.add(value)
                value = 0
            }
        }
        return arcs.joinToString(".")
    }

    private class BerTlv(
        val tag: Int,
        val content: ByteArray,
    )

    private class BerReader(
        private val buffer: ByteArray,
        private val length: Int,
    ) {
        private var offset = 0

        fun readTlv(): BerTlv? {
            if (offset >= length) return null
            val tag = buffer[offset].toInt() and 0xFF
            offset += 1
            val contentLength = readLength() ?: return null
            if (offset + contentLength > length) return null
            val content = buffer.copyOfRange(offset, offset + contentLength)
            offset += contentLength
            return BerTlv(tag, content)
        }

        private fun readLength(): Int? {
            if (offset >= length) return null
            val first = buffer[offset].toInt() and 0xFF
            offset += 1
            if (first < 0x80) return first
            val numBytes = first and 0x7F
            if (numBytes == 0 || offset + numBytes > length) return null
            var result = 0
            repeat(numBytes) {
                result = (result shl 8) or (buffer[offset].toInt() and 0xFF)
                offset += 1
            }
            return result
        }
    }
}
