// Release build of the whole product: the signed Shieldy APK and the module zip, two
// separate files. `./gradlew release` checks the APK, runs build.sh and puts both
// with their SHA-256 sums into out/release/.
//
//   -Pshieldy.skipVersionCheck=true  skip the versionCode check (offline, or a rebuild)
//   -Pmodule.withX86=true            add the x86 binaries (local emulator tests only)
import groovy.json.JsonSlurper
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

val shieldy = gradle.includedBuild("shieldy")
val apkDir = file("shieldy/app/build/outputs/apk/release")
val outDir = file("out/release")
val windows = System.getProperty("os.name").startsWith("Windows")

data class ApkInfo(val code: Int, val name: String)

fun sdkDir(): File {
    val props = Properties()
    file("shieldy/local.properties").takeIf { it.exists() }?.inputStream()?.use(props::load)
    val path = props.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: throw GradleException("Android SDK not found: set sdk.dir in shieldy/local.properties or ANDROID_HOME")
    return file(path)
}

/** apksigner or aapt2 from the newest build-tools. */
fun buildTool(name: String): String {
    val version = { dir: File -> dir.name.split('.', '-').map { it.toIntOrNull() ?: 0 } }
    val dir = File(sdkDir(), "build-tools").listFiles { f -> f.isDirectory }
        ?.maxWithOrNull { a, b ->
            version(a).zip(version(b)).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0
        } ?: throw GradleException("No build-tools in ${sdkDir()}")
    val exe = File(dir, if (!windows) name else if (name == "aapt2") "aapt2.exe" else "$name.bat")
    if (!exe.exists()) throw GradleException("$exe not found")
    return exe.path
}

fun run(command: List<String>, env: Map<String, String> = emptyMap()): String {
    val full = if (windows && command[0].endsWith(".bat")) listOf("cmd", "/c") + command else command
    val process = ProcessBuilder(full).directory(projectDir).redirectErrorStream(true)
        .apply { environment().putAll(env) }.start()
    val output = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) throw GradleException("${File(command[0]).name} failed:\n$output")
    return output
}

fun apkInfo(apk: File): ApkInfo {
    val line = run(listOf(buildTool("aapt2"), "dump", "badging", apk.path))
        .lineSequence().first { it.startsWith("package:") }
    val code = Regex("versionCode='(\\d+)'").find(line)?.groupValues?.get(1)?.toInt()
        ?: throw GradleException("No versionCode in $apk")
    return ApkInfo(code, Regex("versionName='([^']*)'").find(line)?.groupValues?.get(1).orEmpty())
}

/** The Shieldy APK of the newest GitHub release that has one, downloaded to build/previous. */
fun previousReleaseApk(): File? {
    val repo = providers.gradleProperty("shieldy.releaseRepo").get()
    val connection = URI("https://api.github.com/repos/$repo/releases?per_page=30").toURL().openConnection().apply {
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "shieldy-release-check")
        System.getenv("GITHUB_TOKEN")?.let { setRequestProperty("Authorization", "Bearer $it") }
    }
    @Suppress("UNCHECKED_CAST")
    val releases = JsonSlurper().parseText(connection.getInputStream().bufferedReader().readText()) as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val asset = releases.asSequence()
        .filter { it["draft"] != true }
        .flatMap { (it["assets"] as List<Map<String, Any?>>).asSequence() }
        .firstOrNull { Regex("Shieldy-.+\\.apk").matches(it["name"] as String) } ?: return null
    val dest = layout.buildDirectory.file("previous/${asset["name"]}").get().asFile
    dest.parentFile.mkdirs()
    URI(asset["browser_download_url"] as String).toURL().openStream().use { input ->
        dest.outputStream().use { input.copyTo(it) }
    }
    return dest
}

/**
 * The APK the last assembleRelease wrote, by AGP's output-metadata.json: without
 * keystore.properties it is app-release-unsigned.apk, and an older signed
 * app-release.apk may still be lying next to it.
 */
