package dev.enthusiastdev.netinspector.data.wifi

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * design §6.3-adjacent (vendor OUI lookup) - a curated subset of the IEEE-registered OUI
 * blocks (source: Wireshark's `manuf` database), limited to consumer/SMB Wi-Fi router, mesh,
 * and AP vendors rather than the full ~58k-entry registry. General-purpose client-device
 * silicon vendors (Intel, Apple, Samsung, etc.) are deliberately excluded: this answers "what
 * make is this access point," not "what phone is this." A BSSID whose OUI isn't in this list
 * shows no vendor rather than a wrong one - there is no full-database fallback yet (that
 * belongs to :data:persistence in a later phase, per the implementation plan).
 */
@Singleton
class VendorLookup
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val vendorsByOuiHex: Map<String, String> by lazy {
            context.assets.open(ASSET_FILE_NAME).bufferedReader().useLines { lines ->
                lines
                    .mapNotNull { line ->
                        val tab = line.indexOf('\t')
                        if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
                    }.toMap()
            }
        }

        fun vendorFor(bssid: String): String? {
            val ouiHex = bssid.replace(":", "").take(OUI_HEX_LENGTH).uppercase()
            if (ouiHex.length != OUI_HEX_LENGTH) return null
            // IEEE never assigns a vendor OUI with the locally-administered bit set (bit 1 of
            // the first octet) - a hit here would be coincidental, not a real identification.
            // Common for phone-hotspot and MAC-randomized BSSIDs.
            val firstOctet = ouiHex.substring(0, 2).toInt(16)
            if (firstOctet and LOCALLY_ADMINISTERED_BIT != 0) return null
            return vendorsByOuiHex[ouiHex]
        }

        private companion object {
            const val ASSET_FILE_NAME = "oui_vendors.tsv"
            const val OUI_HEX_LENGTH = 6
            const val LOCALLY_ADMINISTERED_BIT = 0x02
        }
    }
