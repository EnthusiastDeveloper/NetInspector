package dev.enthusiastdev.netinspector.core.common.net

import java.net.Inet4Address
import java.net.InetAddress

/** Inclusive range of IPv4 addresses. */
data class Ipv4Range(
    val start: Inet4Address,
    val end: Inet4Address,
)

/**
 * IPv4 network derived from an address and prefix length - never assume `/24` (design §5,
 * §8.2, C-12): the prefix always comes from `LinkAddress.getPrefixLength()`.
 */
data class Ipv4Subnet(
    val address: Inet4Address,
    val prefixLength: Int,
) {
    init {
        require(prefixLength in 0..32) { "prefixLength must be 0..32, was $prefixLength" }
    }

    private val addressBits = address.toUInt32()
    private val maskBits = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL

    val networkAddress: Inet4Address get() = (addressBits and maskBits).toInet4Address()

    /** `null` for `/31` and `/32` - neither has a reserved broadcast address (RFC 3021). */
    val broadcastAddress: Inet4Address?
        get() =
            when (prefixLength) {
                31, 32 -> null
                else -> (addressBits or maskBits.inv().and(0xFFFFFFFFL)).toInet4Address()
            }

    /**
     * The usable host range. `/32` is the single address itself; `/31` is both addresses
     * (RFC 3021 point-to-point, no reserved network/broadcast); everything else excludes the
     * network and broadcast addresses. `null` only when a network this size (`/0`) plus a
     * hostless case can't express a network/broadcast pair, which cannot actually happen for
     * `0..32` - kept as a type-level escape hatch rather than a runtime assumption.
     */
    val usableHostRange: Ipv4Range?
        get() =
            when (prefixLength) {
                32 -> Ipv4Range(address, address)
                31 -> Ipv4Range(networkAddress, (addressBits or maskBits.inv().and(0xFFFFFFFFL)).toInet4Address())
                else -> {
                    val broadcast = broadcastAddress ?: return null
                    val first = networkAddress.toUInt32() + 1
                    val last = broadcast.toUInt32() - 1
                    Ipv4Range(first.toInet4Address(), last.toInet4Address())
                }
            }

    val hostCount: Long
        get() =
            when (prefixLength) {
                32 -> 1L
                31 -> 2L
                else -> (1L shl (32 - prefixLength)) - 2
            }

    /** Enumerates every usable host address. Caller-beware for short prefixes (`/22` and
     * shorter) - this is why the LAN sweep gates on prefix length before calling it. */
    fun hostSequence(): Sequence<Inet4Address> {
        val range = usableHostRange ?: return emptySequence()
        val first = range.start.toUInt32()
        val last = range.end.toUInt32()
        return (first..last).asSequence().map { it.toInet4Address() }
    }
}

fun Inet4Address.toUInt32(): Long {
    val bytes = address
    return ((bytes[0].toLong() and 0xFF) shl 24) or
        ((bytes[1].toLong() and 0xFF) shl 16) or
        ((bytes[2].toLong() and 0xFF) shl 8) or
        (bytes[3].toLong() and 0xFF)
}

fun Long.toInet4Address(): Inet4Address {
    val bytes =
        byteArrayOf(
            ((this shr 24) and 0xFF).toByte(),
            ((this shr 16) and 0xFF).toByte(),
            ((this shr 8) and 0xFF).toByte(),
            (this and 0xFF).toByte(),
        )
    return InetAddress.getByAddress(bytes) as Inet4Address
}
