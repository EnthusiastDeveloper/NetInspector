package dev.enthusiastdev.netinspector.core.common.net

/** design §9.6 - the magic packet: 6 bytes of `0xFF` followed by the target MAC repeated 16
 * times. Pure and unit-tested; the actual UDP broadcast send is impure and lives in
 * `:data:diagnostics`. */
object WakeOnLan {
    private const val SYNC_STREAM_BYTE = 0xFF.toByte()
    private const val SYNC_STREAM_LENGTH = 6
    private const val MAC_REPETITIONS = 16
    private const val MAC_OCTETS = 6

    /** `null` if [mac] isn't 6 colon- or hyphen-separated hex octets. */
    fun buildMagicPacket(mac: String): ByteArray? {
        val octets = parseMac(mac) ?: return null
        val packet = ByteArray(SYNC_STREAM_LENGTH + MAC_REPETITIONS * MAC_OCTETS)
        for (i in 0 until SYNC_STREAM_LENGTH) packet[i] = SYNC_STREAM_BYTE
        for (rep in 0 until MAC_REPETITIONS) {
            octets.copyInto(packet, SYNC_STREAM_LENGTH + rep * MAC_OCTETS)
        }
        return packet
    }

    private fun parseMac(mac: String): ByteArray? {
        val octets = mac.trim().split(":", "-")
        if (octets.size != MAC_OCTETS) return null
        return octets
            .map { octet -> octet.toIntOrNull(16)?.takeIf { it in 0..255 }?.toByte() ?: return null }
            .toByteArray()
    }
}
