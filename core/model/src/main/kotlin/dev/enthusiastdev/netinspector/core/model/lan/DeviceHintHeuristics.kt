package dev.enthusiastdev.netinspector.core.model.lan

/**
 * design §8.2 Stage C, §3 - turns what enrichment found (open ports, ICMP reply TTL) into the
 * single [DeviceHint] a [Host] carries. Pure and heavily unit-tested, same shape as the channel
 * recommendation scoring in `:core:model:wifi` (design §7). Port signatures are checked first
 * and win outright: a specific service on a specific port (design's own examples - 62078 is
 * Apple-only usbmuxd, 5555 is ADB) is a much stronger signal than a coarse OS-family guess from
 * TTL, so it is recorded as [Certainty.LIKELY] against the TTL fingerprint's [Certainty.POSSIBLE].
 */
fun deviceHintFor(
    openPorts: List<OpenPort>,
    icmpReplyTtl: Int?,
): DeviceHint? = portSignatureHint(openPorts) ?: icmpReplyTtl?.let(::ttlDeviceHint)

/** design §8.2 - "an initial TTL of 64 implies Linux/Android/iOS/macOS, 128 implies Windows,
 * 255 implies network equipment." A LAN peer's reply arrives a few hops short of its OS's
 * initial TTL, so the observed value is rounded up to the nearest of the three well-known
 * defaults rather than compared for exact equality. */
fun ttlDeviceHint(observedTtl: Int): DeviceHint? {
    val (initialTtl, label) =
        when {
            observedTtl <= TTL_UNIX_FAMILY -> TTL_UNIX_FAMILY to "Linux/Android/iOS/macOS family"
            observedTtl <= TTL_WINDOWS_FAMILY -> TTL_WINDOWS_FAMILY to "Windows family"
            observedTtl <= TTL_NETWORK_EQUIPMENT -> TTL_NETWORK_EQUIPMENT to "Network equipment"
            else -> return null // beyond every known initial TTL - not a usable signal.
        }
    return DeviceHint(
        label = label,
        basis = "IP TTL $observedTtl (~$initialTtl) → $label",
        certainty = Certainty.POSSIBLE,
    )
}

/** design §3 Phase 6 - "62078 → iOS, 5555 → ADB, 9100 → printer, 8009 → Chromecast,
 * 445+139 → Windows/Samba, 32400 → Plex, and so on." Checked in order; the first match wins. */
private fun portSignatureHint(openPorts: List<OpenPort>): DeviceHint? {
    val open = openPorts.map { it.port }.toSet()
    return PORT_SIGNATURES.firstOrNull { it.ports.all { port -> port in open } }?.let { signature ->
        val portLabel = if (signature.ports.size > 1) "Open ports" else "Open port"
        val ports = signature.ports.joinToString("+")
        DeviceHint(
            label = signature.label,
            basis = "$portLabel $ports → ${signature.label}",
            certainty = Certainty.LIKELY,
        )
    }
}

private data class PortSignature(
    val ports: List<Int>,
    val label: String,
)

private val PORT_SIGNATURES =
    listOf(
        PortSignature(listOf(62078), "iOS device"),
        PortSignature(listOf(5555), "Android debug bridge (ADB)"),
        PortSignature(listOf(8009), "Chromecast"),
        PortSignature(listOf(9100), "Network printer"),
        PortSignature(listOf(32400), "Plex media server"),
        PortSignature(listOf(445, 139), "Windows/Samba file sharing"),
        PortSignature(listOf(3389), "Windows (RDP)"),
        PortSignature(listOf(548), "Apple File Sharing (AFP)"),
        PortSignature(listOf(631), "Network printer (IPP)"),
        PortSignature(listOf(5900), "VNC remote desktop"),
    )

private const val TTL_UNIX_FAMILY = 64
private const val TTL_WINDOWS_FAMILY = 128
private const val TTL_NETWORK_EQUIPMENT = 255
