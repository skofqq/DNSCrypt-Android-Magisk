package com.skofqq.shieldy.data

import android.content.Context
import android.net.Uri
import com.skofqq.shieldy.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What [SettingsBackup] writes first into the archive and checks on import. */
@Serializable
data class BackupManifest(
    val format: Int = 0,
    val app: String = "",
    val appVersion: String = "",
    val moduleVersion: String = "",
    val created: Long = 0L,
    val files: List<String> = emptyList(),
)

/** What an import changed, for the message afterwards. */
data class ImportResult(val files: Int, val blocklists: Boolean, val enabledBlocklists: Int)

class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_A_BACKUP, NEWER_FORMAT, BAD_CONFIG, TOO_LARGE, WRITE_FAILED }
}

/**
 * Settings backup as one zip: module.conf, dnscrypt-proxy.toml, the user's name and IP
 * lists, the proxy profile, and Shieldy's blocklist subscriptions. The blocklist
 * section the app generates inside blocked-names.txt is left out: it is downloaded again.
 */
class SettingsBackup(
    private val context: Context,
    private val shell: RootShell,
    private val module: ModuleRepository,
    private val blocklists: BlocklistRepository,
) {

    suspend fun export(target: Uri, moduleVersion: String): Result<Int> = withContext(Dispatchers.IO) {
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        runCatching {
            val contents = LinkedHashMap<String, String>()
            for (name in MODULE_FILES) {
                readModuleFile(name)?.let { contents[name] = it }
            }
            val manifest = BackupManifest(
                format = FORMAT,
                app = APP,
                appVersion = appVersion,
                moduleVersion = moduleVersion,
                created = System.currentTimeMillis(),
                files = contents.keys.toList(),
            )
            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: throw BackupException(BackupException.Reason.WRITE_FAILED)
            ZipOutputStream(stream).use { zip ->
                zip.putText(MANIFEST, JSON.encodeToString(BackupManifest.serializer(), manifest))
                contents.forEach { (name, text) -> zip.putText("$MODULE_DIR/$name", text) }
                zip.putText(BLOCKLISTS, blocklists.exportJson())
            }
            contents.size
        }
    }

    suspend fun import(source: Uri): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = context.contentResolver.openInputStream(source)?.use { readZip(it) }
                ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            val manifest = entries[MANIFEST]?.let {
                runCatching { JSON.decodeFromString(BackupManifest.serializer(), it) }.getOrNull()
            }
            if (manifest == null || manifest.app != APP) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            if (manifest.format > FORMAT) throw BackupException(BackupException.Reason.NEWER_FORMAT)

            val files = MODULE_FILES.mapNotNull { name -> entries["$MODULE_DIR/$name"]?.let { name to it } }.toMap()
            files[ModuleContract.CONFIG_NAME]?.let { config ->
                if (ConfigValidation.validate(config).isFailure) throw BackupException(BackupException.Reason.BAD_CONFIG)
            }
            // The live config gets the usual dated backup before it is replaced.
            if (files.containsKey(ModuleContract.CONFIG_NAME)) module.createBackup().getOrThrow()
            for ((name, text) in files) writeModuleFile(name, text)
            // A backup made on the custom profile has no profile files: the device's own
            // would otherwise claim a profile the imported config does not have.
            if (files.containsKey(ModuleContract.CONFIG_NAME)) {
                val stale = PROFILE_FILES.filterNot { files.containsKey(it) }
                if (stale.isNotEmpty()) {
                    shell.execLine("rm -f " + stale.joinToString(" ") { RootShell.quote("${ModuleContract.DATA_DIR}/$it") })
                }
            }

            val imported = entries[BLOCKLISTS]?.let { blocklists.importJson(it) }
            ImportResult(files.size, imported != null, imported?.enabledCount ?: 0)
        }
    }

    /** One file of the data directory as text; null when it does not exist. */
    private suspend fun readModuleFile(name: String): String? {
        val path = RootShell.quote("${ModuleContract.DATA_DIR}/$name")
        val read = if (name == BLOCKED_NAMES) {
            // The generated blocklist section is dropped: it is big and gets downloaded again.
            val range = RootShell.quote("/^${ModuleContract.BLOCKLIST_BEGIN_PREFIX}/,/^${ModuleContract.BLOCKLIST_END}\$/d")
            "sed $range $path"
        } else {
            "cat $path"
        }
        val out = shell.execLine("if [ -f $path ]; then echo Y; $read; else echo N; fi").getOrThrow()
        if (!out.isSuccess) throw ShellFailure(out)
        if (out.stdout.firstOrNull() != "Y") return null
        return out.stdout.drop(1).joinToString("\n", postfix = "\n")
    }

    /**
     * Writes one file through a staging copy in the app cache, so no file content
     * travels inside a command line; `cat >` keeps the target's owner and mode. In
     * blocked-names.txt the generated blocklist section of this device is kept.
     */
    private suspend fun writeModuleFile(name: String, text: String) {
        val staging = File(context.cacheDir, "import-$name")
        staging.writeText(text)
        try {
            val src = RootShell.quote(staging.absolutePath)
            val path = RootShell.quote("${ModuleContract.DATA_DIR}/$name")
            val command = if (name == BLOCKED_NAMES) {
                val tmp = RootShell.quote("${ModuleContract.DATA_DIR}/$name.import")
                val keep = RootShell.quote("/^${ModuleContract.BLOCKLIST_BEGIN_PREFIX}/,/^${ModuleContract.BLOCKLIST_END}\$/p")
                "touch $path && { cat $src; sed -n $keep $path; } > $tmp && cat $tmp > $path && rm -f $tmp"
            } else {
                "cat $src > $path && chmod 0600 $path"
            }
            val out = shell.execLine(command).getOrThrow()
            if (!out.isSuccess) throw BackupException(BackupException.Reason.WRITE_FAILED)
        } finally {
            staging.delete()
        }
    }

    private fun readZip(input: InputStream): Map<String, String> {
        val result = HashMap<String, String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name !in KNOWN_ENTRIES) continue
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val read = zip.read(chunk)
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                    total += read
                    if (buffer.size() > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        throw BackupException(BackupException.Reason.TOO_LARGE)
                    }
                }
                result[entry.name] = buffer.toString(Charsets.UTF_8.name())
            }
        }
        if (result.isEmpty()) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        return result
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    companion object {
        const val FORMAT = 1
        private val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        private val PROFILE_FILES = listOf(".profile", "profile-custom.toml")
        const val APP = "shieldy"
        private const val MANIFEST = "manifest.json"
        private const val MODULE_DIR = "module"
        private const val BLOCKLISTS = "app/blocklists.json"
        private const val BLOCKED_NAMES = "blocked-names.txt"
        private const val MAX_ENTRY_BYTES = 4 * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 16L * 1024 * 1024

        /** Files of /data/adb/dnscrypt-proxy that belong to the user's setup. */
        val MODULE_FILES = listOf(
            "module.conf",
            ModuleContract.CONFIG_NAME,
            "allowed-names.txt",
            BLOCKED_NAMES,
            "allowed-ips.txt",
            "blocked-ips.txt",
            "exclude-ips.txt",
            "cloaking-rules.txt",
            "forwarding-rules.txt",
            ".profile",
            "profile-custom.toml",
        )

        private val KNOWN_ENTRIES: Set<String> =
            (MODULE_FILES.map { "$MODULE_DIR/$it" } + MANIFEST + BLOCKLISTS).toSet()

        /** File name offered in the system's save dialog. */
        fun fileName(stamp: String): String = "shieldy-backup-$stamp.zip"
    }
}
