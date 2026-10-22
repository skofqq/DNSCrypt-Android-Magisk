package io.github.skofqq.dnscryptmanager.data

import android.os.Build
import io.github.skofqq.dnscryptmanager.root.NoRootException
import io.github.skofqq.dnscryptmanager.root.RootShell
import io.github.skofqq.dnscryptmanager.root.ShellTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.ZipFile

/** What module.prop of the installed module says. */
data class InstalledModule(
    val version: String,
    val versionCode: Long,
    val updateJson: String,
)

/** One entry of the module's update.json, the same file Magisk itself reads. */
data class ModuleRelease(
    val version: String,
    val versionCode: Long,
    val zipUrl: String,
    val changelogUrl: String?,
)

/** Why a check or an install did not go through. The UI turns each into a sentence. */
sealed interface UpdateError {
    data object NoNetwork : UpdateError
    data class Http(val code: Int) : UpdateError
    data object BadUpdateJson : UpdateError
    data object NoRoot : UpdateError
    data object NotInstalled : UpdateError
    data object BadPackage : UpdateError
    data class WrongModule(val id: String?) : UpdateError
    data class MissingBinary(val arch: String) : UpdateError
    data object NoRootManager : UpdateError
    data class InstallFailed(val code: Int) : UpdateError
    data object Timeout : UpdateError
    data class Other(val message: String) : UpdateError
}

class UpdateException(val error: UpdateError) : Exception(error.toString())

/**
 * Module updates from inside the app: reads the installed version, asks update.json
 * for the latest one, downloads the zip, checks it and hands it to the root manager.
 */
