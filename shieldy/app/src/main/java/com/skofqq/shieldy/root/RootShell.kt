package com.skofqq.shieldy.root

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of a single root command. A non-zero [code] is still a completed call. */
data class ShellOutput(
    val code: Int,
    val stdout: List<String>,
    val stderr: List<String>,
) {
    val isSuccess: Boolean get() = code == 0
    val out: String get() = stdout.joinToString("\n")
    val err: String get() = stderr.joinToString("\n")

    /** Whatever the module printed, preferring stderr because that is where it explains itself. */
    val message: String get() = err.ifBlank { out }.trim()
}

class NoRootException : Exception("Root access was not granted")
class ShellTimeoutException(command: String) : Exception("Command timed out: $command")

/**
 * Thin wrapper over libsu. The shell is created lazily, exactly once, and every
 * command is capped at [COMMAND_TIMEOUT_MS].
 */
class RootShell {

    private val mutex = Mutex()
    private var cached: Shell? = null

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            // No FLAG_REDIRECT_STDERR: stderr has to stay separate so we can show it verbatim.
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(SHELL_INIT_TIMEOUT_SECONDS)
        )
    }

    /** Returns the cached root shell, asking for root the first time only. */
    suspend fun shell(): Result<Shell> = mutex.withLock {
        cached?.let { existing ->
            if (existing.isRoot && existing.isAlive) return@withLock Result.success(existing)
            cached = null
        }
        val created = withContext(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(SHELL_INIT_TIMEOUT_SECONDS * 1000L) { Shell.getShell() }
            }
        }
        created.fold(
            onSuccess = { shell ->
                when {
                    shell == null -> Result.failure(NoRootException())
                    !shell.isRoot -> Result.failure(NoRootException())
                    else -> {
                        cached = shell
                        Result.success(shell)
                    }
                }
            },
            onFailure = { Result.failure(NoRootException()) },
        )
    }

    /** Drops the cached shell so the next call asks for root again. */
    suspend fun invalidate() = mutex.withLock {
        runCatching { cached?.close() }
        cached = null
    }

    suspend fun isRootAvailable(): Boolean = shell().isSuccess

    /**
     * Runs a single command. Arguments are quoted individually, never concatenated raw,
     * so nothing a user types can escape into the shell.
     */
    suspend fun exec(vararg args: String): Result<ShellOutput> = execLine(args.joinToString(" ") { quote(it) })

    /**
     * Runs a raw command line. Callers must have built it from trusted pieces only.
     */
    suspend fun execLine(commandLine: String): Result<ShellOutput> {
        val shell = shell().getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                runCatching {
                    val stdout = ArrayList<String>()
                    val stderr = ArrayList<String>()
                    val res = shell.newJob()
                        .add(commandLine)
                        .to(stdout, stderr)
                        .exec()
                    ShellOutput(res.code, stdout, stderr)
                }
            }
            result ?: Result.failure(ShellTimeoutException(commandLine))
        }
    }

    /**
     * Runs a long command (a module install) on a shell of its own, handing every
     * output line to [onLine] as it arrives. The shared shell stays free, so status
     * polling does not queue up behind it and time out.
     */
    suspend fun execStreaming(
        commandLine: String,
        timeoutMs: Long,
        onLine: (String) -> Unit,
    ): Result<ShellOutput> {
        // Root was granted for the shared shell; this only confirms it before forking another.
        shell().getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            val dedicated = runCatching {
                Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).build()
            }.getOrElse { return@withContext Result.failure(NoRootException()) }
            if (!dedicated.isRoot) {
                runCatching { dedicated.close() }
                return@withContext Result.failure(NoRootException())
            }
            val lines = ArrayList<String>()
            val sink = object : CallbackList<String>(Runnable::run) {
                override fun onAddElement(e: String) {
                    synchronized(lines) { lines.add(e) }
                    onLine(e)
                }
            }
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    runCatching {
                        // stderr goes into the same stream: installers interleave the two.
                        val res = dedicated.newJob().add("{ $commandLine\n} 2>&1").to(sink).exec()
                        ShellOutput(res.code, synchronized(lines) { lines.toList() }, emptyList())
                    }
                }
                result ?: Result.failure(ShellTimeoutException(commandLine))
            } finally {
                runCatching { dedicated.close() }
            }
        }
    }

    companion object {
        const val COMMAND_TIMEOUT_MS = 10_000L
        const val SHELL_INIT_TIMEOUT_SECONDS = 10L

        /** POSIX single-quote quoting: safe for every byte except the quote itself. */
        fun quote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"
    }
}
