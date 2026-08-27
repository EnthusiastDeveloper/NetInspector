package dev.enthusiastdev.netinspector.core.common.icmp

import dev.enthusiastdev.netinspector.core.common.benchmark.Benchmark
import org.junit.Test

/**
 * ideas.md #32 - the LAN sweep pipeline's parsing logic: every ICMP probe in
 * design §8.2 passes 1-2 builds one request and parses one reply, at up to 64-way concurrency
 * (`HostSweeper.PASS1_CONCURRENCY`), so this framing code runs on the hot path of the sweep
 * far more often than any other parser in the app. A regression here (e.g. an accidental
 * allocation added to the checksum loop) would show up as a slower sweep on every network,
 * not just an edge case.
 */
class IcmpPacketBenchmark {
    @Test
    fun `buildEchoRequest - 32 byte payload`() {
        val payload = ByteArray(PAYLOAD_SIZE)
        Benchmark.run("IcmpPacket.buildEchoRequest.32b") {
            IcmpPacket.buildEchoRequest(identifier = IDENTIFIER, sequence = SEQUENCE, payload = payload)
        }
    }

    @Test
    fun `parseEchoReply - typical reply buffer`() {
        val payload = ByteArray(PAYLOAD_SIZE)
        val request = IcmpPacket.buildEchoRequest(identifier = IDENTIFIER, sequence = SEQUENCE, payload = payload)
        // A real echo reply mirrors the request's identifier/sequence/payload with type 0
        // instead of 8 (design C-18) - close enough in shape for a parse-cost benchmark.
        val reply = request.copyOf()
        reply[0] = IcmpPacket.TYPE_ECHO_REPLY.toByte()

        Benchmark.run("IcmpPacket.parseEchoReply.32b") {
            IcmpPacket.parseEchoReply(reply, reply.size)
        }
    }

    @Test
    fun `internetChecksum - typical reply buffer`() {
        val payload = ByteArray(PAYLOAD_SIZE)
        val request = IcmpPacket.buildEchoRequest(identifier = IDENTIFIER, sequence = SEQUENCE, payload = payload)

        Benchmark.run("IcmpPacket.internetChecksum.32b") {
            IcmpPacket.internetChecksum(request)
        }
    }

    private companion object {
        const val PAYLOAD_SIZE = 32
        const val IDENTIFIER = 0x1234
        const val SEQUENCE = 1
    }
}
