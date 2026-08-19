package dev.enthusiastdev.netinspector.data.diagnostics.traceroute

import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructMsghdr
import android.system.StructPollfd
import android.system.StructTimeval
import dev.enthusiastdev.netinspector.core.common.icmp.IcmpPacket
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteProbe
import dev.enthusiastdev.netinspector.core.model.diagnostics.TracerouteTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Tier 1 (design §9.3, spike S-02): a TTL walk over the same unprivileged ICMP datagram socket
 * as [dev.enthusiastdev.netinspector.data.diagnostics.icmp.IcmpSocketEngine], reading
 * intermediate routers' "Time Exceeded" replies from the socket **error queue** rather than the
 * normal receive path - Linux only delivers an ICMP error there, never through a plain
 * `recvfrom` (design C-08).
 *
 * **Spike S-02 outcome: Pass**, verified end to end on the S21 Ultra (Android 15, One UI 7.0)
 * against `8.8.8.8`: the error-queue path correctly surfaced the LAN gateway as the TTL=1
 * offender, and a sufficiently high TTL produced a normal echo reply from the target itself. See
 * `docs/02-android-constraints.md` C-08.
 *
 * The flow per hop, mirroring how `traceroute`/`mtr` use `IP_RECVERR` on Linux generally:
 * 1. Set `IP_TTL` to the hop being probed and enable `IP_RECVERR`.
 * 2. Send one echo request, then `poll()` the socket.
 * 3. `POLLERR` means an error is queued - `recvmsg(..., MSG_ERRQUEUE)` pulls it off, and the
 *    offending router's address rides in the ancillary `IP_RECVERR` cmsg as a
 *    `struct sock_extended_err` immediately followed by a `struct sockaddr_in` (the
 *    `SO_EE_OFFENDER` macro in `<linux/errqueue.h>`).
 * 4. `POLLIN` (no `POLLERR`) means the target replied normally - TTL was high enough to reach
 *    it, so this hop is the final one.
 *
 * Neither `IP_RECVERR` (the sockopt) nor `MSG_ERRQUEUE` (the `recvmsg` flag) has a named
 * constant in [OsConstants], the same gap C-18 found for `IP_RECVTTL` - both are stable Linux
 * UAPI values (`include/uapi/linux/in.h`, `include/uapi/asm-generic/socket.h`) shared by every
 * architecture Android ships on, so they're passed as raw ints rather than waiting on bionic.
 */
class TracerouteSocketEngine
    @Inject
    constructor() {
        suspend fun probeHop(
            target: Inet4Address,
            ttl: Int,
            timeoutMs: Int,
        ): TracerouteProbe =
            withContext(Dispatchers.IO) {
                val fd =
                    try {
                        Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
                    } catch (e: ErrnoException) {
                        return@withContext TracerouteProbe.Error(TIER, "socket() failed: errno ${e.errno}")
                    }

                try {
                    Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, ttl)
                    Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, IP_RECVERR, 1)
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
                    Os.sendto(fd, request, 0, request.size, 0, target, 0)

                    val pollfd =
                        StructPollfd().apply {
                            this.fd = fd
                            events = OsConstants.POLLIN.toShort()
                        }
                    val ready = Os.poll(arrayOf(pollfd), timeoutMs)
                    val rttMs = (System.nanoTime() - sentAtNanos) / 1_000_000.0

                    if (ready <= 0) {
                        TracerouteProbe.Timeout(TIER)
                    } else {
                        val revents = pollfd.revents.toInt()
                        when {
                            revents and OsConstants.POLLERR != 0 -> readErrorQueue(fd, rttMs)
                            revents and OsConstants.POLLIN != 0 -> readNormalReply(fd, rttMs)
                            else -> TracerouteProbe.Timeout(TIER)
                        }
                    }
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EAGAIN) {
                        TracerouteProbe.Timeout(TIER)
                    } else {
                        TracerouteProbe.Error(TIER, "errno ${e.errno}: ${e.message}")
                    }
                } catch (e: IOException) {
                    TracerouteProbe.Error(TIER, e.message ?: "I/O error")
                } finally {
                    runCatching { Os.close(fd) }
                }
            }

        private fun readNormalReply(
            fd: FileDescriptor,
            rttMs: Double,
        ): TracerouteProbe {
            val buffer = ByteArray(IcmpPacket.HEADER_SIZE + PAYLOAD_SIZE + REPLY_SLACK_BYTES)
            val from = InetSocketAddress(0)
            val length = Os.recvfrom(fd, buffer, 0, buffer.size, 0, from)
            val reply = IcmpPacket.parseEchoReply(buffer, length)
            val address = (from.address as? Inet4Address)?.hostAddress
            val isExpectedEchoReply = reply != null && reply.isEchoReply && reply.sequence == SEQUENCE
            return if (isExpectedEchoReply && address != null) {
                TracerouteProbe.Reply(TIER, address, rttMs, reachedTarget = true)
            } else {
                TracerouteProbe.Error(TIER, "unexpected reply on normal receive path: $reply")
            }
        }

        private fun readErrorQueue(
            fd: FileDescriptor,
            rttMs: Double,
        ): TracerouteProbe {
            val iov = ByteBuffer.allocate(IcmpPacket.HEADER_SIZE + PAYLOAD_SIZE)
            val msg = StructMsghdr(InetSocketAddress(0), arrayOf(iov), arrayOf(), 0)
            Os.recvmsg(fd, msg, MSG_ERRQUEUE)
            val offender = offenderAddress(msg)
            return if (offender != null) {
                TracerouteProbe.Reply(TIER, offender, rttMs, reachedTarget = false)
            } else {
                TracerouteProbe.Error(TIER, "error queue entry carried no offender address")
            }
        }

        /** `SO_EE_OFFENDER` (`<linux/errqueue.h>`): the offending router's `sockaddr_in` sits
         * directly after the fixed 16-byte `struct sock_extended_err` in the cmsg payload -
         * family+port (4 bytes) then the 4-byte IPv4 address. */
        private fun offenderAddress(msg: StructMsghdr): String? {
            val cmsg =
                msg.msg_control
                    ?.firstOrNull { it.cmsg_level == OsConstants.IPPROTO_IP && it.cmsg_type == IP_RECVERR }
                    ?: return null
            val data = cmsg.cmsg_data
            val addressOffset = SOCK_EXTENDED_ERR_SIZE + SOCKADDR_FAMILY_AND_PORT_SIZE
            if (data.size < addressOffset + 4) return null
            val addressBytes = data.copyOfRange(addressOffset, addressOffset + 4)
            return runCatching { InetAddress.getByAddress(addressBytes).hostAddress }.getOrNull()
        }

        private companion object {
            val TIER = TracerouteTier.ICMP_ERROR_QUEUE
            const val SEQUENCE = 1
            const val PAYLOAD_SIZE = 32
            const val REPLY_SLACK_BYTES = 64
            const val IP_RECVERR = 11
            const val MSG_ERRQUEUE = 0x2000
            const val SOCK_EXTENDED_ERR_SIZE = 16
            const val SOCKADDR_FAMILY_AND_PORT_SIZE = 4
        }
    }
