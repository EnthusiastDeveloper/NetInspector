package dev.enthusiastdev.netinspector.core.common.net

/** design §9.6 - subnet calculator's "VLSM splitting." One allocation per requested host count,
 * in the same order the caller asked for them; the caller decides ordering (classic VLSM sorts
 * descending to avoid fragmentation, which the UI does before calling this). */
data class VlsmAllocation(
    val requestedHosts: Int,
    val subnet: Ipv4Subnet,
)

/** Allocates a subnet for each entry in [hostCounts] out of this network, each sized to the
 * smallest block that fits its request. Returns `null` if the pool is exhausted partway
 * through - never a partial allocation, since a caller can't usefully act on "some of these
 * fit." */
fun Ipv4Subnet.splitForHostCounts(hostCounts: List<Int>): List<VlsmAllocation>? {
    val poolStart = networkAddress.toUInt32()
    val poolEnd = poolStart + (1L shl (32 - prefixLength)) - 1
    var cursor = poolStart

    val allocations = mutableListOf<VlsmAllocation>()
    for (hosts in hostCounts) {
        val prefix = requiredPrefixLength(hosts) ?: return null
        val blockSize = 1L shl (32 - prefix)
        val alignedStart = alignUp(cursor, blockSize)
        val blockEnd = alignedStart + blockSize - 1
        if (blockEnd > poolEnd) return null

        allocations += VlsmAllocation(hosts, Ipv4Subnet(alignedStart.toInet4Address(), prefix))
        cursor = alignedStart + blockSize
    }
    return allocations
}

private fun alignUp(
    value: Long,
    blockSize: Long,
): Long {
    val remainder = value % blockSize
    return if (remainder == 0L) value else value + (blockSize - remainder)
}

/** The largest prefix length (smallest block) whose usable host count is still >= [hostsNeeded].
 * `null` if even a `/0` couldn't hold it (i.e. `hostsNeeded` is absurd - never actually reachable
 * since a `/0` holds ~4 billion addresses, kept as a type-level guard rather than a runtime
 * assumption). */
private fun requiredPrefixLength(hostsNeeded: Int): Int? {
    if (hostsNeeded <= 0) return 32
    for (prefix in 32 downTo 0) {
        val capacity =
            when (prefix) {
                32 -> 1L
                31 -> 2L
                else -> (1L shl (32 - prefix)) - 2
            }
        if (capacity >= hostsNeeded) return prefix
    }
    return null
}
