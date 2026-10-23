package com.skofqq.shieldy.data

import com.skofqq.shieldy.root.NoRootException
import com.skofqq.shieldy.root.ShellOutput
import com.skofqq.shieldy.root.ShellTimeoutException

/**
 * Something to show the user. Module output travels verbatim as [Raw]; the two
 * failures the app produces itself get a localized string instead of English text
 * from an exception.
 */
sealed interface UiMessage {

    /** Text from the module; [error] false for plain output such as the self-test. */
    data class Raw(val text: String, val error: Boolean = true) : UiMessage

    data object Timeout : UiMessage

    data object NoRoot : UiMessage

    /** A change was saved while the proxy is stopped: it takes effect at the next start. */
    data object SavedForNextStart : UiMessage

    companion object {
        fun of(error: Throwable): UiMessage = when (error) {
            is ShellTimeoutException -> Timeout
            is NoRootException -> NoRoot
            is ShellFailure -> of(error.output)
            else -> Raw(error.message ?: error.javaClass.simpleName)
        }

        fun of(output: ShellOutput): UiMessage =
            Raw(output.message.ifBlank { "exit code ${output.code}" })

        /** Returns a message when the command failed, or null when it went through. */
        fun ofFailure(result: Result<ShellOutput>): UiMessage? = result.fold(
            onSuccess = { output -> if (output.isSuccess) null else of(output) },
            onFailure = { error -> of(error) },
        )
    }
}
