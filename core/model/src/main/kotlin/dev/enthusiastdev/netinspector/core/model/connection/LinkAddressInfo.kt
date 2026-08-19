package dev.enthusiastdev.netinspector.core.model.connection

import java.net.InetAddress

data class LinkAddressInfo(
    val address: InetAddress,
    val prefixLength: Int,
)
