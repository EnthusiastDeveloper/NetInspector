package dev.enthusiastdev.netinspector.core.model.lan

/**
 * docs/ideas.md #1 - a single glanceable "is this okay?" read, aggregated from
 * [PortRiskSeverity] tiers over a host's (or a whole network's) open ports rather than making
 * the user parse a raw port list. [value] starts at 100 (a host or network with nothing
 * [portRiskSeverity] flags) and loses a fixed number of points per [findings] entry, floored at
 * 0 - a deterministic point deduction rather than a continuous formula, so the score drop from
 * one more open Telnet port is always the same number regardless of what else is open, and
 * "score dropped by 25" is reproducible and explainable in one sentence rather than a curve
 * fit. [rating] is the enum-labelled read of [value], following the same "no field without a
 * basis" convention as [HostConfidence]/[Certainty] - [findings] is that basis.
 */
data class HygieneScore(
    val value: Int,
    val findings: List<HygieneFinding>,
) {
    val rating: HygieneRating get() = HygieneRating.forValue(value)

    companion object {
        val CLEAN = HygieneScore(value = 100, findings = emptyList())
    }
}

/**
 * One [PortRiskSeverity] hit that contributed to a [HygieneScore]. [hostAddress] disambiguates
 * which host it came from in a [networkHygieneScore] (several hosts can share a port number);
 * it's redundant, but still populated, in a [hostHygieneScore]'s findings, where the host is
 * already implied by context.
 */
data class HygieneFinding(
    val port: Int,
    val severity: PortRiskSeverity,
    val hostAddress: String?,
)

/**
 * Five-band rating a [HygieneScore.value] maps to. Thresholds line up with [hostHygieneScore]'s
 * per-[PortRiskSeverity] penalties: a single [PortRiskSeverity.CRITICAL] finding alone (-40)
 * already lands at FAIR rather than GOOD, because an unauthenticated remote-access port being
 * reachable at all is not a "minor" issue even in isolation.
 */
enum class HygieneRating {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    CRITICAL,
    ;

    companion object {
        fun forValue(value: Int): HygieneRating =
            when {
                value >= EXCELLENT_MIN -> EXCELLENT
                value >= GOOD_MIN -> GOOD
                value >= FAIR_MIN -> FAIR
                value >= POOR_MIN -> POOR
                else -> CRITICAL
            }
    }
}

private const val EXCELLENT_MIN = 90
private const val GOOD_MIN = 70
private const val FAIR_MIN = 50
private const val POOR_MIN = 25

private const val CRITICAL_PENALTY = 40
private const val HIGH_PENALTY = 20
private const val MODERATE_PENALTY = 10

private fun PortRiskSeverity.penalty(): Int =
    when (this) {
        PortRiskSeverity.CRITICAL -> CRITICAL_PENALTY
        PortRiskSeverity.HIGH -> HIGH_PENALTY
        PortRiskSeverity.MODERATE -> MODERATE_PENALTY
    }

/**
 * Per-host score: every open port on [host] that [portRiskSeverity] flags contributes its
 * tier's penalty.
 */
fun hostHygieneScore(host: Host): HygieneScore = hygieneScoreOf(host.openPorts.mapNotNull { it.findingOn(host) })

/**
 * Per-network score: the same aggregation over every host's open ports at once - equivalent to
 * calling [hostHygieneScore] with a single-host list, so one host carrying three risky ports
 * pulls the network score down exactly as far as three separate hosts carrying one risky port
 * each would.
 */
fun networkHygieneScore(hosts: List<Host>): HygieneScore =
    hygieneScoreOf(hosts.flatMap { host -> host.openPorts.mapNotNull { it.findingOn(host) } })

private fun OpenPort.findingOn(host: Host): HygieneFinding? =
    portRiskSeverity(port)?.let { severity ->
        HygieneFinding(port = port, severity = severity, hostAddress = host.address.hostAddress)
    }

private fun hygieneScoreOf(findings: List<HygieneFinding>): HygieneScore {
    if (findings.isEmpty()) return HygieneScore.CLEAN
    val penalty = findings.sumOf { it.severity.penalty() }
    return HygieneScore(value = (100 - penalty).coerceAtLeast(0), findings = findings)
}
