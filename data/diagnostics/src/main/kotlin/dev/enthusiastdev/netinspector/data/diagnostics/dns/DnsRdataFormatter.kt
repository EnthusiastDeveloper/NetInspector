package dev.enthusiastdev.netinspector.data.diagnostics.dns

import dev.enthusiastdev.netinspector.core.model.diagnostics.DnsRecordType
import java.net.InetAddress

/** design §9.4 - renders a decoded answer's RDATA into the human-readable `DnsRecord.data`
 * string `DnsWireCodec.parseResponse` stores. Split out of [DnsWireCodec] to keep that object's
 * own function count to the framing logic (design's "small... encoder/decoder," not "one giant
 * object"); relies on [DnsWireBytes]'s name/int decoders since RDATA for several record types
 * embeds a (possibly compressed) domain name. */
internal object DnsRdataFormatter {
    fun format(
        buffer: ByteArray,
        type: DnsRecordType?,
        offset: Int,
        length: Int,
    ): String =
        when (type) {
            DnsRecordType.A -> formatIp(buffer, offset, 4)
            DnsRecordType.AAAA -> formatIp(buffer, offset, 16)
            DnsRecordType.CNAME, DnsRecordType.NS, DnsRecordType.PTR ->
                DnsWireBytes.readName(buffer, offset)?.first ?: hexDump(buffer, offset, length)
            DnsRecordType.MX -> formatMx(buffer, offset)
            DnsRecordType.SRV -> formatSrv(buffer, offset)
            DnsRecordType.SOA -> formatSoa(buffer, offset)
            DnsRecordType.TXT -> formatTxt(buffer, offset, length)
            null -> hexDump(buffer, offset, length)
        }

    private fun formatIp(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): String =
        runCatching { InetAddress.getByAddress(buffer.copyOfRange(offset, offset + length)).hostAddress }
            .getOrNull() ?: hexDump(buffer, offset, length)

    private fun formatMx(
        buffer: ByteArray,
        offset: Int,
    ): String {
        val preference = DnsWireBytes.readUInt16(buffer, offset)
        val exchange = DnsWireBytes.readName(buffer, offset + 2)?.first ?: "?"
        return "$preference $exchange"
    }

    private fun formatSrv(
        buffer: ByteArray,
        offset: Int,
    ): String {
        val priority = DnsWireBytes.readUInt16(buffer, offset)
        val weight = DnsWireBytes.readUInt16(buffer, offset + 2)
        val port = DnsWireBytes.readUInt16(buffer, offset + 4)
        val target = DnsWireBytes.readName(buffer, offset + 6)?.first ?: "?"
        return "$priority $weight $port $target"
    }

    private fun formatSoa(
        buffer: ByteArray,
        offset: Int,
    ): String {
        val (mname, afterMname) = DnsWireBytes.readName(buffer, offset) ?: return "?"
        val (rname, afterRname) = DnsWireBytes.readName(buffer, afterMname) ?: return "?"
        if (afterRname + 20 > buffer.size) return "$mname $rname ?"
        val serial = DnsWireBytes.readUInt32(buffer, afterRname)
        val refresh = DnsWireBytes.readUInt32(buffer, afterRname + 4)
        val retry = DnsWireBytes.readUInt32(buffer, afterRname + 8)
        val expire = DnsWireBytes.readUInt32(buffer, afterRname + 12)
        val minimum = DnsWireBytes.readUInt32(buffer, afterRname + 16)
        return "$mname $rname $serial $refresh $retry $expire $minimum"
    }

    private fun formatTxt(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): String {
        val strings = mutableListOf<String>()
        var pos = offset
        val end = offset + length
        while (pos < end) {
            val stringLength = buffer[pos].toInt() and 0xFF
            val start = pos + 1
            if (start + stringLength > end) break
            strings += "\"" + String(buffer, start, stringLength, Charsets.US_ASCII) + "\""
            pos = start + stringLength
        }
        return strings.joinToString(" ")
    }

    private fun hexDump(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): String {
        val end = (offset + length).coerceAtMost(buffer.size)
        return buffer.copyOfRange(offset, end).joinToString(" ") { "%02x".format(it) }
    }
}