fun releaseApk(): File {
    val meta = File(apkDir, "output-metadata.json")
    if (!meta.exists()) throw GradleException("$meta not found: was the release APK built?")
    @Suppress("UNCHECKED_CAST")
    val elements = (JsonSlurper().parse(meta) as Map<String, Any?>)["elements"] as List<Map<String, Any?>>
    return File(apkDir, elements.single()["outputFile"] as String)
}

fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

val checkShieldyApk = tasks.register("checkShieldyApk") {
    group = "release"
    description = "Builds the release APK and fails unless it is signed with the release key, " +
        "carries no x86 libraries and has a higher versionCode than the last released APK."
    dependsOn(shieldy.task(":app:assembleRelease"))
    doLast {
        val apk = releaseApk()
        if (!apk.exists()) throw GradleException("$apk was not built")

        // Unsigned (no keystore.properties) or signed with another key: an update
        // would fail on every device that has the released app.
        val expected = providers.gradleProperty("shieldy.signingCertSha256").get().lowercase()
        val certs = try {
            Regex("certificate SHA-256 digest: ([0-9a-f]+)")
                .findAll(run(listOf(buildTool("apksigner"), "verify", "--print-certs", apk.path)))
                .map { it.groupValues[1] }.toSet()
        } catch (e: GradleException) {
            throw GradleException("The APK is not signed. Is shieldy/keystore.properties in place?\n${e.message}")
        }
        if (certs != setOf(expected)) {
            throw GradleException("The APK is signed with $certs, expected $expected")
        }

        val x86 = ZipFile(apk).use { zip ->
            zip.entries().asSequence().map { it.name }
                .filter { it.startsWith("lib/x86/") || it.startsWith("lib/x86_64/") }.toList()
        }
        if (x86.isNotEmpty()) throw GradleException("x86 libraries in the APK: $x86")

        val info = apkInfo(apk)
        if (providers.gradleProperty("shieldy.skipVersionCheck").orNull == "true") {
            logger.warn("versionCode check skipped")
        } else {
            val previous = try {
                previousReleaseApk()
            } catch (e: Exception) {
                throw GradleException(
                    "Could not fetch the last released APK (${e.message}); " +
                        "rerun with -Pshieldy.skipVersionCheck=true to build anyway",
                )
            }
            if (previous != null) {
                val last = apkInfo(previous)
                if (info.code <= last.code) {
                    throw GradleException(
                        "versionCode ${info.code} (${info.name}) is not greater than ${last.code} " +
                            "in ${previous.name}: raise it in shieldy/app/build.gradle.kts",
                    )
                }
            }
        }
        logger.lifecycle("Shieldy ${info.name} (versionCode ${info.code}): signed, ARM only")
    }
}

tasks.register("release") {
    group = "release"
    description = "Signed Shieldy APK and the module zip, side by side in out/release/."
    dependsOn(checkShieldyApk)
    doLast {
        val apk = releaseApk()
        val info = apkInfo(apk)
        // Git Bash on Windows; a bare "bash" there may be WSL's.
        val bash = if (!windows) "bash" else listOf(
            "C:/Program Files/Git/bin/bash.exe",
            "C:/Program Files/Git/usr/bin/bash.exe",
        ).firstOrNull { File(it).exists() } ?: "bash"
        val args = buildList {
            add(bash)
            add("build.sh")
            if (providers.gradleProperty("module.withX86").orNull == "true") add("--with-x86")
        }
        logger.lifecycle(run(args).trim())

        val version = file("module.prop").readLines().first { it.startsWith("version=") }.substringAfter('=')
        val zip = file("dnscrypt-proxy-android-$version.zip")
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() }
        val apkOut = File(outDir, "Shieldy-${info.name}.apk")
        apk.copyTo(apkOut)
        val zipOut = File(outDir, zip.name)
        zip.copyTo(zipOut)
        zip.delete()
        val sums = listOf(zipOut, apkOut).joinToString("\n", postfix = "\n") { "${sha256(it)}  ${it.name}" }
        File(outDir, "SHA256SUMS").writeText(sums)
        logger.lifecycle("\n${outDir.relativeTo(projectDir)}:\n$sums")
    }
}
