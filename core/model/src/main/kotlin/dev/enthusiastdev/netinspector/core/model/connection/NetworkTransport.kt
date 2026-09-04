package dev.enthusiastdev.netinspector.core.model.connection

/** The transports this app distinguishes when enumerating `ConnectivityManager` networks.
 * Anything else (VPN, Bluetooth PAN, etc.) is out of scope for per-network indicators. */
enum class NetworkTransport {
    WIFI,
    CELLULAR,
    ETHERNET,
}
