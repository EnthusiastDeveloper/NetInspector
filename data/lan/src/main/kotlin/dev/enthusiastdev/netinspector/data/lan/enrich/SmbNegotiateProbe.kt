package dev.enthusiastdev.netinspector.data.lan.enrich

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

/**
 * docs/ideas.md A7 - the dialect a host actually negotiates in a bare SMB2 NEGOTIATE exchange
 * on TCP 445 (Direct TCP transport, [MS-SMB2] §2.1 - no NetBIOS Session Service handshake
 * needed on this port, just its 4-byte length-prefix framing convention) narrows the existing
 * 445+139 port-signature hint's "Windows/Samba file sharing" down to an OS-version range
 * ([DeviceHintHeuristics.smbDialectHint]). This is a connect-and-read protocol exchange over a
 * plain [Socket], the same "no root, no raw socket" constraint as every other probe in this
 * package (design C-07) - it just happens to be SMB2's own binary framing being spoken instead
 * of HTTP/SNMP/TLS's.
 *
 * Deliberately stops at dialect 3.0.2: offering 3.1.1 would also require SMB2's mandatory
 * negotiate contexts (preauth integrity + encryption, their own nested TLV structure) for a
 * fingerprinting gain of approximately zero - Windows 10 and 11 both negotiate 3.1.1 and are
 * indistinguishable by dialect alone regardless, so the extra construction/parsing complexity
 * buys nothing a real host couldn't already show by negotiating down to 3.0.2 against this
 * probe's shorter offered list. Deliberately never attempts NTLM Type 2 message parsing for an
 * exact Windows build number either (the technique `nmap`'s `smb-os-discovery` and similar
 * tools use): that requires wrapping the NTLM negotiate in a GSS-API/SPNEGO ASN.1 envelope,
 * real DER encoding this codebase has no existing precedent for and no live Windows host on
 * hand to validate against - the risk of a silently-wrong hand-rolled ASN.1 encoder outweighs
 * the extra precision over the dialect-only result.
 */
