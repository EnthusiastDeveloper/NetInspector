package dev.enthusiastdev.netinspector.core.common.vendor

/**
 * design §6.3-adjacent (vendor OUI lookup) - a curated subset of the IEEE-registered OUI
 * blocks (source: Wireshark's `manuf` database), limited to consumer/SMB Wi-Fi router, mesh,
 * and AP vendors rather than the full ~58k-entry registry. General-purpose client-device
 * silicon vendors (Intel, Apple, Samsung, etc.) are deliberately excluded: this answers "what
 * make is this access point," not "what phone is this." A MAC/BSSID whose OUI isn't in this
 * list shows no vendor rather than a wrong one - there is no full-database fallback yet (that
 * belongs to a future `:data:persistence`-backed longest-prefix table, per the implementation
 * plan).
 *
 * Lives in `:core:common` (rather than `:data:wifi`, where it originated) so both `:data:wifi`
 * (BSSIDs from Wi-Fi scan results) and `:data:lan` (MAC addresses recovered from NetBIOS NBSTAT
 * responses, docs/ideas.md A3) can use the same table: a data module may
 * depend on `:core:model`/`:core:common` only, never on another data module (design §2.1), so
 * this needed to live somewhere both already reach. That also ruled out an Android `Context`/
 * `AssetManager` for loading the table (`:core:common` has no `android.*` imports by design,
 * so it's usable from a plain JVM unit test) - a plain JVM classpath resource works the same
 * way on Android at runtime without one, so this loads via [Class.getResourceAsStream] instead
 * of the `context.assets.open(...)` the original `:data:wifi` version used. No per-instance
 * state beyond the lazily-computed table, so this is a plain singleton `object`, not a
 * Hilt-injected class - `:core:common` has no Hilt/KSP setup for it to hook into anyway.
 *
 * A NetBIOS-observed Windows PC's actual NIC vendor (Intel, Realtek, Dell, etc.) is exactly the
 * kind of client-device silicon this table's AP-oriented scope excludes, so LAN-host vendor
 * hits will be sparse until the full registry lands.
 */
object VendorLookup {
    private val vendorsByOuiHex: Map<String, String> by lazy {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/$RESOURCE_FILE_NAME")) {
                "$RESOURCE_FILE_NAME missing from :core:common resources"
            }
        stream.bufferedReader().useLines { lines ->
            lines
                .mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
                }.toMap()
        }
    }

    fun vendorFor(macAddress: String): String? {
        val ouiHex = macAddress.replace(":", "").take(OUI_HEX_LENGTH).uppercase()
        if (ouiHex.length != OUI_HEX_LENGTH) return null
        // IEEE never assigns a vendor OUI with the locally-administered bit set (bit 1 of
        // the first octet) - a hit here would be coincidental, not a real identification.
        // Common for phone-hotspot and MAC-randomized BSSIDs. `toIntOrNull` rather than
        // `toInt` because this is now also reached from NetBIOS-parsed input rather than
        // solely well-formed BSSIDs from `WifiManager` - a malformed address should return
        // null like any other non-match, not throw.
        val firstOctet = ouiHex.substring(0, 2).toIntOrNull(16) ?: return null
        if (firstOctet and LOCALLY_ADMINISTERED_BIT != 0) return null
        return vendorsByOuiHex[ouiHex]
    }

    private const val RESOURCE_FILE_NAME = "oui_vendors.tsv"
    private const val OUI_HEX_LENGTH = 6
    private const val LOCALLY_ADMINISTERED_BIT = 0x02
}
