package dev.enthusiastdev.netinspector.history

import dev.enthusiastdev.netinspector.core.model.diagnostics.PortScanFinding
import kotlinx.serialization.Serializable

@Serializable
data class PortScanFindingDto(
    val port: Int,
    val banner: String? = null,
)

@Serializable
data class PortScanRunPayload(
    val portsScanned: Int,
    val findings: List<PortScanFindingDto>,
)

fun PortScanFinding.toDto(): PortScanFindingDto = PortScanFindingDto(port, banner)
