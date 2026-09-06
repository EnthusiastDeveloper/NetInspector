package dev.enthusiastdev.netinspector.data.lan.enrich

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [MS-SMB2] wire format is fiddly and there's no SMB responder on hand in every environment
 * this runs in, so this builds synthetic requests/responses by hand rather than relying on a
 * live one - see [SmbNegotiateProbe.negotiateRequest]'s own doc comment.
 */
class SmbNegotiateProbeTest {
    private val probe = SmbNegotiateProbe()

    @Test
    fun `negotiateRequest builds a well-formed NEGOTIATE request`() {
        val request = probe.negotiateRequest()

        // 4-byte length prefix + 64-byte SMB2 header + 44-byte NEGOTIATE_REQUEST body
        // (36 fixed + 4 dialects x 2 bytes).
        assertThat(request.size).isEqualTo(4 + 64 + 44)
        assertThat(
            request.copyOfRange(4, 8),
        ).isEqualTo(byteArrayOf(0xFE.toByte(), 'S'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte()))
        // Command (offset 4 + 12) = NEGOTIATE (0x0000), little-endian.
        assertThat(request[16]).isEqualTo(0)
        assertThat(request[17]).isEqualTo(0)
        // DialectCount (offset 4 + 64 + 2) = 4, little-endian.
        assertThat(request[70]).isEqualTo(4)
        assertThat(request[71]).isEqualTo(0)
    }

    @Test
    fun `parseDialectRevision extracts the dialect from a response encoded by an independent implementation`() {
        // A real NEGOTIATE_RESPONSE (DialectRevision 0x0302) built by impacket's smb3structs -
        // an independent, spec-tracking SMB2 implementation - rather than another hand-rolled
        // encoder in this same test file, so this actually cross-checks the byte offsets
        // against something other than this probe's own assumptions.
        val bytes = IMPACKET_ENCODED_NEGOTIATE_RESPONSE_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        assertThat(probe.parseDialectRevision(bytes)).isEqualTo(0x0302)
    }

    @Test
    fun `parseDialectRevision extracts the negotiated dialect from a successful response`() {
        val response = negotiateResponse(command = 0x0000, status = 0, dialectRevision = 0x0302)

        assertThat(probe.parseDialectRevision(response)).isEqualTo(0x0302)
    }

    @Test
    fun `parseDialectRevision returns null for a legacy SMB1 protocol id`() {
        val response = negotiateResponse(command = 0x0000, status = 0, dialectRevision = 0x0202)
        response[0] = 0xFF.toByte() // SMB1's "\xFFSMB" protocol id instead of SMB2's "\xFESMB"

        assertThat(probe.parseDialectRevision(response)).isNull()
    }

    @Test
    fun `parseDialectRevision returns null for a non-NEGOTIATE command`() {
        val response = negotiateResponse(command = 0x0001, status = 0, dialectRevision = 0x0302)

        assertThat(probe.parseDialectRevision(response)).isNull()
    }

    @Test
    fun `parseDialectRevision returns null for a non-success status`() {
        val response = negotiateResponse(command = 0x0000, status = 0xC0000001.toInt(), dialectRevision = 0x0302)

        assertThat(probe.parseDialectRevision(response)).isNull()
    }

    @Test
    fun `parseDialectRevision returns null for a truncated message`() {
        assertThat(probe.parseDialectRevision(ByteArray(65))).isNull()
    }

    /** A minimal SMB2 NEGOTIATE_RESPONSE (header + just enough of the body to carry
     * DialectRevision) - real responses carry a security buffer afterward this probe never
     * reads, so it's omitted here. */
    private fun negotiateResponse(
        command: Int,
        status: Int,
        dialectRevision: Int,
    ): ByteArray {
        val message = ByteArray(64 + 8)
        message[0] = 0xFE.toByte()
        message[1] = 'S'.code.toByte()
        message[2] = 'M'.code.toByte()
        message[3] = 'B'.code.toByte()
        writeIntLe(message, 8, status)
        writeShortLe(message, 12, command)
        // NEGOTIATE_RESPONSE body: StructureSize(2) + SecurityMode(2) + DialectRevision(2).
        writeShortLe(message, 64 + 4, dialectRevision)
        return message
    }

    private fun writeShortLe(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value shr 8).toByte()
    }

    private fun writeIntLe(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value shr 8).toByte()
        bytes[offset + 2] = (value shr 16).toByte()
        bytes[offset + 3] = (value shr 24).toByte()
    }

    private companion object {
        // Generated via: impacket.smb3structs.SMB2Negotiate_Response with SecurityMode=1,
        // DialectRevision=0x0302, wrapped in an SMB2Packet header with Command=0/Status=0 -
        // see this probe's own doc comment for why impacket (not a Windows/Samba host) was the
        // cross-check target.
        const val IMPACKET_ENCODED_NEGOTIATE_RESPONSE_HEX =
            "fe534d42400000000000000000000100000000000000000000000000000000000" +
                "00000000000000000000000000000000000000000000000000000000000000041" +
                "00010002030000000000000000000000000000000000000000000000000100000" +
                "0010000000100000000000000000000000000000000008000000000000000"
    }
}
