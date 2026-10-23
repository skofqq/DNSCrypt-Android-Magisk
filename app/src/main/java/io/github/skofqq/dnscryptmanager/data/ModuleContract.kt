package io.github.skofqq.dnscryptmanager.data

/**
 * Everything the app knows about the Magisk module. `dnscrypt-ctl` is the single
 * source of truth; nothing here may be guessed at runtime.
 */
object ModuleContract {

    const val MODULE_ID = "dnscrypt-proxy-android"
    const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
    const val CTL_PATH = "$MODULE_DIR/system/bin/dnscrypt-ctl"
    const val DISABLE_FLAG = "$MODULE_DIR/disable"

    const val DATA_DIR = "/data/adb/dnscrypt-proxy"
    const val CONFIG_PATH = "$DATA_DIR/dnscrypt-proxy.toml"
    const val CONFIG_NAME = "dnscrypt-proxy.toml"
    const val MODULE_CONF = "$DATA_DIR/module.conf"
    const val MODULE_LOG = "$DATA_DIR/module.log"
    const val PROXY_LOG = "$DATA_DIR/dnscrypt-proxy.log"
    const val STDOUT_LOG = "$DATA_DIR/stdout.log"
    const val MIRROR_DIR = "/storage/emulated/0/dnscrypt-proxy"

    const val RESOLVERS_CACHE = "$DATA_DIR/public-resolvers.md"
    const val BLOCKED_NAMES = "$DATA_DIR/blocked-names.txt"
    const val ALLOWED_NAMES = "$DATA_DIR/allowed-names.txt"

    /** Fences around the part of blocked-names.txt the app regenerates from blocklists. */
    const val BLOCKLIST_BEGIN_PREFIX = "# >>> DNSCrypt Manager blocklists"
    const val BLOCKLIST_BEGIN = "$BLOCKLIST_BEGIN_PREFIX (generated, edits here are replaced)"
    const val BLOCKLIST_END = "# <<< DNSCrypt Manager blocklists"

    /** First module release that ships `dnscrypt-ctl json`. */
    const val MIN_VERSION = "2.1.18-r1"

    /** First release with `dnscrypt-ctl servers`. */
    const val MIN_SERVERS_VERSION = "2.1.18-r2"

    /** First release with `dnscrypt-ctl conflicts` and the `run_gid` setting. */
    const val MIN_CONFLICTS_VERSION = "2.1.18-r3"

    /**
     * `run_gid` exists from 2.1.18-r3 on, but until 2.2.1 it could not work: the module
     * called `su` with the options after the user name, and Magisk `su` refuses a group
     * name anyway, so any value put the proxy into a crash loop and left the device
     * without DNS. The setting stays hidden on those releases.
     */
    const val MIN_RUN_GID_VERSION = "2.2.1"

    /**
     * From 2.3 the module keeps nothing on shared storage: `mirror_config` and the
     * `export`/`import` commands are gone, so the controls for them are hidden.
     */
    const val MIRROR_REMOVED_VERSION = "2.3"

    /** `dnscrypt-ctl reset` exists from 2.3.1 on. */
    const val MIN_RESET_VERSION = "2.3.1"

    /** The `autostart` key exists from 2.4 on. */
    const val MIN_AUTOSTART_VERSION = "2.4"

    /**
     * The one stable line the app looks for in `dnscrypt-ctl conflicts`. Everything else
     * in that output is shown verbatim and never parsed.
     */
    /** Logged by the supervisor when `su -g` is refused and it starts without run_gid. */
    const val RUN_GID_FALLBACK_MARKER = "смена первичной группы"

    const val BOX_MARKER = "box_for_magisk: обнаружен"

    /** A resolver cache older than this is worth a gentle note. */
    const val CACHE_STALE_SECONDS = 7L * 24 * 60 * 60

    const val PROJECT_URL = "https://github.com/skofqq/DNSCrypt-2-Magisk-Module"
    const val RELEASES_URL = "$PROJECT_URL/releases"

    /** Used when module.prop has no updateJson line; the module points at the same file. */
    const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/skofqq/DNSCrypt-2-Magisk-Module/main/update.json"

    const val CTL = "dnscrypt-ctl"

    const val DEFAULT_PORT = 5354
}

/** The six keys `dnscrypt-ctl set` accepts, with the type each one takes. */
sealed interface SettingSpec {
    val key: String

    /** Returns the canonical value to pass to `set`, or null when the input is not valid. */
    fun normalize(value: String): String?
}

