package dev.enthusiastdev.netinspector.data.lan.netbios

import dev.enthusiastdev.netinspector.core.model.lan.Evidence
import dev.enthusiastdev.netinspector.core.model.lan.EvidenceSource
import dev.enthusiastdev.netinspector.core.model.lan.HostObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.SocketTimeoutException
import java.time.Clock
import javax.inject.Inject

/**
 * design §8.2 Stage A - a NetBIOS Name Service node-status (NBSTAT) query (RFC 1002 §4.2.18)
 * sent to the subnet's broadcast address on UDP 137. Extracts the first non-group
 * ("unique") name entry with the workstation-service suffix as the host's NetBIOS name.
 */
class NetBiosProbe
    @Inject
    constructor(
        private val clock: Clock,
    ) {
        suspend fun discover(
            broadcastAddress: Inet4Address,
            budgetMs: Long = DEFAULT_BUDGET_MS,
        ): List<HostObservation> =
            withContext(Dispatchers.IO) {
                val socket = DatagramSocket().apply { broadcast = true }
                try {
                    val request = buildNbstatQuery()
                    socket.send(DatagramPacket(request, request.size, broadcastAddress, NETBIOS_PORT))
                    collectResponses(socket, budgetMs)
                } finally {
                    runCatching { socket.close() }
                }
            }

        private fun collectResponses(
            socket: DatagramSocket,
            budgetMs: Long,
        ): List<HostObservation> {
            val results = mutableMapOf<Inet4Address, HostObservation>()
            val deadline = System.currentTimeMillis() + budgetMs
            val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
            var timedOut = false
            while (!timedOut && System.currentTimeMillis() < deadline) {
                val remainingMs = deadline - System.currentTimeMillis()
                socket.soTimeout = remainingMs.toInt().coerceIn(1, Int.MAX_VALUE)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val address = packet.address as? Inet4Address
                    val name = if (address != null) parseNbstatResponse(packet.data, packet.length) else null
                    if (address != null && name != null) {
                        results[address] =
                            HostObservation(
                                address = address,
                                evidence = listOf(Evidence(EvidenceSource.NETBIOS, clock.instant())),
                                hostnames = mapOf(EvidenceSource.NETBIOS to name),
                            )
                    }
                } catch (ignored: SocketTimeoutException) {
                    timedOut = true
                }
            }
            return results.values.toList()
        }

        private fun buildNbstatQuery(): ByteArray {
            val buffer = ByteArray(QUERY_SIZE)
            buffer[0] = (TRANSACTION_ID shr 8).toByte()
            buffer[1] = TRANSACTION_ID.toByte()
            // Flags = 0x0000 (standard query); ANCOUNT/NSCOUNT/ARCOUNT = 0 (already zero).
            buffer[5] = 0x01 // QDCOUNT = 1
            var offset = HEADER_SIZE
            buffer[offset] = ENCODED_NAME_LENGTH.toByte()
            offset += 1
            encodeNetBiosName(WILDCARD_NAME).copyInto(buffer, offset)
            offset += ENCODED_NAME_LENGTH
            buffer[offset] = 0x00 // name terminator (root label)
            offset += 1
            buffer[offset] = 0x00
            buffer[offset + 1] = QTYPE_NBSTAT.toByte()
            offset += 2
            buffer[offset] = 0x00
            buffer[offset + 1] = QCLASS_IN.toByte()
            return buffer
        }

        /** First-level NetBIOS name encoding: each byte splits into two nibbles, each
         * offset into `'A'..'P'`. */
        private fun encodeNetBiosName(name: ByteArray): ByteArray {
            val encoded = ByteArray(name.size * 2)
            for (i in name.indices) {
                val b = name[i].toInt() and 0xFF
                encoded[i * 2] = ('A'.code + (b shr 4)).toByte()
                encoded[i * 2 + 1] = ('A'.code + (b and 0x0F)).toByte()
            }
            return encoded
        }

        /** RDATA layout: NUM_NAMES (1 byte) then NUM_NAMES × [name(15) + type(1) + flags(2)]. */
        private fun parseNbstatResponse(
            buffer: ByteArray,
            length: Int,
        ): String? {
            if (length < HEADER_SIZE) return null
            val answerCount = ((buffer[6].toInt() and 0xFF) shl 8) or (buffer[7].toInt() and 0xFF)
            if (answerCount < 1) return null

            // Skip header + echoed RR name (length byte + encoded name + terminator) + TYPE(2)
            // + CLASS(2) + TTL(4) + RDLENGTH(2).
            val firstEntryOffset = HEADER_SIZE + (1 + ENCODED_NAME_LENGTH + 1) + 2 + 2 + 4 + 2
            if (firstEntryOffset >= length) return null
            val numNames = buffer[firstEntryOffset].toInt() and 0xFF
            val entriesStart = firstEntryOffset + 1

            return (0 until numNames)
                .asSequence()
                .mapNotNull { index ->
                    val entryOffset = entriesStart + index * NAME_ENTRY_SIZE
                    if (entryOffset + NAME_ENTRY_SIZE > length) null else workstationNameAt(buffer, entryOffset)
                }.firstOrNull()
        }

        /** `null` unless this NBSTAT name entry is a non-group name with the workstation-service
         * suffix - the one treated as the host's display name. */
        private fun workstationNameAt(
            buffer: ByteArray,
            entryOffset: Int,
        ): String? {
            val nameBytes = buffer.copyOfRange(entryOffset, entryOffset + NETBIOS_NAME_SIZE)
            val nameType = buffer[entryOffset + NETBIOS_NAME_SIZE].toInt() and 0xFF
            val flagsHighByte = buffer[entryOffset + NETBIOS_NAME_SIZE + 1].toInt() and 0xFF
            val isGroupName = (flagsHighByte and 0x80) != 0
            if (isGroupName || nameType != WORKSTATION_SERVICE_SUFFIX) return null
            return String(nameBytes, Charsets.US_ASCII).trimEnd(' ', '\u0000').ifBlank { null }
        }

        private companion object {
            const val NETBIOS_PORT = 137
            const val DEFAULT_BUDGET_MS = 2_000L
            const val RECEIVE_BUFFER_SIZE = 1024
            const val TRANSACTION_ID = 0x1337
            const val HEADER_SIZE = 12
            const val NETBIOS_NAME_SIZE = 15
            const val NAME_ENTRY_SIZE = 18 // 15-byte name + 1-byte type + 2-byte flags
            const val ENCODED_NAME_LENGTH = 32 // 16-byte name, first-level-encoded to 32 bytes

            // header + length byte + encoded name + terminator + QTYPE(2) + QCLASS(2)
            const val QUERY_SIZE = HEADER_SIZE + 1 + ENCODED_NAME_LENGTH + 1 + 2 + 2
            const val QTYPE_NBSTAT = 0x21
            const val QCLASS_IN = 0x01
            const val WORKSTATION_SERVICE_SUFFIX = 0x00
            val WILDCARD_NAME = ByteArray(16).also { it[0] = '*'.code.toByte() }
        }
    }
