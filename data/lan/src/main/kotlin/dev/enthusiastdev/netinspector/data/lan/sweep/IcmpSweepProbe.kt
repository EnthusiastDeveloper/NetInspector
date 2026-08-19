package dev.enthusiastdev.netinspector.data.lan.sweep

import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructTimeval
import dev.enthusiastdev.netinspector.core.common.icmp.IcmpPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import javax.inject.Inject

/**
 * design §8.2 Stage B, passes 1-2 - a minimal one-shot ICMP echo for the LAN sweep. Deliberately
 * separate from `:data:diagnostics`'s ping-tool engine: the module graph (design §2.1) forbids
 * `:data:lan` depending on another `:data:*` module, even though the underlying
 * `android.system.Os` datagram-socket technique (design C-07) is the same. Returns the RTT in
 * milliseconds, or `null` on timeout/error - the sweep only needs up/down plus latency, not the
 * ping tool's full tiering model.
 */
class IcmpSweepProbe
    @Inject
    constructor() {
        suspend fun probe(
            address: Inet4Address,
            timeoutMs: Int,
        ): Double? =
            withContext(Dispatchers.IO) {
                val fd =
                    try {
                        Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
                    } catch (ignored: ErrnoException) {
                        return@withContext null
                    }
                try {
                    Os.setsockoptTimeval(
                        fd,
                        OsConstants.SOL_SOCKET,
                        OsConstants.SO_RCVTIMEO,
                        StructTimeval.fromMillis(timeoutMs.toLong()),
                    )
                    val identifier = Process.myPid() and 0xFFFF
                    val payload = ByteArray(PAYLOAD_SIZE)
                    val request = IcmpPacket.buildEchoRequest(identifier, SEQUENCE, payload)

                    val sentAtNanos = System.nanoTime()
                    Os.sendto(fd, request, 0, request.size, 0, address, 0)

                    val buffer = ByteArray(IcmpPacket.HEADER_SIZE + payload.size + REPLY_SLACK_BYTES)
                    val from = InetSocketAddress(0)
                    val length = Os.recvfrom(fd, buffer, 0, buffer.size, 0, from)
                    val receivedAtNanos = System.nanoTime()

                    val reply = IcmpPacket.parseEchoReply(buffer, length)
                    if (reply != null && reply.isEchoReply && reply.sequence == SEQUENCE) {
                        (receivedAtNanos - sentAtNanos) / 1_000_000.0
                    } else {
                        null
                    }
                } catch (ignored: ErrnoException) {
                    null
                } catch (ignored: IOException) {
                    null
                } finally {
                    runCatching { Os.close(fd) }
                }
            }

        private companion object {
            const val SEQUENCE = 1
            const val PAYLOAD_SIZE = 32
            const val REPLY_SLACK_BYTES = 64
        }
    }
