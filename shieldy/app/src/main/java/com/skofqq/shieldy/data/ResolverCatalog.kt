package com.skofqq.shieldy.data

/** Protocol byte of an `sdns://` stamp. */
enum class StampProtocol(val byte: Int) {
    PLAIN(0x00),
    DNSCRYPT(0x01),
    DOH(0x02),
    DOT(0x03),
    DOQ(0x04),
    ODOH_TARGET(0x05),
    DNSCRYPT_RELAY(0x81),
    ODOH_RELAY(0x85),
    UNKNOWN(-1);

    /** Relay stamps carry no props field: the address follows the protocol byte directly. */
    val isRelay: Boolean get() = this == DNSCRYPT_RELAY || this == ODOH_RELAY

    companion object {
        fun of(byte: Int): StampProtocol = entries.firstOrNull { it.byte == byte } ?: UNKNOWN
    }
}

/**
 * What the UI needs out of a stamp: the protocol and the three informational flags.
 * [props] is null for relays, which have no props field at all.
 */
data class StampInfo(
    val protocol: StampProtocol,
    val props: Long?,
) {
    val dnssec: Boolean get() = hasBit(0)
    val noLog: Boolean get() = hasBit(1)
    val noFilter: Boolean get() = hasBit(2)

    private fun hasBit(index: Int): Boolean = props?.let { (it shr index) and 1L == 1L } ?: false
}

/**
 * Reads the first nine bytes of an `sdns://` stamp. Everything after that (address,
 * key fingerprint, hostname, path) is of no use to the UI, so it is left alone.
 */
object DnsStamp {

    private const val PREFIX = "sdns://"

    fun decode(stamp: String): StampInfo? {
        val payload = stamp.trim().removePrefix(PREFIX).takeIf { it.isNotEmpty() } ?: return null
        val bytes = decodeBase64Url(payload) ?: return null
        if (bytes.isEmpty()) return null

        val protocol = StampProtocol.of(bytes[0].toInt() and 0xFF)
        if (protocol == StampProtocol.UNKNOWN) return StampInfo(protocol, null)
        if (protocol.isRelay) return StampInfo(protocol, null)
        if (bytes.size < 9) return StampInfo(protocol, null)

        var props = 0L
        for (i in 0 until 8) {
            props = props or ((bytes[1 + i].toLong() and 0xFF) shl (8 * i))
        }
        return StampInfo(protocol, props)
    }

    /** base64url without padding; `=` is added back before decoding. */
    private fun decodeBase64Url(value: String): ByteArray? {
        val cleaned = value.trimEnd('=')
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        return runCatching {
            java.util.Base64.getUrlDecoder().decode(padded)
        }.getOrNull()
    }
}

/** One `## name` block of public-resolvers.md. */
data class ResolverEntry(
    val name: String,
    val description: String,
    val stamps: List<String>,
) {
    /** Properties come from the first stamp; the rest are other addresses of one operator. */
    val stamp: StampInfo? by lazy { stamps.firstOrNull()?.let { DnsStamp.decode(it) } }

    val protocol: StampProtocol get() = stamp?.protocol ?: StampProtocol.UNKNOWN
    val dnssec: Boolean get() = stamp?.dnssec == true
    val noLog: Boolean get() = stamp?.noLog == true
    val noFilter: Boolean get() = stamp?.noFilter == true

    /** First line of the description, for the one-line summary in the list. */
    val summary: String get() = description.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    fun matches(query: String): Boolean =
        name.contains(query, ignoreCase = true) || description.contains(query, ignoreCase = true)
}

/**
 * Splits public-resolvers.md (or relays.md) into blocks. A `## name` line opens a block,
 * free-form description follows, then one or more `sdns://` lines.
 */
object ResolverParser {

    fun parse(markdown: String): List<ResolverEntry> {
        val entries = ArrayList<ResolverEntry>()
        var name: String? = null
        val description = StringBuilder()
        val stamps = ArrayList<String>()

        fun flush() {
            val current = name ?: return
            entries.add(
                ResolverEntry(
                    name = current,
                    description = description.toString().trim(),
                    stamps = stamps.toList(),
                )
            )
            description.setLength(0)
            stamps.clear()
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("## ") -> {
                    flush()
                    name = line.removePrefix("## ").trim()
                }

                name == null -> Unit // preamble before the first block

                line.startsWith("sdns://") -> stamps.add(line)

                else -> {
                    if (line.isNotEmpty() || description.isNotEmpty()) {
                        description.append(line).append('\n')
                    }
                }
            }
        }
        flush()
        return entries
    }
}

/** Builds the argument for `dnscrypt-ctl servers set`. */
object ServerSelection {

    /** Names in the catalogue look like `adguard-dns` or `dnscrypt.eu-nl`. */
    private val NAME = Regex("^[A-Za-z0-9._-]+$")

    fun isValidName(name: String): Boolean = name.isNotEmpty() && NAME.matches(name)

    /** Order matters: the proxy tries servers in the order they are listed. */
    fun normalize(names: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        names.forEach { raw ->
            val name = raw.trim()
            if (name.isNotEmpty()) seen.add(name)
        }
        return seen.toList()
    }

    fun buildSetArgument(names: List<String>): Result<String> {
        val normalized = normalize(names)
        if (normalized.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty selection"))
        }
        val invalid = normalized.firstOrNull { !isValidName(it) }
        if (invalid != null) {
            return Result.failure(IllegalArgumentException("Invalid server name: $invalid"))
        }
        return Result.success(normalized.joinToString(","))
    }

    /** Splits the value of `dnscrypt-ctl servers get`; `auto` means no manual selection. */
    fun parseSelection(value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.equals(AUTO, ignoreCase = true)) return emptyList()
        return normalize(trimmed.split(','))
    }

    const val AUTO = "auto"
}
