package dev.enthusiastdev.netinspector.core.model.lan

/**
 * design §8.2 Stage C, §3 - turns what enrichment found into the single [DeviceHint] a [Host]
 * carries. Pure and heavily unit-tested, same shape as the channel recommendation scoring in
 * `:core:model:wifi` (design §7). Every candidate is built independently and the most certain
 * one wins outright ([Certainty]'s declaration order doubles as rank, lowest ordinal = most
 * certain, same as `HostMerge.preferredHint`) - `snmpSysDescr`/`tlsCertificateCommonName`
 * (docs/ideas.md B1/B3) are self-reported, [Certainty.CONFIRMED] like
 * A1/A2's UPnP/mDNS fields; [httpServerHint] (docs/ideas.md A6) is CONFIRMED for a literal
 * self-reported OS name (Apache's `(Ubuntu)`-style suffix) but only LIKELY for an inferred
 * IIS-version-to-Windows-release mapping; a port signature (design's own examples - 62078 is
 * Apple-only usbmuxd, 5555 is ADB) is also [Certainty.LIKELY], a coarser signal than a literal
 * self-report; the TTL fingerprint is the weakest, [Certainty.POSSIBLE]. A tie between two
 * candidates of the same certainty goes to whichever is listed first below (SNMP's exact
 * firmware string over a certificate's often generic company-name CN).
 */
