package dev.enthusiastdev.netinspector.data.lan.sweep

import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructMsghdr
import android.system.StructTimeval
import dev.enthusiastdev.netinspector.core.common.icmp.IcmpPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * design §8.2 Stage B, passes 1-2 - a minimal one-shot ICMP echo for the LAN sweep. Deliberately
 * separate from `:data:diagnostics`'s ping-tool engine: the module graph (design §2.1) forbids
 * `:data:lan` depending on another `:data:*` module, even though the underlying
 * `android.system.Os` datagram-socket technique (design C-07) is the same. Returns the RTT in
 * milliseconds plus, best-effort, the reply's IP TTL for Stage C's OS-class fingerprint - the
 * sweep only needs up/down plus latency, not the ping tool's full tiering model.
 */
class IcmpSweepProbe
    @Inject
    constructor() {
        suspend fun probe(
            address: Inet4Address,
            timeoutMs: Int,
        ): IcmpEchoResult? =
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
                    // design C-18 - best-effort only; a failure here still leaves the RTT reply
                    // usable, so it must never throw out of this call.
                    runCatching { enableReceivedTtl(fd) }

                    val identifier = Process.myPid() and 0xFFFF
                    val payload = ByteArray(PAYLOAD_SIZE)
                    val request = IcmpPacket.buildEchoRequest(identifier, SEQUENCE, payload)

                    val sentAtNanos = System.nanoTime()
                    Os.sendto(fd, request, 0, request.size, 0, address, 0)

                    val bufferSize = IcmpPacket.HEADER_SIZE + payload.size + REPLY_SLACK_BYTES
                    val received = receiveWithTtl(fd, bufferSize)
                    val receivedAtNanos = System.nanoTime()

                    val reply = IcmpPacket.parseEchoReply(received.buffer, received.length)
                    if (reply != null && reply.isEchoReply && reply.sequence == SEQUENCE) {
                        IcmpEchoResult(
                            rttMs = (receivedAtNanos - sentAtNanos) / 1_000_000.0,
                            replyTtl = received.ttl,
                        )
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

        /**
         * design C-18 - Android's [OsConstants] exposes `IP_TTL` (the outbound sockopt) but not
         * `IP_RECVTTL`, the ancillary-data request needed to learn an *inbound* packet's TTL.
         * `IP_RECVTTL` is a stable Linux UAPI constant (`12`, `include/uapi/linux/in.h`), shared
         * by every architecture Android ships on, so it is safe to pass as a raw int even though
         * bionic gives it no named constant.
         */
        private fun enableReceivedTtl(fd: FileDescriptor) {
            Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, IP_RECVTTL, 1)
        }

        /**
         * [Os.recvfrom] does not surface ancillary data, so the TTL cmsg needs [Os.recvmsg]
         * instead - used unconditionally in place of `recvfrom`, not merely attempted first, so
         * a timeout still throws exactly once rather than being retried into a second full
         * `SO_RCVTIMEO` wait (which would silently double every non-responder's cost across the
         * sweep's 64/32-way concurrent passes). [ErrnoException]/[IOException] here propagate to
         * this method's caller, which already treats a thrown timeout as "no reply."
         */
        private fun receiveWithTtl(
            fd: FileDescriptor,
            bufferSize: Int,
        ): ReceivedDatagram {
            val iov = ByteBuffer.allocate(bufferSize)
            val msg = StructMsghdr(InetSocketAddress(0), arrayOf(iov), arrayOf(), 0)
            val length = Os.recvmsg(fd, msg, 0)
            val buffer =
                ByteArray(length).also {
                    iov.rewind()
                    iov.get(it)
                }
            val ttl =
                msg.msg_control
                    ?.firstOrNull { it.cmsg_level == OsConstants.IPPROTO_IP && it.cmsg_type == OsConstants.IP_TTL }
                    ?.cmsg_data
                    ?.let { data -> if (data.isNotEmpty()) data[0].toInt() and 0xFF else null }
            return ReceivedDatagram(length, buffer, ttl)
        }

        private data class ReceivedDatagram(
            val length: Int,
            val buffer: ByteArray,
            val ttl: Int?,
        )

        private companion object {
            const val SEQUENCE = 1
            const val PAYLOAD_SIZE = 32
            const val REPLY_SLACK_BYTES = 64
            const val IP_RECVTTL = 12
        }
    }

data class IcmpEchoResult(
    val rttMs: Double,
    val replyTtl: Int?,
)
