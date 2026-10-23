package io.github.skofqq.dnscryptmanager.data

import io.github.skofqq.dnscryptmanager.root.RootShell
import io.github.skofqq.dnscryptmanager.root.ShellOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A `.bak` sitting next to the live config, or the `.new` file left by a module update. */
data class ConfigBackup(
    val name: String,
    val path: String,
    val isPending: Boolean,
)

/**
 * One status poll: the module state and, while the proxy runs, the raw answers of
 * `servers active` and `servers live`. See [ModuleRepository.poll].
 */
data class PolledStatus(val state: ModuleState, val activeServer: String?, val liveJson: String?)

/** Part of a log file and the file size it ends at; see [ModuleRepository.logChunk]. */
data class LogChunk(val size: Long, val lines: List<String>, val reset: Boolean)

/** Effective group of the proxy process; see [ModuleRepository.proxyGroup]. */
data class ProxyGroup(val gid: Int, val suRefusedGroup: Boolean)

class ModuleRepository(
    private val shell: RootShell,
    private val cacheDir: File,
) {

    // ---------------------------------------------------------------- state

    suspend fun loadState(): ModuleState = withContext(Dispatchers.IO) {
        val rootCheck = shell.shell()
        if (rootCheck.isFailure) return@withContext ModuleState.NoRoot

        val json = shell.exec(ModuleContract.CTL, "json")
            .getOrElse { return@withContext ModuleState.NoRoot }

        if (json.code == 127 || json.stdout.isEmpty() && json.err.contains("not found", ignoreCase = true)) {
            return@withContext diagnoseMissing()
        }

        val parsed = StatusParser.parse(json.out)
        if (parsed.isFailure) {
            // Command exists but cannot answer `json`: either an old build or a broken install.
            val installed = ctlExists()
            return@withContext if (installed) {
                ModuleState.Outdated(readVersionFallback())
            } else {
                diagnoseMissing()
            }
        }

        val status = parsed.getOrThrow()
        readyState(status, status.pid?.let { readProcessUptime(it) })
    }

    private fun readyState(status: ModuleStatus, uptimeSeconds: Long?): ModuleState {
        val version = status.versionOrNull
        if (version != null && !ModuleVersion.isAtLeast(version, ModuleContract.MIN_VERSION)) {
            return ModuleState.Outdated(version)
        }
        return ModuleState.Ready(status, uptimeSeconds = uptimeSeconds)
    }

    /**
     * Everything the status screen polls for, in one root call: `json`, the process
     * start time and, while the proxy runs and [withServers] asks for it, `servers
     * active` and `servers live`. Anything unusual in the `json` part (missing module,
     * old build) falls back to [loadState].
     */
    suspend fun poll(withServers: Boolean = true): PolledStatus = withContext(Dispatchers.IO) {
        val out = shell.execLine(pollScript(withServers)).getOrNull()
            ?: return@withContext PolledStatus(loadState(), null, null)
        val sections = splitSections(out.stdout)
        val status = StatusParser.parse(sections[0].joinToString("\n")).getOrNull()
            ?: return@withContext PolledStatus(loadState(), null, null)
        val proc = sections.getOrElse(1) { emptyList() }
        PolledStatus(
            state = readyState(status, status.pid?.let { uptimeFrom(proc.getOrNull(0), proc.getOrNull(1)) }),
            activeServer = sections.getOrNull(2)?.joinToString("\n")?.trim()?.ifEmpty { null },
            liveJson = sections.getOrNull(3)?.joinToString("\n")?.trim()?.ifEmpty { null },
        )
    }

    private fun splitSections(lines: List<String>): List<List<String>> {
        val sections = mutableListOf(mutableListOf<String>())
        for (line in lines) {
            if (line == POLL_SEPARATOR) sections += mutableListOf<String>() else sections.last() += line
        }
        return sections
    }

    private suspend fun diagnoseMissing(): ModuleState {
        val probe = shell.execLine(
            "if [ -e ${RootShell.quote(ModuleContract.DISABLE_FLAG)} ]; then echo disabled; " +
                "elif [ -d ${RootShell.quote(ModuleContract.MODULE_DIR)} ]; then echo present; " +
                "else echo absent; fi"
        ).getOrNull()
        return when (probe?.out?.trim()) {
            "disabled" -> ModuleState.ModuleDisabled
            // Directory is there but the binary is not mounted: Magisk has not applied it.
            "present" -> ModuleState.ModuleDisabled
            else -> ModuleState.ModuleMissing
        }
    }

    private suspend fun ctlExists(): Boolean =
        shell.execLine("[ -x ${RootShell.quote(ModuleContract.CTL_PATH)} ]")
            .getOrNull()?.isSuccess == true

    /** Older builds print their version through `--version`; used only to label the warning. */
    private suspend fun readVersionFallback(): String? {
        val out = shell.exec(ModuleContract.CTL, "version").getOrNull() ?: return null
        return out.out.trim().takeIf { it.isNotEmpty() && it.length < 40 }
    }

    /**
     * Seconds since the proxy process was started, from `/proc`. `null` when the
     * process disappeared between the two reads.
     */
    private suspend fun readProcessUptime(pid: Int): Long? {
        val out = shell.execLine("cat /proc/uptime; cat /proc/$pid/stat").getOrNull() ?: return null
        if (!out.isSuccess || out.stdout.size < 2) return null
        return uptimeFrom(out.stdout[0], out.stdout[1])
    }

    /** Process age from a `/proc/uptime` line and the process's `/proc/<pid>/stat` line. */
    private fun uptimeFrom(uptimeLine: String?, statLine: String?): Long? {
        if (uptimeLine == null || statLine == null) return null
        val systemUptime = uptimeLine.trim().split(' ').firstOrNull()?.toDoubleOrNull() ?: return null
        // Field 22 of /proc/pid/stat is the start time in clock ticks. The process name
        // in field 2 can hold spaces, so cut everything up to the closing parenthesis.
        val afterComm = statLine.substringAfterLast(") ", missingDelimiterValue = "")
        val fields = afterComm.split(' ')
        val startTicks = fields.getOrNull(19)?.toDoubleOrNull() ?: return null
        val seconds = systemUptime - startTicks / CLOCK_TICKS_PER_SECOND
        return seconds.toLong().coerceAtLeast(0L)
    }

    // ------------------------------------------------------------- commands

    suspend fun start(): Result<ShellOutput> = ctl("start")

    suspend fun stop(): Result<ShellOutput> = ctl("stop")

    suspend fun restart(): Result<ShellOutput> = ctl("restart")

    /**
     * Restarts only a proxy that is running. `restart` on a stopped one would start it and
     * clear a manual stop, which must never happen behind the user's back (for instance
     * while box_for_magisk is the one in charge). Null when it was left alone.
     */
    suspend fun restartIfRunning(): Result<ShellOutput>? {
        val ready = loadState() as? ModuleState.Ready
        if (ready?.status?.running != true) return null
        return restart()
    }

    /**
     * Primary group the running proxy really has, from `/proc`. The module drops run_gid
     * and starts normally when `su -g` is not supported, and says so in its log. Null
     * when the proxy is not running.
     */
    suspend fun proxyGroup(): ProxyGroup? {
        val status = (loadState() as? ModuleState.Ready)?.status ?: return null
        val pid = status.pid?.takeIf { status.running } ?: return null
        val out = shell.execLine(
            "grep '^Gid:' /proc/$pid/status; " +
                "tail -n 200 ${RootShell.quote(ModuleContract.MODULE_LOG)} | grep -c '${ModuleContract.RUN_GID_FALLBACK_MARKER}'"
        ).getOrNull() ?: return null
        val gid = out.stdout.firstOrNull { it.startsWith("Gid:") }
            ?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull() ?: return null
        val fallbacks = out.stdout.lastOrNull()?.trim()?.toIntOrNull() ?: 0
        return ProxyGroup(gid, suRefusedGroup = fallbacks > 0)
    }

    suspend fun test(): Result<ShellOutput> = ctl("test")

    suspend fun rules(): Result<ShellOutput> = ctl("rules")

    suspend fun moduleLog(lines: Int): Result<ShellOutput> = ctl("log", lines.toString())

    suspend fun proxyLog(lines: Int): Result<ShellOutput> = ctl("proxylog", lines.toString())

    /**
     * Replaces the app's generated section of blocked-names.txt with [merged] (the
     * section goes away when the file is empty). Lines outside the markers, the user's
     * own, are kept as they are; `cat >` keeps the file's owner and mode.
     */
    suspend fun installBlocklists(merged: File): Result<Unit> {
        val file = RootShell.quote(ModuleContract.BLOCKED_NAMES)
        val tmp = RootShell.quote(ModuleContract.BLOCKED_NAMES + ".dnscm")
        val src = RootShell.quote(merged.absolutePath)
        val begin = RootShell.quote(ModuleContract.BLOCKLIST_BEGIN)
        val end = RootShell.quote(ModuleContract.BLOCKLIST_END)
        // toybox has sed but no awk. The markers hold no regex specials besides '.'.
        val range = RootShell.quote("/^${ModuleContract.BLOCKLIST_BEGIN_PREFIX}/,/^${ModuleContract.BLOCKLIST_END}\$/d")
        val out = shell.execLine(
            "touch $file && " +
                "sed $range $file > $tmp && " +
                "{ if [ -s $src ]; then echo $begin; cat $src; echo $end; fi; } >> $tmp && " +
                "cat $tmp > $file && rm -f $tmp"
        ).getOrElse { return Result.failure(it) }
        return if (out.isSuccess) Result.success(Unit) else Result.failure(ShellFailure(out))
    }

    /**
     * Appends one name to blocked-names.txt or allowed-names.txt unless it is already
     * there. False when it was there. The name must pass [BlocklistFormat.isDomain].
     */
    suspend fun addName(allowed: Boolean, name: String): Result<Boolean> {
        require(BlocklistFormat.isDomain(name)) { "Not a domain: $name" }
        val file = RootShell.quote(if (allowed) ModuleContract.ALLOWED_NAMES else ModuleContract.BLOCKED_NAMES)
        val quoted = RootShell.quote(name)
        val out = shell.execLine(
            "if grep -qxF $quoted $file 2>/dev/null; then echo present; else echo $quoted >> $file && echo added; fi"
        ).getOrElse { return Result.failure(it) }
        if (!out.isSuccess) return Result.failure(ShellFailure(out))
        return Result.success(out.out.trim() == "added")
    }

    /**
     * Reads a log by byte offset. With [from] null, or when the file shrank (rotated or
     * cleared), it returns the last [lines] lines and [LogChunk.reset]; otherwise only the
     * bytes written since [from]. The proxy log falls back to stdout.log like `proxylog`.
     * Fails when the file is missing, so callers can show the module's own message.
     */
    suspend fun logChunk(proxy: Boolean, from: Long?, lines: Int): Result<LogChunk> {
        val path = if (proxy) {
            "f=${RootShell.quote(ModuleContract.PROXY_LOG)}; [ -f \"\$f\" ] || f=${RootShell.quote(ModuleContract.STDOUT_LOG)}"
        } else {
            "f=${RootShell.quote(ModuleContract.MODULE_LOG)}"
        }
        val off = from ?: -1L
        val out = shell.execLine(
            "$path; s=\$(stat -c %s \"\$f\" 2>/dev/null) || exit 1; echo \$s; " +
                "if [ $off -lt 0 ] || [ \"\$s\" -lt $off ]; then echo R; tail -n $lines \"\$f\"; " +
                "else echo A; [ \"\$s\" -gt $off ] && tail -c +\$(($off + 1)) \"\$f\" | head -c \$((s - $off)); fi; true"
        ).getOrElse { return Result.failure(it) }
        if (!out.isSuccess || out.stdout.size < 2) return Result.failure(ShellFailure(out))
        val size = out.stdout[0].trim().toLongOrNull() ?: return Result.failure(ShellFailure(out))
        return Result.success(LogChunk(size, out.stdout.drop(2), reset = out.stdout[1] == "R"))
    }

    suspend fun exportConfig(): Result<ShellOutput> = ctl("export")

    suspend fun importConfig(): Result<ShellOutput> = ctl("import")

    /** Restores module.conf from the template shipped with the module. */
    suspend fun resetSettings(): Result<ShellOutput> = ctl("reset")

    /** Who else on the device is grabbing port 53. Shown verbatim, never restructured. */
    suspend fun conflicts(): Result<ShellOutput> = ctl("conflicts")

    // ------------------------------------------------------------- resolvers

    /** The whole public-resolvers.md cache, roughly 300 KB. */
    suspend fun serversRaw(): Result<ShellOutput> = ctl("servers", "raw")

    /** relays.md, the Anonymized DNSCrypt relays. */
    suspend fun serversRelays(): Result<ShellOutput> = ctl("servers", "relays")

    suspend fun serversInfo(): Result<ShellOutput> = ctl("servers", "info")

    /** Current value: comma-separated names, or `auto`. */
    suspend fun serversGet(): Result<ShellOutput> = ctl("servers", "get")

    /** Which server the proxy settled on, as one line from its log. */
    suspend fun serversActive(): Result<ShellOutput> = ctl("servers", "active")

    /** The servers of the current run as one JSON line (module 2.4.1+); rc 1 without a log. */
    suspend fun serversLive(): Result<ShellOutput> = ctl("servers", "live")

    suspend fun serversAuto(): Result<ShellOutput> = ctl("servers", "auto")

    /** disabled_server_names as a comma list; skipped even in automatic mode. */
    suspend fun serversExcluded(): Result<ShellOutput> = ctl("servers", "excluded")

    suspend fun serversExclude(names: Collection<String>): Result<ShellOutput> = editExcluded("exclude", names)

    suspend fun serversInclude(names: Collection<String>): Result<ShellOutput> = editExcluded("include", names)

    private suspend fun editExcluded(verb: String, names: Collection<String>): Result<ShellOutput> {
        val invalid = names.firstOrNull { !ServerSelection.isValidName(it) }
        if (invalid != null) return Result.failure(IllegalArgumentException("Invalid server name: $invalid"))
        if (names.isEmpty()) return Result.success(ShellOutput(0, emptyList(), emptyList()))
        return ctl("servers", verb, names.joinToString(","))
    }

    /**
     * Writes the whole selection in one call. Doing this instead of a run of add/remove
     * keeps it to a single config backup and leaves no room for races between taps.
     */
    suspend fun serversSet(names: List<String>): Result<ShellOutput> {
        val argument = ServerSelection.buildSetArgument(names)
            .getOrElse { return Result.failure(it) }
        return ctl("servers", "set", argument)
    }

    /** For changing exactly one entry; the screen uses [serversSet] instead. */
    suspend fun serversAdd(name: String): Result<ShellOutput> {
        if (!ServerSelection.isValidName(name)) {
            return Result.failure(IllegalArgumentException("Invalid server name: $name"))
        }
        return ctl("servers", "add", name)
    }

    suspend fun serversRemove(name: String): Result<ShellOutput> {
        if (!ServerSelection.isValidName(name)) {
            return Result.failure(IllegalArgumentException("Invalid server name: $name"))
        }
        return ctl("servers", "remove", name)
    }

    /**
     * Modification time of the resolver cache, in epoch seconds. Read from the file
     * itself rather than parsed out of `servers info`, whose wording may change.
     */
    suspend fun resolverCacheTimestamp(): Long? {
        val out = shell.execLine(
            "stat -c %Y ${RootShell.quote(ModuleContract.RESOLVERS_CACHE)} 2>/dev/null"
        ).getOrNull() ?: return null
        return out.out.trim().toLongOrNull()
    }

    private suspend fun ctl(vararg args: String): Result<ShellOutput> =
        shell.exec(ModuleContract.CTL, *args)

    /**
     * Applies one module setting. The key is checked against the allow-list and the
     * value against its type before anything is quoted into a command.
     */
    suspend fun setOption(key: String, value: String): Result<ShellOutput> {
        val validated = ModuleSettings.validate(key, value).getOrElse { return Result.failure(it) }
        return shell.exec(ModuleContract.CTL, "set", validated.first, validated.second)
    }

    // ------------------------------------------------------ module.conf

    suspend fun readModuleConf(): Result<Map<String, String>> {
        val out = shell.execLine("cat ${RootShell.quote(ModuleContract.MODULE_CONF)}")
            .getOrElse { return Result.failure(it) }
        if (!out.isSuccess) return Result.failure(ShellFailure(out))
        return Result.success(ModuleSettings.parseConf(out.out))
    }

    // ---------------------------------------------------------- config file

    suspend fun readConfig(path: String = ModuleContract.CONFIG_PATH): Result<String> {
        val out = shell.execLine("cat ${RootShell.quote(path)}")
            .getOrElse { return Result.failure(it) }
        if (!out.isSuccess) return Result.failure(ShellFailure(out))
        return Result.success(out.out)
    }

    /** Copies the live config next to itself as `dnscrypt-proxy.toml-<date>.bak`. */
    suspend fun createBackup(): Result<String> {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = "${ModuleContract.CONFIG_PATH}-$stamp.bak"
        val out = shell.execLine(
            "cp -a ${RootShell.quote(ModuleContract.CONFIG_PATH)} ${RootShell.quote(target)}"
        ).getOrElse { return Result.failure(it) }
        if (!out.isSuccess) return Result.failure(ShellFailure(out))
        return Result.success(target)
    }

    /**
     * Writes the config through a temporary file in the app cache, so no config text
     * ever travels inside a shell command line. A backup is always taken first.
     */
    suspend fun writeConfig(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        ConfigValidation.validate(text).getOrElse { return@withContext Result.failure(it) }
        createBackup().getOrElse { return@withContext Result.failure(it) }
        val temp = File(cacheDir, "config-staging.toml")
        runCatching { temp.writeText(text) }.getOrElse { return@withContext Result.failure(it) }
        val out = shell.execLine(
            "cat ${RootShell.quote(temp.absolutePath)} > ${RootShell.quote(ModuleContract.CONFIG_PATH)}"
        ).getOrElse {
            temp.delete()
            return@withContext Result.failure(it)
        }
        temp.delete()
        if (!out.isSuccess) Result.failure(ShellFailure(out)) else Result.success(Unit)
    }

    suspend fun listBackups(): Result<List<ConfigBackup>> {
        val out = shell.execLine("ls -1 ${RootShell.quote(ModuleContract.DATA_DIR)}")
            .getOrElse { return Result.failure(it) }
        if (!out.isSuccess) return Result.failure(ShellFailure(out))
        val backups = out.stdout
            .map { it.trim() }
            .filter { name ->
                name.startsWith(ModuleContract.CONFIG_NAME) && name != ModuleContract.CONFIG_NAME
            }
            .map { name ->
                ConfigBackup(
                    name = name,
                    path = "${ModuleContract.DATA_DIR}/$name",
                    isPending = name.endsWith(".new"),
                )
            }
            .sortedByDescending { it.name }
        return Result.success(backups)
    }

    /** Restores a backup, after checking that the name really is one we listed. */
    suspend fun restoreBackup(backup: ConfigBackup): Result<Unit> {
        val known = listBackups().getOrElse { return Result.failure(it) }
        if (known.none { it.path == backup.path }) {
            return Result.failure(IllegalArgumentException("Unknown backup: ${backup.name}"))
        }
        createBackup().getOrElse { return Result.failure(it) }
        val out = shell.execLine(
            "cat ${RootShell.quote(backup.path)} > ${RootShell.quote(ModuleContract.CONFIG_PATH)}"
        ).getOrElse { return Result.failure(it) }
        return if (out.isSuccess) Result.success(Unit) else Result.failure(ShellFailure(out))
    }

    private companion object {
        const val CLOCK_TICKS_PER_SECOND = 100.0

        const val POLL_SEPARATOR = "@@dnscm-poll@@"

        /**
         * The pid comes out of the json by parameter expansion and /proc is read with the
         * `read` builtin, so the only forks are the module's own scripts. The servers part
         * (`active` + `live`, about as costly as `json` itself) runs only when asked for.
         */
        fun pollScript(withServers: Boolean): String {
            val servers = if (withServers) {
                "echo $POLL_SEPARATOR; ${ModuleContract.CTL} servers active 2>/dev/null; " +
                    "echo $POLL_SEPARATOR; ${ModuleContract.CTL} servers live 2>/dev/null"
            } else {
                ""
            }
            return """
                j=$(${ModuleContract.CTL} json 2>/dev/null); echo "${'$'}j"; echo $POLL_SEPARATOR
                case "${'$'}j" in *'"running":true'*)
                  p=${'$'}{j#*'"pid":'}; p=${'$'}{p%%[!0-9]*}
                  read u < /proc/uptime; echo "${'$'}u"
                  [ -n "${'$'}p" ] && read s < /proc/${'$'}p/stat 2>/dev/null && echo "${'$'}s"
                  $servers ;;
                esac
            """.trimIndent()
        }
    }
}

/** Carries the module output so the UI can print stderr verbatim. */
class ShellFailure(val output: ShellOutput) : Exception(
    output.message.ifBlank { "Command failed with exit code ${output.code}" }
)

object ConfigValidation {

    const val REQUIRED_MARKER = "listen_addresses"

    fun validate(text: String): Result<Unit> = when {
        text.isBlank() -> Result.failure(IllegalArgumentException("EMPTY"))
        !text.contains(REQUIRED_MARKER) -> Result.failure(IllegalArgumentException("NO_LISTEN"))
        else -> Result.success(Unit)
    }
}
