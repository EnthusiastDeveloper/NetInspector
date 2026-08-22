package dev.enthusiastdev.netinspector.ui.screens.dashboard

import com.google.common.truth.Truth.assertThat
import dev.enthusiastdev.netinspector.core.model.lan.SweepProgress
import org.junit.Test

private fun progress(isRunning: Boolean) = SweepProgress(isRunning, addressesProbed = 0, addressesTotal = 0)

class DashboardFormattingTest {
    @Test
    fun `diagnosticsStatus is IDLE when nothing is running`() {
        val status = diagnosticsStatus(progress(isRunning = false), isMonitoringActive = false)
        assertThat(status).isEqualTo(DiagnosticsStatus.IDLE)
    }

    @Test
    fun `diagnosticsStatus is SCANNING when only a sweep is running`() {
        val status = diagnosticsStatus(progress(isRunning = true), isMonitoringActive = false)
        assertThat(status).isEqualTo(DiagnosticsStatus.SCANNING)
    }

    @Test
    fun `diagnosticsStatus is MONITORING when only monitoring is active`() {
        val status = diagnosticsStatus(progress(isRunning = false), isMonitoringActive = true)
        assertThat(status).isEqualTo(DiagnosticsStatus.MONITORING)
    }

    @Test
    fun `diagnosticsStatus is SCANNING_AND_MONITORING when both are active`() {
        val status = diagnosticsStatus(progress(isRunning = true), isMonitoringActive = true)
        assertThat(status).isEqualTo(DiagnosticsStatus.SCANNING_AND_MONITORING)
    }

    @Test
    fun `hostCountLabel handles zero, singular, and plural counts`() {
        assertThat(hostCountLabel(0)).isEqualTo("No devices discovered yet")
        assertThat(hostCountLabel(1)).isEqualTo("1 device on this network")
        assertThat(hostCountLabel(5)).isEqualTo("5 devices on this network")
    }
}
