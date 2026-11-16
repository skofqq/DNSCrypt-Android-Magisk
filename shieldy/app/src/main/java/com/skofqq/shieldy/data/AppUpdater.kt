package com.skofqq.shieldy.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.skofqq.shieldy.root.NoRootException
import com.skofqq.shieldy.root.RootShell
import com.skofqq.shieldy.root.ShellTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The newest Shieldy APK attached to a GitHub release of the project. */
data class AppRelease(
    val version: String,
    val apkUrl: String,
    val size: Long?,
    val pageUrl: String,
)

/**
 * Updates of Shieldy itself. Module and app are released together, so the APK of the
 * latest release is the one to have. It is checked against the installed app (same
 * package, newer version, same signing certificate) and installed with root `pm`.
 */
class AppUpdater(
    private val context: Context,
    private val shell: RootShell,
) {

    /** The installed app's version name, as Gradle wrote it into the manifest. */
    val installedVersion: String
        get() = installedPackage()?.versionName.orEmpty()

    suspend fun latest(): Result<AppRelease> = withContext(Dispatchers.IO) {
        val body = runCatching { httpGet(ModuleContract.LATEST_RELEASE_API) }
            .getOrElse { return@withContext Result.failure(it.toUpdateException()) }
        parseRelease(body)?.let { Result.success(it) }
            ?: Result.failure(UpdateException(UpdateError.BadUpdateJson))
    }

    fun isNewer(release: AppRelease): Boolean =
        installedVersion.isNotEmpty() && ModuleVersion.compare(release.version, installedVersion) > 0

    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            val target = File(context.cacheDir, APK_NAME)
            runCatching {
                val connection = open(release.apkUrl)
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw UpdateException(UpdateError.Http(code))
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size ?: -1L
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
     * Refuses an APK that is not Shieldy, is not newer than the installed build, or is
     * signed with another key: Android would reject the last one anyway, after the
     * download, with a message nobody understands.
     */
    suspend fun verify(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val archive = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: throw UpdateException(UpdateError.BadPackage)
            if (archive.packageName != context.packageName) {
                throw UpdateException(UpdateError.WrongModule(archive.packageName))
            }
            val installed = installedPackage() ?: throw UpdateException(UpdateError.BadPackage)
            if (archive.longVersionCode <= installed.longVersionCode) throw UpdateException(UpdateError.NotNewer)
            if (signers(archive) != signers(installed) || signers(archive).isEmpty()) {
                throw UpdateException(UpdateError.WrongSignature)
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
     * Installs the APK with root `pm install`, from /data/local/tmp because the package
     * manager cannot read the app's private cache. On success Android stops this
     * process to replace it, so the call may never return; the new version starts fresh.
     */
    suspend fun install(file: File): Result<Unit> {
        val staged = STAGED_APK
        val command = "( A=${RootShell.quote(staged)}; " +
            "cat ${RootShell.quote(file.absolutePath)} > \"\$A\" && chmod 0644 \"\$A\" && " +
            "pm install -r -i ${RootShell.quote(context.packageName)} \"\$A\"; rc=\$?; rm -f \"\$A\"; exit \$rc )"
        val out = shell.execStreaming(command, INSTALL_TIMEOUT_MS) {}
            .getOrElse {
                file.delete()
                return Result.failure(it.toUpdateException())
            }
        file.delete()
        return if (out.isSuccess) Result.success(Unit) else Result.failure(UpdateException(UpdateError.InstallFailed(out.code)))
    }

    private fun installedPackage(): PackageInfo? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    }.getOrNull()

    private fun signers(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs.orEmpty().map { it.toCharsString() }.toSet()
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Not an https URL: $url" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Shieldy")
            setRequestProperty("Accept", "application/vnd.github+json")
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

    companion object {
        private const val APK_NAME = "shieldy-update.apk"
        private const val STAGED_APK = "/data/local/tmp/shieldy-update.apk"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val INSTALL_TIMEOUT_MS = 120_000L

        private val APK_ASSET = Regex("""^Shieldy-[0-9][0-9A-Za-z.\-]*\.apk$""")

        /**
         * Reads GitHub's release JSON: the tag is the version, and the asset is the signed
         * `Shieldy-<version>.apk` (never one marked unsigned). Null when there is none.
         */
        fun parseRelease(body: String): AppRelease? = runCatching {
            val obj = StatusParser.json.parseToJsonElement(body).jsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull?.trim()?.removePrefix("v")
            if (tag.isNullOrEmpty()) return null
            val page = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: ModuleContract.RELEASES_URL
            val asset = obj["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                APK_ASSET.matches(name) && !name.contains("unsigned")
            } ?: return null
            val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull
            if (url == null || !url.startsWith("https://")) return null
            AppRelease(
                version = tag,
                apkUrl = url,
                size = asset["size"]?.jsonPrimitive?.longOrNull,
                pageUrl = page,
            )
        }.getOrNull()

        private fun Throwable.toUpdateException(): UpdateException = when (this) {
            is UpdateException -> this
            is NoRootException -> UpdateException(UpdateError.NoRoot)
            is ShellTimeoutException -> UpdateException(UpdateError.Timeout)
            is IOException -> UpdateException(UpdateError.NoNetwork)
            else -> UpdateException(UpdateError.Other(message ?: javaClass.simpleName))
        }
    }
}
