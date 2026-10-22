package io.github.skofqq.dnscryptmanager.data

/** Severity of a log line, as dnscrypt-proxy writes it in brackets. */
enum class LogLevel { DEBUG, INFO, NOTICE, WARNING, ERROR }

/** What the level chip on the log screen keeps. */
enum class LevelFilter { ALL, INFO, WARNING, ERROR }

/**
 * One line split into its parts for colouring: the leading timestamp, the bracketed
 * level and the rest. module.log lines have a timestamp and no level.
 */
data class LogLine(
    val raw: String,
    val timestamp: String?,
    val level: LogLevel?,
    val levelText: String?,
    val message: String,
) {
    fun passes(filter: LevelFilter): Boolean = when (filter) {
        LevelFilter.ALL -> true
        LevelFilter.INFO -> level != LogLevel.DEBUG
        LevelFilter.WARNING -> level == LogLevel.WARNING || level == LogLevel.ERROR
        LevelFilter.ERROR -> level == LogLevel.ERROR
    }

    companion object {
        // `[2026-10-22 19:03:20]` (dnscrypt-proxy) or `2026-10-22 19:32:47` (module.log).
        private val TIMESTAMP = Regex("""^\[?(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?Z?)]?\s*""")
        private val LEVEL = Regex("""^\[(DEBUG|INFO|NOTICE|WARN|WARNING|ERROR|CRITICAL|FATAL)]\s*""", RegexOption.IGNORE_CASE)

        fun parse(raw: String): LogLine {
            var rest = raw
            val time = TIMESTAMP.find(rest)?.let {
                rest = rest.substring(it.range.last + 1)
                it.groupValues[1]
            }
            val levelMatch = LEVEL.find(rest)
            val level = levelMatch?.let {
                rest = rest.substring(it.range.last + 1)
                when (it.groupValues[1].uppercase()) {
                    "DEBUG" -> LogLevel.DEBUG
                    "INFO" -> LogLevel.INFO
                    "NOTICE" -> LogLevel.NOTICE
                    "WARN", "WARNING" -> LogLevel.WARNING
                    else -> LogLevel.ERROR
                }
            }
            return LogLine(
                raw = raw,
                timestamp = time,
                level = level,
                levelText = levelMatch?.groupValues?.get(1),
                message = rest,
            )
        }
    }
}
