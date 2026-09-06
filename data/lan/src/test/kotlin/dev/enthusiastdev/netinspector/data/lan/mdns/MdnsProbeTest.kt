package dev.enthusiastdev.netinspector.data.lan.mdns

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [airplayMacAddress] is a pure top-level function precisely so this can exercise the two
 * Apple/Bonjour MAC conventions (docs/ideas.md A4) without an `NsdManager` - see that
 * function's own doc comment.
 */
class MdnsProbeTest {
    @Test
    fun `airplayMacAddress reads the deviceid TXT key for _airplay_tcp`() {
        val mac =
            airplayMacAddress(
                serviceType = "_airplay._tcp",
                serviceName = "Living Room",
                txtRecords = mapOf("deviceid" to "58:d5:6e:6c:73:c4"),
            )

        assertThat(mac).isEqualTo("58:D5:6E:6C:73:C4")
    }

    @Test
    fun `airplayMacAddress tolerates a trailing dot on the service type`() {
        val mac =
            airplayMacAddress(
                serviceType = "_airplay._tcp.",
                serviceName = "Living Room",
                txtRecords = mapOf("deviceid" to "58:D5:6E:6C:73:C4"),
            )

        assertThat(mac).isEqualTo("58:D5:6E:6C:73:C4")
    }

    @Test
    fun `airplayMacAddress tolerates the stray leading dot NsdServiceInfo sometimes returns`() {
        val mac =
            airplayMacAddress(
                serviceType = "._airplay._tcp",
                serviceName = "Living Room",
                txtRecords = mapOf("deviceid" to "58:D5:6E:6C:73:C4"),
            )

        assertThat(mac).isEqualTo("58:D5:6E:6C:73:C4")
    }

    @Test
    fun `airplayMacAddress rejects a malformed deviceid value`() {
        val mac =
            airplayMacAddress(
                serviceType = "_airplay._tcp",
                serviceName = "Living Room",
                txtRecords = mapOf("deviceid" to "not-a-mac"),
            )

        assertThat(mac).isNull()
    }

    @Test
    fun `airplayMacAddress returns null when the deviceid key is absent`() {
        val mac =
            airplayMacAddress(
                serviceType = "_airplay._tcp",
                serviceName = "Living Room",
                txtRecords = emptyMap(),
            )

        assertThat(mac).isNull()
    }

    @Test
    fun `airplayMacAddress extracts the MAC prefix from a _raop_tcp instance name`() {
        val mac =
            airplayMacAddress(
                serviceType = "_raop._tcp",
                serviceName = "58d56e6c73c4@Kitchen Speaker",
                txtRecords = emptyMap(),
            )

        assertThat(mac).isEqualTo("58:D5:6E:6C:73:C4")
    }

    @Test
    fun `airplayMacAddress returns null for a _raop_tcp instance name without a MAC prefix`() {
        val mac =
            airplayMacAddress(
                serviceType = "_raop._tcp",
                serviceName = "Kitchen Speaker",
                txtRecords = emptyMap(),
            )

        assertThat(mac).isNull()
    }

    @Test
    fun `airplayMacAddress ignores unrelated service types`() {
        val mac =
            airplayMacAddress(
                serviceType = "_http._tcp",
                serviceName = "58d56e6c73c4@Kitchen Speaker",
                txtRecords = mapOf("deviceid" to "58:D5:6E:6C:73:C4"),
            )

        assertThat(mac).isNull()
    }

    @Test
    fun `airplayMacAddress handles null serviceType and serviceName`() {
        assertThat(airplayMacAddress(null, null, emptyMap())).isNull()
    }
}
