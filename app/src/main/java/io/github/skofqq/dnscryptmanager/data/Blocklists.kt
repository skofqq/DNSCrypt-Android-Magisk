package io.github.skofqq.dnscryptmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One downloadable list. [entries] and [updatedAt] describe its last good download. */
@Serializable
data class Blocklist(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = false,
    val entries: Int? = null,
    val updatedAt: Long? = null,
    val error: String? = null,
)

@Serializable
data class BlocklistSettings(
    val lists: List<Blocklist> = BlocklistPresets.ALL,
    val autoUpdate: Boolean = false,
    val unmeteredOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    /** Domains in the generated section after the last merge (lists overlap). */
    val mergedEntries: Int? = null,
) {
    val enabledCount: Int get() = lists.count { it.enabled }
}

/**
 * Plain-domain lists that dnscrypt-proxy reads as "this domain and its subdomains".
 * OISD comes from its GitHub mirror: oisd.nl itself stalls on some networks.
 */
object BlocklistPresets {
    private const val HAGEZI = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard"
    private const val OISD = "https://raw.githubusercontent.com/sjhgvr/oisd/main"

    val ALL = listOf(
        Blocklist("hagezi-light", "HaGeZi Multi Light", "$HAGEZI/light-onlydomains.txt"),
        Blocklist("hagezi-normal", "HaGeZi Multi Normal", "$HAGEZI/multi-onlydomains.txt"),
        Blocklist("hagezi-pro", "HaGeZi Multi Pro", "$HAGEZI/pro-onlydomains.txt"),
        Blocklist("oisd-small", "OISD small", "$OISD/domainswild2_small.txt"),
        Blocklist("oisd-big", "OISD big", "$OISD/domainswild2_big.txt"),
    )

    fun isPreset(id: String): Boolean = ALL.any { it.id == id }
}

/**
 * Turns a downloaded list into names dnscrypt-proxy accepts. Takes plain domains,
 * `*.domain`, hosts lines (`0.0.0.0 domain`) and adblock `||domain^`; drops comments,
 * IPs and anything that is not a hostname, so nothing odd reaches blocked-names.txt.
 */
object BlocklistFormat {
    private val DOMAIN = Regex("^(?=.{1,253}$)([a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?\\.)+[a-z0-9-]{2,63}$")
    private val HOSTS_TARGETS = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
    private val LOCAL_NAMES = setOf("localhost", "localhost.localdomain", "local", "broadcasthost")

    fun domain(line: String): String? {
        var text = line.substringBefore('#').trim().lowercase()
        if (text.isEmpty() || text.startsWith("!") || text.startsWith("[")) return null
        val parts = text.split(Regex("\\s+"))
        if (parts.size >= 2 && parts[0] in HOSTS_TARGETS) text = parts[1]
        text = text.removePrefix("||").removeSuffix("^").removePrefix("*.").removePrefix(".")
        if (text in LOCAL_NAMES || !DOMAIN.matches(text)) return null
        // Something like 10.0.0.1 passes the shape check; a numeric last label is an IP.
        if (text.substringAfterLast('.').all { it.isDigit() }) return null
        return text
    }

    fun isDomain(text: String): Boolean = domain(text) == text
}

/** Domains mentioned in a log line, for blocking or allowing them from the Logs tab. */
object LogDomains {
    private val TOKEN = Regex("[A-Za-z0-9._-]+")

    /** File names look like hostnames ("dnscrypt-proxy.toml"); their extensions give them away. */
    private val FILE_SUFFIXES = setOf(
        "txt", "toml", "log", "md", "sh", "conf", "json", "zip", "pid", "bak", "new", "minisig", "so", "apk", "prop",
    )

    fun extract(line: String): List<String> = TOKEN.findAll(line)
        .map { it.value.trimEnd('.') }
        .mapNotNull { BlocklistFormat.domain(it) }
        .filter { it.substringAfterLast('.') !in FILE_SUFFIXES }
        .distinct()
        .toList()
}

