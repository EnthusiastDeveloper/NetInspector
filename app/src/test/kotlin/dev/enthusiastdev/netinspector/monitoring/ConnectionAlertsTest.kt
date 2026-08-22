package dev.enthusiastdev.netinspector.monitoring

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.connection.ConnectionSnapshot
import dev.enthusiastdev.netinspector.core.model.wifi.WifiStandard
import org.junit.Test

private fun snapshot(
    ssid: String? = "Home",
    rssiDbm: Int? = -60,
) = ConnectionSnapshot(
    ssid = ssid,
    bssid = null,
    rssiDbm = rssiDbm,
    txLinkSpeedMbps = null,
    rxLinkSpeedMbps = null,
    span = null,
    standard = WifiStandard.UNKNOWN,
    ipv4 = null,
    ipv6 = emptyList(),
    gateway = null,
    dnsServers = emptyList(),
    domains = null,
    hasInternet = true,
    isCaptivePortal = false,
    isMetered = false,
)

private val ALL_ENABLED =
    ConnectionAlertSettings(
        rssiAlertThresholdDbm = -75,
        alertOnRssiDrop = true,
        alertOnDisconnect = true,
        alertOnReconnect = true,
    )

private val ALL_DISABLED =
    ConnectionAlertSettings(
        rssiAlertThresholdDbm = -75,
        alertOnRssiDrop = false,
        alertOnDisconnect = false,
        alertOnReconnect = false,
    )

class ConnectionAlertsTest {
    @Test
    fun `connect to disconnect transition raises a disconnected alert`() {
        val alerts = connectionAlertsFor(previous = snapshot(ssid = "Home"), current = null, settings = ALL_ENABLED)
        assertThat(alerts).containsExactly(ConnectionAlert.Disconnected(previousSsid = "Home"))
    }

    @Test
    fun `disconnect to connect transition raises a reconnected alert`() {
        val alerts = connectionAlertsFor(previous = null, current = snapshot(ssid = "Home"), settings = ALL_ENABLED)
        assertThat(alerts).containsExactly(ConnectionAlert.Reconnected(ssid = "Home"))
    }

    @Test
    fun `staying disconnected raises nothing`() {
        val alerts = connectionAlertsFor(previous = null, current = null, settings = ALL_ENABLED)
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `staying connected with stable rssi raises nothing`() {
        val alerts =
            connectionAlertsFor(
                previous = snapshot(rssiDbm = -60),
                current = snapshot(rssiDbm = -61),
                settings = ALL_ENABLED,
            )
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `rssi crossing below threshold raises a weak signal alert once`() {
        val alerts =
            connectionAlertsFor(
                previous = snapshot(rssiDbm = -70),
                current = snapshot(rssiDbm = -80),
                settings = ALL_ENABLED,
            )
        assertThat(alerts).containsExactly(ConnectionAlert.WeakSignal(ssid = "Home", rssiDbm = -80))
    }

    @Test
    fun `rssi already below threshold does not re-alert on every subsequent reading`() {
        val alerts =
            connectionAlertsFor(
                previous = snapshot(rssiDbm = -80),
                current = snapshot(rssiDbm = -82),
                settings = ALL_ENABLED,
            )
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `rssi recovering back above threshold raises nothing`() {
        val alerts =
            connectionAlertsFor(
                previous = snapshot(rssiDbm = -80),
                current = snapshot(rssiDbm = -60),
                settings = ALL_ENABLED,
            )
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `reconnecting straight into a weak signal raises both alerts`() {
        val alerts =
            connectionAlertsFor(
                previous = null,
                current = snapshot(ssid = "Home", rssiDbm = -85),
                settings = ALL_ENABLED,
            )
        assertThat(alerts)
            .containsExactly(
                ConnectionAlert.Reconnected(ssid = "Home"),
                ConnectionAlert.WeakSignal(ssid = "Home", rssiDbm = -85),
            )
    }

    @Test
    fun `disabled toggles suppress every alert type`() {
        val disconnectAlerts = connectionAlertsFor(snapshot(), null, ALL_DISABLED)
        val reconnectAlerts = connectionAlertsFor(null, snapshot(), ALL_DISABLED)
        val weakSignalAlerts = connectionAlertsFor(snapshot(rssiDbm = -60), snapshot(rssiDbm = -90), ALL_DISABLED)
        assertThat(disconnectAlerts).isEmpty()
        assertThat(reconnectAlerts).isEmpty()
        assertThat(weakSignalAlerts).isEmpty()
    }
}
