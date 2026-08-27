package dev.enthusiastdev.netinspector.debug

/** ideas.md #22 - a small, `:app`-local summary of one diagnostic run
 * (`:data:persistence`'s `DiagnosticRunEntity` isn't reused directly so this stays independent
 * of the Room model shape). */
data class DiagnosticRunSummary(
    val toolType: String,
    val target: String,
    val timestampMillis: Long,
    val summary: String,
)