/** Lists, their settings and the download/merge cycle. */
class BlocklistRepository(
    context: Context,
    private val module: ModuleRepository,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val listsDir = File(context.filesDir, "blocklists").apply { mkdirs() }
    private val mergedFile = File(context.cacheDir, "blocklists-merged.txt")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<BlocklistSettings> = _settings.asStateFlow()

    private fun read(): BlocklistSettings {
        val stored = prefs.getString(KEY, null)?.let {
            runCatching { json.decodeFromString(BlocklistSettings.serializer(), it) }.getOrNull()
        } ?: return BlocklistSettings()
        // Presets added in later versions show up without touching the user's choices.
        val missing = BlocklistPresets.ALL.filter { preset -> stored.lists.none { it.id == preset.id } }
        return stored.copy(lists = stored.lists + missing)
    }

    private fun save(transform: (BlocklistSettings) -> BlocklistSettings) {
        _settings.update(transform)
        prefs.edit().putString(KEY, json.encodeToString(BlocklistSettings.serializer(), _settings.value)).apply()
    }

    fun setEnabled(id: String, enabled: Boolean) =
        save { s -> s.copy(lists = s.lists.map { if (it.id == id) it.copy(enabled = enabled) else it }) }

    fun add(name: String, url: String) = save { s ->
        s.copy(lists = s.lists + Blocklist("custom-${System.currentTimeMillis()}", name, url, enabled = true))
    }

    fun remove(id: String) {
        if (BlocklistPresets.isPreset(id)) return
        File(listsDir, "$id.txt").delete()
        save { s -> s.copy(lists = s.lists.filterNot { it.id == id }) }
    }

    fun setAutoUpdate(enabled: Boolean) = save { it.copy(autoUpdate = enabled) }
    fun setUnmeteredOnly(enabled: Boolean) = save { it.copy(unmeteredOnly = enabled) }
    fun setChargingOnly(enabled: Boolean) = save { it.copy(chargingOnly = enabled) }

    /**
     * Downloads every enabled list (a failed one keeps its last good copy), merges them
     * without duplicates and writes the result into the generated section of
     * blocked-names.txt. Restarts the proxy only if it is running and [restart] asks
     * for it; a stopped proxy stays stopped. Returns the merged domain count.
     */
    suspend fun updateAll(restart: Boolean): Result<Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val enabled = _settings.value.lists.filter { it.enabled }
            for (list in enabled) {
                val result = runCatching { download(list) }
                save { s ->
                    s.copy(lists = s.lists.map {
                        if (it.id != list.id) {
                            it
                        } else {
                            result.fold(
                                onSuccess = { count -> it.copy(entries = count, updatedAt = System.currentTimeMillis(), error = null) },
                                onFailure = { e -> it.copy(error = e.message ?: e.javaClass.simpleName) },
                            )
                        }
                    })
                }
            }
            val merged = merge(enabled.map { File(listsDir, "${it.id}.txt") }.filter { it.exists() })
            module.installBlocklists(mergedFile).getOrElse { return@withContext Result.failure(it) }
            save { it.copy(mergedEntries = merged) }
            if (restart) module.restartIfRunning()
            Result.success(merged)
        }
    }

    /** Fetches one list into its own file of clean names; returns how many it holds. */
    private fun download(list: Blocklist): Int {
        require(list.url.startsWith("https://")) { "Not an https URL" }
        val connection = (URL(list.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DNSCryptManager")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val target = File(listsDir, "${list.id}.txt")
            val partial = File(listsDir, "${list.id}.part")
            var count = 0
            partial.bufferedWriter().use { out ->
                connection.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val name = BlocklistFormat.domain(line) ?: continue
                        out.write(name)
                        out.newLine()
                        count++
                    }
                }
            }
            // An empty answer (captive portal, truncated file) must not wipe a good copy.
            if (count == 0) {
                partial.delete()
                error("no domains in the download")
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            return count
        } finally {
            connection.disconnect()
        }
    }

    private fun merge(files: List<File>): Int {
        val seen = HashSet<String>()
        mergedFile.bufferedWriter().use { out ->
            for (file in files) {
                file.useLines { lines ->
                    for (name in lines) {
                        if (seen.add(name)) {
                            out.write(name)
                            out.newLine()
                        }
                    }
                }
            }
        }
        return seen.size
    }

    private companion object {
        const val PREFS = "blocklists"
        const val KEY = "settings"
    }
}
