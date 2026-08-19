package dev.enthusiastdev.netinspector.data.wifi

import android.net.wifi.ScanResult
import dev.enthusiastdev.netinspector.core.model.wifi.AccessPoint
import dev.enthusiastdev.netinspector.core.model.wifi.ChannelSpan
import dev.enthusiastdev.netinspector.core.model.wifi.bandOf
import dev.enthusiastdev.netinspector.core.model.wifi.channelWidthMhz
import dev.enthusiastdev.netinspector.core.model.wifi.freqToChannel
import dev.enthusiastdev.netinspector.core.model.wifi.is6GhzPsc
import dev.enthusiastdev.netinspector.core.model.wifi.isDfsChannel
import dev.enthusiastdev.netinspector.core.model.wifi.securityTypesOf
import dev.enthusiastdev.netinspector.core.model.wifi.wifiStandardOf
import java.nio.ByteBuffer
import java.time.Instant

/**
 * design §6.2/§6.3 - vendor OUI lookup is deferred (persistence-backed, Phase 3 sub-task) so
 * it's always null here; `firstSeen`/`lastSeen` are both set to [now] because this maps a
 * single scan in isolation - carrying `firstSeen` forward across scans is the repository's
 * job (design §3: "refreshed in place on each scan rather than re-created").
 */
internal fun ScanResult.toAccessPoint(
    connectedBssid: String?,
    now: Instant,
): AccessPoint {
    val widthMhz = channelWidthMhz(channelWidth)
    val band = bandOf(frequency)
    val primaryChannel = freqToChannel(frequency) ?: 0
    val centerMhz = if (centerFreq0 != 0) centerFreq0 else frequency
    val span = ChannelSpan(centerMhz = centerMhz, widthMhz = widthMhz, primaryChannel = primaryChannel, band = band)
    val secondarySpan =
        if (centerFreq1 != 0) {
            ChannelSpan(centerMhz = centerFreq1, widthMhz = widthMhz, primaryChannel = primaryChannel, band = band)
        } else {
            null
        }

    return AccessPoint(
        bssid = BSSID,
        ssid = SSID,
        rssiDbm = level,
        span = span,
        secondarySpan = secondarySpan,
        security = securityTypesOf(securityTypes),
        standard = wifiStandardOf(wifiStandard),
        vendor = null,
        isConnected = connectedBssid != null && connectedBssid == BSSID,
        isDfsChannel = isDfsChannel(band, primaryChannel),
        is6GhzPsc = is6GhzPsc(band, primaryChannel),
        firstSeen = now,
        lastSeen = now,
    )
}

/** `duplicate()` so reading doesn't consume the position of a buffer something else might
 * still reference - [ScanResult.InformationElement.getBytes] returns the platform's own
 * backing buffer, not a defensive copy. */
internal fun ByteBuffer.toByteArray(): ByteArray {
    val duplicate = duplicate()
    val bytes = ByteArray(duplicate.remaining())
    duplicate.get(bytes)
    return bytes
}
