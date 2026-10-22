package io.github.skofqq.dnscryptmanager.data

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One upstream that answered dnscrypt-proxy's startup probe. */
data class LiveServer(val name: String, val protocol: String, val rttMs: Int)

/**
 * The servers dnscrypt-proxy is balancing across, read from its log: every
 * `[name] OK (DNSCrypt) - rtt: 23ms` line since the last start banner.
 */
data class LiveServers(
    val servers: List<LiveServer>,
    /** `lb_strategy` from the config; dnscrypt-proxy's default is `p2`. */
    val strategy: String,
    /** The proxy's own "live servers: N" count, which can exceed the parsed list. */
    val liveCount: Int = servers.size,
) {
    /** How many of the fastest servers the strategy sends queries to. */
    val inUseCount: Int
        get() = when {
            servers.isEmpty() -> 0
            strategy == "first" -> 1
            strategy == "ph" -> (servers.size + 1) / 2
            strategy == "random" -> servers.size
            strategy.startsWith("p") -> strategy.drop(1).toIntOrNull()?.coerceIn(1, servers.size) ?: 2
            else -> minOf(2, servers.size)
        }.coerceAtMost(servers.size)

    val inUse: List<LiveServer> get() = servers.take(inUseCount)

    companion object {
        const val DEFAULT_STRATEGY = "p2"

        private val OK_LINE = Regex("""\[([^\]\s]+)] OK \(([^)]+)\) - rtt: (\d+)ms""")
        // "[2026-10-22 22:11:09] [NOTICE] dnscrypt-proxy 2.1.18": the start of a run.
        private val BANNER = Regex("""] dnscrypt-proxy \d+\.\d+""")
        private val STRATEGY = Regex("""^\s*lb_strategy\s*=\s*['"]([^'"]+)['"]""", RegexOption.MULTILINE)

        /** `dnscrypt-ctl servers live`: {"strategy":"p2","live":172,"servers":[{"name":…,"proto":…,"rtt":…}]}. */
        fun parseJson(line: String): LiveServers? = runCatching {
            val root = StatusParser.json.parseToJsonElement(line.trim()).jsonObject
            val servers = root["servers"]?.jsonArray.orEmpty().mapNotNull { element ->
                val o = element.jsonObject
                val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                LiveServer(
                    name = name,
                    protocol = o["proto"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    rttMs = o["rtt"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                )
            }.sortedBy { it.rttMs }
            LiveServers(
                servers = servers,
                strategy = root["strategy"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: DEFAULT_STRATEGY,
                liveCount = root["live"]?.jsonPrimitive?.intOrNull ?: servers.size,
            )
        }.getOrNull()

        fun parse(logLines: List<String>, config: String?): LiveServers {
            val start = logLines.indexOfLast { BANNER.containsMatchIn(it) }.coerceAtLeast(0)
            val best = LinkedHashMap<String, LiveServer>()
            logLines.drop(start).forEach { line ->
                val match = OK_LINE.find(line) ?: return@forEach
                val (name, protocol, rtt) = match.destructured
                val server = LiveServer(name, protocol, rtt.toInt())
                // Each server logs twice when it has a second certificate; keep the faster.
                val known = best[name]
                if (known == null || server.rttMs < known.rttMs) best[name] = server
            }
            val strategy = config?.let { STRATEGY.find(it)?.groupValues?.get(1) } ?: DEFAULT_STRATEGY
            return LiveServers(best.values.sortedBy { it.rttMs }, strategy)
        }
    }
}
