package io.github.skofqq.dnscryptmanager.data

/**
 * The server dnscrypt-proxy settled on, read from the log line that
 * `dnscrypt-ctl servers active` prints:
 *
 * `[2026-10-22 19:03:20] [NOTICE] Server with the lowest initial latency: dnscry.pt-helsinki-ipv4 (rtt: 21ms), live servers: 173`
 *
 * [raw] keeps the whole line for the case the wording changes upstream.
 */
data class ActiveServer(
    val name: String,
    val rttMs: Int?,
    val liveServers: Int?,
    val raw: String,
) {
    companion object {
        private val PATTERN = Regex(
            """lowest initial latency:\s*(\S+)(?:\s*\(rtt:\s*(\d+)\s*ms\))?(?:,\s*live servers:\s*(\d+))?""",
            RegexOption.IGNORE_CASE,
        )

        /** Null when the line does not carry a server name. */
        fun parse(line: String): ActiveServer? {
            val match = PATTERN.find(line) ?: return null
            val (name, rtt, live) = match.destructured
            return ActiveServer(
                name = name,
                rttMs = rtt.toIntOrNull(),
                liveServers = live.toIntOrNull(),
                raw = line.trim(),
            )
        }
    }
}
