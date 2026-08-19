package dev.enthusiastdev.netinspector.ui.screens.tools.subnetcalc

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.common.net.prefixLengthToNetmask
import dev.enthusiastdev.netinspector.core.common.net.splitForHostCounts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

/** design §9.6 - entirely offline; every field recomputes on each keystroke rather than
 * waiting for an explicit "calculate," since the underlying math (design §5's [Ipv4Subnet],
 * this phase's CIDR/mask conversion and VLSM split) is pure and cheap. */
@HiltViewModel
class SubnetCalculatorViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow(SubnetCalculatorUiState())
        val uiState: StateFlow<SubnetCalculatorUiState> = _uiState.asStateFlow()

        init {
            recompute()
        }

        fun updateAddress(value: String) {
            _uiState.update { it.copy(addressInput = value) }
            recompute()
        }

        fun updatePrefix(value: String) {
            _uiState.update { it.copy(prefixInput = value) }
            recompute()
        }

        fun updateVlsm(value: String) {
            _uiState.update { it.copy(vlsmInput = value) }
            recompute()
        }

        private fun recompute() {
            val state = _uiState.value
            val address = parseIpv4(state.addressInput)
            val prefix = state.prefixInput.toIntOrNull()

            if (address == null || prefix == null || prefix !in 0..32) {
                _uiState.update {
                    it.copy(
                        subnet = null,
                        netmaskText = null,
                        vlsmAllocations = null,
                        errorMessage = "Enter a valid IPv4 address and prefix (0-32)",
                    )
                }
                return
            }

            val subnet = Ipv4Subnet(address, prefix)
            val hostCounts =
                state.vlsmInput.split(",").mapNotNull { entry ->
                    entry.trim().toIntOrNull()?.takeIf { it > 0 }
                }
            val vlsm = if (hostCounts.isEmpty()) null else subnet.splitForHostCounts(hostCounts)
            val vlsmError = "Requested hosts don't fit in this subnet"

            _uiState.update {
                it.copy(
                    subnet = subnet,
                    netmaskText = prefixLengthToNetmask(prefix).hostAddress,
                    vlsmAllocations = vlsm,
                    errorMessage = if (hostCounts.isNotEmpty() && vlsm == null) vlsmError else null,
                )
            }
        }

        /** A strict dotted-quad parser, not `InetAddress.getByName` - that falls back to a
         * blocking DNS lookup for anything that isn't a literal IP, which would stall this
         * screen's per-keystroke recompute on the main thread. */
        private fun parseIpv4(text: String): Inet4Address? {
            val octets = text.trim().split(".")
            if (octets.size != 4) return null
            val bytes =
                octets.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 }?.toByte() ?: return null }.toByteArray()
            return InetAddress.getByAddress(bytes) as Inet4Address
        }
    }