class ModuleUpdater(
    private val shell: RootShell,
    private val cacheDir: File,
) {

    suspend fun installed(): Result<InstalledModule> {
        val out = shell.execLine("cat ${RootShell.quote(MODULE_PROP)}")
            .getOrElse { return Result.failure(it.toUpdateException()) }
        if (!out.isSuccess) return Result.failure(UpdateException(UpdateError.NotInstalled))
        val props = parseProps(out.out)
        val version = props["version"] ?: return Result.failure(UpdateException(UpdateError.NotInstalled))
        return Result.success(
            InstalledModule(
                version = version,
                versionCode = props["versionCode"]?.toLongOrNull() ?: 0L,
                // Older module.prop files may lack it; the project's file is the same one.
                updateJson = props["updateJson"]?.takeIf { it.startsWith("https://") }
                    ?: ModuleContract.UPDATE_JSON_URL,
            )
        )
    }

    suspend fun latest(updateJsonUrl: String): Result<ModuleRelease> = withContext(Dispatchers.IO) {
        val body = runCatching { httpGet(updateJsonUrl) }
            .getOrElse { return@withContext Result.failure(it.toUpdateException()) }
        parseUpdateJson(body)?.let { Result.success(it) }
            ?: Result.failure(UpdateException(UpdateError.BadUpdateJson))
    }

    /**
     * The changelog sections newer than [currentVersion]. Null when there is no
     * changelog or it cannot be fetched: it is a nice-to-have, never a reason to fail.
     */
    suspend fun changelog(url: String?, currentVersion: String): String? = withContext(Dispatchers.IO) {
        if (url == null) return@withContext null
        runCatching { httpGet(url) }.getOrNull()?.let { newerSections(it, currentVersion) }
    }

    /** Downloads [release] into the app cache. [onProgress] gets 0..1, or -1 when the size is unknown. */
    suspend fun download(release: ModuleRelease, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            val target = File(cacheDir, UPDATE_ZIP_NAME)
            runCatching {
                val connection = open(release.zipUrl)
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw UpdateException(UpdateError.Http(code))
                    val total = connection.contentLengthLong
                    var done = 0L
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                onProgress(if (total > 0) done.toFloat() / total else -1f)
                            }
                        }
                    }
                    if (done == 0L) throw UpdateException(UpdateError.BadPackage)
                    target
                } finally {
                    connection.disconnect()
                }
            }.onFailure { target.delete() }
                .fold({ Result.success(it) }, { Result.failure(it.toUpdateException()) })
        }

    /**
     * Refuses a zip that is not this module or has no binary for this device. The 2.4
     * release shipped arm64 only, and Magisk aborted half-way on x86_64 and arm.
     */
    suspend fun verify(file: File, abi: String = primaryAbi()): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ZipFile(file).use { zip ->
                val propEntry = zip.getEntry("module.prop") ?: throw UpdateException(UpdateError.BadPackage)
                val props = zip.getInputStream(propEntry).bufferedReader().use { parseProps(it.readText()) }
                val id = props["id"]
                if (id != ModuleContract.MODULE_ID) throw UpdateException(UpdateError.WrongModule(id))
                val arch = binaryArch(abi)
                if (arch != null && zip.getEntry("binary/dnscrypt-proxy-$arch") == null) {
                    throw UpdateException(UpdateError.MissingBinary(arch))
                }
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = {
                file.delete()
                Result.failure(if (it is UpdateException) it else UpdateException(UpdateError.BadPackage))
            },
        )
    }

    /**
     * Installs the verified zip with whichever root manager is present. The zip is
     * staged in /data/local/tmp first: KernelSU and APatch cannot always read the
     * app's private cache. Every installer line goes to [onLine] as it is printed.
     */
    suspend fun install(file: File, onLine: (String) -> Unit): Result<Unit> {
        val manager = detectManager().getOrElse { return Result.failure(it) }
        val staged = STAGED_ZIP
        val command = "( Z=${RootShell.quote(staged)}; " +
            "cat ${RootShell.quote(file.absolutePath)} > \"\$Z\" && chmod 0644 \"\$Z\" && " +
            "${manager.command} \"\$Z\"; rc=\$?; rm -f \"\$Z\"; exit \$rc )"
        val out = shell.execStreaming(command, INSTALL_TIMEOUT_MS, onLine)
            .getOrElse {
                file.delete()
                return Result.failure(it.toUpdateException())
            }
        file.delete()
        return if (out.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(UpdateException(UpdateError.InstallFailed(out.code)))
        }
    }

    suspend fun reboot(): Result<Unit> {
        val out = shell.execLine("/system/bin/svc power reboot || /system/bin/reboot")
            .getOrElse { return Result.failure(it.toUpdateException()) }
        return if (out.isSuccess) Result.success(Unit) else Result.failure(UpdateException(UpdateError.Other(out.message)))
    }

    private suspend fun detectManager(): Result<RootManager> {
        val out = shell.execLine(
            "if command -v magisk >/dev/null 2>&1; then echo magisk; " +
                "elif [ -x /data/adb/ksud ]; then echo ksu; " +
                "elif [ -x /data/adb/apd ]; then echo apatch; " +
                "else echo none; fi"
        ).getOrElse { return Result.failure(it.toUpdateException()) }
        return when (out.out.trim()) {
            "magisk" -> Result.success(RootManager.MAGISK)
            "ksu" -> Result.success(RootManager.KERNELSU)
            "apatch" -> Result.success(RootManager.APATCH)
            else -> Result.failure(UpdateException(UpdateError.NoRootManager))
        }
    }

    private fun open(url: String): HttpURLConnection {
        // update.json is written by the module author, but nothing in it may downgrade to plain HTTP.
        require(url.startsWith("https://")) { "Not an https URL: $url" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
    }

    private fun httpGet(url: String): String {
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw UpdateException(UpdateError.Http(code))
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private enum class RootManager(val command: String) {
        MAGISK("magisk --install-module"),
        KERNELSU("/data/adb/ksud module install"),
        APATCH("/data/adb/apd module install"),
    }

    companion object {
        const val MODULE_PROP = "${ModuleContract.MODULE_DIR}/module.prop"
        private const val UPDATE_ZIP_NAME = "module-update.zip"
        private const val STAGED_ZIP = "/data/local/tmp/dnscrypt-proxy-android-update.zip"
        private const val USER_AGENT = "DNSCryptManager"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val INSTALL_TIMEOUT_MS = 180_000L
        private const val CHANGELOG_MAX_CHARS = 6_000

        fun parseProps(text: String): Map<String, String> = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

        fun parseUpdateJson(body: String): ModuleRelease? = runCatching {
            val obj: JsonObject = StatusParser.json.parseToJsonElement(body).jsonObject
            val version = obj["version"]?.jsonPrimitive?.contentOrNull?.trim()
            val code = obj["versionCode"]?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
            val zip = obj["zipUrl"]?.jsonPrimitive?.contentOrNull?.trim()
            if (version.isNullOrEmpty() || code == null || zip.isNullOrEmpty() || !zip.startsWith("https://")) {
                return null
            }
            ModuleRelease(
                version = version,
                versionCode = code,
                zipUrl = zip,
                changelogUrl = obj["changelog"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf { it.startsWith("https://") },
            )
        }.getOrNull()

        /**
         * CHANGELOG.md starts with the newest `# <version>` heading. Everything above the
         * heading of the installed version is what the update brings.
         */
        fun newerSections(changelog: String, currentVersion: String): String? {
            val lines = changelog.lines()
            val stop = lines.indexOfFirst { it.trim() == "# ${currentVersion.trim()}" }
            val newer = if (stop >= 0) lines.take(stop) else lines
            val text = newer.joinToString("\n").trim()
            if (text.isEmpty()) return null
            return if (text.length > CHANGELOG_MAX_CHARS) text.take(CHANGELOG_MAX_CHARS).trimEnd() + "\n…" else text
        }

        /** The device's preferred ABI, which decides the binary customize.sh picks. */
        fun primaryAbi(): String = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()

        /** Binary suffix inside the module zip for an Android ABI; null when unknown. */
        fun binaryArch(abi: String): String? = when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a", "armeabi" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "i386"
            else -> null
        }

        private fun Throwable.toUpdateException(): UpdateException = when (this) {
            is UpdateException -> this
            is NoRootException -> UpdateException(UpdateError.NoRoot)
            is ShellTimeoutException -> UpdateException(UpdateError.Timeout)
            is UnknownHostException -> UpdateException(UpdateError.NoNetwork)
            is IOException -> UpdateException(UpdateError.NoNetwork)
            else -> UpdateException(UpdateError.Other(message ?: javaClass.simpleName))
        }
    }
}