data class BoolSetting(override val key: String) : SettingSpec {
    override fun normalize(value: String): String? = when (value.trim().lowercase()) {
        "true", "1", "yes", "on" -> "true"
        "false", "0", "no", "off" -> "false"
        else -> null
    }
}

data class EnumSetting(override val key: String, val values: List<String>) : SettingSpec {
    override fun normalize(value: String): String? =
        values.firstOrNull { it.equals(value.trim(), ignoreCase = true) }
}

/** A POSIX group: a numeric GID, a group name, or empty for the module default. */
data class GroupSetting(override val key: String) : SettingSpec {

    override fun normalize(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> ""
            trimmed.all { it.isDigit() } -> trimmed
            GROUP_NAME.matches(trimmed) -> trimmed
            else -> null
        }
    }

    private companion object {
        val GROUP_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}

data class IntSetting(override val key: String, val min: Int, val max: Int) : SettingSpec {
    override fun normalize(value: String): String? {
        val parsed = value.trim().toIntOrNull() ?: return null
        return if (parsed in min..max) parsed.toString() else null
    }
}

object ModuleSettings {

    const val REDIRECT_IPV4 = "redirect_ipv4"
    const val REDIRECT_IPV6 = "redirect_ipv6"
    const val TETHER_REDIRECT = "tether_redirect"
    const val DISABLE_PRIVATE_DNS = "disable_private_dns"
    const val MIRROR_CONFIG = "mirror_config"
    const val RESTART_DELAY_MAX = "restart_delay_max"

    /** Added in module 2.4: whether the module comes up at boot. */
    const val AUTOSTART = "autostart"

    /** Added in module 2.1.18-r3; only meaningful next to box_for_magisk. */
    const val RUN_GID = "run_gid"

    const val RUN_GID_NET_ADMIN = "net_admin"

    /** Android's AID_NET_ADMIN, the group box_for_magisk lets past its interception. */
    const val NET_ADMIN_GID = 3005

    /**
     * The GID [RUN_GID] asks for, as the module's run_gid_numeric() reads it: null for a
     * normal start or a name the module does not know.
     */
    fun expectedGid(value: String): Int? = when (value) {
        "" -> null
        RUN_GID_NET_ADMIN -> NET_ADMIN_GID
        "inet" -> 3003
        "net_raw" -> 3004
        else -> value.toIntOrNull()
    }

    val IPV6_MODES = listOf("auto", "redirect", "off")

    const val RESTART_DELAY_MIN_SECONDS = 5
    const val RESTART_DELAY_MAX_SECONDS = 600
    const val RESTART_DELAY_DEFAULT = 60

    /** The allow-list. A key that is not in here never reaches the shell. */
    val SPECS: Map<String, SettingSpec> = listOf<SettingSpec>(
        BoolSetting(AUTOSTART),
        BoolSetting(REDIRECT_IPV4),
        EnumSetting(REDIRECT_IPV6, IPV6_MODES),
        BoolSetting(TETHER_REDIRECT),
        BoolSetting(DISABLE_PRIVATE_DNS),
        BoolSetting(MIRROR_CONFIG),
        IntSetting(RESTART_DELAY_MAX, RESTART_DELAY_MIN_SECONDS, RESTART_DELAY_MAX_SECONDS),
        GroupSetting(RUN_GID),
    ).associateBy { it.key }

    val KEYS: List<String> = SPECS.keys.toList()

    /**
     * Validates a key/value pair before it is handed to `dnscrypt-ctl set`.
     * Unknown keys and out-of-range values are rejected here, not by the shell.
     */
    fun validate(key: String, value: String): Result<Pair<String, String>> {
        val spec = SPECS[key] ?: return Result.failure(
            IllegalArgumentException("Unknown module setting: $key")
        )
        val normalized = spec.normalize(value) ?: return Result.failure(
            IllegalArgumentException("Invalid value for $key: $value")
        )
        return Result.success(key to normalized)
    }

    /** Parses `module.conf` (KEY=VALUE, `#` comments), keeping only known keys. */
    fun parseConf(text: String): Map<String, String> = buildMap {
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val name = line.substringBefore('=', missingDelimiterValue = "").trim()
            if (name.isEmpty() || name !in SPECS) return@forEach
            val value = line.substringAfter('=').trim().trim('"', '\'')
            SPECS.getValue(name).normalize(value)?.let { put(name, it) }
        }
    }
}
