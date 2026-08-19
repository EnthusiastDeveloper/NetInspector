package dev.enthusiastdev.netinspector.core.common.net

import java.net.Inet4Address

/** design §9.6 - subnet calculator's "CIDR ↔ mask" conversion. */
fun prefixLengthToNetmask(prefixLength: Int): Inet4Address {
    require(prefixLength in 0..32) { "prefixLength must be 0..32, was $prefixLength" }
    val bits = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
    return bits.toInet4Address()
}

/** `null` if [mask] isn't a contiguous run of leading 1-bits - a malformed mask (`255.0.255.0`,
 * for instance) has no valid prefix length rather than a nearest-guess one. */
fun netmaskToPrefixLength(mask: Inet4Address): Int? {
    val bits = mask.toUInt32()
    val prefixLength = java.lang.Long.numberOfLeadingZeros(bits.inv() and 0xFFFFFFFFL) - 32
    return if (prefixLengthToNetmask(prefixLength).toUInt32() == bits) prefixLength else null
}
