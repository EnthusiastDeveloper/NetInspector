package dev.enthusiastdev.netinspector.ui.screens.devices

import dev.enthusiastdev.netinspector.core.model.lan.Host
import dev.enthusiastdev.netinspector.core.model.lan.HostConfidence
import dev.enthusiastdev.netinspector.core.model.lan.primaryHostname
import java.time.Instant

/** Every sort key the Devices list offers. [GROUP] is the original default (confidence tier,
 * self/gateway pinned first) - every other value sorts the *whole* list by that key, including
 * self/gateway, since choosing one explicitly means wanting that literal ordering rather than
 * the pinned convenience view. */
enum class DevicesSortOrder {
    GROUP,
    IP_ADDRESS,
    NAME,
    DEVICE_TYPE,
    LATENCY,
    LAST_SEEN,
}

internal fun DevicesSortOrder.label(): String =
    when (this) {
        DevicesSortOrder.GROUP -> "Group"
        DevicesSortOrder.IP_ADDRESS -> "IP address"
        DevicesSortOrder.NAME -> "Name"
        DevicesSortOrder.DEVICE_TYPE -> "Device type"
        DevicesSortOrder.LATENCY -> "Latency"
        DevicesSortOrder.LAST_SEEN -> "Last seen"
    }

internal fun List<Host>.sortedForDisplay(sortOrder: DevicesSortOrder): List<Host> =
    when (sortOrder) {
        DevicesSortOrder.GROUP ->
            sortedWith(
                compareBy(
                    { host -> if (host.isSelf || host.isGateway) 0 else 1 },
                    { host -> host.confidence.sortOrder() },
                    { host -> host.address.toSortableString() },
                ),
            )
        DevicesSortOrder.IP_ADDRESS -> sortedBy { it.address.toSortableString() }
        DevicesSortOrder.NAME -> sortedBy { it.displayName().lowercase() }
        // Hosts with no guess sort last ("￿" - greater than any real label) rather than
        // clumping at the front as an implicit empty string would.
        DevicesSortOrder.DEVICE_TYPE ->
            sortedWith(compareBy({ it.deviceHint?.label ?: "￿" }, { it.address.toSortableString() }))
        // Hosts with no RTT sample (never answered ICMP) sort last, not first.
        DevicesSortOrder.LATENCY ->
            sortedWith(compareBy({ it.rttMedianMs ?: Double.MAX_VALUE }, { it.address.toSortableString() }))
        DevicesSortOrder.LAST_SEEN ->
            sortedByDescending { host -> host.evidence.maxOfOrNull { it.observedAt } ?: Instant.MIN }
    }

internal fun List<Host>.filteredByConfidence(visible: Set<HostConfidence>): List<Host> =
    filter { it.confidence in visible }

/** Confirmed first, most useful; announced next; stale last since design §8.3 wants it
 * visually and positionally de-emphasised. */
private fun HostConfidence.sortOrder(): Int =
    when (this) {
        HostConfidence.CONFIRMED -> 0
        HostConfidence.ANNOUNCED -> 1
        HostConfidence.STALE -> 2
    }

/** design §11.3 - a confirmed hostname always wins; absent that, a [DeviceHint] is a real but
 * *inferred* signal, so it's only used as a stand-in for the name (never silently equated with
 * one) and callers must style it distinctly - see [Host.hasInferredDisplayName]. A manual
 * [Host.nickname] (docs/device-identification-ideas.md D) outranks all of that: it's the one
 * signal here that's authoritative rather than observed or guessed. */
internal fun Host.displayName(): String =
    nickname ?: when {
        isSelf -> "This device"
        isGateway -> primaryHostname?.let { "$it (gateway)" } ?: "Gateway"
        else -> primaryHostname ?: deviceHint?.label ?: "Unknown device"
    }

/** Whether [displayName] fell back to the [DeviceHint] guess rather than a confirmed hostname
 * or a manual nickname - callers use this to render the name as visibly inferred (e.g. italic)
 * and to skip a separate hint line that would otherwise just repeat the title verbatim. */
internal val Host.hasInferredDisplayName: Boolean
    get() = nickname == null && !isSelf && !isGateway && primaryHostname == null && deviceHint != null
