package io.github.skofqq.dnscryptmanager.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One line of JSON from `dnscrypt-ctl json`. Unknown keys are ignored on purpose:
 * newer module releases are allowed to add fields.
 */
@Serializable
data class ModuleStatus(
    val running: Boolean = false,
    val pid: Int? = null,
    @SerialName("supervisor_pid") val supervisorPid: Int? = null,
    val port: Int = ModuleContract.DEFAULT_PORT,
    /**
     * A string before 2.4.2 (empty when there is no IPv6 loopback port), a number or
     * null after. Kept as a raw element so both shapes parse.
     */
    @SerialName("port6") val port6Raw: JsonElement? = null,
    /** `redirected`, `blocked` or `bypass` from 2.4.2 on; absent before. */
    @SerialName("ipv6_dns") val ipv6DnsRaw: String? = null,
    @SerialName("rules_v4") val rulesV4: Boolean = false,
    @SerialName("redirect_ipv6") val redirectIpv6: String = "auto",
    @SerialName("tether_redirect") val tetherRedirect: Boolean = false,
    /** May be empty when the module could not read `private_dns_mode`. */
    @SerialName("private_dns") val privateDnsRaw: String = "",
    val config: String = "",
    val version: String = "",
    /** Comma-separated resolver names, or `auto`. */
    val servers: String = ServerSelection.AUTO,
    @SerialName("resolvers_cached") val resolversCached: Boolean = false,
    /**
     * True when box_for_magisk's rules really capture port 53 (not merely installed or
     * running in a mode that lets DNS through). Null before the module reported it.
     */
    @SerialName("box_dns") val boxDns: Boolean? = null,
    /** Stopped with `dnscrypt-ctl stop`: the proxy stays down across reboots until started. */
    @SerialName("manual_stop") val manualStop: Boolean = false,
) {
    /** Empty when the proxy picks servers itself. */
    val serverSelection: List<String> get() = ServerSelection.parseSelection(servers)

    val serversAuto: Boolean get() = serverSelection.isEmpty()

    /** `dnscrypt-ctl servers` exists from 2.1.18-r2 on. */
    val supportsServers: Boolean
        get() = versionAtLeast(ModuleContract.MIN_SERVERS_VERSION)

    /** `dnscrypt-ctl conflicts` and the `run_gid` setting exist from 2.1.18-r3 on. */
    val supportsConflicts: Boolean
        get() = versionAtLeast(ModuleContract.MIN_CONFLICTS_VERSION)

    /** Turning off the boot start needs the `autostart` key. */
    val supportsAutostart: Boolean
        get() = versionAtLeast(ModuleContract.MIN_AUTOSTART_VERSION)

    /** Restoring every module setting at once needs `dnscrypt-ctl reset`. */
    val supportsReset: Boolean
        get() = versionAtLeast(ModuleContract.MIN_RESET_VERSION)

    /** Before 2.3 the module mirrored its files to /storage/emulated/0. */
    val supportsMirror: Boolean
        get() = !versionAtLeast(ModuleContract.MIRROR_REMOVED_VERSION)

    /** `run_gid` is only offered where it actually works; see MIN_RUN_GID_VERSION. */
    val supportsRunGid: Boolean
        get() = versionAtLeast(ModuleContract.MIN_RUN_GID_VERSION)

    private fun versionAtLeast(minimum: String): Boolean =
        versionOrNull?.let { ModuleVersion.isAtLeast(it, minimum) } == true

    /** An empty string or null means "no data", not "port 0". */
    val port6: Int?
        get() = (port6Raw as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()

    /** Null when the module is too old to say. */
    val ipv6Dns: Ipv6Dns? get() = Ipv6Dns.parse(ipv6DnsRaw)

    /**
     * IPv6 DNS goes around the proxy: running is not the same as protected. Seen on
     * kernels without ip6tables nat, where the resolver asks an IPv6 server directly.
     */
    val ipv6Bypass: Boolean get() = running && ipv6Dns == Ipv6Dns.BYPASS

    val privateDns: String? get() = privateDnsRaw.trim().takeIf { it.isNotEmpty() }

    /**
     * The supervisor is alive but the proxy is not: the module is in the middle of
     * bringing it back up, which is a different story from "stopped by the user".
     */
    val isRestarting: Boolean get() = !running && supervisorPid != null

    val versionOrNull: String? get() = version.trim().takeIf { it.isNotEmpty() }
}

/** What happens to IPv6 DNS queries, as `dnscrypt-ctl json` reports it in `ipv6_dns`. */
enum class Ipv6Dns {
    /** Sent through the proxy with ip6tables nat. */
    REDIRECTED,

    /** Rejected, so the resolver falls back to IPv4, which is intercepted. */
    BLOCKED,

    /** Neither: IPv6 queries reach the network's resolver unencrypted. */
    BYPASS;

    companion object {
        fun parse(raw: String?): Ipv6Dns? = when (raw?.trim()?.lowercase()) {
            "redirected" -> REDIRECTED
            "blocked" -> BLOCKED
            "bypass" -> BYPASS
            else -> null
        }
    }
}

object StatusParser {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * Parses the single JSON line printed by `dnscrypt-ctl json`. Anything the module
     * printed before the JSON (warnings and the like) is skipped.
     */
    fun parse(raw: String): Result<ModuleStatus> {
        val line = raw.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("{") && it.endsWith("}") }
            ?: return Result.failure(IllegalArgumentException("No JSON object in output"))
        return runCatching { json.decodeFromString(ModuleStatus.serializer(), line) }
    }
}

/**
 * Compares module versions of the shape `2.1.18-r1`: dotted numbers first,
 * then the `-rN` revision. Missing parts count as zero.
 */
object ModuleVersion {

    fun parse(version: String): List<Int> {
        val cleaned = version.trim().removePrefix("v")
        val (base, revision) = when {
            cleaned.contains("-r") -> cleaned.substringBefore("-r") to cleaned.substringAfter("-r")
            else -> cleaned to "0"
        }
        val numbers = base.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        return numbers + (revision.takeWhile { it.isDigit() }.toIntOrNull() ?: 0)
    }

    fun compare(left: String, right: String): Int {
        val a = parse(left)
        val b = parse(right)
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    fun isAtLeast(version: String, minimum: String): Boolean = compare(version, minimum) >= 0
}
