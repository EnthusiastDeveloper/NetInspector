package dev.enthusiastdev.netinspector.core.model.diagnostics

/** design §9.6 - one hop of the referral chain (IANA -> RIR -> registrar), capped at 3 hops. */
data class WhoisHop(
    val server: String,
    val responseText: String,
)

sealed interface WhoisOutcome {
    data class Success(
        val hops: List<WhoisHop>,
    ) : WhoisOutcome

    data class Error(
        val message: String,
    ) : WhoisOutcome
}
