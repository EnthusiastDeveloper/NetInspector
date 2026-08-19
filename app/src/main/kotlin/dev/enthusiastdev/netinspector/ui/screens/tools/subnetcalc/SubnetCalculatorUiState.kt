package dev.enthusiastdev.netinspector.ui.screens.tools.subnetcalc

import dev.enthusiastdev.netinspector.core.common.net.Ipv4Subnet
import dev.enthusiastdev.netinspector.core.common.net.VlsmAllocation

data class SubnetCalculatorUiState(
    val addressInput: String = "192.168.1.0",
    val prefixInput: String = "24",
    val vlsmInput: String = "",
    val subnet: Ipv4Subnet? = null,
    val netmaskText: String? = null,
    val vlsmAllocations: List<VlsmAllocation>? = null,
    val errorMessage: String? = null,
)
