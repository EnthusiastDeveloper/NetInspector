package dev.enthusiastdev.netinspector.core.model.lan

/**
 * design §8.2 Stage C, §3 - turns what enrichment found into the single [DeviceHint] a [Host]
 * carries. Pure and heavily unit-tested, same shape as the channel recommendation scoring in
 * `:core:model:wifi` (design §7). Every candidate is built independently and the most certain
 * one wins outright ([Certainty]'s declaration order doubles as rank, lowest ordinal = most
 * certain, same as `HostMerge.preferredHint`) - `snmpSysDescr`/`tlsCertificateCommonName`
 * (docs/ideas.md B1/B3) are self-reported, [Certainty.CONFIRMED] like
 * A1/A2's UPnP/mDNS fields; a port signature (design's own examples - 62078 is Apple-only
 * usbmuxd, 5555 is ADB) is [Certainty.LIKELY], a coarser signal than either; the TTL fingerprint
 * is the weakest, [Certainty.POSSIBLE]. A tie between two [Certainty.CONFIRMED] candidates goes
 * to whichever is listed first below (SNMP's exact firmware string over a certificate's often
 * generic company-name CN).
 */
fun deviceHintFor(
    openPorts: List<OpenPort>,
    icmpReplyTtl: Int?,
    snmpSysDescr: String? = null,
    tlsCertificateCommonName: String? = null,
): DeviceHint? =
    listOfNotNull(
        snmpDeviceHint(snmpSysDescr),
        tlsCertificateDeviceHint(tlsCertificateCommonName),
        portSignatureHint(openPorts),
        icmpReplyTtl?.let(::ttlDeviceHint),
    ).minByOrNull { it.certainty }

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
 * 445+139 → Windows/Samba, 32400 → Plex, and so on." Checked in order; the first match wins,
 * so signatures that are a superset of another (e.g. an AD domain controller also has
 * 445+139 open) must be listed before the more general one. */
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
        // Checked before the plain Windows/Samba signature below: a domain controller also
        // has 445+139 open, so the more specific match must win.
        PortSignature(listOf(88, 636), "Windows domain controller (Active Directory)"),
        PortSignature(listOf(445, 139), "Windows/Samba file sharing"),
        PortSignature(listOf(3389), "Windows (RDP)"),
        PortSignature(listOf(548), "Apple File Sharing (AFP)"),
        PortSignature(listOf(631), "Network printer (IPP)"),
        PortSignature(listOf(5900), "VNC remote desktop"),
        PortSignature(listOf(554), "IP camera / streaming device"),
        PortSignature(listOf(3306), "Database server (MySQL)"),
        PortSignature(listOf(1723), "VPN router (PPTP)"),
        PortSignature(listOf(23), "Telnet-enabled device (legacy/insecure)"),
    )

private const val TTL_UNIX_FAMILY = 64
private const val TTL_WINDOWS_FAMILY = 128
private const val TTL_NETWORK_EQUIPMENT = 255

/** docs/ideas.md A1 - SSDP/UPnP's LOCATION-XML `manufacturer`/
 * `modelName` are the device's own declared identity, not an inference, so this is
 * [Certainty.CONFIRMED] - stronger evidence than a port signature or TTL guess. */
fun upnpDeviceHint(
    manufacturer: String?,
    modelName: String?,
): DeviceHint? {
    val label = listOfNotNull(manufacturer, modelName).joinToString(" ").ifBlank { return null }
    return DeviceHint(
        label = label,
        basis = "UPnP device description → $label",
        certainty = Certainty.CONFIRMED,
    )
}

/** docs/ideas.md B1 - SNMP `sysDescr` (OID 1.3.6.1.2.1.1.1.0) is a
 * device's own self-reported firmware/model string, [Certainty.CONFIRMED] exactly like A1/A2's
 * manufacturer/model fields. */
