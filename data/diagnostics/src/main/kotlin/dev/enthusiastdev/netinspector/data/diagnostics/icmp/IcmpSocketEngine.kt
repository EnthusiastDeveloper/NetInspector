package dev.enthusiastdev.netinspector.data.diagnostics.icmp

import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructTimeval
import dev.enthusiastdev.netinspector.core.common.icmp.IcmpPacket
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingProbeResult
import dev.enthusiastdev.netinspector.core.model.diagnostics.PingTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import javax.inject.Inject

/**
 * Tier 1 (design §9.1): unprivileged ICMP datagram sockets via `android.system.Os`. Android's
 * init sets `net.ipv4.ping_group_range` permissively, so `SOCK_DGRAM`/`IPPROTO_ICMP` needs no
 * root and no native code. Validated against spike S-01 on the device matrix before Phase 7
 * depends on it (see `docs/02-android-constraints.md` C-07).
 *
 * The kernel rewrites the ICMP identifier and recomputes the checksum on send, so replies are
 * matched on sequence number (and implicitly on source address, since `recvfrom` only returns
 * data received on this bound socket) rather than the identifier this class set.
 */
class IcmpSocketEngine
    @Inject
    constructor() {
        /**
         * Capability check for [PingRepository]'s tier-1/tier-2 selection (design §9.1: "Fail
         * on some devices -> runtime capability detection and tier 2 as automatic fallback").
         * A brief blocking socket create/close, not a network operation - safe to call
         * without `Dispatchers.IO`.
         */
        fun isSupported(): Boolean =
            try {
                Os.close(Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP))
                true
            } catch (ignored: ErrnoException) {
                false
            }

        suspend fun probe(
            address: Inet4Address,
            sequence: Int,
            timeoutMs: Int = 1_000,
            ttl: Int = 64,
            payloadSize: Int = 32,
        ): PingProbeResult =
            withContext(Dispatchers.IO) {
                val fd =
                    try {
                        openSocket(timeoutMs, ttl)
                    } catch (e: ErrnoException) {
                        return@withContext PingProbeResult.Error(
                            sequence,
                            PingTier.ICMP_SOCKET,
                            "socket() failed: errno ${e.errno}",
                        )
                    }
                try {
                    probeOnSocket(fd, address, sequence, payloadSize)
                } finally {
                    closeSocket(fd)
                }
            }

        /**
         * Opens and configures a socket without sending anything - split out of [probe] so a
         * caller making many probes in quick succession (the LAN throughput test's burst) pays
         * the `socket()`/`setsockopt()` cost once rather than per packet. The caller owns the
         * returned descriptor and must [closeSocket] it.
         */
        @Throws(ErrnoException::class)
        fun openSocket(
            timeoutMs: Int = 1_000,
            ttl: Int = 64,
        ): FileDescriptor {
            val fd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
            try {
                Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, ttl)
                Os.setsockoptTimeval(
                    fd,
                    OsConstants.SOL_SOCKET,
                    OsConstants.SO_RCVTIMEO,
                    StructTimeval.fromMillis(timeoutMs.toLong()),
                )
            } catch (e: ErrnoException) {
                runCatching { Os.close(fd) }
                throw e
            }
            return fd
        }

        fun closeSocket(fd: FileDescriptor) {
            runCatching { Os.close(fd) }
        }

        /** One echo request/reply on an already-open [fd] (see [openSocket]) - the per-packet
         * half of what [probe] used to do inline. */
        suspend fun probeOnSocket(
            fd: FileDescriptor,
            address: Inet4Address,
            sequence: Int,
            payloadSize: Int = 32,
        ): PingProbeResult =
            withContext(Dispatchers.IO) {
                try {
                    val payload = ByteArray(payloadSize) { it.toByte() }
                    val identifier = Process.myPid() and 0xFFFF
                    val request = IcmpPacket.buildEchoRequest(identifier, sequence, payload)

                    val sentAtNanos = System.nanoTime()
                    Os.sendto(fd, request, 0, request.size, 0, address, 0)

                    val buffer = ByteArray(IcmpPacket.HEADER_SIZE + payloadSize + 64)
                    val fromAddress = InetSocketAddress(0)
                    val receivedLength = Os.recvfrom(fd, buffer, 0, buffer.size, 0, fromAddress)
                    val receivedAtNanos = System.nanoTime()

                    toProbeResult(sequence, buffer, receivedLength, sentAtNanos, receivedAtNanos)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EAGAIN) {
                        PingProbeResult.Timeout(sequence, PingTier.ICMP_SOCKET)
                    } else {
                        PingProbeResult.Error(sequence, PingTier.ICMP_SOCKET, "errno ${e.errno}: ${e.message}")
                    }
                } catch (e: IOException) {
                    PingProbeResult.Error(sequence, PingTier.ICMP_SOCKET, e.message ?: "I/O error")
                }
            }

        private fun toProbeResult(
            sequence: Int,
            buffer: ByteArray,
            receivedLength: Int,
            sentAtNanos: Long,
            receivedAtNanos: Long,
        ): PingProbeResult {
            val reply = IcmpPacket.parseEchoReply(buffer, receivedLength)
            return when {
                reply == null || !reply.isEchoReply ->
                    PingProbeResult.Error(sequence, PingTier.ICMP_SOCKET, "malformed or non-reply ICMP packet: $reply")
                reply.sequence != sequence ->
                    PingProbeResult.Error(
                        sequence,
                        PingTier.ICMP_SOCKET,
                        "sequence mismatch: expected $sequence, got ${reply.sequence}",
                    )
                else ->
                    PingProbeResult.Reply(sequence, PingTier.ICMP_SOCKET, (receivedAtNanos - sentAtNanos) / 1_000_000.0)
            }
        }
    }