fun deviceHintFor(
    openPorts: List<OpenPort>,
    icmpReplyTtl: Int?,
    snmpSysDescr: String? = null,
    tlsCertificateCommonName: String? = null,
    smbDialectRevision: Int? = null,
): DeviceHint? =
    listOfNotNull(
        snmpDeviceHint(snmpSysDescr),
        tlsCertificateDeviceHint(tlsCertificateCommonName),
        httpServerHint(openPorts),
        smbDialectRevision?.let(::smbDialectHint),
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

/** docs/ideas.md A7 - the dialect an SMB2 host actually negotiates narrows the
 * generic 445+139 [portSignatureHint] "Windows/Samba file sharing" down to an OS-version range,
 * the same [Certainty.LIKELY] tier but more specific - so this is listed ahead of that port
 * signature in [deviceHintFor] to win the tie. Deliberately doesn't claim a bare "Windows"
 * verdict: Samba can be configured to negotiate any of these same dialects, so the label names
 * the dialect-implied Windows range while the basis text carries the Samba caveat. Only
 * dialects `SmbNegotiateProbe` actually offers (up to 3.0.2) are mapped; 3.1.1 needs mandatory
 * negotiate contexts the probe deliberately doesn't implement (see that class's doc comment),
 * so it never appears here regardless of what a real 3.1.1-capable host might have offered. */
fun smbDialectHint(dialectRevision: Int): DeviceHint? {
    val range = SMB_DIALECT_WINDOWS_RANGES[dialectRevision] ?: return null
    val dialectLabel = SMB_DIALECT_LABELS.getValue(dialectRevision)
    return DeviceHint(
        label = range,
        basis = "SMB2 negotiate dialect $dialectLabel → $range (or a NAS/Samba host presenting the same dialect)",
        certainty = Certainty.LIKELY,
    )
}

private val SMB_DIALECT_LABELS =
    mapOf(
        0x0202 to "SMB 2.0.2",
        0x0210 to "SMB 2.1",
        0x0300 to "SMB 3.0",
        0x0302 to "SMB 3.0.2",
    )

private val SMB_DIALECT_WINDOWS_RANGES =
    mapOf(
        0x0202 to "Windows Vista SP1 / Server 2008 era",
        0x0210 to "Windows 7 / Server 2008 R2 era",
        0x0300 to "Windows 8 / Server 2012 era",
        0x0302 to "Windows 8.1+ / Server 2012 R2+ era",
    )

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

/** docs/ideas.md A6 - the extended port probe's HTTP banner (`ExtendedPortProbe.
 * formatHttpBanner`, formatted as `"Server: <value>; Title: <value>"`) carries the web server's
 * own `Server` response header. Two sub-techniques, same split as [mdnsServiceHint]: Apache's
 * common `product/version (OSName)` convention names the OS outright ([Certainty.CONFIRMED] -
 * as literal a self-report as SNMP/TLS above), while IIS only reports its own version number,
 * requiring a version→Windows-release lookup table ([Certainty.LIKELY] - an inference, not a
 * literal report, and ambiguous where one IIS version spans multiple Windows releases). */
fun httpServerHint(openPorts: List<OpenPort>): DeviceHint? {
    val serverHeaders =
        openPorts
            .mapNotNull { it.banner }
            .mapNotNull {
                HTTP_SERVER_BANNER_SEGMENT_REGEX
                    .find(it)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            }
    return serverHeaders.firstNotNullOfOrNull { apacheOsHint(it) ?: iisWindowsReleaseHint(it) }
}

private val HTTP_SERVER_BANNER_SEGMENT_REGEX = Regex("""Server:\s*([^;]+)""")
private val APACHE_OS_PARENTHETICAL_REGEX = Regex("""^Apache/\S+\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
private val IIS_VERSION_REGEX = Regex("""^Microsoft-IIS/(\d+\.\d+)""", RegexOption.IGNORE_CASE)

/** Apache's OS-name suffix isn't a fixed vocabulary, so only a recognized token is trusted -
 * an unrecognized parenthetical (a custom build string, a module list) is treated as noise
 * rather than guessed at. */
private val APACHE_OS_LABELS =
    listOf("Ubuntu", "Debian", "CentOS", "Red Hat", "Fedora", "FreeBSD", "Unix", "Win32", "Win64")

private fun apacheOsHint(serverHeader: String): DeviceHint? {
    val parenthetical =
        APACHE_OS_PARENTHETICAL_REGEX
            .find(serverHeader)
            ?.groupValues
            ?.get(1)
            ?.trim() ?: return null
    val label = APACHE_OS_LABELS.firstOrNull { parenthetical.startsWith(it, ignoreCase = true) } ?: return null
    return DeviceHint(label = label, basis = "HTTP Server header → $serverHeader", certainty = Certainty.CONFIRMED)
}

/** docs.microsoft.com's own IIS-version history - one IIS version can span more than one
 * Windows release (workstation and server SKUs share a kernel/IIS build), so the label says so
 * rather than guessing a single one. */
private val IIS_TO_WINDOWS_RELEASE =
    mapOf(
        "5.0" to "Windows 2000",
        "5.1" to "Windows XP",
        "6.0" to "Windows Server 2003",
        "7.0" to "Windows Vista / Server 2008",
        "7.5" to "Windows 7 / Server 2008 R2",
        "8.0" to "Windows 8 / Server 2012",
        "8.5" to "Windows 8.1 / Server 2012 R2",
        "10.0" to "Windows 10 / Server 2016+",
    )

private fun iisWindowsReleaseHint(serverHeader: String): DeviceHint? {
    val version = IIS_VERSION_REGEX.find(serverHeader)?.groupValues?.get(1) ?: return null
    val release = IIS_TO_WINDOWS_RELEASE[version] ?: return null
    return DeviceHint(
        label = release,
        basis = "HTTP Server: IIS $version → $release",
        certainty = Certainty.LIKELY,
    )
}

/** docs/ideas.md A6 - SSDP/UPnP's `SERVER` response header follows a loose
 * "OS/version UPnP/x.y product/version" convention (UDA §1.1.4 - see the "Windows" real-world
 * value being `Microsoft-Windows/10.0` rather than a bare `Windows/`, which is why this matches
 * on a curated prefix table rather than a single literal string). Not every stack follows the
 * convention, so an unrecognized first token is treated as noise rather than guessed at -
 * [Certainty.CONFIRMED] like the other self-reported signals above when it is recognized. */
fun ssdpServerHint(server: String?): DeviceHint? {
    val firstToken = server?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: return null
    val (osToken, osVersion) = firstToken.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
    val match = SSDP_OS_PREFIX_LABELS.firstOrNull { (prefix, _) -> osToken.startsWith(prefix, ignoreCase = true) }
    val label = match?.second ?: return null
    val fullLabel = if (osVersion != null) "$label $osVersion" else label
    return DeviceHint(label = fullLabel, basis = "SSDP SERVER header → $server", certainty = Certainty.CONFIRMED)
}

private val SSDP_OS_PREFIX_LABELS =
    listOf(
        "Microsoft-Windows" to "Windows",
        "Windows" to "Windows",
        "Linux" to "Linux",
        "Darwin" to "macOS",
        "FreeBSD" to "FreeBSD",
        "VxWorks" to "VxWorks",
    )

/** docs/ideas.md A2 - two tiers from one mDNS record: an explicit model
 * string in a well-known TXT key ([Certainty.CONFIRMED], self-reported exactly like A1's UPnP
 * fields) if present, else a generic label purely from the service type ([Certainty.LIKELY],
 * the same tier as [portSignatureHint] - advertising `_airplay._tcp` is as strong a signal as
 * a specific open port, but not as strong as a device naming its own model). Tolerates a
 * leading and/or trailing dot on [serviceType] - DNS's own optional root-label dot, and (docs/
 * ideas.md A5) a stray leading dot `NsdServiceInfo` carries on some Android versions. */
fun mdnsServiceHint(
    serviceType: String?,
    txtRecords: Map<String, String>,
): DeviceHint? {
    val type = serviceType?.trim('.') ?: return null
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

/** docs/ideas.md A5 - every mDNS service type this file knows how to turn into a
 * [DeviceHint], plus [APPLE_DEVICE_INFO_SERVICE] (a hint source but not itself a
 * [MDNS_SERVICE_TYPE_LABELS] entry). `MdnsProbe` browses this list directly rather than relying
 * solely on the `_services._dns-sd._udp` meta-query to learn it exists: that meta-query is
 * genuinely optional in DNS-SD and a real, common gap in practice - many embedded mDNS
 * responders (ESPHome, ad hoc `avahi-publish-service` records, even some commercial TVs'
 * AirPlay stacks) answer a direct browse for their own service type but never register the
 * meta-query's browse-domain PTR, so relying on the meta-query alone silently finds nothing on
 * those networks. */
val WELL_KNOWN_MDNS_SERVICE_TYPES: Set<String> = MDNS_SERVICE_TYPE_LABELS.keys + APPLE_DEVICE_INFO_SERVICE
