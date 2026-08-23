package dev.enthusiastdev.netinspector.data.lan.snmp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import javax.inject.Inject

/**
 * docs/device-identification-ideas.md B1 - a single SNMP v2c GET-request for OID
 * `1.3.6.1.2.1.1.1.0` (`sysDescr`) and `1.3.6.1.2.1.1.5.0` (`sysName`), sent to UDP 161 with
 * the default read-only community string `public`. Printers, managed switches, UPSes, and NAS
 * boxes very often leave this enabled and return an exact model/firmware string plus an
 * admin-set device name - historically one of the highest-value single probes for exactly the
 * "Network equipment" bucket that's otherwise weakest.
 */
class SnmpProbe
    @Inject
    constructor() {
        data class Result(
            val sysDescr: String?,
            val sysName: String?,
        )

        suspend fun query(
            address: Inet4Address,
            timeoutMs: Int,
            community: String = DEFAULT_COMMUNITY,
        ): Result? =
            withContext(Dispatchers.IO) {
                val socket = DatagramSocket()
                try {
                    socket.soTimeout = timeoutMs
                    val requestId = (System.nanoTime() and REQUEST_ID_MASK).toInt()
                    val request = SnmpBer.buildGetRequest(community, requestId, listOf(OID_SYS_DESCR, OID_SYS_NAME))
                    socket.send(DatagramPacket(request, request.size, address, SNMP_PORT))
                    receiveResult(socket)
                } catch (ignored: IOException) {
                    null
                } finally {
                    runCatching { socket.close() }
                }
            }

        private fun receiveResult(socket: DatagramSocket): Result? {
            val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val values = SnmpBer.parseGetResponse(packet.data, packet.length)
            val sysDescr = values[OID_SYS_DESCR]
            val sysName = values[OID_SYS_NAME]
            return if (sysDescr == null && sysName == null) null else Result(sysDescr, sysName)
        }

        private companion object {
            const val SNMP_PORT = 161
            const val RECEIVE_BUFFER_SIZE = 1500
            const val DEFAULT_COMMUNITY = "public"
            const val OID_SYS_DESCR = "1.3.6.1.2.1.1.1.0"
            const val OID_SYS_NAME = "1.3.6.1.2.1.1.5.0"
            const val REQUEST_ID_MASK = 0x7FFFFFFFL
        }
    }
