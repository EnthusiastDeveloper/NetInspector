package dev.enthusiastdev.netinspector.data.lan.enrich

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * [ReverseDnsProbe.resolve] calls `android.net.DnsResolver`, which only exists on a real
 * Android runtime, so it isn't covered here (no `ReverseDnsProbeTest` existed before this
 * class for that reason). [ReverseDnsProbe.resolveViaGateway] is plain `java.net` socket code
 * added for docs/adr/c-19-private-dns-breaks-reverse-lookup.md, so it's exercised against a
 * real loopback UDP responder standing in for the LAN gateway.
 */
class ReverseDnsProbeTest {
    private val probe = ReverseDnsProbe()
    private val loopback = InetAddress.getByName("127.0.0.1") as Inet4Address
    private val target = InetAddress.getByName("192.168.1.50") as Inet4Address

    @Test
    fun `resolveViaGateway parses the PTR answer from a direct gateway response`() =
        runTest {
            val server = DatagramSocket(0, loopback)
            val responder = thread { respondOnce(server, "printer.lan") }
            try {
                val hostname = probe.resolveViaGateway(target, loopback, TIMEOUT_MS, server.localPort)
                assertThat(hostname).isEqualTo("printer.lan")
            } finally {
                responder.join()
                server.close()
            }
        }

    @Test
    fun `resolveViaGateway returns null when nothing answers`() =
        runTest {
            val server = DatagramSocket(0, loopback) // bound but nothing ever reads/responds
            try {
                val hostname = probe.resolveViaGateway(target, loopback, timeoutMs = 200, server.localPort)
                assertThat(hostname).isNull()
            } finally {
                server.close()
            }
        }

    /** Waits for one query, then sends back a minimal well-formed PTR response echoing the
     * query's own ID and question section - mirrors the wire format [DnsPtrQuery] itself
     * produces/expects (RFC 1035 §4.1), built by hand here since the codec is deliberately
     * query-only (no response encoder - production code never needs to build a DNS response). */
    private fun respondOnce(
        server: DatagramSocket,
        hostname: String,
    ) {
        val buffer = ByteArray(512)
        val packet = DatagramPacket(buffer, buffer.size)
        server.receive(packet)
        val query = packet.data.copyOf(packet.length)
        val response = buildPtrResponse(query, hostname)
        server.send(DatagramPacket(response, response.size, packet.address, packet.port))
    }

    private fun buildPtrResponse(
        query: ByteArray,
        hostname: String,
    ): ByteArray {
        val question = query.copyOfRange(HEADER_SIZE, query.size)
        val header =
            byteArrayOf(query[0], query[1], 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)
        val answerName = question.copyOfRange(0, question.size - QTYPE_QCLASS_SIZE)
        val rdata = encodeName(hostname)
        val answer =
            answerName +
                byteArrayOf(0x00, 0x0C, 0x00, 0x01, 0, 0, 0, 0) +
                byteArrayOf((rdata.size shr 8).toByte(), rdata.size.toByte()) +
                rdata
        return header + question + answer
    }

    private fun encodeName(name: String): ByteArray {
        val bytes = mutableListOf<Byte>()
        name.split(".").forEach { label ->
            bytes += label.length.toByte()
            bytes += label.toByteArray(Charsets.US_ASCII).toList()
        }
        bytes += 0
        return bytes.toByteArray()
    }

    private companion object {
        const val TIMEOUT_MS = 2_000
        const val HEADER_SIZE = 12
        const val QTYPE_QCLASS_SIZE = 4
    }
}
