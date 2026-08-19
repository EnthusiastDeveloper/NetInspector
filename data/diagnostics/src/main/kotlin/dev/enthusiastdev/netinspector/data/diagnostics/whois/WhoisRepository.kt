package dev.enthusiastdev.netinspector.data.diagnostics.whois

import dev.enthusiastdev.netinspector.core.model.diagnostics.WhoisHop
import dev.enthusiastdev.netinspector.core.model.diagnostics.WhoisOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject

interface WhoisRepository {
    /** design §9.6 - plain TCP/43, referral chasing (IANA -> RIR -> registrar) capped at
     * [maxHops]. */
    suspend fun query(
        target: String,
        maxHops: Int = 3,
        timeoutMs: Int = 5_000,
    ): WhoisOutcome
}

class DefaultWhoisRepository
    @Inject
    constructor() : WhoisRepository {
        override suspend fun query(
            target: String,
            maxHops: Int,
            timeoutMs: Int,
        ): WhoisOutcome =
            withContext(Dispatchers.IO) {
                val hops = mutableListOf<WhoisHop>()
                var server = IANA_ROOT
                var queryTarget = target

                repeat(maxHops) {
                    val response =
                        try {
                            queryServer(server, queryTarget, timeoutMs)
                        } catch (ignored: SocketTimeoutException) {
                            return@withContext errorOrPartial(hops, "no response from $server within ${timeoutMs}ms")
                        } catch (e: IOException) {
                            return@withContext errorOrPartial(hops, e.message ?: "connection to $server failed")
                        }
                    hops += WhoisHop(server, response)

                    val referral = findReferral(response)
                    if (referral == null || referral == server) {
                        return@withContext WhoisOutcome.Success(hops)
                    }
                    server = referral
                    // Most referred servers expect the bare domain again, not the original
                    // query verbatim - safe to reuse since this tool only targets domains/IPs,
                    // never the flag-laden queries a full WHOIS client would need to preserve.
                    queryTarget = target
                }
                WhoisOutcome.Success(hops)
            }

        private fun errorOrPartial(
            hops: List<WhoisHop>,
            message: String,
        ): WhoisOutcome = if (hops.isEmpty()) WhoisOutcome.Error(message) else WhoisOutcome.Success(hops)

        private fun queryServer(
            server: String,
            target: String,
            timeoutMs: Int,
        ): String =
            Socket().use { socket ->
                socket.connect(InetSocketAddress(server, WHOIS_PORT), timeoutMs)
                socket.soTimeout = timeoutMs
                socket.getOutputStream().write("$target\r\n".toByteArray(Charsets.US_ASCII))
                socket
                    .getInputStream()
                    .bufferedReader(Charsets.ISO_8859_1)
                    .readText()
                    .take(MAX_RESPONSE_CHARS)
            }

        private fun findReferral(response: String): String? =
            REFERRAL_PATTERN
                .find(response)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.removePrefix("whois://")
                ?.takeIf { it.isNotEmpty() }

        private companion object {
            const val IANA_ROOT = "whois.iana.org"
            const val WHOIS_PORT = 43
            const val MAX_RESPONSE_CHARS = 16_384
            val REFERRAL_PATTERN =
                Regex("""(?im)^\s*(?:refer|registrar whois server|whois server|referralserver)\s*:\s*(\S+)""")
        }
    }
