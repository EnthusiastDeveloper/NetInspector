package dev.enthusiastdev.netinspector.ui.screens.tools.dns

import dev.enthusiastdev.netinspector.core.model.connection.NetworkTransport
import dev.enthusiastdev.netinspector.core.model.diagnostics.RegisteredDnsNetwork
import java.net.InetAddress

internal fun NetworkTransport.label(): String =
    when (this) {
        NetworkTransport.WIFI -> "Wi-Fi"
        NetworkTransport.CELLULAR -> "Cellular"
        NetworkTransport.ETHERNET -> "Ethernet"
    }

internal fun List<InetAddress>.addressListLabel(): String =
    if (isEmpty()) "None" else joinToString(", ") { it.hostAddress ?: it.toString() }

/** `privateDnsServerName` is only non-null when Private DNS is in strict (user-chosen hostname)
 * mode - a null name with `isPrivateDnsActive` true means the automatic/opportunistic mode
 * upgraded a plain resolver to DoT with no specific hostname to show, so this deliberately says
 * just "Active" rather than guessing which mode that was. */
internal fun RegisteredDnsNetwork.privateDnsLabel(): String =
    when {
        !isPrivateDnsActive -> "Inactive"
        privateDnsServerName != null -> "Active ($privateDnsServerName)"
        else -> "Active"
    }
