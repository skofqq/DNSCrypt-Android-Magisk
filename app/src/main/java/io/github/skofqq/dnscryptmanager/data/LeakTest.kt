package io.github.skofqq.dnscryptmanager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** One row of the bash.ws report: a resolver that asked on our behalf, or our own address. */
@Serializable
data class LeakEntry(
    val ip: String = "",
    val country: String = "",
    @kotlinx.serialization.SerialName("country_name") val countryName: String = "",
    val asn: String = "",
    val org: String = "",
    val type: String = "",
) {
    /** "AS3214 xTom GmbH" → "AS3214". */
    val asnNumber: String get() = asn.substringBefore(' ').trim()

    /** Regional-indicator flag for a two-letter country code. */
    val flag: String
        get() = country.uppercase().takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
            ?.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }?.joinToString("").orEmpty()
}

data class LeakReport(val publicIp: LeakEntry?, val resolvers: List<LeakEntry>)

/** What the check concludes about where DNS queries go. */
enum class DnsVerdict { THROUGH_PROXY, LEAK, PROXY_STOPPED, UNKNOWN }

/**
 * The bash.ws DNS leak test: resolve unique names under a fresh id through the
 * system resolver, then ask bash.ws which resolvers came asking for them.
 */
object LeakTest {

    private const val BASE = "https://bash.ws"
    private const val NAMES = 10
    private const val TIMEOUT_MS = 10_000

    suspend fun run(): Result<LeakReport> = withContext(Dispatchers.IO) {
        runCatching {
            val id = get("$BASE/id").trim()
            require(id.isNotEmpty() && id.all { it.isLetterOrDigit() }) { "bash.ws gave no test id" }
            // Answers do not matter: bash.ws only counts who asked. Failures are expected.
            coroutineScope {
                (1..NAMES).map { n ->
                    async {
                        withTimeoutOrNull(4_000) { runCatching { InetAddress.getByName("$n.$id.bash.ws") } }
                    }
                }.awaitAll()
            }
            delay(1_000)
            parse(get("$BASE/dnsleak/test/$id?json"))
        }
    }

    fun parse(json: String): LeakReport {
        val entries = StatusParser.json.decodeFromString<List<LeakEntry>>(json)
        return LeakReport(
            publicIp = entries.firstOrNull { it.type == "ip" },
            resolvers = entries.filter { it.type == "dns" },
        )
    }

    /**
     * A resolver in our own network's ASN, or one of the network's DNS servers, means the
     * provider sees the queries. Without a running proxy there is nothing to judge.
     */
    fun verdict(report: LeakReport?, proxyActive: Boolean, networkDns: List<String>): DnsVerdict {
        if (!proxyActive) return DnsVerdict.PROXY_STOPPED
        if (report == null) return DnsVerdict.UNKNOWN
        val ownAsn = report.publicIp?.asnNumber?.takeIf { it.isNotEmpty() }
        val leaking = report.resolvers.any { (ownAsn != null && it.asnNumber == ownAsn) || it.ip in networkDns }
        return when {
            leaking -> DnsVerdict.LEAK
            report.resolvers.isEmpty() -> DnsVerdict.UNKNOWN
            else -> DnsVerdict.THROUGH_PROXY
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "DNSCryptManager")
        try {
            val code = connection.responseCode
            require(code in 200..299) { "bash.ws answered HTTP $code" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