class SmbNegotiateProbe
    @Inject
    constructor() {
        suspend fun negotiateDialect(
            address: Inet4Address,
            timeoutMs: Int,
        ): Int? =
            withContext(Dispatchers.IO) {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(address, SMB_PORT), timeoutMs)
                        socket.soTimeout = timeoutMs
                        socket.getOutputStream().write(negotiateRequest())
                        parseDialectRevision(readMessage(socket))
                    }
                } catch (ignored: IOException) {
                    null
                }
            }

        /** [MS-SMB2] §2.2.3 NEGOTIATE_REQUEST, wrapped in the header from §2.2.1.1 and the
         * 4-byte Direct-TCP-transport length prefix (§2.1) every SMB2 message needs on port 445.
         * `ClientGuid`/`ClientStartTime` are left zeroed - this exchange is never authenticated
         * and the connection is dropped right after, so neither field is ever read back.
         * `internal`, along with [parseDialectRevision], so [SmbNegotiateProbeTest] can check the
         * wire format directly - there's no SMB responder on hand in every environment this runs
         * in, the same reasoning [dev.enthusiastdev.netinspector.data.lan.netbios.NetBiosProbe]
         * gives for its own `internal` parsing function. */
        internal fun negotiateRequest(): ByteArray {
            val body =
                buildBody {
                    putShortLe(NEGOTIATE_REQUEST_STRUCTURE_SIZE)
                    putShortLe(OFFERED_DIALECTS.size) // DialectCount
                    putShortLe(SIGNING_ENABLED) // SecurityMode
                    putShortLe(0) // Reserved
                    putIntLe(0) // Capabilities
                    put(ByteArray(CLIENT_GUID_SIZE)) // ClientGuid
                    put(ByteArray(CLIENT_START_TIME_SIZE)) // ClientStartTime (no 3.1.1 offered)
                    OFFERED_DIALECTS.forEach { putShortLe(it) }
                }
            val header = smb2Header(command = COMMAND_NEGOTIATE)
            return lengthPrefixed(header + body)
        }

        private fun smb2Header(command: Int): ByteArray =
            buildBody {
                put(SMB2_PROTOCOL_ID)
                putShortLe(SMB2_HEADER_STRUCTURE_SIZE)
                putShortLe(0) // CreditCharge
                putIntLe(0) // ChannelSequence + Reserved (a request's Status field is unused)
                putShortLe(command)
                putShortLe(1) // CreditRequest
                putIntLe(0) // Flags - a client request, not a compounded/related/signed one
                putIntLe(0) // NextCommand - not compounded
                put(ByteArray(MESSAGE_ID_SIZE)) // MessageId - 0 is valid for a connection's first request
                put(ByteArray(RESERVED_AND_TREE_ID_SIZE)) // Reserved + TreeId
                put(ByteArray(SESSION_ID_SIZE)) // SessionId - none established yet
                put(ByteArray(SIGNATURE_SIZE)) // Signature - request isn't signed
            }

        private fun lengthPrefixed(message: ByteArray): ByteArray {
            val prefix =
                byteArrayOf(0, (message.size shr 16).toByte(), (message.size shr 8).toByte(), message.size.toByte())
            return prefix + message
        }

        private fun readMessage(socket: Socket): ByteArray {
            val input = DataInputStream(socket.getInputStream())
            val prefix = ByteArray(LENGTH_PREFIX_SIZE)
            input.readFully(prefix)
            val length =
                ((prefix[1].toInt() and 0xFF) shl 16) or ((prefix[2].toInt() and 0xFF) shl 8) or
                    (prefix[3].toInt() and 0xFF)
            if (length !in 1..MAX_RESPONSE_SIZE) throw IOException("implausible SMB2 response length $length")
            val message = ByteArray(length)
            input.readFully(message)
            return message
        }

        /** `null` for anything that isn't a well-formed, successful SMB2 NEGOTIATE response -
         * notably including a legacy SMB1-only server, which answers with protocol ID `0xFF
         * 'SMB'` instead of `0xFE 'SMB'` and is a wire format this probe doesn't parse. */
        internal fun parseDialectRevision(message: ByteArray): Int? {
            if (message.size < SMB2_HEADER_STRUCTURE_SIZE + DIALECT_REVISION_OFFSET_IN_BODY + 2) return null
            if (!message.copyOfRange(0, SMB2_PROTOCOL_ID.size).contentEquals(SMB2_PROTOCOL_ID)) return null
            val command = readShortLe(message, COMMAND_OFFSET)
            val status = readIntLe(message, STATUS_OFFSET)
            if (command != COMMAND_NEGOTIATE || status != STATUS_SUCCESS) return null
            val dialectOffset = SMB2_HEADER_STRUCTURE_SIZE + DIALECT_REVISION_OFFSET_IN_BODY
            return readShortLe(message, dialectOffset)
        }

        private fun readShortLe(
            bytes: ByteArray,
            offset: Int,
        ): Int = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

        private fun readIntLe(
            bytes: ByteArray,
            offset: Int,
        ): Int =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)

        /** Tiny little-endian byte-buffer builder so the field-by-field layout above reads in
         * wire order top to bottom, the same shape [dev.enthusiastdev.netinspector.data.lan
         * .snmp.SnmpBer] uses for BER rather than pulling in a general-purpose buffer library
         * for one probe's fixed, small packets. */
        private class BodyBuilder {
            private val bytes = mutableListOf<Byte>()

            fun put(value: ByteArray) {
                bytes.addAll(value.toList())
            }

            fun putShortLe(value: Int) {
                bytes.add(value.toByte())
                bytes.add((value shr 8).toByte())
            }

            fun putIntLe(value: Int) {
                bytes.add(value.toByte())
                bytes.add((value shr 8).toByte())
                bytes.add((value shr 16).toByte())
                bytes.add((value shr 24).toByte())
            }

            fun toByteArray(): ByteArray = bytes.toByteArray()
        }

        private inline fun buildBody(block: BodyBuilder.() -> Unit): ByteArray =
            BodyBuilder().apply(block).toByteArray()

        private companion object {
            const val SMB_PORT = 445
            val SMB2_PROTOCOL_ID = byteArrayOf(0xFE.toByte(), 'S'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte())
            const val SMB2_HEADER_STRUCTURE_SIZE = 64
            const val NEGOTIATE_REQUEST_STRUCTURE_SIZE = 36
            const val SIGNING_ENABLED = 0x0001
            const val CLIENT_GUID_SIZE = 16
            const val CLIENT_START_TIME_SIZE = 8
            const val MESSAGE_ID_SIZE = 8
            const val RESERVED_AND_TREE_ID_SIZE = 8
            const val SESSION_ID_SIZE = 8
            const val SIGNATURE_SIZE = 16
            const val LENGTH_PREFIX_SIZE = 4
            const val MAX_RESPONSE_SIZE = 16 * 1024
            const val COMMAND_NEGOTIATE = 0x0000
            const val COMMAND_OFFSET = 12
            const val STATUS_OFFSET = 8
            const val STATUS_SUCCESS = 0

            // NEGOTIATE_RESPONSE (§2.2.4): StructureSize(2) + SecurityMode(2) precede DialectRevision.
            const val DIALECT_REVISION_OFFSET_IN_BODY = 4

            // §2.2.3 - only dialects this probe can request without also needing 3.1.1's
            // mandatory negotiate contexts (see the class doc comment).
            val OFFERED_DIALECTS = listOf(0x0202, 0x0210, 0x0300, 0x0302)
        }
    }