fun snmpDeviceHint(sysDescr: String?): DeviceHint? {
    val label = sysDescr?.trim()?.ifBlank { null } ?: return null
    return DeviceHint(label = label, basis = "SNMP sysDescr → $label", certainty = Certainty.CONFIRMED)
}

/** docs/ideas.md B3 - a self-signed admin-UI certificate's CN commonly
 * carries the product name outright; [Certainty.CONFIRMED], the same tier as SNMP's
 * self-reported `sysDescr` above. */
fun tlsCertificateDeviceHint(commonName: String?): DeviceHint? {
    val label = commonName?.trim()?.ifBlank { null } ?: return null
    return DeviceHint(label = label, basis = "TLS certificate CN → $label", certainty = Certainty.CONFIRMED)
}

/** docs/ideas.md A2 - two tiers from one mDNS record: an explicit model
 * string in a well-known TXT key ([Certainty.CONFIRMED], self-reported exactly like A1's UPnP
 * fields) if present, else a generic label purely from the service type ([Certainty.LIKELY],
 * the same tier as [portSignatureHint] - advertising `_airplay._tcp` is as strong a signal as
 * a specific open port, but not as strong as a device naming its own model). */
fun mdnsServiceHint(
    serviceType: String?,
    txtRecords: Map<String, String>,
): DeviceHint? {
    val type = serviceType?.trimEnd('.') ?: return null
    return mdnsTxtModelHint(type, txtRecords) ?: mdnsServiceTypeHint(type)
}

private fun mdnsTxtModelHint(
    serviceType: String,
    txt: Map<String, String>,
): DeviceHint? {
    val model =
        when (serviceType) {
            APPLE_DEVICE_INFO_SERVICE -> txt[APPLE_MODEL_TXT_KEY]?.let { "Apple device ($it)" }
            GOOGLE_CAST_SERVICE -> txt[GOOGLE_CAST_MODEL_TXT_KEY]
            IPP_SERVICE, PRINTER_SERVICE -> txt[PRINTER_MODEL_TXT_KEY]
            else -> null
        } ?: return null
    return DeviceHint(
        label = model,
        basis = "mDNS $serviceType TXT record → $model",
        certainty = Certainty.CONFIRMED,
    )
}

private fun mdnsServiceTypeHint(serviceType: String): DeviceHint? =
    MDNS_SERVICE_TYPE_LABELS[serviceType]?.let { label ->
        DeviceHint(
            label = label,
            basis = "mDNS service $serviceType → $label",
            certainty = Certainty.LIKELY,
        )
    }

private const val APPLE_DEVICE_INFO_SERVICE = "_device-info._tcp"
private const val GOOGLE_CAST_SERVICE = "_googlecast._tcp"
private const val IPP_SERVICE = "_ipp._tcp"
private const val PRINTER_SERVICE = "_printer._tcp"
private const val APPLE_MODEL_TXT_KEY = "model"
private const val GOOGLE_CAST_MODEL_TXT_KEY = "md"
private const val PRINTER_MODEL_TXT_KEY = "ty"

private val MDNS_SERVICE_TYPE_LABELS =
    mapOf(
        "_airplay._tcp" to "Apple device (AirPlay)",
        "_raop._tcp" to "Apple device (AirPlay audio)",
        GOOGLE_CAST_SERVICE to "Chromecast / Google Cast device",
        "_hap._tcp" to "HomeKit accessory",
        "_homekit._tcp" to "HomeKit accessory",
        "_spotify-connect._tcp" to "Spotify Connect speaker",
        "_sonos._tcp" to "Sonos speaker",
        "_hue._tcp" to "Philips Hue bridge",
        "_androidtvremote2._tcp" to "Android TV",
        "_workstation._tcp" to "Mac (macOS)",
        "_smb._tcp" to "Windows/Samba file sharing",
        "_esphome._tcp" to "ESPHome device",
        "_matter._tcp" to "Matter device",
        IPP_SERVICE to "Network printer",
        PRINTER_SERVICE to "Network printer",
    )
