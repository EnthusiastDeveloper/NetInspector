package dev.enthusiastdev.netinspector.data.diagnostics.wol

import dev.enthusiastdev.netinspector.core.common.net.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject

interface WakeOnLanRepository {
    /** design §9.6 - magic packet to UDP broadcast port 9. Returns `false` for a malformed MAC
     * rather than throwing - this is user-typed input, not a programming error. */
    suspend fun wake(
        mac: String,
        broadcastAddress: String = "255.255.255.255",
    ): Boolean
}

class DefaultWakeOnLanRepository
    @Inject
    constructor() : WakeOnLanRepository {
        override suspend fun wake(
            mac: String,
            broadcastAddress: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val packet = WakeOnLan.buildMagicPacket(mac) ?: return@withContext false
                try {
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        val address = InetAddress.getByName(broadcastAddress)
                        socket.send(DatagramPacket(packet, packet.size, address, WOL_PORT))
                    }
                    true
                } catch (ignored: IOException) {
                    false
                }
            }

        private companion object {
            const val WOL_PORT = 9
        }
    }
